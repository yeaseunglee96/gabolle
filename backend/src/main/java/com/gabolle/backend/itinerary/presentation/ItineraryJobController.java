package com.gabolle.backend.itinerary.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.itinerary.application.ItineraryRecalculationService;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryEditJobRequest;
import com.gabolle.backend.recommendation.application.RecommendationJobRunner;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.presentation.dto.RecommendationJobResponse;

import jakarta.validation.Valid;

/**
 * 장소 제외·재계산 Job 접수.
 * 이 경로도 계산을 기다리지 않는다. {@code 202} 로 작업 번호만 즉시 돌려주고 실제 재계산은
 * Worker 가 뒤에서 이어간다. 이 컨트롤러가 보장하는 것은 접수 전 검증뿐이다 — 권한이 없거나
 * 바탕 판이 낡았거나 대상 항목이 없으면 Job 을 아예 만들지 않는다.
 * {@code @Profile({"db","dev"})}·{@code @ConditionalOnBean(RecommendationJobRunner.class)} 인
 * 이유는 러너가 없는 배포에서 이 컨트롤러가 생기면 생성자 주입이 실패해 컨텍스트 전체가 못
 * 뜨기 때문이다.
 * 예외 번역은 {@code ItineraryExceptionHandler} 를 재사용하지 않는다. 그 핸들러는
 * {@code assignableTypes = ItineraryEditController.class} 로 좁혀져 있어 이 컨트롤러의 예외를
 * 못 잡는다. 대신 {@link ItineraryJobExceptionHandler} 를 둔다.
 */
@RestController
@RequestMapping("/api/v1/itineraries/{itineraryId}")
@Profile({ "db", "dev" })
@ConditionalOnBean(RecommendationJobRunner.class)
public class ItineraryJobController {

	private final ItineraryRecalculationService recalculationService;

	public ItineraryJobController(ItineraryRecalculationService recalculationService) {
		this.recalculationService = recalculationService;
	}

	/**
	 * 항목 하나를 빼고 그 날짜만 다시 계산하는 Job 을 접수한다.
	 * 뺄 항목은 URL 의 {@code itemId} 다 — 본문의 {@code fromItemId} 는 이 경로에서 안 쓴다.
	 */
	@PostMapping("/items/{itemId}/remove")
	public ResponseEntity<ApiResponse<RecommendationJobResponse>> removeItem(
			@PathVariable String itineraryId,
			@PathVariable String itemId,
			@Valid @RequestBody ItineraryEditJobRequest request,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();

		RecommendationJob job = this.recalculationService.removeItem(itineraryId, itemId,
				request.baseVersion(), request.operationalReason(), requester);

		return accepted(job);
	}

	/**
	 * 하루를 다시 계산하는 Job 을 접수한다.
	 * 어느 날인지는 두 가지로 말할 수 있다 — {@code fromItemId}(그 항목이 속한 날, 그 항목부터
	 * 뒤만) 또는 {@code dayIndex}(그 날 전체, 고정 항목만 남긴다). 둘 다 없으면 무엇을 다시
	 * 계산하라는 것인지 알 수 없으므로 400 이다 — 없는 항목을 찾는 404 와 다르다.
	 */
	@PostMapping("/recalculate")
	public ResponseEntity<ApiResponse<RecommendationJobResponse>> recalculate(
			@PathVariable String itineraryId,
			@Valid @RequestBody ItineraryEditJobRequest request,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		if (!request.hasFromItem() && request.dayIndex() == null) {
			throw new MissingRecalculationTargetException();
		}

		RecommendationJob job = request.hasFromItem()
				? this.recalculationService.recalculate(itineraryId, request.fromItemId(), request.baseVersion(), requester)
				: this.recalculationService.recalculateDay(itineraryId, request.dayIndex(), request.baseVersion(),
						requester);

		return accepted(job);
	}

	/** 재계산 요청에 {@code fromItemId} 도 {@code dayIndex} 도 없다 — 400. */
	public static class MissingRecalculationTargetException extends RuntimeException {

		public MissingRecalculationTargetException() {
			super("재계산할 대상이 없습니다 — fromItemId 또는 dayIndex 중 하나를 주세요.");
		}
	}

	/** {@code RecommendationJobController} 가 202 를 만드는 방식 그대로다. */
	private static ResponseEntity<ApiResponse<RecommendationJobResponse>> accepted(RecommendationJob job) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(ApiResponse.success(RecommendationJobResponse.of(job), "req_" + UUID.randomUUID()));
	}
}
