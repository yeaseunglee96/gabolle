package com.gabolle.backend.tripnaming.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.tripnaming.application.TripNameSuggestionService;
import com.gabolle.backend.tripnaming.presentation.dto.TripNameSuggestionsResponse;

/**
 * 여행 이름 후보를 지어 준다 — S15P21E201-1025.
 *
 * <pre>
 * POST /api/v1/trips/{tripId}/name-suggestions      로그인 필수 · 본문 없음
 * </pre>
 *
 * <h2>🔴 이름을 <b>저장하지 않는다</b></h2>
 *
 * 후보를 줄 뿐이고, 고르는 것은 사람이다. 고른 뒤 저장은
 * {@code PUT /api/v1/trips/{tripId}/title}(S15P21E201-1023)이 한다.
 * 여기서 바로 저장하면 <b>사용자가 안 고른 이름이 여행에 박힌다.</b>
 *
 * <h2>왜 POST 인가</h2>
 *
 * 읽기처럼 보이지만 부를 때마다 <b>바깥 모델을 부르고 크레딧을 쓴다.</b> GET 으로 두면
 * 화면·프록시·브라우저가 마음대로 다시 부르거나 캐시한다 — 둘 다 여기서는 틀린 동작이다.
 */
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripNameSuggestionController {

	private final TripNameSuggestionService service;

	public TripNameSuggestionController(TripNameSuggestionService service) {
		this.service = service;
	}

	@PostMapping("/name-suggestions")
	public ApiResponse<TripNameSuggestionsResponse> suggest(
			@PathVariable String tripId, Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		return ApiResponse.success(this.service.suggest(tripId, requester), "req_" + UUID.randomUUID());
	}
}
