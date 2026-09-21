package com.gabolle.backend.review.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.review.config.ReviewProperties;
import com.gabolle.backend.review.domain.PlaceReview;
import com.gabolle.backend.review.repository.PlaceReviewRepository;
import com.gabolle.backend.review.repository.PlaceVisitVerificationRepository;

/**
 * 장소 리뷰.
 *
 * 인증 여부는 요청에서 받지 않고 서버가
 * {@link PlaceVisitVerificationRepository#existsByUserIdAndPlaceId} 로 직접 정한다. 요청이
 * 정하게 하면 아무나 인증된 평가를 쓸 수 있다.
 *
 * 미인증 리뷰도 저장하되 {@code verified=false} 로 남아 로컬 점수 계산에서 빠진다.
 *
 * 로컬 점수({@code place_feature} 의 {@code LOCALITY_SCORE})는 여기서 계산해 저장하지 않는다.
 * 계산은 일정 생성 쪽 몫이고, 이 클래스는 인증된 평가만 내주는 창구
 * ({@link PlaceReviewRepository#findVerifiedByPlaceId})까지만 만든다. 여기서 계산해 저장하면
 * 같은 값을 두 곳이 서로 다르게 유지하게 된다.
 */
@Service
@Profile({ "db", "dev" })
public class PlaceReviewService {

	private final PlaceRepository placeRepository;

	private final PlaceReviewRepository reviewRepository;

	private final PlaceVisitVerificationRepository verificationRepository;

	private final ReviewProperties properties;

	private final Clock clock;

	/**
	 * 자기 자신을 지연 주입한다. {@code this.insertNew(...)} 로 직접 부르면 스프링 AOP 프록시를
	 * 거치지 않아 {@code @Transactional} 이 조용히 무시된다. 이 필드를 통해 불러야 {@link #write}
	 * 의 동시성 처리가 실제로 분리된 두 트랜잭션으로 돈다.
	 */
	private final PlaceReviewService self;

	public PlaceReviewService(PlaceRepository placeRepository, PlaceReviewRepository reviewRepository,
			PlaceVisitVerificationRepository verificationRepository, ReviewProperties properties, Clock clock,
			@Lazy PlaceReviewService self) {
		this.placeRepository = placeRepository;
		this.reviewRepository = reviewRepository;
		this.verificationRepository = verificationRepository;
		this.properties = properties;
		this.clock = clock;
		this.self = self;
	}

	/**
	 * 평가를 쓰거나 다시 쓴다. 다시 쓰면 덮어쓴다(표의 {@code UNIQUE (place_id, user_id)} 와
	 * 짝이다).
	 *
	 * 선조회만으로는 막지 못한다 — 같은 사람이 거의 동시에 두 번 보내면 둘 다 "없다" 를 보고
	 * 삽입을 시도해 UNIQUE 위반이 난다. PostgreSQL 은 한 문장이 실패하면 트랜잭션 전체가
	 * 중단 상태가 되어 예외를 잡아도 이후 문장이 전부 실패하므로, 삽입 시도를
	 * {@code REQUIRES_NEW} 독립 트랜잭션에 둔다. 그래야 충돌로 롤백돼도 이 메서드는 그대로
	 * 진행해 다시 쓰기로 넘어갈 수 있다.
	 *
	 * 네이티브 SQL({@code INSERT ... ON CONFLICT})은 쓰지 않는다.
	 * {@code hibernate.default_schema} 는 네이티브 SQL에 적용되지 않아 운영 schema 대신
	 * {@code public} 을 보는데, 테스트 DB는 표를 {@code public} 에 만들어 그 어긋남이 테스트로
	 * 안 잡힌다. 자세한 것은 {@code docs/DB-STANDARD.md} 2절.
	 */
	public PlaceReview write(UUID placeId, UUID userId, PlaceReview.Scores scores, String body, String region) {
		ensurePlaceExists(placeId);
		boolean verified = this.verificationRepository.existsByUserIdAndPlaceId(userId, placeId);
		Instant now = Instant.now(this.clock);

		Optional<PlaceReview> existing = this.reviewRepository.findByPlaceIdAndUserIdAndDeletedAtIsNull(placeId,
				userId);
		if (existing.isPresent()) {
			return this.self.rewriteExisting(existing.get().getPlaceReviewId(), scores, body, verified, region, now);
		}
		try {
			return this.self.insertNew(placeId, userId, scores, body, verified, region, now);
		}
		catch (DataIntegrityViolationException conflict) {
			// 선조회는 통과했지만 그사이 다른 요청이 먼저 만들었다. 방금 커밋된 행을 다시 읽어 덮어쓴다.
			PlaceReview created = this.reviewRepository.findByPlaceIdAndUserIdAndDeletedAtIsNull(placeId, userId)
					.orElseThrow(() -> conflict);
			return this.self.rewriteExisting(created.getPlaceReviewId(), scores, body, verified, region, now);
		}
	}

	/** 이 시도만의 독립 트랜잭션이어야 한다 — 이유는 {@link #write} javadoc. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected PlaceReview insertNew(UUID placeId, UUID userId, PlaceReview.Scores scores, String body,
			boolean verified, String region, Instant now) {
		PlaceReview review = PlaceReview.write(placeId, userId, scores, body, verified, region, now);
		return this.reviewRepository.saveAndFlush(review);
	}

	@Transactional
	protected PlaceReview rewriteExisting(UUID reviewId, PlaceReview.Scores scores, String body, boolean verified,
			String region, Instant now) {
		PlaceReview review = this.reviewRepository.findById(reviewId)
				.orElseThrow(() -> new IllegalStateException("방금 찾은 리뷰가 사라졌습니다: " + reviewId));
		review.rewrite(scores, body, verified, region, now);
		return this.reviewRepository.save(review);
	}

	/**
	 * 리뷰 목록과, 인증된 평가만으로 낸 평균.
	 *
	 * 평균은 목록과 다른 조회({@link PlaceReviewRepository#findVerifiedByPlaceId})를 쓴다. 같은
	 * 조회를 쓰면 위치 권한 없이도 평균을 움직일 수 있다.
	 *
	 * 인증된 평가가 하나도 없으면 평균은 {@code null} 이다. 0 은 최하점과 구분이 안 된다.
	 */
	@Transactional(readOnly = true)
	public ListResult list(UUID placeId) {
		ensurePlaceExists(placeId);
		List<PlaceReview> reviews = this.reviewRepository.findByPlaceId(placeId,
				Limit.of(this.properties.getReviewListMaxSize()));
		List<PlaceReview> verifiedReviews = this.reviewRepository.findVerifiedByPlaceId(placeId);
		return new ListResult(reviews, averageOf(verifiedReviews));
	}

	private Double averageOf(List<PlaceReview> verifiedReviews) {
		List<Short> scores = new ArrayList<>();
		for (PlaceReview review : verifiedReviews) {
			addIfPresent(scores, review.getFoodScore());
			addIfPresent(scores, review.getPriceScore());
			addIfPresent(scores, review.getAccessibilityScore());
			addIfPresent(scores, review.getOnsiteScore());
		}
		if (scores.isEmpty()) {
			return null;
		}
		double sum = 0;
		for (short score : scores) {
			sum += score;
		}
		return sum / scores.size();
	}

	private void addIfPresent(List<Short> target, Short value) {
		if (value != null) {
			target.add(value);
		}
	}

	private void ensurePlaceExists(UUID placeId) {
		if (!this.placeRepository.existsById(placeId)) {
			throw new PlaceNotFoundException(placeId);
		}
	}

	public record ListResult(List<PlaceReview> reviews, Double averageScore) {
	}

	/** 404 로 나간다. */
	public static class PlaceNotFoundException extends RuntimeException {

		private final UUID placeId;

		public PlaceNotFoundException(UUID placeId) {
			super("장소를 찾을 수 없습니다: " + placeId);
			this.placeId = placeId;
		}

		public UUID placeId() {
			return this.placeId;
		}
	}
}
