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
 * 방문 인증 판정 — S15P21E201-279.
 *
 * <h2>🔴 이 클래스의 핵심은 거리 계산이 아니라 좌표를 남기지 않는 것이다</h2>
 *
 * 요청으로 받은 {@code lat}·{@code lng} 는 {@link #verify} 메서드 지역 변수로만 존재한다.
 * 이 메서드가 끝나면 그 값을 담을 자리가 코드 어디에도 없다 — {@link PlaceVisitVerification#record}
 * 가 좌표를 인자로도 받지 않기 때문이다. <b>로그에도 남기지 않는다</b> — 이 클래스 어디에도
 * {@code log.debug(..., lat, lng)} 같은 자리를 만들지 않은 것이 그 약속이다. 나중에 누가
 * "디버깅에 필요하다" 며 좌표를 로그로 찍는 줄을 추가하려 한다면, 그 줄이 이 클래스의 존재
 * 이유를 깬다는 것을 이 주석이 알려준다.
 *
 * <p>남기는 것은 판정 결과인 거리(m)뿐이다. 결과는 위치가 아니다 — 그 값 하나로는 원래
 * 좌표를 복원할 수 없다.
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
	 * <p>순서가 중요하다. 정확도부터 본다 — 정확도가 나쁘면 <b>거리 계산 자체를 하지 않는다.</b>
	 * "판정을 안 한다" 를 "판정했는데 거절했다" 와 구분하기 위해서다. 오차가 200m 인 좌표로
	 * 200m 밖이라고 답하면, 실제로는 안에 있었을 수도 있는데 잘못된 확신을 준다.
	 *
	 * @param placeId 인증할 장소
	 * @param userId 인증하는 사람 — 반드시 인증(로그인)된 사용자여야 한다. 컨트롤러가
	 *        {@code AuthenticatedUsers.requireId} 로 얻어 넘긴다
	 * @param lat 기기가 보낸 위도. 🔴 이 메서드 밖으로 나가지 않는다
	 * @param lng 기기가 보낸 경도. 🔴 이 메서드 밖으로 나가지 않는다
	 * @param accuracyM 기기가 보낸 위치 정확도(m). 숫자가 클수록 부정확하다
	 */
	@Transactional
	public VisitVerificationOutcome verify(UUID placeId, UUID userId, double lat, double lng, int accuracyM) {
		// 🔴 좌표를 보기 전에 동의를 본다 — S15P21E201-549 후속.
		//
		//    이 검사만 순서가 거꾸로다(다른 검사는 형식 → 동의 순인데 여기는 동의가 먼저다).
		//    이유는 여기서 다루는 값이 <b>기기의 현재 좌표</b>이기 때문이다. 형식을 먼저 보면
		//    동의 없는 사람의 좌표가 이미 이 메서드 안에 들어와 거리 계산까지 지난 뒤에
		//    거절된다 — 저장은 안 되지만 예외 메시지·스택·로그에 남을 자리가 그만큼 늘어난다.
		//
		//    docs/recommendation-data-collection-p0.md 11.4:
		//    "정밀 위치 별도 동의가 없으면 수집하지 않는다",
		//    "위치 미동의 사용자의 방문 여부를 추측해서 채우지 않는다".
		//    이 메서드가 바로 그 "방문 여부를 채우는" 자리다.
		this.consentGuard.requirePreciseLocation(userId);

		Place place = this.placeRepository.findById(placeId)
				.orElseThrow(() -> new PlaceNotFoundException(placeId));
		if (!place.hasCoordinates()) {
			// 요청 모양은 맞지만 이 장소로는 거리를 잴 수 없다 — 400(요청이 틀렸다)이 아니라
			// 422(요청은 맞는데 처리할 수 없다)다.
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
	 * 판정 결과. 세 갈래뿐이다.
	 *
	 * <p>{@link Status#LOW_ACCURACY} 는 {@link #distanceM} 이 {@code null} 이다 — 그 경우
	 * 거리를 재지 않았다는 사실 자체가 응답이 담아야 할 정보다("잰 결과 멀다" 와 "아예 재지
	 * 않았다" 를 구분해야 한다).
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

	/** 인증하려는 장소가 없다 — 404. */
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

	/** 장소에 좌표가 없어 거리를 잴 수 없다 — 422. */
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
