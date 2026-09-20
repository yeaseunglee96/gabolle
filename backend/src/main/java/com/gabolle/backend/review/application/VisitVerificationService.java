package com.gabolle.backend.review.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.GeoDistance;
import com.gabolle.backend.review.config.ReviewProperties;
import com.gabolle.backend.review.domain.PlaceVisitVerification;
import com.gabolle.backend.review.repository.PlaceVisitVerificationRepository;
import com.gabolle.backend.user.application.ConsentGuard;

/**
 * 방문 인증 판정.
 *
 * 받은 좌표는 이 클래스 밖으로 나가지 않는다. 저장도 로깅도 하지 않으며,
 * {@link PlaceVisitVerification#record} 는 좌표를 인자로도 받지 않는다. 디버깅 목적이더라도
 * 좌표를 로그에 찍는 줄을 추가하면 이 클래스의 존재 이유가 깨진다.
 *
 * 남기는 것은 판정 결과인 거리(m)뿐이다 — 그 값 하나로는 원래 좌표를 복원할 수 없다.
 */
@Service
@Profile({ "db", "dev" })
public class VisitVerificationService {

	private final PlaceRepository placeRepository;

	private final PlaceVisitVerificationRepository verificationRepository;

	private final ReviewProperties properties;

	private final ConsentGuard consentGuard;

	private final Clock clock;

	public VisitVerificationService(PlaceRepository placeRepository,
			PlaceVisitVerificationRepository verificationRepository, ReviewProperties properties,
			ConsentGuard consentGuard, Clock clock) {
		this.placeRepository = placeRepository;
		this.verificationRepository = verificationRepository;
		this.properties = properties;
		this.consentGuard = consentGuard;
		this.clock = clock;
	}

	/**
	 * 방문을 인증한다.
	 *
	 * 검사 순서가 중요하다. 정확도가 나쁘면 거리 계산 자체를 하지 않는다 — "판정을 안 했다" 와
	 * "판정했는데 멀다" 는 다르다. 오차 200m 인 좌표로 200m 밖이라고 답하면 잘못된 확신을 준다.
	 *
	 * @param userId 반드시 로그인된 사용자여야 한다
	 * @param accuracyM 기기가 보낸 위치 정확도(m). 숫자가 클수록 부정확하다
	 */
	@Transactional
	public VisitVerificationOutcome verify(UUID placeId, UUID userId, double lat, double lng, int accuracyM) {
		// 다른 검사와 달리 형식보다 동의를 먼저 본다. 형식을 먼저 보면 동의 없는 사람의 좌표가
		// 거리 계산까지 지난 뒤에 거절되어, 예외 메시지·스택·로그에 남을 자리가 그만큼 늘어난다.
		this.consentGuard.requirePreciseLocation(userId);

		Place place = this.placeRepository.findById(placeId)
				.orElseThrow(() -> new PlaceNotFoundException(placeId));
		if (!place.hasCoordinates()) {
			// 요청 모양은 맞지만 이 장소로는 거리를 잴 수 없다 — 400 이 아니라 422 다.
			throw new CoordinatesMissingException(placeId);
		}
		if (accuracyM > this.properties.getVisitVerificationAccuracyLimitM()) {
			return VisitVerificationOutcome.retryLowAccuracy();
		}

		double distance = GeoDistance.meters(place.getLat(), place.getLng(), lat, lng);
		int distanceM = (int) Math.round(distance);

		if (distanceM > this.properties.getVisitVerificationDistanceThresholdM()) {
			return VisitVerificationOutcome.tooFar(distanceM);
		}

		PlaceVisitVerification verification = PlaceVisitVerification.record(placeId, userId, distanceM,
				Instant.now(this.clock));
		this.verificationRepository.save(verification);
		return VisitVerificationOutcome.verified(distanceM);
	}

	/**
	 * {@link Status#LOW_ACCURACY} 일 때만 {@link #distanceM} 이 {@code null} 이다 — 거리를 재지
	 * 않았다는 사실 자체가 응답이 담아야 할 정보다.
	 */
	public record VisitVerificationOutcome(Status status, Integer distanceM) {

		public enum Status {
			VERIFIED, TOO_FAR, LOW_ACCURACY
		}

		public static VisitVerificationOutcome verified(int distanceM) {
			return new VisitVerificationOutcome(Status.VERIFIED, distanceM);
		}

		public static VisitVerificationOutcome tooFar(int distanceM) {
			return new VisitVerificationOutcome(Status.TOO_FAR, distanceM);
		}

		public static VisitVerificationOutcome retryLowAccuracy() {
			return new VisitVerificationOutcome(Status.LOW_ACCURACY, null);
		}
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

	/** 422 로 나간다. */
	public static class CoordinatesMissingException extends RuntimeException {

		private final UUID placeId;

		public CoordinatesMissingException(UUID placeId) {
			super("이 장소는 좌표가 없어 방문을 인증할 수 없습니다: " + placeId);
			this.placeId = placeId;
		}

		public UUID placeId() {
			return this.placeId;
		}
	}
}
