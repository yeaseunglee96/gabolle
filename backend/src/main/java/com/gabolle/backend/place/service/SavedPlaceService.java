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
 * 저장한 장소(하트) — S15P21E201-1013.
 *
 * <p>여행과 무관한 전역 목록이다. 여행별 후보 판단은 {@code RecommendationPlaceAction} 이
 * 따로 갖는다.
 */
@Service
@Profile({ "db", "dev" })
public class SavedPlaceService {

	/**
	 * 한 번에 돌려주는 최대 개수 — S15P21E201-1037.
	 *
	 * <p>상한을 두면 <b>알리는 칸을 함께</b> 둬야 한다({@code hasMore}). 상한만 두고 안
	 * 알리면 목록이 조용히 잘리고, 사용자에게는 「내가 누른 하트가 사라졌다」로 보인다.
	 * 같은 판단을 축제 목록과 일정 판 목록이 먼저 했다(S15P21E201-1011).
	 */
	public static final int MAX_ITEMS = 500;

	private final SavedPlaceRepository savedPlaceRepository;

	private final PlaceRepository placeRepository;

	/**
	 * 🔴 <b>{@code ObjectProvider} 인 이유 — 2026-09-16 CI 실측.</b>
	 *
	 * <p>처음에는 그냥 받았다. 그랬더니 {@code PlaceSliceApplication}(장소 도메인만 스캔하는
	 * 시험 컨텍스트)이 <b>통째로 안 떴다</b> — 그 슬라이스는 {@code com.gabolle.backend.common}
	 * 과 {@code .place} 만 스캔해서 {@code EventIngestService} 빈이 없다. 이벤트와 아무 상관
	 * 없는 장소 검사 <b>145건</b>이 한꺼번에 빨개졌다.
	 *
	 * <p>슬라이스의 스캔 범위를 넓히는 방법도 있지만, 그러면 {@code event} 와 {@code user} 의
	 * 빈·엔티티·리포지토리가 장소 검사에 전부 딸려 들어온다. <b>이 클래스 하나 때문에 남의
	 * 검사 환경을 넓히지 않는다.</b> 같은 판단을 {@code RecommendationService} 가 이미 했다.
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

	/**
	 * 내가 저장한 것. 상한까지만 돌려주고, 더 있으면 그 사실을 함께 알린다.
	 *
	 * <p>상한보다 하나 더 요청해서 더 있는지 본다 — 따로 개수를 세면 같은 표를 두 번 읽는다.
	 */
	@Transactional(readOnly = true)
	public Page list(UUID userId) {
		List<SavedPlace> found = this.savedPlaceRepository
				.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, MAX_ITEMS + 1));

		boolean hasMore = found.size() > MAX_ITEMS;
		return new Page(hasMore ? found.subList(0, MAX_ITEMS) : found, hasMore);
	}

	/**
	 * 하트를 켠다. <b>몇 번을 보내도 같다.</b>
	 *
	 * <p>2026-09-16 (S15P21E201-1037) — 그전에는 {@code exists} 로 보고 없으면 넣었다.
	 * 연타하면 두 요청이 둘 다 통과한 뒤 하나가 유일 제약에 걸려 500 이 나갔다. 지금은
	 * 넣기와 판정을 DB 한 문장에서 끝낸다 — {@link SavedPlaceRepository#insertIfAbsent} 참고.
	 *
	 * <p>장소가 없는지는 여전히 먼저 본다. 그것은 경쟁이 아니라 <b>잘못된 요청</b>이고,
	 * 화면이 404 를 보고 「기기에 남아 있던 옛 이름표」를 지우는 근거로 쓴다.
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
	 * 하트를 켠 것을 행동 신호로 남긴다 — S15P21E201-1080.
	 *
	 * <h2>🔴 이것이 없으면 취향 벡터에 행동이 한 건도 안 들어간다</h2>
	 *
	 * {@code EventType.PLACE_LIKE} 는 이미 취향 신호 목록에 있고 접기 배치도 날마다 돈다.
	 * 그런데 {@code event_outbox} 가 비어 있었다 — <b>부르는 자리가 없었기 때문이다.</b>
	 * 그래서 접기 결과가 늘 {@code rebuilt=0 watermarkAdvanced=N} 이었다. 표시만 움직이고
	 * 벡터 내용은 설문뿐이었다.
	 *
	 * <h2>🔴 실제로 켜진 경우에만 적는다 — {@code inserted == 1}</h2>
	 *
	 * 하트는 켜짐/꺼짐이라 <b>"두 번 켠 상태" 가 없다.</b> 연타하거나 앱이 재시도할 때마다
	 * 신호를 하나씩 더하면, 손가락이 빠른 사람의 취향이 그만큼 세게 반영된다 — 그건 취향이
	 * 아니라 <b>네트워크 사정</b>이다.
	 *
	 * <p>그 판정을 여기서 다시 하지 않고 {@code insertIfAbsent} 의 반환값을 쓴다. 판정이
	 * {@code ON CONFLICT DO NOTHING} 안에 있어서 <b>두 요청이 동시에 와도 한쪽만 1 을 받는다</b>
	 * (S15P21E201-1037). 자바에서 {@code exists} 로 다시 보면 그 사이가 벌어져, 막으려던
	 * 중복이 이벤트 쪽에서 되살아난다.
	 *
	 * <h2>끄는 것은 여기서 안 적는다</h2>
	 *
	 * 하트 해제는 {@code PLACE_DISLIKE} 가 아니다. "싫다" 와 "이제 관심 없다" 는 다른 사건이고,
	 * 섞으면 학습이 틀린 것을 배운다. 해제를 남길지는 별도 판단이다.
	 *
	 * <p>🔴 <b>개인화를 끈 사람은 저절로 빠진다.</b> {@code recordFromServer} 안의
	 * {@code collectsBehaviorOf} 가 거른다(S15P21E201-549) — 여기서 또 검사하면 같은 규칙이
	 * 두 곳에 생기고, 둘은 반드시 어긋난다.
	 *
	 * <p>🔴 {@code tripId} 와 {@code requestId} 가 {@code null} 인 것은 <b>모르는 것이지 빠뜨린
	 * 것이 아니다.</b> 이 경로({@code /api/v1/me/saved-places/{placeId}})는 여행 밖 화면에서도
	 * 불린다. {@code PLACE_LIKE} 의 축이 {@code USER} 인 이유가 그것이다(S15P21E201-735).
	 * 추천 카드에서 누른 하트를 노출과 잇는 것은 {@code requestId} 를 받는 경로가 생긴 뒤다.
	 */
	private void recordLike(UUID userId, UUID placeId) {
		// 🔴 빈이 없으면 안 적는다. 하트 자체는 이미 저장됐고, 신호 하나가 비는 것이
		//    저장을 실패시키는 것보다 낫다 — 이 경로는 사용자가 기다리는 화면이다.
		EventIngestService ingest = this.events.getIfAvailable();
		if (ingest == null) {
			return;
		}
		ingest.recordFromServer(UUID.randomUUID(), EventType.PLACE_LIKE, 1,
				userId, null, null, Map.of("placeId", placeId.toString()));
	}

	/**
	 * 하트를 끈다. 안 켜져 있던 것을 꺼도 성공이다 — 끄기의 결과는 「안 켜져 있음」이고
	 * 그것은 이미 참이다. 없다고 404 를 주면 화면이 두 번 누른 사용자에게 오류를 보여 준다.
	 */
	@Transactional
	public void remove(UUID userId, UUID placeId) {
		this.savedPlaceRepository.deleteByUserIdAndPlaceId(userId, placeId);
	}

	/**
	 * 잘라 온 목록과, 잘렸는지 여부.
	 *
	 * @param hasMore 상한에 걸려 <b>더 있는데 안 보냈다.</b> 이 칸이 없으면 부르는 쪽이
	 *     「상한에 걸린 것」과 「이게 전부인 것」을 구분할 수 없다
	 */
	public record Page(List<SavedPlace> items, boolean hasMore) {
	}
}
