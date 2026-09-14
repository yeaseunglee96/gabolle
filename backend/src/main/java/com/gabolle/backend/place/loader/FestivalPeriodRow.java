package com.gabolle.backend.place.loader;

import java.time.LocalDate;

/**
 * 수집본에서 읽은 축제 회차 한 건 — S15P21E201-863.
 *
 * <p>입력은 {@code bigData/collect/tourapi-festival.mjs} 가 남긴 한 줄이고, 한 줄이 축제 하나다.
 *
 * <pre>
 * {"contentid":"3576410","title":"광복로 겨울빛 트리축제",
 *  "eventstartdate":"20251205","eventenddate":"20260222"}
 * </pre>
 *
 * @param contentId 관광공사가 매긴 식별자. 장소 id 를 이 값에서 계산한다
 *                  ({@link TourApiPlaceLoader#placeIdOf}) — 장소 적재가 같은 규칙을 쓰므로 이을
 *                  단계가 따로 없다
 * @param title     회차 이름. 해마다 달라지는 부분을 담는다
 * @param startDate 시작일
 * @param endDate   종료일
 */
public record FestivalPeriodRow(String contentId, String title, LocalDate startDate, LocalDate endDate) {
}
