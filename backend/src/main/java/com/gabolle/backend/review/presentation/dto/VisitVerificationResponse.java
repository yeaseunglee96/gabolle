package com.gabolle.backend.review.presentation.dto;

import com.gabolle.backend.review.application.VisitVerificationService.VisitVerificationOutcome;

/**
 * 방문 인증 응답.
 *
 * @param verified 인증됐는가
 * @param distanceM 판정에 쓴 거리(m). {@code status} 가 {@code LOW_ACCURACY} 면 거리를 재지
 *        않았으므로 {@code null} 이다 — "멀어서 거절" 과 "정확도가 나빠 재지도 않음" 을
 *        구분해야 한다
 * @param status {@code VERIFIED} · {@code TOO_FAR} · {@code LOW_ACCURACY}
 * @param message 화면에 그대로 보여줄 수 있는 한국어 문장
 */
public record VisitVerificationResponse(boolean verified, Integer distanceM, String status, String message) {

	public static VisitVerificationResponse from(VisitVerificationOutcome outcome) {
		return switch (outcome.status()) {
			case VERIFIED -> new VisitVerificationResponse(true, outcome.distanceM(), "VERIFIED", "방문이 인증되었습니다.");
			case TOO_FAR -> new VisitVerificationResponse(false, outcome.distanceM(), "TOO_FAR",
					"장소와 너무 멀리 떨어져 있어 인증할 수 없습니다.");
			case LOW_ACCURACY -> new VisitVerificationResponse(false, null, "LOW_ACCURACY",
					"위치 정확도가 낮아 다시 시도해 주세요.");
		};
	}
}
