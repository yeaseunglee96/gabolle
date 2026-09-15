package com.gabolle.backend.event.presentation;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.event.application.AnalyticsQueryService;
import com.gabolle.backend.event.presentation.dto.AnalyticsKpiResponse;

/**
 * 지표 조회 — S15P21E201-160 작업 내용 5번.
 *
 * <p>🔴 이 응답에 있는 값의 범위는 {@link AnalyticsQueryService} 주석에 적었다 — 사업
 * KPI 를 아직 안 담는다.
 *
 * <h2>🔴 2026-09-15 — 운영자 전용으로 옮겼다 (S15P21E201-1010)</h2>
 *
 * 이 자리의 예전 주석은 <i>"이 저장소에는 관리자 역할이 없다 … 로그인만 하면 볼 수 있다"</i>
 * 였다. <b>둘 다 더는 사실이 아니다.</b> 운영자 역할은 {@code -686} 이 만들었고, 그 사이
 * <b>익명 출입증</b>({@code POST /api/v1/auth/anonymous})이 생겨서 "로그인만 하면" 이
 * <b>"아무나"</b> 와 같은 말이 됐다 — 이 메서드는 {@code Authentication} 을 아예 받지 않아
 * 익명 출입증 하나로 이벤트 적재 건수·추천 작업 건수가 그대로 읽혔다.
 *
 * <p>내부 운영 숫자는 서비스 규모의 단서다. 지금 막는 이유는 <b>아직 아무도 안 부르기
 * 때문</b>이다(프론트 호출 0건, 2026-09-15 감사) — 화면이 붙은 뒤에는 막기 어려워진다.
 *
 * <h2>🔴 인가는 이 컨트롤러가 하지 않는다 — 경로 앞자리가 한다</h2>
 * 이 저장소는 메서드 보안({@code @EnableMethodSecurity})이 <b>꺼져 있어서</b>
 * {@code @PreAuthorize} 를 붙이면 컴파일도 되고 리뷰에서도 "막혀 있다" 로 보이지만 실제로는
 * <b>조용히 무시된다.</b> 그래서 막는 것은 {@code SecurityConfig} 의
 * {@code requestMatchers("/api/v1/admin/**").hasRole("ADMIN")} 한 줄뿐이고,
 * <b>이 클래스가 {@code /api/v1/admin/} 아래 있다는 것 자체가 유일한 잠금장치다.</b>
 * 경로를 옮기면 잠금이 풀린다 — {@code AdminModerationController} 와 같은 구조다.
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@Profile({ "db", "dev" })
public class AnalyticsController {

	private final AnalyticsQueryService service;

	public AnalyticsController(AnalyticsQueryService service) {
		this.service = service;
	}

	/**
	 * @param from 생략하면 {@code to - 24시간}.
	 * @param to 생략하면 지금.
	 */
	@GetMapping("/kpis")
	public ApiResponse<AnalyticsKpiResponse> kpis(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		AnalyticsKpiResponse body = this.service.kpis(from, to);
		return ApiResponse.success(body, resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
