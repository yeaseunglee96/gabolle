package com.gabolle.backend.itinerary.presentation;

import java.time.LocalDate;
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
import com.gabolle.backend.itinerary.application.ItineraryAccess;
import com.gabolle.backend.itinerary.application.ItineraryEditService;
import com.gabolle.backend.itinerary.application.ItineraryQueryService;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.presentation.dto.AddItemRequest;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryEditResponse;
import com.gabolle.backend.itinerary.presentation.dto.LockItemRequest;
import com.gabolle.backend.itinerary.presentation.dto.RevertRequest;
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
 * <p>🔴 <b>2026-09-06 (S15P21E201-224) — 이제 있다.</b> {@link ItineraryAccess#requireEditor}
 * 가 편집 전에 막는다. 회원이 아니면(또는 그 일정이 없으면) 존재를 감춘 404, 회원이지만
 * VIEWER 면 403 — 둘을 구분한다. 공유 링크 단계(명세가 말하는 네 번째 역할)는 공유 링크
 * 개념 자체가 아직 없어 이번 작업에서는 없다.
 */
@RestController
@RequestMapping("/api/v1/itineraries/{itineraryId}")
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class ItineraryEditController {

	private final ItineraryEditService editService;

	private final ItineraryQueryService queryService;

	private final ItineraryAccess itineraryAccess;

	public ItineraryEditController(ItineraryEditService editService, ItineraryQueryService queryService,
			ItineraryAccess itineraryAccess) {
		this.editService = editService;
		this.queryService = queryService;
		this.itineraryAccess = itineraryAccess;
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
	 * S15P21E201-467 — 사용자가 고른 장소를 그 날의 마지막에 더한다. 축제 화면이 이것을 부른다.
	 *
	 * <p>명세 3.5 에 "일정에 장소를 더한다" 경로가 없어 여기서 정한다 —
	 * {@code POST .../items}, 본문 {@code {placeId, dayIndex, baseVersion}}. 응답은 고정·되돌리기와
	 * 같은 모양(일정 전체 + 판 정보)이라 앱이 그대로 화면 상태에 넣을 수 있다.
	 *
	 * <h2>🔴 더한 뒤 재계산 Job 을 접수하지 않는다 — 화면이 이어서 부른다</h2>
	 * 시각과 이동 구간은 그 날짜 재계산이 정하는데({@link ItineraryEditService#addPlace} 주석),
	 * 그 Job 접수를 여기서 함께 하면 <b>이 응답이 두 가지 실패를 섞어 버린다.</b> 판은 만들어졌는데
	 * Job 접수만 실패한 경우(예: 제약을 하나도 답하지 않은 여행)를 "장소 더하기 실패" 로 답하면
	 * 화면은 이미 저장된 편집을 없는 것으로 다룬다.
	 *
	 * <p>그래서 여기서는 판까지만 만들고, 화면이 응답의 새 판 번호로
	 * {@code POST .../recalculate} 를 이어 부른다. 재계산이 실패해도 더한 장소는 남고 시각만 비어
	 * 있다 — 그게 사용자가 요청한 사실에 맞는 상태다.
	 *
	 * <p>{@code dayIndex} 가 여행 기간을 벗어나면 400 이다. 여행이 3일인데 5일째에 넣으면 그
	 * 항목은 어느 날에도 보이지 않는다 — 표의 CHECK 는 이것을 막지 못한다(마이그레이션
	 * {@code V20260905120000} 주석 "막지 못하는 것").
	 */
	@PostMapping("/items")
	public ResponseEntity<ApiResponse<ItineraryEditResponse>> addItem(
			@PathVariable String itineraryId,
			@Valid @RequestBody AddItemRequest request,
			Authentication authentication) {

		String editor = AuthenticatedUsers.requireId(authentication).toString();
		ItineraryAccess.Access access = this.itineraryAccess.requireEditor(itineraryId, editor);

		LocalDate visitDate = visitDateOf(access, request.dayIndex());

		ItineraryVersion saved = this.editService.addPlace(itineraryId, request.placeId(),
				request.dayIndex(), visitDate, request.baseVersion(), editor);
		ItineraryDetailResponse detail = this.queryService.getDetail(itineraryId, editor);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(ItineraryEditResponse.of(detail, saved),
						"req_" + UUID.randomUUID()));
	}

	/**
	 * 며칠째가 실제로 어느 날짜인가. 여행 시작일에 {@code dayIndex} 를 더한다.
	 *
	 * <p>🔴 여행 마지막 날을 넘으면 거부한다. 그 항목은 어느 날 화면에도 안 나타나므로
	 * 사용자에게는 "더했는데 사라졌다" 로 보인다.
	 */
	private static LocalDate visitDateOf(ItineraryAccess.Access access, int dayIndex) {
		LocalDate visitDate = access.trip().startDate().plusDays(dayIndex);
		if (visitDate.isAfter(access.trip().finishDate())) {
			throw new DayOutsideTripException(dayIndex, access.trip().startDate(),
					access.trip().finishDate());
		}
		return visitDate;
	}

	/** {@code dayIndex} 가 여행 기간을 벗어났다 — 400. */
	public static class DayOutsideTripException extends RuntimeException {

		private final int dayIndex;

		public DayOutsideTripException(int dayIndex, LocalDate start, LocalDate finish) {
			super("여행 기간(" + start + "~" + finish + ") 밖의 날짜입니다: dayIndex=" + dayIndex);
			this.dayIndex = dayIndex;
		}

		public int dayIndex() {
			return this.dayIndex;
		}
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
	/**
	 * S15P21E201-284 — 되돌리기. 마지막 편집 직전(또는 {@code toVersion})의 내용을 새 판으로 복사한다.
	 * 명세 3.5 에는 되돌리기 경로가 없어 여기서 정한다 — {@code POST .../revert}, 본문
	 * {@code {baseVersion, toVersion?}}. 응답은 고정과 같은 모양(일정 전체 + 판 정보)이라 앱이 그대로
	 * 화면 상태에 넣을 수 있다. 되돌릴 편집이 없으면 422 {@code ITINERARY_NOTHING_TO_REVERT}.
	 */
	@PostMapping("/revert")
	public ResponseEntity<ApiResponse<ItineraryEditResponse>> revert(
			@PathVariable String itineraryId,
			@Valid @RequestBody RevertRequest request,
			Authentication authentication) {
		String editor = AuthenticatedUsers.requireId(authentication).toString();
		this.itineraryAccess.requireEditor(itineraryId, editor);
		ItineraryVersion saved = this.editService.revert(itineraryId, request.baseVersion(), request.toVersion(),
				editor);
		ItineraryDetailResponse detail = this.queryService.getDetail(itineraryId, editor);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(ItineraryEditResponse.of(detail, saved), "req_" + UUID.randomUUID()));
	}

	private ItineraryEditResponse applyLock(String itineraryId, String itemKey, boolean locked,
			int baseVersion, Authentication authentication) {

		// 🔴 S15P21E201-610 — 요청자를 X-User-Id 헤더가 아니라 인증 주체에서 정한다.
		//    헤더는 부르는 쪽이 정하는 값이라 검사가 그 주장 위에서 돌고, 앱은 그 헤더를
		//    보내지도 않는다(Authorization 만 싣는다).
		String editor = AuthenticatedUsers.requireId(authentication).toString();

		// 🔴 S15P21E201-224 — 편집 전에 막는다. 회원이 아니면 404(존재를 감춘다),
		//    회원이지만 VIEWER 면 403. 이 호출이 없으면 인증만 되면 남의 일정도 고칠 수 있었다.
		this.itineraryAccess.requireEditor(itineraryId, editor);

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
