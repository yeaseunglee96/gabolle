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
import com.gabolle.backend.itinerary.presentation.dto.ReorderDayRequest;
import com.gabolle.backend.itinerary.presentation.dto.RevertRequest;
import com.gabolle.backend.trip.application.TripQueryService;

import jakarta.validation.Valid;

/**
 * 일정 편집 — 항목 고정과 해제.
 * 경로와 응답 코드는 API 명세 3.5 가 정의한 것을 그대로 쓴다. 서버가 임의로 경로를 정하면
 * FE·APP 이 못 붙는다.
 * 응답은 판 메타데이터가 아니라 조회와 같은 모양({@link ItineraryEditResponse})이다. 앱이
 * 이 응답을 일정 전체로 받아 화면 상태에 그대로 넣기 때문에, {@code days} 가 없으면 렌더가 죽는다.
 * {@link ItineraryQueryService} 를 쓰므로 {@link ItineraryQueryController} 와 같은
 * {@code @Profile}·{@code @ConditionalOnBean} 조합이 필요하다. Spring 컨텍스트를 안 띄우는
 * standalone 테스트로는 판 복사가 됐는지 증명할 수 없어 PostgreSQL 통합 테스트로 검증한다.
 * {@link ItineraryAccess#requireEditor} 가 편집 전에 막는다 — 회원이 아니면(또는 그 일정이
 * 없으면) 존재를 감춘 404, 회원이지만 VIEWER 면 403.
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
	 * 항목을 고정한다. 본문에 {@code locked:false} 를 실으면 해제도 된다.
	 * {@code baseVersion} 이 최신이 아니면 {@code 409 ITINERARY_VERSION_CONFLICT} 다. 그 변환은
	 * {@link ItineraryExceptionHandler} 가 한다 — 도메인은 HTTP 를 모른다.
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
	 * 사용자가 고른 장소를 그 날의 마지막에 더한다. 축제 화면이 이것을 부른다.
	 * 명세에 이 경로가 없어 여기서 정한다 — {@code POST .../items}, 본문
	 * {@code {placeId, dayIndex, baseVersion}}. 응답은 고정·되돌리기와 같은 모양이다.
	 * 더한 뒤 재계산 Job 을 접수하지 않는다. 함께 하면 이 응답이 두 가지 실패를 섞는다 — 판은
	 * 만들어졌는데 Job 접수만 실패한 경우를 "장소 더하기 실패" 로 답하면 화면이 이미 저장된
	 * 편집을 없는 것으로 다룬다. 화면이 응답의 새 판 번호로 {@code POST .../recalculate} 를 이어
	 * 부른다. 재계산이 실패해도 더한 장소는 남고 시각만 빈다.
	 * {@code dayIndex} 가 여행 기간을 벗어나면 400 이다. 여행이 3일인데 5일째에 넣으면 그 항목은
	 * 어느 날에도 보이지 않는데, 표의 CHECK 는 이것을 막지 못한다.
	 * 검사 순서가 곧 정보 노출 경계다 — 권한({@link ItineraryAccess#requireEditor}) → 여행 기간
	 * 안의 날인가 → 판이 최신인가 → 축제가 그 날 열리는가 → 이미 담겨 있는가. 권한이 맨 앞이
	 * 아니면 남의 여행에 아무 장소나 던져 본 사람이 오류 코드의 차이로 그 일정 안에 무엇이 들어
	 * 있는지 알아낼 수 있다.
	 */
	@PostMapping("/items")
	public ResponseEntity<ApiResponse<ItineraryEditResponse>> addItem(
			@PathVariable String itineraryId,
			@Valid @RequestBody AddItemRequest request,
			Authentication authentication) {

		String editor = AuthenticatedUsers.requireId(authentication).toString();
		ItineraryAccess.Access access = this.itineraryAccess.requireEditor(itineraryId, editor);

		requireDayInsideTrip(access, request.dayIndex());

		ItineraryVersion saved = this.editService.addPlace(itineraryId, request.placeId(),
				request.dayIndex(), access.trip().startDate(), access.trip().finishDate(),
				request.baseVersion(), editor);
		ItineraryDetailResponse detail = this.queryService.getDetail(itineraryId, editor);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(ItineraryEditResponse.of(detail, saved,
						this.editService.openingHoursForDay(itineraryId, saved.version(), request.dayIndex())),
						"req_" + UUID.randomUUID()));
	}

	/**
	 * 하루 안의 방문 순서를 바꾼다. 화면의 끌어 옮기기와 위·아래 버튼이 여기로 온다.
	 * 그날의 순서를 통째로 받는다. "이 항목을 3번째로" 가 아니다 — 이유는
	 * {@link ReorderDayRequest} 에 있다.
	 * 응답은 더하기·고정과 같은 모양이라 화면이 다시 조회할 필요가 없다. 새 자원을 만든 것이
	 * 아니라 있는 것을 고친 것이라 {@code 201} 이 아니라 {@code 200} 이다.
	 */
	@PostMapping("/days/{dayIndex}/reorder")
	public ApiResponse<ItineraryEditResponse> reorderDay(
			@PathVariable String itineraryId,
			@PathVariable int dayIndex,
			@Valid @RequestBody ReorderDayRequest request,
			Authentication authentication) {

		String editor = AuthenticatedUsers.requireId(authentication).toString();
		ItineraryAccess.Access access = this.itineraryAccess.requireEditor(itineraryId, editor);

		requireDayInsideTrip(access, dayIndex);

		ItineraryEditService.ReorderOutcome outcome = this.editService.reorderDay(itineraryId, dayIndex,
				request.itemKeys(), request.baseVersion(), editor);
		ItineraryDetailResponse detail = this.queryService.getDetail(itineraryId, editor);

		return ApiResponse.success(
				ItineraryEditResponse.of(detail, outcome.version(), outcome.openingHours()),
				"req_" + UUID.randomUUID());
	}

	/**
	 * 남은 하루를 다시 계획한다. 지나간 방문지는 그대로 두고, 아직 안 간 방문지의 시각만 실제
	 * 도착·출발 기록을 반영해 다시 매긴다. 장소와 순서는 하나도 바뀌지 않는다.
	 * 바탕 판은 순서 바꾸기와 같은 방식으로 받는다 — {@code If-Match} 헤더나 {@code baseVersion}
	 * 쿼리, 둘 다 없으면 400 이다. 재계획도 편집이라 낡은 판 위에서 계산하면 다른 사람의 편집을
	 * 덮어쓸 수 있다.
	 * 속도 계수는 아직 이 경로에 배선하지 않아 항상 {@code null} 을 넘긴다.
	 * 응답은 순서 바꾸기·고정과 같은 모양이고, 있는 판을 고친 것이라 {@code 200} 이다.
	 */
	@PostMapping(value = "/days/{dayIndex}/replan")
	public ApiResponse<ItineraryEditResponse> replanDay(
			@PathVariable String itineraryId,
			@PathVariable int dayIndex,
			@RequestHeader(value = "If-Match", required = false) String ifMatch,
			@RequestParam(value = "baseVersion", required = false) Integer baseVersionParam,
			Authentication authentication) {

		String editor = AuthenticatedUsers.requireId(authentication).toString();
		this.itineraryAccess.requireEditor(itineraryId, editor);

		Integer baseVersion = baseVersionParam != null ? baseVersionParam : parseIfMatch(ifMatch);
		if (baseVersion == null) {
			throw new MissingBaseVersionException();
		}

		ItineraryVersion saved = this.editService.replanDay(itineraryId, dayIndex, baseVersion, null, editor);
		ItineraryDetailResponse detail = this.queryService.getDetail(itineraryId, editor);

		return ApiResponse.success(ItineraryEditResponse.of(detail, saved,
				this.editService.openingHoursForDay(itineraryId, saved.version(), dayIndex)),
				"req_" + UUID.randomUUID());
	}

	/**
	 * 며칠째가 실제로 여행 안에 있는 날인가. 여행 시작일에 {@code dayIndex} 를 더해 본다.
	 * 여행 마지막 날을 넘으면 거부한다. 그 항목은 어느 날 화면에도 안 나타나므로 사용자에게는
	 * "더했는데 사라졌다" 로 보인다.
	 * 날짜 계산 자체는 {@link ItineraryEditService#addPlace} 가 다시 한다. 같은 값을 두 번
	 * 구하는 셈이지만, 이 검사는 요청을 받아들일지를 정하는 표현 계층의 일이고 저쪽은 어느
	 * 날짜 행을 쓸지를 정하는 일이라 각자의 자리에 둔다.
	 */
	private static void requireDayInsideTrip(ItineraryAccess.Access access, int dayIndex) {
		LocalDate visitDate = access.trip().startDate().plusDays(dayIndex);
		if (visitDate.isAfter(access.trip().finishDate())) {
			throw new DayOutsideTripException(dayIndex, access.trip().startDate(),
					access.trip().finishDate());
		}
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
	 * 고정을 푼다. 명세가 {@code If-Match} 로 바탕 판을 받는다.
	 * {@code baseVersion} 쿼리도 함께 받는다 — DELETE 에 본문을 싣는 것은 클라이언트 라이브러리마다
	 * 거동이 달라 믿을 수 없고, 앱은 {@code If-Match} 를 보내지 않는다. 둘 중 하나는 있어야 하고
	 * 없으면 400 이다. 화면이 무엇을 보고 있었는지 모르면 덮어쓰기를 막을 수 없다.
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
	 * 편집과 조회를 서로 다른 트랜잭션으로 둔다. 편집이 커밋된 뒤에 읽어야 앱이 받는 것이 실제로
	 * 저장된 것이다. 같은 트랜잭션 안에서 만들어 돌려주면 저장에 실패해도 성공한 것처럼 보이는
	 * 응답을 만들 수 있다.
	 */
	/**
	 * 되돌리기. 마지막 편집 직전(또는 {@code toVersion})의 내용을 새 판으로 복사한다.
	 * 명세에 되돌리기 경로가 없어 여기서 정한다 — {@code POST .../revert}, 본문
	 * {@code {baseVersion, toVersion?}}. 응답은 고정과 같은 모양이다. 되돌릴 편집이 없으면
	 * 422 {@code ITINERARY_NOTHING_TO_REVERT}.
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
				.body(ApiResponse.success(ItineraryEditResponse.of(detail, saved,
						this.editService.openingHoursForAll(itineraryId, saved.version())),
						"req_" + UUID.randomUUID()));
	}

	private ItineraryEditResponse applyLock(String itineraryId, String itemKey, boolean locked,
			int baseVersion, Authentication authentication) {

		// 요청자를 X-User-Id 헤더가 아니라 인증 주체에서 정한다. 헤더는 부르는 쪽이 정하는 값이라
		//    검사가 그 주장 위에서 돌고, 앱은 그 헤더를 보내지도 않는다(Authorization 만 싣는다).
		String editor = AuthenticatedUsers.requireId(authentication).toString();

		// 편집 전에 막는다. 회원이 아니면 404(존재를 감춘다), 회원이지만 VIEWER 면 403.
		//    이 호출이 없으면 인증만 되면 남의 일정도 고칠 수 있다.
		this.itineraryAccess.requireEditor(itineraryId, editor);

		ItineraryVersion saved = this.editService.setItemLocked(itineraryId, itemKey, locked, baseVersion, editor);
		ItineraryDetailResponse detail = this.queryService.getDetail(itineraryId, editor);
		return ItineraryEditResponse.of(detail, saved,
				this.editService.openingHoursForItem(itineraryId, saved.version(), itemKey));
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
