package com.gabolle.backend.place.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;

/**
 * 여행 기간과 겹치는 축제 조회 응답 (S15P21E201-465). FE 와 이미 합의한 필드 이름이라 이름을 바꾸지
 * 않는다.
 *
 * @param count items 의 개수. 화면이 목록 길이를 다시 세지 않아도 되게 한다.
 * @param hasMore 🔴 상한에 걸려 <b>더 있는데 안 보낸</b> 회차가 있다 (S15P21E201-1011).
 *     예전에는 행 수 상한이 아예 없어서 이 칸이 필요 없었다 — 대신 축제가 늘면 응답이
 *     그만큼 커졌다. 상한을 두면서 이 칸을 <b>함께</b> 넣는다: 상한만 두고 알리지 않으면
 *     목록이 <b>조용히 잘리고</b>, 그때 사용자에게는 "있던 축제가 사라졌다" 로 보인다.
 *     참이면 화면은 {@code offset} 을 옮겨 다음 쪽을 더 받을 수 있다.
 *     <p>🔴 {@code items}·{@code count} 의 이름과 뜻은 <b>바뀌지 않았다</b> — 칸이 하나
 *     늘었을 뿐이라 이 응답을 이미 읽고 있는 화면은 그대로 동작한다.
 */
public record FestivalResponse(List<FestivalItem> items, int count, boolean hasMore) {

	/**
	 * @param title 회차 이름. {@code PlaceEventPeriod#getTitle()} 이 그대로 온다.
	 *        🔴 {@code null} 이면 이 칸을 응답 JSON 에서 통째로 뺀다 — 화면은 그때 {@code nameKo}
	 *        를 대신 쓴다. 그래서 이 record 에만 {@code @JsonInclude(NON_NULL)} 을 건다. 전역
	 *        설정을 바꾸면 이 저장소의 다른 응답도 조용히 영향을 받는다.
	 * @param photoUrl 대표 사진 주소({@code Place#getPhotoUrl()}). 🔴 지금은 채우는 경로가 없어
	 *        항상 {@code null} 이다 — 외부 사진 검색(-146 · -480)이 붙어야 값이 생긴다. 없으면
	 *        칸 자체를 뺀다. 화면이 사진 자리를 비워 두는 형태로 미리 만들어질 수 있게 칸만 둔다.
	 * @param priceLevel 입장료. {@code place_feature.feature_type = 'PRICE_LEVEL'} 행에서 온다.
	 *        그 행 자체가 없으면(아직 적재 안 됨) 이 칸을 통째로 뺀다 — {@code UNKNOWN}(수집은
	 *        했는데 못 정함)과는 다른 상태이므로 섞으면 안 된다.
	 * @param overlapDates 축제 기간과 여행 기간의 <b>교집합</b> 날짜 목록. "여행 3일차에 열린다" 를
	 *        화면이 그릴 때 쓴다. 교집합은 여행 기간이 상한이라 아무리 길어도 여행 일수를 넘지 않는다.
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
			LocalDate startDate,
			LocalDate endDate,
			PriceLevel priceLevel,
			List<LocalDate> overlapDates) {
	}

	/**
	 * 입장료 한 칸. {@code PlaceFeatureView} 와 같은 모양이다 — 입장료는 대부분 {@code ESTIMATED}
	 * (추정값)라서 화면이 "확인된 값" 과 구분해 보여줘야 한다.
	 *
	 * @param value {@code UNKNOWN} 이면 항상 {@code null} 이다 ({@code ck_place_feature_unknown_has_no_value}).
	 * @param evidenceStatus {@code VERIFIED} · {@code ESTIMATED} · {@code UNKNOWN} 중 하나.
	 */
	public record PriceLevel(JsonNode value, String evidenceStatus) {
	}
}
