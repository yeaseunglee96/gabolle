package com.gabolle.backend.recommendation.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
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
 * <p>🔴 <b>소유권 검사를 새로 짜지 않고 {@link TripQueryService#get} 을 지난다.</b>
 * 추천 요청·조회가 이미 지나는 관문이라 거절 모양이 같다 — 검사를 따로 만들면 두 경로의
 * 404 가 언젠가 갈라지고, 그 차이가 "있는데 너는 못 본다" 는 신호가 된다.
 *
 * <p>🔴 {@code @ConditionalOnBean(TripQueryService.class)} — {@code RecommendationJobRunner}
 * 와 같은 이유다. 추천 도메인만 스캔하는 테스트 슬라이스에는 {@code TripQueryService} 빈이
 * 없어서, 이 조건이 없으면 그 슬라이스를 쓰는 무관한 테스트까지 컨텍스트 로딩에서 깨진다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class RecommendationActionService {

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
	 * 이 여행에서 내가 내린 판단 전부.
	 *
	 * @throws TripQueryService.TripNotFoundException 여행이 없거나 요청자가 그 여행의 회원이 아니다
	 */
	@Transactional(readOnly = true)
	public List<RecommendationPlaceAction> list(String tripId, String userId) {
		this.tripQueryService.get(tripId, userId);
		return this.repository.findByUserIdAndTripId(UUID.fromString(userId), UUID.fromString(tripId));
	}

	/**
	 * 판단을 적는다. 이미 있으면 <b>바꾼다</b>(새로 만들지 않는다).
	 *
	 * <p>🔴 <b>같은 요청을 두 번 보내도 결과가 같다.</b> 화면의 하트는 사용자가 연타할 수
	 * 있고 통신이 끊기면 앱이 재시도한다 — "눌렀다" 를 더하는 방식이면 그때마다 행이 쌓이거나
	 * 상태가 뒤집힌다. 그래서 "이 장소의 판단은 이것이다" 를 통째로 적는 모양으로 뒀다.
	 *
	 * @return 적히고 난 뒤의 판단
	 */
	@Transactional
	public RecommendationPlaceAction put(String tripId, String userId, String placeId,
			RecommendationPlaceAction.Action action) {
		this.tripQueryService.get(tripId, userId);

		UUID user = UUID.fromString(userId);
		UUID trip = UUID.fromString(tripId);
		UUID place = UUID.fromString(placeId);
		OffsetDateTime now = OffsetDateTime.now(this.clock);

		return this.repository.findByUserIdAndTripIdAndPlaceId(user, trip, place)
				.map(existing -> {
					existing.changeTo(action, now);
					return existing;
				})
				.orElseGet(() -> this.repository.save(
						RecommendationPlaceAction.of(UUID.randomUUID(), user, trip, place, action, now)));
	}

	/**
	 * 판단을 거둔다 — 하트를 다시 눌러 끈 것이다.
	 *
	 * <p>🔴 <b>없는 것을 지워도 성공이다.</b> 이미 지워진 뒤에 재시도가 도착하는 일이 흔하고,
	 * 그때 404 를 내면 화면은 "지워졌는데 못 지웠다고 한다" 를 그린다. 지우기의 결과는
	 * "그 판단이 없는 상태" 이고 그건 두 경우 모두 같다.
	 */
	@Transactional
	public void remove(String tripId, String userId, String placeId) {
		this.tripQueryService.get(tripId, userId);
		this.repository.deleteByUserIdAndTripIdAndPlaceId(UUID.fromString(userId), UUID.fromString(tripId),
				UUID.fromString(placeId));
	}
}
