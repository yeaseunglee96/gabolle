package com.gabolle.backend.itinerary.presentation.dto;

import java.util.List;

import com.gabolle.backend.recommendation.domain.FallbackMode;

/**
 * 완성된 일정표 조회 응답 — S15P21E201-604. 프론트가 이미 코드에 박아 놓고 부르는 모양을
 * 그대로 따른다({@code ItineraryQueryController} 참고).
 *
 * <p>🔴 {@code ItineraryVersionDto}(API 명세 4.4, 참조 0건이라 이 작업에서 지웠다)를 대신하지
 * 않는다 — 그 DTO 는 판(version) 하나를 편집 응답 모양 그대로 실었고, 이 DTO 는 화면이
 * 그리는 "완성된 일정표" 모양이다. 목적이 다르다.
 */
public record ItineraryDetailResponse(
		String id,
		/** 🔴 {@code itineraries} 표에 제목 칸이 없어 여행 기간으로 지어낸 값이다(근거는
		 * {@code ItineraryQueryService} 참고). */
		String title,
		int version,
		List<Day> days,
		/** 항목 비용의 합. 전부 {@code null} 이면 {@code null} — {@code 0} 은 "무료"라는 다른 사실이다. */
		Integer totalEstimatedCostKrw,
		/** 구간 도보 거리의 합. 하나도 없으면 {@code 0} 이 아니라 {@code null} — "안 걸었다"와
		 * "안 쟀다"는 다르다. */
		Integer totalWalkingMeters,
		/** 이 판을 만든 추천 요청의 fallback_mode. 사용자가 손으로 만든 판이면 {@code null}. */
		FallbackMode fallbackMode,

		/**
		 * 🔴 S15P21E201-224 — 맨 뒤에 더한 칸이다. 앱은 아직 이 칸을 안 읽는다(모르는
		 * JSON 키는 무시하므로 맨 뒤에 더하는 것이 안전하다). {@code OWNER} · {@code EDITOR}
		 * · {@code VIEWER} — {@code TripMember.Role} 의 이름 그대로.
		 */
		String myRole,
		/** {@code myRole} 이 {@code OWNER} 나 {@code EDITOR} 면 {@code true} — VIEWER 는 {@code false}. */
		boolean canEdit,
		/**
		 * 🔴 2026-09-07 추가 — S15P21E201-218 완료 기준("경고가 있는 일정은 경고 목록이
		 * 응답에 함께 들어온다")의 마지막 남은 항목. {@code itinerary_versions.warning_codes}
		 * 를 그대로 옮긴다. 경고가 없으면 빈 배열이지 {@code null} 이 아니다.
		 */
		List<String> warningCodes) {

	/** 여행 기간의 날짜 하나 — 항목이 0개인 날도 포함된다(빈 {@code items}). */
	public record Day(String date, List<Item> items) {
	}

	public record Item(
			/** {@code itinerary_item.item_key} — PK 가 아니다. 판이 바뀌어도 같은 항목을 가리킨다. */
			String id,
			/** ISO-8601. {@code start_time} 이 없으면 {@code null}. */
			String startsAt,
			String title,
			/** 🔴 항상 {@code null} — {@code place} 표에 설명 칸이 없다. 주소를 대신 넣지 않는다. */
			String description,
			Integer estimatedCostKrw,
			/** 이 항목으로 들어오는 구간의 도보 거리. 그 구간이 없거나 도보가 아니면 {@code null}. */
			Integer walkingMeters,
			boolean locked,
			/** {@code VERIFIED} · {@code ESTIMATED} · {@code UNKNOWN}. */
			String dataStatus) {
	}
}
