package com.gabolle.backend.help.presentation;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.help.application.HelpPlaceService;
import com.gabolle.backend.help.domain.HelpKind;
import com.gabolle.backend.help.presentation.dto.NearbyHelpResponse;

/**
 * 가까운 병원·약국·경찰 — 긴급 도움의 지도가 부른다(S15P21E201-1893).
 *
 * <p>로그인만 요구한다(익명 세션도 된다 — 다른 조회와 같은 문, {@code SecurityConfig} 를 안 바꾼다). 좌표와 갈래만으로
 * 답이 정해지고 사용자별로 다른 것이 없다. 자료는 DB 가 아니라 자원 파일이라({@code HelpPlaceCatalog}) 프로필을 가리지 않는다.
 */
@RestController
@RequestMapping("/api/v1/help-places")
public class HelpPlaceController {

	private final HelpPlaceService service;

	public HelpPlaceController(HelpPlaceService service) {
		this.service = service;
	}

	/**
	 * @param kind {@code HOSPITAL}·{@code PHARMACY}·{@code POLICE}
	 * @param limit 가까운 순 몇 곳 — 1~20, 기본 5
	 * @param openNow true 면 지금 진료 중인 곳만(진료시간을 아는 곳 가운데)
	 */
	@GetMapping("/nearby")
	public ApiResponse<NearbyHelpResponse> nearby(
			@RequestParam HelpKind kind,
			@RequestParam double lat,
			@RequestParam double lng,
			@RequestParam(defaultValue = "5") int limit,
			@RequestParam(defaultValue = "false") boolean openNow,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		NearbyHelpResponse response = NearbyHelpResponse.from(this.service.nearby(kind, lat, lng, limit, openNow));
		return ApiResponse.success(response, requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId);
	}
}
