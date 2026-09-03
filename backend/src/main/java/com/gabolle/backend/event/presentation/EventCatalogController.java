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
 * 이벤트 사전 조회 — S15P21E201-352 작업 내용 4번.
 *
 * <p>🔴 <b>{@code no-db} 프로필에도 있다.</b> {@link EventType} 은 열거값이라 DB 도
 * {@code OutboxService} 도 필요 없다. 수집 API({@link EventIngestController})는
 * DB 프로필에서만 뜨지만, "무슨 이벤트가 있는지" 를 알려주는 이 조회는 그 제약을 받지
 * 않는다 — FE·분석 쪽이 서버를 db 프로필로 안 띄운 환경에서도 사전을 확인할 수 있어야
 * {@code -542} 8.1 목록과 실제 코드가 어긋났는지를 아무 환경에서나 검증할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventCatalogController {

	/**
	 * 실제로 정의된 이벤트 종류 전부를 내보낸다.
	 *
	 * <p>🔴 M1 필수 여부로 거르지 않는다. 후속 마일스톤 이벤트까지 전부 보여줘야
	 * "지금 코드가 아는 이벤트 종류의 전체 목록" 이라는 뜻이 선다 — 일부만 보여주면
	 * 이 응답 자체가 새로운 불일치의 원인이 된다.
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
