package com.gabolle.backend.place.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.place.domain.Place;
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
		List<SavedPlace> items = hasMore ? found.subList(0, MAX_ITEMS) : found;
		// 장소 요약은 한 번에 모아 읽는다(S15P21E201-1971) — 저장마다 따로 읽으면 화면에서 없앤 N+1 이 서버 안으로 옮겨 올 뿐이다.
		// 장소 행이 사라진 저장은 지도에 없을 뿐 목록에서 빼지 않는다 — 화면이 번호로 상세를 불러 404 를 보고 정리한다.
		Map<UUID, Place> places = items.isEmpty() ? Map.of()
				: this.placeRepository.findAllById(items.stream().map(SavedPlace::getPlaceId).toList()).stream()
						.collect(Collectors.toMap(Place::getPlaceId, Function.identity(), (a, b) -> a));
		return new Page(items, hasMore, places);
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
	 * <p>🔴 하트 해제는 {@link #recordLikeRemoved} 가 따로 적는다 (S15P21E201-1506). 예전에는
	 * 아예 안 적었는데, 이유는 {@code PLACE_DISLIKE}("싫다")와 "이제 관심 없다" 가 다른
	 * 사건이라 섞으면 학습이 틀린 것을 배우기 때문이었다. 그 판단은 옳았고 — 빠뜨린 것이
	 * 아니라 <b>적을 칸이 없었다.</b> 이제 {@code PLACE_LIKE_REMOVED} 가 생겨서 적는다.
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
		int deleted = this.savedPlaceRepository.deleteByUserIdAndPlaceId(userId, placeId);

		// 🔴 실제로 꺼졌을 때만 적는다. 위 주석대로 「안 켜져 있던 것을 끈 요청」도 성공으로
		//    받으므로, 지운 행 수를 안 보면 두 번 누르기와 재시도가 전부 취향 신호가 된다.
		//    save() 가 insertIfAbsent 의 반환값을 보는 것과 같은 규칙이다.
		if (deleted > 0) {
			recordLikeRemoved(userId, placeId);
		}
	}

	/**
	 * 하트를 끈 것을 행동 신호로 남긴다 (S15P21E201-1506).
	 *
	 * <p>🔴 이것은 「싫다」가 아니라 <b>「안 누른 상태로 되돌리기」</b>다. 되돌린 뒤에는 하트를
	 * 아예 안 누른 사람과 같아져야 한다 — 그 판정은 {@code BehaviorTasteFolder} 가 한다.
	 * 여기서는 사실만 적는다.
	 *
	 * <p>{@link #recordLike} 와 같은 규칙을 따른다 — 빈이 없으면 안 적고, 개인화를 껐는지는
	 * {@code recordFromServer} 안에서 거르므로 여기서 또 검사하지 않는다.
	 */
	private void recordLikeRemoved(UUID userId, UUID placeId) {
		EventIngestService ingest = this.events.getIfAvailable();
		if (ingest == null) {
			return;
		}
		ingest.recordFromServer(UUID.randomUUID(), EventType.PLACE_LIKE_REMOVED, 1,
				userId, null, null, Map.of("placeId", placeId.toString()));
	}

	/**
	 * @param hasMore 상한에 걸려 더 있는데 안 보냈다. 이 칸이 없으면 부르는 쪽이 상한에 걸린
	 *     것과 이게 전부인 것을 구분할 수 없다
	 * @param places 저장한 장소의 행. 장소 번호로 찾는다 — 행이 사라진 저장은 여기에 없다
	 */
	public record Page(List<SavedPlace> items, boolean hasMore, Map<UUID, Place> places) {

		public Page(List<SavedPlace> items, boolean hasMore) {
			this(items, hasMore, Map.of());
		}
	}
}
