package com.gabolle.backend.place.api;

import java.util.List;

import com.gabolle.backend.place.adapter.OriginCandidate;

/**
 * 출발지 검색 응답.
 *
 * <p>{@code degraded} 가 {@code true} 인 것은 오류가 아니다 — 카카오 키가 없거나 호출이 실패해서
 * {@code place} 표 대체 목록으로 내려갔다는 뜻이고, HTTP 상태는 여전히 200 이다.
 */
public record OriginSearchResponse(List<Item> items, boolean degraded, String degradedReason, int limit) {

	/** 후보 한 건. {@code OriginCandidate} 를 그대로 노출하지 않고 API 계약으로 한 번 감싼다. */
	public record Item(String name, String address, double lat, double lng, String externalId, String source) {

		public static Item from(OriginCandidate candidate) {
			return new Item(candidate.name(), candidate.address(), candidate.lat(), candidate.lng(),
					candidate.externalId(), candidate.source().name());
		}
	}
}
