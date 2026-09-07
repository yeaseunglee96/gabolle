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
 * 장소 리뷰 — S15P21E201-287 · -408.
 *
 * <h2>🔴 인증 여부는 요청이 아니라 이 클래스가 정한다</h2>
 *
 * {@link #write} 는 요청에서 인증 여부를 받지 않는다.
 * {@link PlaceVisitVerificationRepository#existsByUserIdAndPlaceId} 로 서버가 직접 조회해 정한다.
 * 요청이 정하게 하면 아무나 인증된 평가를 쓸 수 있다({@link PlaceReview#write} 의 javadoc 과 같은
 * 근거).
 *
 * <h2>미인증 리뷰도 받는다</h2>
 *
 * 위치 권한을 거부한 사람의 리뷰도 저장한다(거부하지 않는다) — 다만 {@code verified=false} 로
 * 남아 로컬 점수 계산에서 빠진다. {@link #list} 가 돌려주는 평균이 그 경계를 지킨다.
 *
 * <h2>🔴 로컬 점수는 여기서 계산해 저장하지 않는다</h2>
 *
 * {@code place_feature} 의 {@code LOCALITY_SCORE} 표식을 실제로 계산해 쓰는 것은 일정 생성
 * Epic(S15P21E201-122 · -131, 다른 담당자)의 몫이다. 이 클래스가 하는 일은 "인증된 평가만
 * 넣고 미인증은 뺀다" 는 <b>창구</b>를 만드는 것까지다 —
 * {@link PlaceReviewRepository#findVerifiedByPlaceId} 가 그 창구다. 여기서 점수를 계산해
 * {@code place_feature} 에 쓰면 그 티켓이 나중에 계산할 때 두 곳이 같은 값을 다르게 유지하게
 * 된다. {@link #list} 가 응답에 담는 평균은 <b>이 화면이 바로 쓰는 값일 뿐 저장하지 않으므로</b>
 * 이 경계를 넘지 않는다.
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
	 * 🔴 자기 자신을 지연 주입한다({@code @Lazy}). {@link #insertNew}·{@link #rewriteExisting} 를
	 * {@code this.insertNew(...)} 로 직접 부르면 스프링 AOP 프록시를 거치지 않아
	 * {@code @Transactional} 이 조용히 무시된다("자기 호출" 함정 — 프록시는 빈 <b>밖에서</b>
	 * 들어오는 호출만 가로챈다). 이 필드를 통해 불러야 프록시를 거치고, 아래 {@link #write} 의
	 * 동시성 처리가 실제로 두 개의 분리된 트랜잭션으로 돈다.
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
	 * <h2>🔴 선조회만으로 막지 않는다</h2>
	 *
	 * 존재 확인 후 삽입하는 순서만 믿으면, 같은 사람이 거의 동시에 두 번 보냈을 때 <b>둘 다</b>
	 * "없다" 를 보고 둘 다 삽입을 시도해 표의 UNIQUE 위반이 난다. 이 메서드는 그 경쟁을 겪는다.
	 *
	 * <p>여기서 <b>트랜잭션 하나로 둘 다 처리하지 않는다.</b> PostgreSQL 은 트랜잭션 안에서 한
	 * 문장이 실패하면 그 트랜잭션 전체가 "중단됨" 상태가 되어 롤백 전까지 이후 문장이 전부
	 * 실패한다(자바에서 예외를 잡아도 소용없다) — {@code JpaTripMembershipRepository} 의 javadoc
	 * 이 같은 함정을 기록해 두었다. 그래서 삽입 시도({@link #insertNew})를
	 * {@code REQUIRES_NEW} 로 <b>독립된 트랜잭션</b>에 두어, 그것이 충돌로 롤백돼도 이 메서드
	 * 자체는 멀쩡한 상태로 계속 진행해 다시 쓰기로 넘어갈 수 있게 한다.
	 *
	 * <p>🔴 네이티브 SQL({@code INSERT ... ON CONFLICT})은 쓰지 않는다.
	 * {@code docs/DB-STANDARD.md} 2절 — {@code hibernate.default_schema} 는 JPQL·Hibernate 가
	 * 생성한 SQL에만 적용되고 네이티브 SQL은 물려받지 않는다. 운영 schema({@code gabolle})가
	 * 아닌 연결 기본 schema({@code public})를 봐서 표를 못 찾는데, 테스트 DB는 표를
	 * {@code public}에 만들어 그 어긋남이 테스트로는 안 잡힌다. 그래서 순수 JPA 재시도로
	 * 푼다.
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
			// 동시에 두 번 보낸 경쟁 — 선조회는 통과했지만 그사이 다른 요청이 먼저 만들었다.
			// insertNew 는 REQUIRES_NEW 라 그 시도만 깔끔히 롤백됐다. 방금 커밋된 행을 다시
			// 읽어 덮어쓴다.
			PlaceReview created = this.reviewRepository.findByPlaceIdAndUserIdAndDeletedAtIsNull(placeId, userId)
					.orElseThrow(() -> conflict);
			return this.self.rewriteExisting(created.getPlaceReviewId(), scores, body, verified, region, now);
		}
	}

	/** 🔴 {@code REQUIRES_NEW} — 이 시도만의 독립 트랜잭션. {@link #write} javadoc 참고. */
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
	 * <h2>평균이 인증된 평가만으로 계산되는 이유</h2>
	 *
	 * {@link PlaceReviewRepository#findVerifiedByPlaceId} 를 쓴다 — 목록 조회
	 * ({@link PlaceReviewRepository#findByPlaceId})와 다른 메서드다. 같은 메서드를 쓰면 위치
	 * 권한 없이도 평균을 움직일 수 있게 되어 인증을 만든 의미가 사라진다.
	 *
	 * <p>인증된 평가가 하나도 없으면 평균은 {@code null} 이다. {@code 0} 을 쓰지 않는다 — 0 은
	 * 최하점과 구분이 안 된다.
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

	/** 리뷰 목록과 인증된 평가만의 평균. */
	public record ListResult(List<PlaceReview> reviews, Double averageScore) {
	}

	/** 리뷰를 쓰거나 조회하려는 장소가 없다 — 404. */
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
