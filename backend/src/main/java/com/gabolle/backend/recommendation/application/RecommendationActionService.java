package com.gabolle.backend.recommendation.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.recommendation.domain.RecommendationPlaceAction;
import com.gabolle.backend.recommendation.repository.RecommendationPlaceActionRepository;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 추천 후보에 대한 담아두기·빼기를 서버에 남긴다 — S15P21E201-1013.
 *
 * <p>지금까지 이 판단은 기기에만 있었다. 기기를 바꾸면 사라졌다.
 *
 * <h2>🔴 권한 규칙이 하나다 — "그 여행의 참여자인가"</h2>
 *
 * 판단은 <b>여행별</b>이고 동행자가 함께 본다(2026-09-15 제품 결정). 공유는 "같이 일정짜기"
 * 를 따로 만들어서가 아니라 <b>이미 있는 여행 초대</b>로 이뤄진다 — 사람은 초대를 받아야
 * 참여자가 되고, 참여자면 읽고 쓸 수 있다.
 *
 * <p>그래서 역할 검사를 따로 짜지 않는다. {@link TripQueryService#get} 이 이미 "참여자가
 * 아니면 여행 자체가 없는 것처럼 404" 를 하고 있고, <b>초대 장치가 곧 권한 장치</b>다.
 * 추천 요청·조회가 지나는 관문과 같은 것이라 거절 모양도 같다 — 검사를 따로 만들면 두
 * 경로의 404 가 언젠가 갈라지고, 그 차이가 "있는데 너는 못 본다" 는 신호가 된다.
 *
 * <p>🔴 {@code @ConditionalOnBean(TripQueryService.class)} — {@code RecommendationJobRunner}
 * 와 같은 이유다. 추천 도메인만 스캔하는 테스트 슬라이스에는 {@code TripQueryService} 빈이
 * 없어서, 이 조건이 없으면 그 슬라이스를 쓰는 무관한 테스트까지 컨텍스트 로딩에서 깨진다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class RecommendationActionService {

	/**
	 * 한 번에 돌려주는 최대 개수 — S15P21E201-1037.
	 *
	 * <p>🔴 상한을 두면 <b>알리는 칸을 함께</b> 둬야 한다({@code hasMore}). 상한만 두고 안
	 * 알리면 목록이 조용히 잘리고, 동행자에게는 「담아 둔 곳이 사라졌다」로 보인다.
	 */
	public static final int MAX_ITEMS = 500;

	private final TripQueryService tripQueryService;

	private final RecommendationPlaceActionRepository repository;

	private final Clock clock;

	public RecommendationActionService(TripQueryService tripQueryService,
			RecommendationPlaceActionRepository repository, Clock clock) {
		this.tripQueryService = tripQueryService;
		this.repository = repository;
		this.clock = clock;
	}

	/**
	 * 이 여행의 판단 전부 — 동행자가 남긴 것도 함께 온다.
	 *
	 * @throws TripQueryService.TripNotFoundException 여행이 없거나 요청자가 그 여행의 참여자가 아니다
	 */
	@Transactional(readOnly = true)
	public Page list(String tripId, String userId) {
		this.tripQueryService.get(tripId, userId);

		List<RecommendationPlaceAction> found = this.repository
				.findByTripId(UUID.fromString(tripId), PageRequest.of(0, MAX_ITEMS + 1));

		boolean hasMore = found.size() > MAX_ITEMS;
		return new Page(hasMore ? found.subList(0, MAX_ITEMS) : found, hasMore);
	}

	/**
	 * 판단을 적는다. 이미 있으면 <b>바꾼다</b>(새로 만들지 않는다).
	 *
	 * <p>🔴 <b>같은 요청을 두 번 보내도 결과가 같다.</b> 화면의 하트는 사용자가 연타할 수
	 * 있고 통신이 끊기면 앱이 재시도한다 — "눌렀다" 를 더하는 방식이면 그때마다 행이 쌓이거나
	 * 상태가 뒤집힌다. 그래서 "이 장소의 판단은 이것이다" 를 통째로 적는 모양으로 뒀다.
	 *
	 * <p>동행자가 이미 정해 둔 판단도 바꿀 수 있다 — 함께 쓰는 값이라 그것이 기능이다.
	 * 대신 <b>마지막에 누가 정했는지</b>를 함께 적어, 바뀐 이유를 되짚을 수 있게 한다.
	 *
	 * @return 적히고 난 뒤의 판단
	 */
	@Transactional
	public RecommendationPlaceAction put(String tripId, String userId, String placeId,
			RecommendationPlaceAction.Action action) {
		UUID trip = UUID.fromString(tripId);
		UUID place = UUID.fromString(placeId);
		UUID decidedBy = UUID.fromString(userId);
		OffsetDateTime now = OffsetDateTime.now(this.clock);

		// 🔴 찾아보고 없으면 넣는 대신 한 문장으로 끝낸다 — 동행자 둘이 같은 후보를 동시에
		//    누르면 그 사이로 둘 다 들어가 하나가 유일 제약에 걸려 500 이 됐다.
		this.repository.upsert(UUID.randomUUID(), trip, place, action.name(), decidedBy, now);

		return this.repository.findByTripIdAndPlaceId(trip, place)
				.orElseThrow(() -> new WriteReadBackFailedException(
						"방금 적은 판단을 도로 읽지 못했다: tripId=" + tripId + " placeId=" + placeId));
	}

	/**
	 * 판단을 거둔다 — 하트를 다시 눌러 끈 것이다.
	 *
	 * <p>🔴 <b>없는 것을 지워도 성공이다.</b> 이미 지워진 뒤에 재시도가 도착하는 일이 흔하고,
	 * 그때 404 를 내면 화면은 "지워졌는데 못 지웠다고 한다" 를 그린다. 지우기의 결과는
	 * "그 판단이 없는 상태" 이고 그건 두 경우 모두 같다.
	 *
	 * <p>동행자가 정해 둔 판단도 거둘 수 있다 — {@link #put} 과 같은 이유다.
	 */
	@Transactional
	public void remove(String tripId, String userId, String placeId) {
		this.tripQueryService.get(tripId, userId);
		this.repository.deleteByTripIdAndPlaceId(UUID.fromString(tripId), UUID.fromString(placeId));
	}

	/**
	 * 잘라 온 판단 목록과, 잘렸는지 여부.
	 *
	 * @param hasMore 🔴 상한에 걸려 <b>더 있는데 안 보냈다</b>. 이 칸이 없으면 부르는 쪽이
	 *     「잘린 것」과 「이게 전부인 것」을 구분할 수 없다
	 */
	public record Page(List<RecommendationPlaceAction> items, boolean hasMore) {
	}

	/**
	 * 적은 직후 그 행을 도로 못 읽었다 — 일어나면 안 되는 일이다.
	 *
	 * <p>🔴 {@code IllegalArgumentException} 이 아닌 이유는 그것이
	 * {@code RecommendationJobExceptionHandler} 에서 400 이 되기 때문이다. 이것은 부르는 쪽
	 * 잘못이 아니라 우리 쪽 불변식이 깨진 것이라 500 이 맞다.
	 */
	public static class WriteReadBackFailedException extends RuntimeException {

		public WriteReadBackFailedException(String message) {
			super(message);
		}
	}
}
