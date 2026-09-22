package com.gabolle.backend.recommendation.presentation.dto;

import java.util.List;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.recommendation.domain.FallbackMode;

/** 추천 결과 응답. 프론트가 이미 코드에 박아 놓고 부르는 모양을 그대로 따른다. */
public record RecommendationResultResponse(
		/** {@code COMPLETED} · {@code PARTIAL} · {@code FAILED}. */
		String status,
		List<Item> items,
		String itineraryId,
		FallbackMode fallbackMode,
		List<String> conflicts,
		String errorMessage,
		/** {@code items.size()} 와 같다 — 화면이 매번 배열 길이를 세지 않아도 되게 미리 준다. */
		int placeCount,
		/**
		 * 일정의 구간(leg)마다 있는 {@code duration_min} 합이다. 값이 없는 구간은 건너뛰므로
		 * "적어도 이만큼" 이지 정확한 총합이 아닐 수 있다. 구간이 하나도 없거나 전부 값이
		 * 없으면 {@code null} 이다.
		 */
		Integer estimatedTravelMinutes,
		/**
		 * 이 결과를 만든 추천 요청의 정본 키. 앱의 행동 이벤트가 이 값으로만 "무엇을 보여줬고
		 * 그중 무엇을 골랐나" 와 이어진다. 결과가 실패여도 채운다.
		 */
		String requestId,
		/**
		 * 이 추천이 어느 여행의 것인가. 앱의 추천 화면 주소에 들어 있는 것은 작업 번호라,
		 * 담아두기·빼기를 보낼 주소를 이 값 없이는 알 수 없다. 실패 응답에서도 채우고,
		 * 여행에 매이지 않은 작업이면 {@code null} 이다.
		 */
		String tripId) {

	public record Item(
			/** {@code place_id}. */
			String id,
			/** {@code place.name_ko}. */
			String title,
			/**
			 * 대표 사진 주소({@code place.photo_url}). 없으면 {@code null} 이고, 그때 기본 이미지를
			 * 지어내지 않는다 — 갈래 아이콘을 그리는 것은 화면의 몫이다(S15P21E201-1378).
			 *
			 * <p>🔴 2026-09-22 이전에는 <b>항상 {@code null}</b> 이었고, 그 옆의 주석이
			 * <i>"{@code place} 표에 이미지 칸이 없다"</i> 고 <b>틀리게</b> 적어 두고 있었다.
			 * 칸은 {@code Place.photoUrl} 로 처음부터 있었다. 그 한 줄 때문에 사진이 있는 후보
			 * <b>710곳(반환 후보의 24%)</b>이 통째로 버려지고 있었다(S15P21E201-1496).
			 */
			String imageUrl,
			/**
			 * 사진 출처 표기 문구. {@link #imageUrl} 과 <b>짝이다</b> — 주소만 보내면 화면이
			 * 출처 없이 사진을 건다.
			 *
			 * <p>🔴 <b>이것이 선택 사항이 아닌 이유.</b> 지금 실려 있는 사진은 TourAPI 등
			 * 공공누리 자료라 <b>출처 표기가 이용 조건</b>이다. 프론트({@code PlaceVisual})는
			 * {@code photoUrl} 이 있을 때 이 값으로 출처 줄을 그리게 되어 있고
			 * (S15P21E201-1125 — 「출처 표기를 구조로 강제한다」), 서버가 이 값을 빼면 그
			 * 강제가 조용히 무력해진다.
			 */
			String photoSource,
			/**
			 * 그 사진이 무엇을 찍은 것인가. {@code SELF} 는 이 장소 자체를 찍은 사진이고
			 * {@code VENUE} 는 장소가 아니라 그곳이 열리는 자리를 찍은 사진이다.
			 *
			 * <p>목록·근처 응답이 같은 이유로 이미 싣고 있다(S15P21E201-1205) — 이 값이 없으면
			 * 화면이 「이 장소를 찍은 사진인가」 뱃지를 달 수 없어, 주변 시설 사진을 이 장소
			 * 사진처럼 그리게 된다.
			 */
			Place.PhotoSubject photoSubject,
			List<String> reasonCodes,
			/**
			 * 대표 메뉴 한 가지의 값(원). 조사된 곳만 숫자이고 <b>나머지는 {@code null}</b> 이다
			 * — {@code 0} 을 넣지 않는다. 0 은 "모름" 이 아니라 <b>"공짜"</b> 로 읽힌다.
			 *
			 * <p>🔴 2026-09-22 이전에는 <b>항상 {@code null}</b> 이었다. 그때는 비용 자료가 정말
			 * 없었고, 이제는 {@code MENU_PRICE_WON} 이 실려 있다(S15P21E201-1479). 값이 있는 곳이
			 * 아직 일부라 <b>「비었으니 무료」로 읽으면 안 된다</b>는 것은 그대로다.
			 */
			Integer estimatedCostKrw,
			/** {@code LOW} · {@code MEDIUM} · {@code HIGH}. 근거가 없으면 {@code null}. */
			String crowdLevel,
			/** 이동 관련 경고만. 없으면 {@code null}(빈 배열이 아니다). */
			List<String> mobilityWarnings,
			/** {@code VERIFIED} · {@code ESTIMATED} · {@code UNKNOWN}. */
			String dataStatus,
			FallbackMode fallbackMode,
			String itineraryId) {
	}
}
