package com.gabolle.backend.place.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.place.domain.SavedPlace;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.SavedPlaceRepository;

/**
 * 저장한 장소(하트). 여행과 무관한 전역 목록이다 — 여행별 후보 판단은
 * {@code RecommendationPlaceAction} 이 따로 갖는다.
 */
@Service
@Profile({ "db", "dev" })
public class SavedPlaceService {

	/**
	 * 한 번에 돌려주는 최대 개수. 상한에 걸렸다는 사실을 {@code hasMore} 로 반드시 함께
	 * 알린다 — 안 알리면 목록이 조용히 잘리고 사용자에게는 하트가 사라진 것으로 보인다.
	 */
	public static final int MAX_ITEMS = 500;

	private final SavedPlaceRepository savedPlaceRepository;

	private final PlaceRepository placeRepository;

	/**
	 * {@code ObjectProvider} 로 받는다. {@code PlaceSliceApplication} 은 {@code common} 과
	 * {@code place} 만 스캔해 {@code EventIngestService} 빈이 없고, 그냥 받으면 그 컨텍스트가
	 * 통째로 안 뜬다.
	 */
	private final ObjectProvider<EventIngestService> events;

	private final Clock clock;

	public SavedPlaceService(SavedPlaceRepository savedPlaceRepository, PlaceRepository placeRepository,
			ObjectProvider<EventIngestService> events, Clock clock) {
		this.savedPlaceRepository = savedPlaceRepository;
		this.placeRepository = placeRepository;
		this.events = events;
		this.clock = clock;
	}

	/** 상한보다 하나 더 요청해서 더 있는지 본다 — 따로 개수를 세면 같은 표를 두 번 읽는다. */
	@Transactional(readOnly = true)
	public Page list(UUID userId) {
		List<SavedPlace> found = this.savedPlaceRepository
				.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, MAX_ITEMS + 1));

		boolean hasMore = found.size() > MAX_ITEMS;
		return new Page(hasMore ? found.subList(0, MAX_ITEMS) : found, hasMore);
	}

	/**
	 * 하트를 켠다. 몇 번을 보내도 결과가 같다 — 넣기와 판정을 DB 한 문장에서 끝내므로
	 * ({@link SavedPlaceRepository#insertIfAbsent}) 연타해도 유일 제약에 걸리지 않는다.
	 *
	 * <p>장소가 없는 것은 경쟁이 아니라 잘못된 요청이라 404 를 던진다. 화면은 그것을 보고
	 * 기기에 남은 옛 항목을 지운다.
	 */
	@Transactional
	public void save(UUID userId, UUID placeId) {
		if (!this.placeRepository.existsById(placeId)) {
			throw new PlaceNotFoundException(placeId);
		}
		int inserted = this.savedPlaceRepository.insertIfAbsent(UUID.randomUUID(), userId, placeId,
				OffsetDateTime.now(this.clock));

		if (inserted == 1) {
			recordLike(userId, placeId);
		}
	}

	/**
	 * 하트를 켠 것을 취향 벡터용 행동 신호로 남긴다.
	 *
	 * <p>부르는 쪽이 {@code inserted == 1} 일 때만 부르는 것이 중요하다. 하트는 켜짐/꺼짐이라
	 * "두 번 켠 상태" 가 없어서, 연타나 재시도마다 신호를 더하면 네트워크 사정이 취향으로
	 * 기록된다. 그 판정은 {@code insertIfAbsent} 의 반환값을 쓴다 — 자바에서 {@code exists} 로
	 * 다시 보면 두 요청 사이가 벌어져 중복이 되살아난다.
	 *
	 * <p>하트 해제는 여기서 안 적는다. {@code PLACE_DISLIKE}("싫다")와 "이제 관심 없다" 는 다른
	 * 사건이라 섞으면 학습이 틀린 것을 배운다.
	 *
	 * <p>개인화를 끈 사람은 {@code recordFromServer} 안의 {@code collectsBehaviorOf} 가 거르므로
	 * 여기서 또 검사하지 않는다. 같은 규칙이 두 곳에 생기면 반드시 어긋난다.
	 *
	 * <p>{@code tripId}·{@code requestId} 가 {@code null} 인 것은 모르는 것이지 빠뜨린 것이
	 * 아니다 — 이 경로는 여행 밖 화면에서도 불린다.
	 */
	private void recordLike(UUID userId, UUID placeId) {
		// 빈이 없으면 안 적는다. 신호 하나가 비는 것이 하트 저장을 실패시키는 것보다 낫다.
		EventIngestService ingest = this.events.getIfAvailable();
		if (ingest == null) {
			return;
		}
		ingest.recordFromServer(UUID.randomUUID(), EventType.PLACE_LIKE, 1,
				userId, null, null, Map.of("placeId", placeId.toString()));
	}

	/** 안 켜져 있던 것을 꺼도 성공이다 — 404 를 주면 두 번 누른 사용자가 오류를 보게 된다. */
	@Transactional
	public void remove(UUID userId, UUID placeId) {
		this.savedPlaceRepository.deleteByUserIdAndPlaceId(userId, placeId);
	}

	/**
	 * @param hasMore 상한에 걸려 더 있는데 안 보냈다. 이 칸이 없으면 부르는 쪽이 상한에 걸린
	 *     것과 이게 전부인 것을 구분할 수 없다
	 */
	public record Page(List<SavedPlace> items, boolean hasMore) {
	}
}
