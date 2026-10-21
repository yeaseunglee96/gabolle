package com.gabolle.backend.trip.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.trip.application.TripExpenseService;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 여행 돈 — {@code /api/v1/trips/{tripId}/expenses}·{@code /budget}(S15P21E201-1935).
 * 모든 경로가 같은 모양(예산·합계·줄들)을 돌려준다 — 화면이 응답 하나로 장부를 다시 그린다.
 */
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripExpenseController {

	private final TripExpenseService service;

	public TripExpenseController(TripExpenseService service) {
		this.service = service;
	}

	@GetMapping("/expenses")
	public ApiResponse<TripExpenseService.Ledger> list(@PathVariable String tripId, Authentication authentication) {
		return ok(this.service.find(tripId, AuthenticatedUsers.requireId(authentication)));
	}

	@PostMapping("/expenses")
	public ApiResponse<TripExpenseService.Ledger> add(@PathVariable String tripId,
			@RequestBody(required = false) TripExpenseService.NewExpense request, Authentication authentication) {
		return ok(this.service.add(tripId, AuthenticatedUsers.requireId(authentication), request));
	}

	@DeleteMapping("/expenses/{expenseId}")
	public ApiResponse<TripExpenseService.Ledger> remove(@PathVariable String tripId, @PathVariable UUID expenseId,
			Authentication authentication) {
		return ok(this.service.remove(tripId, AuthenticatedUsers.requireId(authentication), expenseId));
	}

	@PutMapping("/budget")
	public ApiResponse<TripExpenseService.Ledger> budget(@PathVariable String tripId,
			@RequestBody(required = false) BudgetRequest request, Authentication authentication) {
		return ok(this.service.setBudget(tripId, AuthenticatedUsers.requireId(authentication),
				request == null ? null : request.amountKrw()));
	}

	private static ApiResponse<TripExpenseService.Ledger> ok(TripExpenseService.Ledger ledger) {
		return ApiResponse.success(ledger, "req_" + UUID.randomUUID());
	}

	/** {@code {"amountKrw": 300000}} — 비우면 예산을 없앤다. */
	public record BudgetRequest(Integer amountKrw) {
	}
}
