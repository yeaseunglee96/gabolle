package com.gabolle.backend.place.adapter;

/**
 * 출발지 후보 한 건. 카카오 로컬 검색이든 {@code place} 표 대체 검색이든 이 모양으로 맞춰서
 * 돌려준다 — {@code OriginSearchService} 가 출처를 몰라도 그대로 응답에 실을 수 있게.
 */
public record OriginCandidate(String name, String address, double lat, double lng, String externalId,
		Source source) {

	/** 이 후보가 어디서 왔는가. 응답의 {@code source} 필드로 그대로 나간다. */
	public enum Source {
		KAKAO_LOCAL,
		INTERNAL_FALLBACK
	}
}
