package com.gabolle.backend.place.api;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.place.service.ConditionCoverageService;

/**
 * 「어느 조건을 판정할 자료가 있나」 조회 — S15P21E201-1508.
 *
 * <p>여행 조건 화면이 자료 없는 문항에 「지금은 이 조건을 확인할 자료가 없어요」를 붙이는 데
 * 쓴다(프론트는 S15P21E201-1044). 그 목록을 화면에 박으면 자료가 들어온 날 거짓말이 되므로
 * 서버가 알려준다.
 *
 * <p>{@code X-User-Id} 를 받지 않는다. 답이 사용자와 무관하다 — 장소 자료가 얼마나 있는지는
 * 누가 물어도 같은 값이다. {@link NearbyPlaceController} 가 같은 이유로 같은 선택을 했다.
 */
@RestController
@RequestMapping("/api/v1/places")
@Profile({ "db", "dev" })
public class ConditionCoverageController {

	private final ConditionCoverageService conditionCoverageService;

	public ConditionCoverageController(ConditionCoverageService conditionCoverageService) {
		this.conditionCoverageService = conditionCoverageService;
	}

	/**
	 * 문항마다 판정할 장소 자료가 몇 곳에 있는지.
	 *
	 * <p>🔴 <b>「있다/없다」가 아니라 개수를 준다.</b> 문턱을 서버가 몰래 정하면 화면은 그
	 * 기준을 모른 채 따르게 된다 — 지금은 0곳만 가리면 되지만, 나중에 「28% 뿐이에요」처럼
	 * 말하려면 숫자가 있어야 한다. 전체 장소 수도 같이 줘서 비율을 화면이 낸다.
	 */
	@GetMapping("/condition-coverage")
	public ApiResponse<ConditionCoverageResponse> conditionCoverage() {
		return ApiResponse.success(this.conditionCoverageService.describe(), "req_" + UUID.randomUUID());
	}
}
