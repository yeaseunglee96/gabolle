package com.gabolle.backend.itinerary.presentation.dto;

import java.util.List;

import com.gabolle.backend.recommendation.domain.FallbackMode;

/**
 * 완성된 일정표 조회 응답. 프론트가 이미 코드에 박아 놓고 부르는 모양을 그대로 따른다.
 */
public record ItineraryDetailResponse(
		String id,
		/** {@code itineraries} 표에 제목 칸이 없어 여행 기간으로 지어낸 값이다. */
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

		/** {@code OWNER} · {@code EDITOR} · {@code VIEWER} — {@code TripMember.Role} 의 이름 그대로. */
		String myRole,
		/** {@code myRole} 이 {@code OWNER} 나 {@code EDITOR} 면 {@code true} — VIEWER 는 {@code false}. */
		boolean canEdit,
		/**
		 * {@code itinerary_versions.warning_codes} 를 그대로 옮긴다. 경고가 없으면 빈 배열이지
		 * {@code null} 이 아니다.
		 */
		List<String> warningCodes,

		/** {@code trip.trip_id} 그대로. 일정은 반드시 여행 하나에 속하므로 비지 않는다. */
		String tripId,

		/**
		 * 이 일정에서 휠체어 접근을 안 재 본 항목 수. 0 보다 크면 화면이 「확인 안 된 곳이
		 * 있다」고 알린다. 참거짓 칸을 따로 두지 않는다 — 이 값이 0 보다 큰지가 곧 그 뜻이다.
		 */
		int accessibilityUnverifiedCount,

		/** 이 여행을 몇 명이 가는가. {@code trip.party_size} 그대로다. */
		int partySize) {

	/** 여행 기간의 날짜 하나 — 항목이 0개인 날도 포함된다(빈 {@code items}). */
	public record Day(String date, List<Item> items) {
	}

	public record Item(
			/** {@code itinerary_item.item_key} — PK 가 아니다. 판이 바뀌어도 같은 항목을 가리킨다. */
			String id,
			/** ISO-8601. {@code start_time} 이 없으면 {@code null}. */
			String startsAt,
			String title,
			/** 항상 {@code null} — {@code place} 표에 설명 칸이 없다. 주소를 대신 넣지 않는다. */
			String description,
			Integer estimatedCostKrw,
			/** 이 항목으로 들어오는 구간의 도보 거리. 그 구간이 없거나 도보가 아니면 {@code null}. */
			Integer walkingMeters,
			boolean locked,
			/** {@code VERIFIED} · {@code ESTIMATED} · {@code UNKNOWN}. */
			String dataStatus,

			/**
			 * 실제로 도착한 시각. ISO-8601 + Asia/Seoul. 계획인 {@code startsAt} 을 덮어쓰지 않고
			 * 자리를 따로 둔다. 기록이 없으면 {@code null} 이고, 그때도 칸 자체는 응답에 있다.
			 */
			String actualArrivedAt,
			/** 실제로 출발한 시각. 없으면 {@code null} — 도착만 적고 출발은 안 적을 수 있다. */
			String actualDepartedAt,
			/**
			 * {@code place.place_id} 그대로. 항목은 반드시 장소 하나를 가리키므로 비지 않는다.
			 * 제목으로 장소를 다시 찾지 않는다 — 같은 이름의 가게가 여럿이다.
			 */
			String placeId,

			/**
			 * 이 방문지로 오는 데 걸리는 시간(분). 그 구간이 없으면(그날 첫 방문지 앞) {@code null} 이다.
			 */
			Integer travelDurationMin,
			/**
			 * {@code travelDurationMin} · {@code walkingMeters} 를 얼마나 믿을 수 있는가 —
			 * {@code VERIFIED} 길찾기 실제 응답 · {@code ESTIMATED} 직선거리 어림값 ·
			 * {@code UNKNOWN} 좌표가 없어 못 쟀다. 이 기능 이전에 만들어진 판은 {@code null}.
			 */
			String travelDataStatus,
			/**
			 * 이 항목으로 들어오는 구간의 이동 요금(원). {@code null} 은 모른다는 뜻이고
			 * {@code 0} 은 공짜라는 뜻이다. 값이 있는 것은 자동차 계열(택시·자가용·렌터카)뿐이다.
			 * 그 장소에 들어가는 돈인 {@code estimatedCostKrw} 와 더하지 않는다.
			 */
			Integer travelFareKrw,

			/**
			 * 이 항목에 붙은 경고. {@code itinerary_item.warning_codes} 를 그대로 옮긴다. 경고가
			 * 없으면 빈 배열이지 {@code null} 이 아니다 — 판 수준 {@code warningCodes} 와 같다.
			 * 프런트는 {@code src/plan/warningLabels.ts} 에 짝이 없는 코드를 그리지 않으므로,
			 * 새 코드를 내보낼 때는 그 사전도 함께 본다.
			 */
			List<String> warningCodes,

			/**
			 * 이 방문지의 좌표. 모르면 {@code null} 이지 {@code 0} 이 아니다 — 위도 0·경도 0 은
			 * 기니만 바다 한가운데이고 지도에 실제로 점이 찍힌다.
			 */
			Double lat,
			Double lng) {
	}
}
