package com.gabolle.backend.tripnaming.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.tripnaming.application.TripNameSuggestionService;
import com.gabolle.backend.tripnaming.presentation.dto.TripNameSuggestionsResponse;

/**
 * 여행 이름 후보를 지어 준다. 로그인 필수, 본문 없음.
 *
 * <p>이름을 저장하지 않는다. 고른 뒤 저장은 {@code PUT /api/v1/trips/{tripId}/title} 이 한다.
 *
 * <p>읽기처럼 보이지만 부를 때마다 바깥 모델을 부르고 크레딧을 쓰므로 GET 이 아니라 POST 다.
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
			@PathVariable String tripId, Authentication authentication,
			@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		return ApiResponse.success(this.service.suggest(tripId, requester, TripNameSuggestionService.NameLanguage.of(acceptLanguage)), "req_" + UUID.randomUUID());
	}
}
