package com.gabolle.backend.collection.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.collection.application.CollectionService;
import com.gabolle.backend.collection.domain.Collection;
import com.gabolle.backend.collection.domain.CollectionItem;
import com.gabolle.backend.collection.presentation.dto.CollectionResponse;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;

/**
 * 컬렉션 — S15P21E201-1013.
 *
 * <pre>
 * GET    /api/v1/me/collections                        내 컬렉션 전부 (담긴 것까지)
 * POST   /api/v1/me/collections                        컬렉션을 만든다
 * GET    /api/v1/me/collections/{id}                   하나
 * PATCH  /api/v1/me/collections/{id}                   이름·설명을 고친다
 * DELETE /api/v1/me/collections/{id}                   지운다 (담긴 것도 함께)
 *
 * POST   /api/v1/me/collections/{id}/items             담는다 — 두 종류를 한 경로로 받는다
 * PATCH  /api/v1/me/collections/{id}/items/{itemId}    담긴 것을 고친다
 * DELETE /api/v1/me/collections/{id}/items/{itemId}    뺀다
 * </pre>
 *
 * <p>🔴 경로에 <b>남의 번호를 넣을 자리가 없다</b>({@code /me}). 사용자 번호는 인증 주체에서만
 * 읽고, 컬렉션도 언제나 주인과 함께 찾는다 — 저장한 장소({@code /me/saved-places})와 같은 방식이다.
 *
 * <p>🔴 <b>담는 경로가 하나다.</b> 종류마다 경로를 나누면 화면이 «무엇을 담는가» 에 따라 다른
 * 주소를 알아야 하고, 나중에 종류가 늘 때 경로가 또 는다. 본문의 {@code kind} 가 가른다.
 */
@RestController
@RequestMapping("/api/v1/me/collections")
@Profile({ "db", "dev" })
public class CollectionController {

	private final CollectionService service;

	public CollectionController(CollectionService service) {
		this.service = service;
	}

	@GetMapping
	public ApiResponse<CollectionResponse.Page> list(Authentication authentication) {
		UUID userId = AuthenticatedUsers.requireId(authentication);
		return ok(CollectionResponse.Page.of(this.service.list(userId)));
	}

	@GetMapping("/{collectionId}")
	public ApiResponse<CollectionResponse> get(@PathVariable UUID collectionId, Authentication authentication) {
		UUID userId = AuthenticatedUsers.requireId(authentication);
		return ok(CollectionResponse.of(this.service.get(userId, collectionId)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<CollectionResponse>> create(@Valid @RequestBody NameRequest request,
			Authentication authentication) {
		UUID userId = AuthenticatedUsers.requireId(authentication);
		UUID id = this.service.create(userId, name(request), description(request)).getId();
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ok(CollectionResponse.of(this.service.get(userId, id))));
	}

	@PatchMapping("/{collectionId}")
	public ApiResponse<CollectionResponse> rename(@PathVariable UUID collectionId,
			@Valid @RequestBody NameRequest request, Authentication authentication) {
		UUID userId = AuthenticatedUsers.requireId(authentication);
		this.service.rename(userId, collectionId, name(request), description(request));
		return ok(CollectionResponse.of(this.service.get(userId, collectionId)));
	}

	@DeleteMapping("/{collectionId}")
	public ResponseEntity<Void> delete(@PathVariable UUID collectionId, Authentication authentication) {
		this.service.delete(AuthenticatedUsers.requireId(authentication), collectionId);
		return ResponseEntity.noContent().build();
	}

	// ── 담긴 것 ─────────────────────────────────────────────────────────────

	/**
	 * 담는다. 본문의 {@code kind} 가 두 종류를 가른다.
	 *
	 * <pre>
	 * { "kind": "PLACE",  "placeId": "…",           "note": "…" }
	 * { "kind": "CUSTOM", "name": "동네 빵집", "locality": "…", "lat": …, "lng": …,
	 *   "photoUrl": "…", "note": "…" }
	 * </pre>
	 *
     * <p>🔴 {@code photoUrl} 은 <b>이미 올라간 사진의 주소</b>다. 파일을 이 경로로 직접 받지
	 * 않는다 — 기존 이미지 업로드 경로로 먼저 올리고 그 주소를 준다. 프로필 사진(-844)과 같다.
	 */
	@PostMapping("/{collectionId}/items")
	public ResponseEntity<ApiResponse<CollectionResponse>> addItem(@PathVariable UUID collectionId,
			@Valid @RequestBody ItemRequest request, Authentication authentication) {

		UUID userId = AuthenticatedUsers.requireId(authentication);
		if (request == null || request.kind() == null) {
			throw new IllegalArgumentException("kind 는 필수다 (PLACE 또는 CUSTOM)");
		}

		switch (request.kind()) {
			case PLACE -> {
				if (request.placeId() == null) {
					throw new IllegalArgumentException("kind 가 PLACE 면 placeId 가 있어야 한다");
				}
				this.service.addPlace(userId, collectionId, request.placeId(), request.note());
			}
			case CUSTOM -> this.service.addCustom(userId, collectionId, request.name(), request.locality(),
					request.lat(), request.lng(), request.photoUrl(), request.note());
		}

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ok(CollectionResponse.of(this.service.get(userId, collectionId))));
	}

	@PatchMapping("/{collectionId}/items/{itemId}")
	public ApiResponse<CollectionResponse> editItem(@PathVariable UUID collectionId, @PathVariable UUID itemId,
			@Valid @RequestBody ItemRequest request, Authentication authentication) {

		UUID userId = AuthenticatedUsers.requireId(authentication);
		this.service.editItem(userId, collectionId, itemId, request.name(), request.locality(), request.lat(),
				request.lng(), request.photoUrl(), request.note(), request.position());
		return ok(CollectionResponse.of(this.service.get(userId, collectionId)));
	}

	/** 🔴 없는 것을 빼도 성공이다 — 이유는 {@link CollectionService#removeItem}. */
	@DeleteMapping("/{collectionId}/items/{itemId}")
	public ResponseEntity<Void> removeItem(@PathVariable UUID collectionId, @PathVariable UUID itemId,
			Authentication authentication) {
		this.service.removeItem(AuthenticatedUsers.requireId(authentication), collectionId, itemId);
		return ResponseEntity.noContent().build();
	}

	private static <T> ApiResponse<T> ok(T body) {
		return ApiResponse.success(body, "req_" + UUID.randomUUID());
	}

	private static String name(NameRequest request) {
		return (request == null) ? null : request.name();
	}

	private static String description(NameRequest request) {
		return (request == null) ? null : request.description();
	}

	/**
	 * 컬렉션의 이름과 설명.
	 *
	 * <p>상한은 {@link Collection} 의 상수에서 읽는다 (S15P21E201-1037) — 여기에 숫자를
	 * 다시 적으면 열을 넓히는 날 한쪽만 고쳐진다. 도메인도 같은 상수로 한 번 더 보므로,
	 * 이 어노테이션이 없어도 값은 안전하다. 여기 있는 이유는 <b>어느 칸이 틀렸는지</b>를
	 * 응답에 담아 주기 위해서다.
	 */
	public record NameRequest(
			@NotBlank(message = "이름은 비울 수 없어요")
			@Size(max = Collection.NAME_MAX_LENGTH, message = "이름이 너무 길어요") String name,
			@Size(max = Collection.DESCRIPTION_MAX_LENGTH, message = "설명이 너무 길어요") String description) {
	}

	/** 담을 것. {@code kind} 에 따라 채우는 칸이 다르다 — 위 {@link #addItem} 참고. */
	public record ItemRequest(CollectionItem.Kind kind, UUID placeId,
			@Size(max = CollectionItem.NAME_MAX_LENGTH, message = "이름이 너무 길어요") String name,
			@Size(max = CollectionItem.LOCALITY_MAX_LENGTH, message = "지역이 너무 길어요") String locality,
			Double lat, Double lng,
			@Size(max = CollectionItem.PHOTO_URL_MAX_LENGTH, message = "사진 주소가 너무 길어요") String photoUrl,
			@Size(max = CollectionItem.NOTE_MAX_LENGTH, message = "메모가 너무 길어요") String note,
			Integer position) {
	}
}
