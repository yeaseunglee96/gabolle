package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.PlaceFacetView;
import com.gabolle.backend.place.repository.PlaceFacetViewRepository;

/**
 * 갈래 열람 기록을 자기 트랜잭션에서 저장한다.
 *
 * <p>부르는 쪽과 클래스를 나눈 이유는 둘이다. 실패를 삼키는 {@code catch} 가 트랜잭션 밖에
 * 있어야 하고(저장 실패는 대개 커밋할 때 터진다), 같은 클래스 안에서 자기 메서드를 부르면
 * Spring 프록시를 지나지 않아 {@code @Transactional} 이 조용히 무효가 된다.
 *
 * <p>{@code REQUIRES_NEW} 라야 이 저장이 실패해도 바깥 트랜잭션에 롤백 표시가 안 붙는다.
 */
@Component
@Profile({ "db", "dev" })
public class PlaceFacetViewWriter {

	private final PlaceFacetViewRepository repository;

	public PlaceFacetViewWriter(PlaceFacetViewRepository repository) {
		this.repository = repository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void save(String facetKey, UUID tripId, OffsetDateTime viewedAt) {
		this.repository.save(PlaceFacetView.of(facetKey, tripId, viewedAt));
	}

	/** 여행에 안 묶인 전역 탐색에서 연 기록. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void saveGlobal(String facetKey, OffsetDateTime viewedAt) {
		this.repository.save(PlaceFacetView.ofGlobal(facetKey, viewedAt));
	}
}
