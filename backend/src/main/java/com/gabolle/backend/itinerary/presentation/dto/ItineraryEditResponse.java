package com.gabolle.backend.itinerary.presentation.dto;

import java.util.List;

import com.gabolle.backend.itinerary.application.ItineraryOpeningHoursChecker;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.recommendation.domain.FallbackMode;

/**
 * 편집이 만든 새 판 — ITN-03 · ITN-04 의 응답 본문(S15P21E201-662).
 *
 * <h2>🔴 앞 일곱 칸은 {@link ItineraryDetailResponse} 와 이름·타입이 같다. 계약이다</h2>
 * 앱은 고정 요청의 응답을 <b>일정 전체</b>로 받아 화면 상태에 그대로 넣는다
 * ({@code frontend/src/plan/itinerary.ts} 의 {@code setItineraryItemLocked} 가
 * {@code apiRequest<ItineraryDto>} 로 부른다). 그래서 {@code days} 가 없으면 그 자리에서
 * 화면이 깨진다 — 2026-09-06 이전이 정확히 그 상태였다.
 *
 * <p><b>앞 일곱 칸의 이름·타입을 바꾸면 앱이 깨진다.</b> 조회와 편집이 같은 모양을 내야
 * 한다는 뜻이고, 한쪽만 고치면 안 된다.
 *
 * <h2>왜 {@code ItineraryDetailResponse} 를 그대로 쓰지 않는가</h2>
 * 명세 ITN-03 의 응답은 "새 {@code ItineraryVersion}" 이라 판 번호·바탕 판·편집 종류가
 * 필요하다. 그것을 조회 DTO 에 넣으면 조회 응답까지 편집 메타데이터로 오염된다. 그래서
 * 앞쪽 칸을 겹쳐 놓은 별도 레코드로 둔다 — 두 계약을 동시에 만족하는 유일한 모양이다.
 *
 * <p>뒤쪽 칸들은 앱이 안 읽는다. JSON 은 모르는 키를 무시하므로 더해도 안전하다.
 *
 * <p>🔴 S15P21E201-224 — {@code myRole}·{@code canEdit} 두 칸을 맨 뒤에 더 붙였다.
 * 이 응답이 나온다는 것 자체가 이미 편집을 성공한 뒤라는 뜻이라 {@code canEdit} 은 항상
 * {@code true} 다 — VIEWER 였다면 {@code ItineraryAccess.requireEditor} 가 이 지점까지
 * 오기 전에 403 으로 막는다. 그래도 조회 응답과 모양을 맞추기 위해 그대로 옮겨 싣는다.
 */
public record ItineraryEditResponse(
		String id,
		String title,
		int version,
		List<ItineraryDetailResponse.Day> days,
		Integer totalEstimatedCostKrw,
		Integer totalWalkingMeters,
		FallbackMode fallbackMode,

		Integer baseVersion,
		String operation,
		String createdBy,
		String requestId,
		String createdAt,

		String myRole,
		boolean canEdit,
		/** S15P21E201-284 — operation 이 REVERT 일 때 어느 판으로 돌아갔나. 그 외에는 null. 맨 끝에 붙여 앞 칸을 흔들지 않는다. */
		Integer revertedFromVersion,

		/**
		 * S15P21E201-268 — 이 편집의 결과에 대해 알려 줄 것. 지금은 영업시간 위반 하나뿐이다.
		 *
		 * <p>비어 있는 것은 <b>"봤고 알릴 것이 없음"</b> 이다. 봤는지 자체는 아래
		 * {@code notChecked} 가 말한다.
		 */
		List<Warning> warnings,

		/**
		 * S15P21E201-268 — 못 한 검사와 그 이유.
		 *
		 * <p>🔴 이 칸이 있는 이유가 하나다. 영업시간을 못 보는 상태에서 경고 목록만 비워 보내면
		 * 화면은 그것을 "확인했고 문제 없음" 으로 그린다. 못 본 것을 못 봤다고 말해야 <b>틀린
		 * 안심</b>을 안 준다. 같은 어휘를 장소 후보 조회가 이미 쓴다
		 * ({@code PlaceCandidateResponse.notApplied}).
		 */
		List<NotChecked> notChecked) {

	/**
	 * @param code    무엇을 어겼나
	 * @param itemId  어긴 항목. {@code days[].items[].id} 와 같은 값이라 화면이 두 목록을 이어
	 *                붙일 수 있다
	 * @param placeId 그 항목의 장소
	 * @param at      어긴 시각. ISO-8601 + 시간대
	 */
	public record Warning(String code, String itemId, String placeId, String at) {
	}

	/**
	 * @param check  못 한 검사의 이름
	 * @param reason 왜 못 했나
	 */
	public record NotChecked(String check, String reason) {
	}

	/**
	 * 알릴 것을 판정하지 않는 편집 — 고정·더하기·되돌리기가 이 자리로 온다. 그날의 방문 순서를
	 * 바꾸지 않으므로 영업시간 판정을 새로 할 이유가 없다.
	 */
	public static ItineraryEditResponse of(ItineraryDetailResponse detail, ItineraryVersion saved) {
		return of(detail, saved, ItineraryOpeningHoursChecker.Result.notEvaluated());
	}

	/** 순서 바꾸기 — 판정 결과를 함께 싣는다 (S15P21E201-268). */
	public static ItineraryEditResponse of(ItineraryDetailResponse detail, ItineraryVersion saved,
			ItineraryOpeningHoursChecker.Result openingHours) {

		List<Warning> warnings = openingHours.violations().stream()
				.map((violation) -> new Warning(violation.code(), violation.itemKey(),
						violation.placeId(), violation.at()))
				.toList();
		List<NotChecked> notChecked = openingHours.notChecked().stream()
				.map((entry) -> new NotChecked(entry.check(), entry.reason()))
				.toList();

		return new ItineraryEditResponse(
				detail.id(),
				detail.title(),
				detail.version(),
				detail.days(),
				detail.totalEstimatedCostKrw(),
				detail.totalWalkingMeters(),
				detail.fallbackMode(),
				saved.baseVersion(),
				saved.operation().name(),
				saved.createdBy(),
				saved.requestId(),
				saved.createdAt().toString(),
				detail.myRole(),
				detail.canEdit(),
				saved.revertedFromVersion(),
				warnings,
				notChecked);
	}
}
