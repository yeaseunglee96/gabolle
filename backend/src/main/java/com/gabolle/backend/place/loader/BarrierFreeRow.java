package com.gabolle.backend.place.loader;

import java.util.List;

/** 무장애 자료에서 뽑은 한 장소. {@code contentId} 는 관광공사 식별자로, 장소 id 를 여기서 만든다. */
public record BarrierFreeRow(String contentId, List<String> codes) {
}
