package com.gabolle.backend.place.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.repository.PlaceFacetViewRepository;

/**
 * 갈래 열람을 기록하고 세는 자리 (S15P21E201-475).
 *
 * <h2>기록은 부수적인 일이다</h2>
 * 티켓의 완료 기준에 <i>"기록 저장을 실패시켜도 갈래 목록은 그대로 열린다"</i> 가 있다. 그래서
 * {@link #record} 는 <b>어떤 실패도 위로 던지지 않는다.</b> 화면이 갈래를 여는 것과 우리가
 * 그것을 세는 것은 사용자에게 전혀 다른 무게다 — 세다가 실패해서 화면이 안 열리면 기능을 하나
 * 잃는 것이고, 못 세면 우선순위 판단이 하루치 흐려지는 것뿐이다.
 *
 * <p>저장은 {@link PlaceFacetViewWriter} 가 자기 트랜잭션에서 한다. 왜 나눴는지는 그 클래스
 * 머리말에 있다 — 요약하면 <b>삼키는 자리가 트랜잭션 밖이어야</b> 한다.
 */
@Service
@Profile({ "db", "dev" })
public class PlaceFacetViewService {

	private static final Logger log = LoggerFactory.getLogger(PlaceFacetViewService.class);

	private final PlaceFacetViewRepository repository;

	private final PlaceFacetViewWriter writer;

	private final Clock clock;

	public PlaceFacetViewService(PlaceFacetViewRepository repository, PlaceFacetViewWriter writer, Clock clock) {
		this.repository = repository;
		this.writer = writer;
		this.clock = clock;
	}

	/**
	 * 갈래를 열었다는 사실을 남긴다. 실패하면 로그만 남기고 넘어간다.
	 *
	 * @param facetKey 어느 갈래인가. 값을 검사하지 않는다 — 갈래가 늘거나 이름이 바뀌어도 그때의
	 *                 값으로 남아야 하고, 모르는 값이 오면 그것 자체가 알아야 할 사실이다
	 * @param tripId   어느 여행에서 열었나
	 */
	public void record(String facetKey, UUID tripId) {
		try {
			this.writer.save(facetKey, tripId, OffsetDateTime.now(this.clock));
		}
		catch (RuntimeException ex) {
			// 화면은 이미 갈래를 열었다. 여기서 던지면 그 화면이 대신 실패한다.
			log.warn("갈래 열람 기록에 실패했습니다 — 화면은 그대로 둡니다. facetKey={}, tripId={}",
					facetKey, tripId, ex);
		}
	}

	/** 갈래별 열람 수를 많은 순으로. 한 번도 안 열린 갈래는 여기 안 나온다. */
	@Transactional(readOnly = true)
	public List<FacetViewCount> counts() {
		List<FacetViewCount> counts = new ArrayList<>();
		for (Object[] row : this.repository.countByFacetKey()) {
			counts.add(new FacetViewCount((String) row[0], ((Number) row[1]).longValue()));
		}
		return List.copyOf(counts);
	}

	/**
	 * @param facetKey 갈래 코드
	 * @param views    그 갈래를 연 횟수
	 */
	public record FacetViewCount(String facetKey, long views) {
	}
}
