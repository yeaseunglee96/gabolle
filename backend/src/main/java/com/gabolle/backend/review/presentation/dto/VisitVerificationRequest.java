package com.gabolle.backend.review.presentation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 방문 인증 요청 — S15P21E201-279.
 *
 * <p>🔴 이 좌표는 컨트롤러를 지나 {@code VisitVerificationService.verify} 지역 변수로만
 * 쓰이고 저장되지 않는다. 이 record 자체도 요청 처리 한 번의 수명이고, 어딘가에 담겨
 * 오래 살아남지 않는다.
 */
public record VisitVerificationRequest(
		@NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
		@NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
		@NotNull @Min(0) Integer accuracyM) {
}
