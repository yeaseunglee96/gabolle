package com.gabolle.backend.itinerary.presentation.dto;

import java.util.List;

import com.gabolle.backend.itinerary.application.ItineraryOpeningHoursChecker;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.recommendation.domain.FallbackMode;

/**
 * 편집이 만든 새 판 — 고정·해제의 응답 본문.
 * 앞 일곱 칸은 {@link ItineraryDetailResponse} 와 이름·타입이 같고, 그것이 계약이다. 앱은 고정
 * 요청의 응답을 일정 전체로 받아 화면 상태에 그대로 넣으므로 {@code days} 가 없으면 그 자리에서
 * 화면이 깨진다. 앞 일곱 칸의 이름·타입은 조회 쪽과 함께 바꿔야 한다.
 * {@link ItineraryDetailResponse} 를 그대로 쓰지 않는 이유는 편집 응답에 판 번호·바탕 판·편집
 * 종류가 필요해서다. 그것을 조회 DTO 에 넣으면 조회 응답까지 편집 메타데이터로 오염된다.
 * 뒤쪽 칸들은 앱이 안 읽는다. JSON 은 모르는 키를 무시하므로 더해도 안전하다.
 * {@code canEdit} 은 항상 {@code true} 다 — 이 응답이 나온다는 것 자체가 편집을 성공한 뒤라는
 * 뜻이고, VIEWER 였다면 여기까지 오기 전에 403 으로 막힌다. 그래도 조회 응답과 모양을 맞추려고
 * 그대로 옮겨 싣는다.
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
		/** operation 이 REVERT 일 때 어느 판으로 돌아갔나. 그 외에는 null. 맨 끝에 붙여 앞 칸을 흔들지 않는다. */
		Integer revertedFromVersion,

		/**
		 * 이 편집의 결과에 대해 알려 줄 것. 지금은 영업시간 위반 하나뿐이다.
		 * 비어 있는 것은 "봤고 알릴 것이 없음" 이다. 봤는지 자체는 {@code notChecked} 가 말한다.
		 */
		List<Warning> warnings,

		/**
		 * 못 한 검사와 그 이유.
		 * 영업시간을 못 보는 상태에서 경고 목록만 비워 보내면 화면은 그것을 "확인했고 문제 없음" 으로
		 * 그린다. 못 본 것을 못 봤다고 말해야 틀린 안심을 안 준다. 장소 후보 조회의
		 * {@code notApplied} 와 같은 어휘다.
		 */
		List<NotChecked> notChecked) {

	/**
	 * @param itemId 어긴 항목. {@code days[].items[].id} 와 같은 값이라 화면이 두 목록을 이어
	 *               붙일 수 있다
	 * @param at 어긴 시각. ISO-8601 + 시간대
	 */
	public record Warning(String code, String itemId, String placeId, String at) {
	}

	public record NotChecked(String check, String reason) {
	}

	/**
	 * 알릴 것을 판정하지 않는 편집 — 고정·더하기·되돌리기가 이 자리로 온다. 그날의 방문 순서를
	 * 바꾸지 않으므로 영업시간 판정을 새로 할 이유가 없다.
	 */
	public static ItineraryEditResponse of(ItineraryDetailResponse detail, ItineraryVersion saved) {
		return of(detail, saved, ItineraryOpeningHoursChecker.Result.notEvaluated());
	}

	/** 순서 바꾸기 — 판정 결과를 함께 싣는다. */
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
