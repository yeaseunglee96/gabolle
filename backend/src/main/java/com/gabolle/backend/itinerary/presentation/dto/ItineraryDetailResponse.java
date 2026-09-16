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
		List<String> warningCodes,

		/**
		 * 🔴 S15P21E201-1113 — 맨 뒤에 더한 칸이다. {@code trip.trip_id} 그대로다.
		 *
		 * <p>앱의 주소는 {@code /trips/{id}/itinerary} 인데 그 {@code id} 자리에 <b>일정 번호</b>가
		 * 들어간다. 그래서 일정 화면에 서 있는 앱은 <b>자기가 어느 여행에 속하는지 알 방법이
		 * 없었고</b>, 추천으로 넘어갈 때 일정 번호를 여행 번호인 척 넘겨
		 * {@code GET /api/v1/trips/{tripId}/recommendation-jobs} 가 404 를 냈다. 화면은 그 404 를
		 * 「아직 추천이 없어요」로 그렸다 — 실제로는 그 여행에 성공한 추천이 있었다(2026-09-16 실측).
		 *
		 * <p>목록을 한 번 더 받아 맞추게 하지 않는다. 일정은 반드시 여행 하나에 속하므로 이 칸은
		 * 비지 않고, 아는 쪽이 알려주는 것이 부르는 쪽이 뒤지는 것보다 싸다.
		 */
		String tripId) {

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
			String dataStatus,

			/**
			 * 2026-09-07 추가 — S15P21E201-293. <b>실제로</b> 도착한 시각이다.
			 * {@code startsAt}(계획)과 다른 사실이라 그 칸을 덮어쓰지 않고 자리를 따로 둔다.
			 *
			 * <p>기록이 없으면 {@code null} 이고 <b>그때도 이 칸은 응답에 있다.</b> 칸을 아예
			 * 빼면 화면은 "아직 안 갔다" 와 "이 서버는 이 기능을 모른다" 를 구분할 수 없다.
			 * 형식은 {@code startsAt} 과 같은 ISO-8601 + 시간대(Asia/Seoul)다.
			 */
			String actualArrivedAt,
			/** 실제로 출발한 시각. 없으면 {@code null} — 도착만 적고 출발은 안 적을 수 있다. */
			String actualDepartedAt,
			/**
			 * 🔴 S15P21E201-744 — 맨 뒤에 더한 칸이다. {@code place.place_id} 그대로다.
			 * 앱이 "이 장소 평가하기" 버튼을 눌러 리뷰 API({@code /api/v1/places/{placeId}/reviews})로
			 * 넘어갈 때, 그리고 장소 상세로 넘어갈 때 쓰는 값이다. 항목은 반드시 장소 하나를
			 * 가리키므로 이 칸은 비지 않는다. 제목으로 장소를 다시 찾지 않는다 — 같은 이름의
			 * 가게가 여럿이면 엉뚱한 가게에 리뷰가 달린다.
			 */
			String placeId,

			/**
			 * 🔴 2026-09-08 추가 — S15P21E201-179. <b>이 방문지로 오는 데 걸리는 시간</b>(분).
			 * 그 구간이 없으면(그날 첫 방문지 앞) {@code null} 이다.
			 *
			 * <p>지금까지 이 값은 아예 없었다. 일정에 이동 시간이 안 들어가서 화면은 "9시에
			 * 여기, 11시에 저기" 만 보여줄 뿐 그 사이에 얼마나 걸리는지 말할 수 없었다.
			 */
			Integer travelDurationMin,
			/**
			 * 🔴 위 두 값({@code travelDurationMin} · {@code walkingMeters})을 얼마나 믿을 수
			 * 있는가 — {@code VERIFIED} 길찾기 실제 응답 · {@code ESTIMATED} 직선거리 어림값 ·
			 * {@code UNKNOWN} 좌표가 없어 못 쟀다. 이 기능 이전에 만들어진 판은 {@code null}.
			 *
			 * <p><b>화면은 이 값을 반드시 봐야 한다.</b> {@code ESTIMATED} 를 실제 소요시간처럼
			 * 그리면 사용자는 그 시간에 맞춰 움직이다 늦는다. 참·거짓이 아닌 이유는 "어림잡았다"
			 * 와 "아무것도 못 쟀다" 가 화면에 서로 다르게 그려져야 하기 때문이다.
			 */
			String travelDataStatus,
			/**
			 * 🔴 S15P21E201-1109 — 이 항목으로 들어오는 구간의 <b>이동 요금(원)</b>.
			 *
			 * <p><b>{@code null} 은 "얼마인지 모른다" 이고 {@code 0} 은 "공짜다" 다.</b> 화면이
			 * 둘을 같게 그리면 요금 출처가 없는 이동수단이 전부 「무료」로 보인다. 모르면
			 * <b>줄을 만들지 않는 것</b>이 맞다.
			 *
			 * <p>지금 값이 있는 것은 자동차 계열(택시·자가용·렌터카)뿐이다 — 카카오모빌리티가
			 * 택시 요금과 통행료를 주기 때문이다. 도보에는 요금이라는 것이 없고, 대중교통은
			 * 업체가 주지 않는다(노선망이 들어오면 계산한다 — S15P21E201-1104).
			 *
			 * <p>🔴 <b>{@code estimatedCostKrw} 와 더하지 마라.</b> 이쪽은 "가는 데 드는 돈",
			 * 저쪽은 "그 장소에 들어가는 데 드는 돈" 이다. 합치면 입장료 자료가 없는 지금
			 * "교통비만 낸 합계" 가 "총비용" 으로 읽힌다.
			 */
			Integer travelFareKrw) {
	}
}
