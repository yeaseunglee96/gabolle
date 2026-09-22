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
 * 갈래 열람을 기록하고 센다.
 *
 * <p>기록은 부수적인 일이라 {@link #record} 는 어떤 실패도 위로 던지지 않는다 — 못 세는 것보다
 * 화면이 안 열리는 것이 훨씬 나쁘다. 저장을 {@link PlaceFacetViewWriter} 가 자기 트랜잭션에서
 * 하는 이유는 그 클래스 머리말에 있다.
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
	 * @param facetKey 값을 검사하지 않는다 — 갈래가 늘거나 이름이 바뀌어도 그때의 값으로 남아야
	 *                 하고, 모르는 값이 오면 그것 자체가 알아야 할 사실이다
	 */
	public void record(String facetKey, UUID tripId) {
		try {
			this.writer.save(facetKey, tripId, OffsetDateTime.now(this.clock));
		}
		catch (RuntimeException ex) {
			log.warn("갈래 열람 기록에 실패했습니다 — 화면은 그대로 둡니다. facetKey={}, tripId={}",
					facetKey, tripId, ex);
		}
	}

	/**
	 * 여행에 안 묶인 전역 탐색에서 갈래를 열었다는 사실을 남긴다. 같은 표에 쌓이므로 집계는
	 * 여행 안 열람과 한 숫자로 합쳐진다.
	 */
	public void recordGlobal(String facetKey) {
		try {
			this.writer.saveGlobal(facetKey, OffsetDateTime.now(this.clock));
		}
		catch (RuntimeException ex) {
			log.warn("갈래 열람 기록에 실패했습니다 — 화면은 그대로 둡니다. facetKey={}, 전역 탐색", facetKey, ex);
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

	public record FacetViewCount(String facetKey, long views) {
	}
}
