package com.gabolle.backend.place.api;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.place.service.OriginSearchService;

/**
 * 출발지 검색 API. {@code GET /api/v1/origins?query=서면역&limit=10}.
 *
 * <p>대체 목록이 {@code PlaceRepository} 를 쓰므로 {@code no-db} 프로필에서는 뜰 수 없다 —
 * {@code OriginSearchService} 와 같은 프로필({@code db}, {@code dev})만 연다.
 */
@RestController
@Profile({"db", "dev"})
public class OriginController {

	private final OriginSearchService originSearchService;

	public OriginController(OriginSearchService originSearchService) {
		this.originSearchService = originSearchService;
	}

	@GetMapping("/api/v1/origins")
	public ApiResponse<OriginSearchResponse> search(@RequestParam String query,
			@RequestParam(defaultValue = "10") int limit,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		OriginSearchResponse response = this.originSearchService.search(query, limit);
		return ApiResponse.success(response, resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
