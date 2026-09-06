package com.gabolle.backend.itinerary.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.itinerary.application.ItineraryEditService;
import com.gabolle.backend.itinerary.application.ItineraryQueryService;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryEditResponse;
import com.gabolle.backend.itinerary.presentation.dto.LockItemRequest;
import com.gabolle.backend.trip.application.TripQueryService;

import jakarta.validation.Valid;

/**
 * 일정 편집 — API 명세 ITN-03(고정) · ITN-04(해제).
 *
 * <p>🔴 <b>경로와 응답 코드를 발명하지 않았다.</b> 명세 3.5 가 이미 정의하고 있다.
 * 처음에 {@code POST /itineraries/{id}/edits} 를 만들려 했는데, 명세를 확인하니
 * ITN-01~09 가 이미 있었다. 서버가 임의로 경로를 정하면 FE·APP 이 못 붙는다.
 *
 * <h2>🔴 2026-09-06 (S15P21E201-662) — 이 경로가 일정을 지우고 있었다</h2>
 * 세 가지가 어긋나 있었고 셋 다 배포된 화면에서 드러났다.
 * <ol>
 *   <li><b>새 판에 내용이 안 옮겨졌다.</b> 항목·구간의 부모가 판이라 판을 더할 때마다
 *       내용을 복사해야 하는데 판 행만 만들었다. 조회는 최신 판을 읽으므로 고정 한 번에
 *       일정이 통째로 비었다. 고친 곳은 {@code ItineraryEditService}·
 *       {@code ItineraryRepository} 다</li>
 *   <li><b>응답 모양이 앱 계약과 달랐다.</b> 앱은 이 응답을 일정 전체로 받아 화면 상태에
 *       그대로 넣는다. 판 메타데이터만 주면 {@code days} 가 없어 렌더가 죽는다.
 *       지금은 조회와 같은 모양({@link ItineraryEditResponse})을 준다</li>
 *   <li><b>{@code locked} 를 읽지도 저장하지도 않았다.</b> 앱은 이 경로에
 *       {@code {locked, baseVersion}} 을 토글로 보내는데 서버는 항상 고정으로 처리했고,
 *       {@code itinerary_item.locked} 에 {@code true} 를 쓰는 코드가 저장소에 없었다</li>
 * </ol>
 *
 * <p>🔴 그래서 이 컨트롤러가 {@link ItineraryQueryService} 를 쓰게 됐고, 같은
 * {@code @Profile}·{@code @ConditionalOnBean} 조합이 필요해졌다 —
 * {@link ItineraryQueryController} 와 같은 이유다. Spring 컨텍스트를 안 띄우는
 * standalone 테스트로는 더 이상 검증할 수 없고, 애초에 그런 테스트는 "판 복사가
 * 됐는가" 를 증명할 수 없었다(인메모리 저장소에 항목이 없었으므로). 실제 PostgreSQL
 * 통합 테스트로 옮겼다.
 *
 * <p>🔴 <b>아직 없는 것</b> — 객체 권한 검증(OWNER/EDITOR). {@code trip_member} 표는
 * 이미 있으므로(V20260904030000) 만들 수 있고, {@code S15P21E201-224} 가 그 티켓이다.
 * 그전까지는 인증된 사용자면 통과한다 — <b>MR 에 이 사실을 적는다.</b>
 */
@RestController
@RequestMapping("/api/v1/itineraries/{itineraryId}")
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class ItineraryEditController {

	private final ItineraryEditService editService;

	private final ItineraryQueryService queryService;

	public ItineraryEditController(ItineraryEditService editService, ItineraryQueryService queryService) {
		this.editService = editService;
		this.queryService = queryService;
	}

	/**
	 * 항목을 고정한다 — ITN-03. 본문에 {@code locked:false} 를 실으면 해제도 된다.
	 *
	 * <p>🔴 {@code baseVersion} 이 최신이 아니면 {@code 409 ITINERARY_VERSION_CONFLICT} 다.
	 * 그 변환은 {@link ItineraryExceptionHandler} 가 한다 — 도메인은 HTTP 를 모른다.
	 */
	@PostMapping("/items/{itemId}/lock")
	public ResponseEntity<ApiResponse<ItineraryEditResponse>> lockItem(
			@PathVariable String itineraryId,
			@PathVariable String itemId,
			@Valid @RequestBody LockItemRequest request,
			Authentication authentication) {

		ItineraryEditResponse body = applyLock(itineraryId, itemId,
				request.lockedOrDefault(), request.baseVersion(), authentication);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(body, "req_" + UUID.randomUUID()));
	}

	/**
	 * 고정을 푼다 — ITN-04. 명세가 {@code If-Match} 로 바탕 판을 받는다.
	 *
	 * <p>🔴 {@code baseVersion} 쿼리도 함께 받는다. DELETE 에 본문을 싣는 것은 클라이언트
	 * 라이브러리마다 거동이 달라 믿을 수 없고, 앱은 {@code If-Match} 를 보내지 않는다
	 * ({@code frontend/src/api/client.ts} 의 헤더 목록에 없다). 둘 중 하나는 있어야 한다 —
	 * 없으면 400 이다. 화면이 무엇을 보고 있었는지 모르면 덮어쓰기를 막을 수 없다(API-09).
	 */
	@DeleteMapping("/items/{itemId}/lock")
	public ApiResponse<ItineraryEditResponse> unlockItem(
			@PathVariable String itineraryId,
			@PathVariable String itemId,
			@RequestHeader(value = "If-Match", required = false) String ifMatch,
			@RequestParam(value = "baseVersion", required = false) Integer baseVersionParam,
			Authentication authentication) {

		Integer baseVersion = baseVersionParam != null ? baseVersionParam : parseIfMatch(ifMatch);
		if (baseVersion == null) {
			throw new MissingBaseVersionException();
		}

		ItineraryEditResponse body = applyLock(itineraryId, itemId, false, baseVersion, authentication);
		return ApiResponse.success(body, "req_" + UUID.randomUUID());
	}

	/**
	 * 편집하고, 그 결과 판을 그대로 읽어 돌려준다.
	 *
	 * <p>🔴 편집과 조회를 <b>서로 다른 트랜잭션</b>으로 둔다. 편집이 커밋된 뒤에 읽어야
	 * 앱이 받는 것이 "실제로 저장된 것" 이다. 같은 트랜잭션 안에서 만들어 돌려주면
	 * 저장에 실패해도 성공한 것처럼 보이는 응답을 만들 수 있다.
	 */
	private ItineraryEditResponse applyLock(String itineraryId, String itemKey, boolean locked,
			int baseVersion, Authentication authentication) {

		// 🔴 S15P21E201-610 — 요청자를 X-User-Id 헤더가 아니라 인증 주체에서 정한다.
		//    헤더는 부르는 쪽이 정하는 값이라 검사가 그 주장 위에서 돌고, 앱은 그 헤더를
		//    보내지도 않는다(Authorization 만 싣는다).
		String editor = AuthenticatedUsers.requireId(authentication).toString();

		ItineraryVersion saved = this.editService.setItemLocked(itineraryId, itemKey, locked, baseVersion, editor);
		ItineraryDetailResponse detail = this.queryService.getDetail(itineraryId, editor);
		return ItineraryEditResponse.of(detail, saved);
	}

	/** {@code If-Match: "7"} · {@code If-Match: W/"7"} · {@code If-Match: 7} 을 모두 받는다. */
	private static Integer parseIfMatch(String ifMatch) {
		if (ifMatch == null || ifMatch.isBlank()) {
			return null;
		}
		String value = ifMatch.trim();
		if (value.startsWith("W/")) {
			value = value.substring(2).trim();
		}
		value = value.replace("\"", "").trim();
		try {
			return Integer.valueOf(value);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	/** 바탕 판을 안 보냈다 — 400. {@link ItineraryExceptionHandler} 가 번역한다. */
	public static class MissingBaseVersionException extends RuntimeException {
		public MissingBaseVersionException() {
			super("바탕 판 번호가 필요합니다. If-Match 헤더나 baseVersion 쿼리로 보내십시오.");
		}
	}
}
