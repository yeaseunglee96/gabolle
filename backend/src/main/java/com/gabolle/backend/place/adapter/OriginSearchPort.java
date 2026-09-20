package com.gabolle.backend.place.adapter;

import java.util.List;

/**
 * 출발지 후보를 찾는 자리. 카카오 로컬 검색이든 {@code place} 표를 이용한 대체 목록이든 이
 * 인터페이스 뒤에 숨어서, {@link com.gabolle.backend.place.service.OriginSearchService} 는 어느
 * 쪽이 호출됐는지 몰라도 된다.
 */
public interface OriginSearchPort {

	/**
	 * {@code query} 로 후보를 찾는다. 실패하면 예외를 던진다 — 대체 목록으로 내려갈지는 이 포트가
	 * 아니라 {@code OriginSearchService} 가 판단한다.
	 */
	List<OriginCandidate> search(String query, int limit);
}
