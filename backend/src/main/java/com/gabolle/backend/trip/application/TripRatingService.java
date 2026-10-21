package com.gabolle.backend.trip.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.infra.TripRatingJpaEntity;
import com.gabolle.backend.trip.infra.TripRatingJpaRepository;

/**
 * 여행 별점(S15P21E201-1908). 여행 하나에 구성원마다 1~5 하나.
 *
 * <p>권한 판정은 {@link TripQueryService#get(String, String)} 에 맡긴다 — 구성원이 아니거나 지운
 * 여행이면 404 로 존재를 감춘다. 보기 전용 동행자도 매길 수 있다 — 함께 다녀온 사람의 평가다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripRatingService {

	private final TripQueryService tripQueryService;

	private final TripRatingJpaRepository repository;

	private final Clock clock;

	public TripRatingService(TripQueryService tripQueryService, TripRatingJpaRepository repository, Clock clock) {
		this.tripQueryService = tripQueryService;
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Rating find(String tripId, UUID requester) {
		this.tripQueryService.get(tripId, requester.toString());
		return read(UUID.fromString(tripId), requester);
	}

	/** 매기거나 다시 매긴다. 비었거나 범위 밖이면 {@link IllegalArgumentException}(400). */
	@Transactional
	public Rating rate(String tripId, UUID requester, Integer score) {
		if (score == null) {
			throw new IllegalArgumentException("별점(score)이 비었습니다.");
		}
		TripRatingJpaEntity.requireScore(score);
		this.tripQueryService.get(tripId, requester.toString());
		UUID trip = UUID.fromString(tripId);
		OffsetDateTime now = OffsetDateTime.now(this.clock);
		this.repository.findById(new TripRatingJpaEntity.Key(trip, requester)).ifPresentOrElse(
				existing -> existing.rescore(score, now),
				() -> this.repository.save(new TripRatingJpaEntity(trip, requester, score, now)));
		this.repository.flush();
		return read(trip, requester);
	}

	/** 내 별점을 지운다. 없던 것을 지워도 실패가 아니다 — 결과가 같다. */
	@Transactional
	public Rating clear(String tripId, UUID requester) {
		this.tripQueryService.get(tripId, requester.toString());
		UUID trip = UUID.fromString(tripId);
		this.repository.findById(new TripRatingJpaEntity.Key(trip, requester)).ifPresent(this.repository::delete);
		this.repository.flush();
		return read(trip, requester);
	}

	private Rating read(UUID trip, UUID requester) {
		Integer mine = this.repository.findById(new TripRatingJpaEntity.Key(trip, requester))
				.map(TripRatingJpaEntity::getScore).orElse(null);
		long count = this.repository.countByTrip(trip);
		Double average = count == 0 ? null : this.repository.averageByTrip(trip);
		return new Rating(mine, average, count);
	}

	/**
	 * @param myScore 내가 매긴 점수. 안 매겼으면 {@code null}
	 * @param average 구성원 평균. 아무도 안 매겼으면 {@code null}
	 */
	public record Rating(Integer myScore, Double average, long count) {
	}
}
