package com.gabolle.backend.placesuggestion.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.placesuggestion.application.PlaceSuggestionService;
import com.gabolle.backend.placesuggestion.application.PlaceSuggestionService.Duplicate;
import com.gabolle.backend.placesuggestion.application.PlaceSuggestionService.LocalPick;
import com.gabolle.backend.placesuggestion.application.PlaceSuggestionService.SubmitCommand;
import com.gabolle.backend.placesuggestion.application.PlaceSuggestionService.Suggestion;

/**
 * 현지인 장소 제안 — 로그인한 사람 누구나(S15P21E201-2031). 둘러보기 손님(익명 출입증)은 못 낸다 — 누가 추천했는지를
 * 장소에 붙이므로 계정이 있어야 한다.
 */
@RestController
@Profile({ "db", "dev" })
public class PlaceSuggestionController {

	private final PlaceSuggestionService service;

	public PlaceSuggestionController(PlaceSuggestionService service) {
		this.service = service;
	}

	public record SubmitRequest(String name, String category, Double lat, Double lng, String reason, String photoUrl,
			String openingHours, String phone) {
	}

	public record Items<T>(List<T> items) {
	}

	@PostMapping("/api/v1/place-suggestions")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<Suggestion> submit(@RequestBody SubmitRequest body, Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID me = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.service.submit(me, new SubmitCommand(body.name(), body.category(), body.lat(),
				body.lng(), body.reason(), body.photoUrl(), body.openingHours(), body.phone())), rid(requestId));
	}

	/** 보내기 전에 「이미 있는 장소예요」를 보여 주려고 부른다. 보낼 때도 서버가 다시 본다. */
	@GetMapping("/api/v1/place-suggestions/duplicates")
	public ApiResponse<Items<Duplicate>> duplicates(@RequestParam String name, @RequestParam double lat,
			@RequestParam double lng, Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(new Items<>(this.service.duplicates(name, lat, lng)), rid(requestId));
	}

	@GetMapping("/api/v1/place-suggestions/mine")
	public ApiResponse<Items<Suggestion>> mine(Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID me = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(new Items<>(this.service.mine(me)), rid(requestId));
	}

	/** 이 장소를 추천한 현지인. 제안으로 생긴 장소가 아니면 404. 장소 상세와 같이 로그인만 본다. */
	@GetMapping("/api/v1/places/{placeId}/local-pick")
	public ApiResponse<LocalPick> localPick(@PathVariable UUID placeId,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		return ApiResponse.success(
				this.service.localPick(placeId).orElseThrow(PlaceSuggestionService.SuggestionNotFoundException::new),
				rid(requestId));
	}

	static String rid(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
