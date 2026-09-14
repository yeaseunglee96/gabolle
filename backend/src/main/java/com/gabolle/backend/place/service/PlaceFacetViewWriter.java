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
 * 갈래 열람 기록을 <b>자기 트랜잭션에서</b> 저장한다 — S15P21E201-475.
 *
 * <h2>왜 클래스를 나눴나</h2>
 * 실패를 삼키는 자리가 <b>트랜잭션 밖</b>이어야 한다. 삼키는 코드를 트랜잭션 안에 두면 그
 * {@code catch} 는 아무것도 못 잡는다 — 저장 실패는 대개 <b>커밋할 때</b> 터지고, 그 커밋은
 * 트랜잭션 경계를 벗어나는 순간에 일어난다. 실제로 한 클래스로 만들었다가 그 상태로 검사가
 * 500 을 받았다.
 *
 * <p>그리고 같은 클래스 안에서 자기 메서드를 부르면 Spring 프록시를 지나지 않아
 * {@code @Transactional} 이 <b>조용히 무효</b>가 된다. 이 저장소는 같은 이유로
 * {@code RecommendationRecorder} 와 {@code RecommendationJobWorker} 를 이미 갈라 뒀다.
 *
 * <p>{@code REQUIRES_NEW} 인 이유는 부르는 쪽 트랜잭션을 지키기 위해서다. 얹으면 이 저장이
 * 실패했을 때 바깥 트랜잭션까지 롤백 표시가 붙어, 예외를 잡아도 바깥이 커밋에서 터진다.
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

	/** 여행에 안 묶인 전역 탐색에서 연 기록 — S15P21E201-894. 트랜잭션 규칙은 위와 같다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void saveGlobal(String facetKey, OffsetDateTime viewedAt) {
		this.repository.save(PlaceFacetView.ofGlobal(facetKey, viewedAt));
	}
}
