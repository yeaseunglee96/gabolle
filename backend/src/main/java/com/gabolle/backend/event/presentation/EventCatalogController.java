package com.gabolle.backend.event.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.presentation.dto.EventCatalogEntry;

/**
 * 이벤트 사전 조회. 수집 API 와 달리 {@code no-db} 프로필에도 있다 — {@link EventType} 은
 * 열거값이라 DB 가 필요 없고, 사전과 실제 코드가 어긋났는지를 아무 환경에서나 볼 수 있어야 한다.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventCatalogController {

	/**
	 * 정의된 이벤트 종류 전부. M1 필수 여부로 거르지 않는다 — 일부만 보여주면 이 응답 자체가
	 * 새로운 불일치의 원인이 된다.
	 */
	@GetMapping("/catalog")
	public ApiResponse<List<EventCatalogEntry>> catalog(
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		List<EventCatalogEntry> entries = List.of(EventType.values()).stream()
				.map(EventCatalogEntry::from)
				.toList();

		return ApiResponse.success(entries, resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
