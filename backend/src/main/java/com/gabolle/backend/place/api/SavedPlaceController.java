package com.gabolle.backend.place.api;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.place.service.SavedPlaceService;

/**
 * 저장한 장소(하트). 목록은 최근 순이다.
 *
 * <p>경로에 남의 식별자를 넣을 자리가 없다({@code /me}). 사용자 번호는 인증 주체에서만 읽으므로
 * 남의 목록에 닿을 길이 없다.
 *
 * <p>POST 가 아니라 PUT 인 이유는 하트가 켜짐/꺼짐이라 "두 번 켠 상태" 가 없기 때문이다. 화면에서
 * 연타할 수 있고 통신이 끊기면 앱이 재시도하므로 몇 번을 보내도 같은 결과여야 한다.
 *
 * <p>{@code place.api} 패키지에 있어 {@link PlaceExceptionHandler} 가 자동으로 덮는다 — 없는 장소는
 * 거기서 404 {@code PLACE_NOT_FOUND} 가 된다.
 */
@RestController
@RequestMapping("/api/v1/me/saved-places")
@Profile({ "db", "dev" })
public class SavedPlaceController {

	private final SavedPlaceService service;

	public SavedPlaceController(SavedPlaceService service) {
		this.service = service;
	}

	@GetMapping
	public ApiResponse<SavedPlaceResponse.Page> list(Authentication authentication) {
		UUID userId = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(SavedPlaceResponse.Page.of(this.service.list(userId)),
				"req_" + UUID.randomUUID());
	}

	@PutMapping("/{placeId}")
	public ResponseEntity<Void> save(@PathVariable UUID placeId, Authentication authentication) {
		UUID userId = AuthenticatedUsers.requireId(authentication);
		this.service.save(userId, placeId);
		return ResponseEntity.noContent().build();
	}

	/** 안 켜져 있던 것을 꺼도 성공이다 — 이유는 {@link SavedPlaceService#remove}. */
	@DeleteMapping("/{placeId}")
	public ResponseEntity<Void> remove(@PathVariable UUID placeId, Authentication authentication) {
		UUID userId = AuthenticatedUsers.requireId(authentication);
		this.service.remove(userId, placeId);
		return ResponseEntity.noContent().build();
	}
}
