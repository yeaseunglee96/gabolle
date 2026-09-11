package com.gabolle.backend.place.loader;

import java.util.List;

/**
 * 무장애 자료에서 뽑은 한 장소 — S15P21E201-331.
 *
 * @param contentId 관광공사 식별자. 장소 id 를 여기서 만든다
 * @param codes     붙일 접근성 코드. 비어 있으면 이 줄은 아무것도 안 남긴다
 */
public record BarrierFreeRow(String contentId, List<String> codes) {
}
