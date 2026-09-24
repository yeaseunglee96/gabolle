package com.gabolle.backend.place.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.gabolle.backend.place.domain.Place;

import tools.jackson.databind.JsonNode;

/**
 * 여행 기간과 겹치는 축제 조회 응답. FE 와 이미 합의한 필드 이름이라 이름을 바꾸지 않는다.
 *
 * @param hasMore 상한에 걸려 더 있는데 안 보낸 회차가 있다. 참이면 화면은 {@code offset} 을 옮겨
 *     다음 쪽을 더 받을 수 있다.
 */
public record FestivalResponse(List<FestivalItem> items, int count, boolean hasMore) {

	/**
	 * @param title 회차 이름. {@code null} 이면 이 칸을 응답 JSON 에서 통째로 뺀다 — 화면은 그때
	 *        {@code nameKo} 를 대신 쓴다. 그래서 전역 설정 대신 이 record 에만
	 *        {@code @JsonInclude(NON_NULL)} 을 건다.
	 * @param photoUrl 대표 사진 주소. 없으면 칸 자체를 뺀다
	 * @param photoSource 사진 출처 표기 문구. 사진을 그리면 이것도 함께 그려야 한다 — 저작권 표기
	 *        없이 남의 사진을 쓰지 않으려고 주소와 짝으로 둔 값이다. 없으면 칸을 뺀다
	 * @param photoSubject 그 사진이 무엇을 찍은 것인가. {@code SELF}=이 축제를 찍은 사진,
	 *        {@code VENUE}=이 축제가 열리는 곳을 찍은 사진. 화면은 이 값으로 둘을 갈라 그려야 한다 —
	 *        {@code VENUE} 를 그냥 띄우면 축제를 찍은 사진으로 읽힌다. {@code null} 은 모른다는
	 *        뜻이고, 모르는 것을 {@code SELF} 로 다루지 않는다
	 * @param photoLicense 사진의 라이선스 — 이름·주소·원본 파일 페이지(S15P21E201-1606). 위키미디어
	 *        사진(CC BY 등)은 출처 문구와 함께 이것을 보여야 쓸 수 있다. 없으면 칸을 뺀다
	 * @param priceLevel 입장료. {@code place_feature.feature_type = 'PRICE_LEVEL'} 행에서 온다.
	 *        그 행 자체가 없으면 이 칸을 통째로 뺀다 — {@code UNKNOWN}(수집은 했는데 못 정함)과는
	 *        다른 상태이므로 섞으면 안 된다.
	 * @param overlapDates 축제 기간과 여행 기간의 교집합 날짜 목록. 여행 기간이 상한이라 아무리
	 *        길어도 여행 일수를 넘지 않는다.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FestivalItem(
			UUID placeId,
			String nameKo,
			String nameEn,
			String title,
			String address,
			Double lat,
			Double lng,
			String photoUrl,
			// photoUrl 바로 옆에 둔다. 떨어뜨려 두면 사진만 그리고 출처·피사체를 빠뜨리기 쉽다.
			String photoSource,
			Place.PhotoSubject photoSubject,
			Place.PhotoLicense photoLicense,
			LocalDate startDate,
			LocalDate endDate,
			PriceLevel priceLevel,
			List<LocalDate> overlapDates) {
	}

	/**
	 * 입장료 한 칸. 입장료는 대부분 {@code ESTIMATED}(추정값)라서 화면이 "확인된 값" 과 구분해
	 * 보여줘야 한다.
	 *
	 * @param value {@code UNKNOWN} 이면 항상 {@code null} 이다 ({@code ck_place_feature_unknown_has_no_value}).
	 * @param evidenceStatus {@code VERIFIED} · {@code ESTIMATED} · {@code UNKNOWN} 중 하나.
	 */
	public record PriceLevel(JsonNode value, String evidenceStatus) {
	}
}
