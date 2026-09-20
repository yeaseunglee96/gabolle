package com.gabolle.backend.place.loader;

import java.time.LocalDate;

/**
 * 가게 하나의 폐업 여부.
 *
 * @param storeId 상가업소번호. {@link SbizPlaceLoader#placeIdOf} 가 이것으로 장소 아이디를
 *     만든다 — 같은 번호는 언제나 같은 장소다
 * @param closedOn 폐업일자. 여기서만은 {@code null} 이 「모른다」가 아니라 「영업 중」이다 —
 *     인허가와 이어진 줄만 오기 때문이다
 */
public record PlaceClosureRow(String storeId, LocalDate closedOn) {
}
