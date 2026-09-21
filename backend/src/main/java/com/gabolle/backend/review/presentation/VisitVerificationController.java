package com.gabolle.backend.review.presentation;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.review.application.VisitVerificationService;
import com.gabolle.backend.review.application.VisitVerificationService.VisitVerificationOutcome;
import com.gabolle.backend.review.presentation.dto.VisitVerificationRequest;
import com.gabolle.backend.review.presentation.dto.VisitVerificationResponse;

/**
 * 방문 인증.
 *
 * 사용자는 인증 principal 에서만 얻는다. {@code X-User-Id} 헤더를 쓰지 않는다.
 */
@RestController
@RequestMapping("/api/v1/places/{placeId}/visit-verifications")
@Profile({ "db", "dev" })
public class VisitVerificationController {

	private final VisitVerificationService visitVerificationService;

	public VisitVerificationController(VisitVerificationService visitVerificationService) {
		this.visitVerificationService = visitVerificationService;
	}

	@PostMapping
	public ApiResponse<VisitVerificationResponse> verify(@PathVariable UUID placeId,
			@Valid @RequestBody VisitVerificationRequest request, Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		UUID userId = AuthenticatedUsers.requireId(authentication);
		VisitVerificationOutcome outcome = this.visitVerificationService.verify(placeId, userId, request.lat(),
				request.lng(), request.accuracyM());
		return ApiResponse.success(VisitVerificationResponse.from(outcome), resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
