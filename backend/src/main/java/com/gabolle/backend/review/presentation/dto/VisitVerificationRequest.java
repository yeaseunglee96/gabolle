package com.gabolle.backend.review.presentation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 여기 담긴 좌표는 요청 처리 중에만 쓰이고 저장되지 않는다. */
public record VisitVerificationRequest(
		@NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
		@NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
		@NotNull @Min(0) Integer accuracyM) {
}
