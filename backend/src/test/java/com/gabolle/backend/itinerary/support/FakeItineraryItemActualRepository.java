package com.gabolle.backend.itinerary.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.ItineraryItemActualRepository;

/**
 * 테스트용 대역(fake) — S15P21E201-293. DB 없이 도는 테스트가 쓴다.
 *
 * <p>🔴 이 대역은 <b>표의 UNIQUE 를 흉내낸다.</b> {@code uq_itinerary_item_actual} 이
 * {@code (itinerary_id, item_key)} 에 걸려 있으므로 같은 방문지에 두 번 넣으면 행이 늘지 않고
 * 마지막 값이 남아야 한다. 여기서 그것을 지키지 않으면 "덮어쓰기가 된다" 는 확인이 대역의
 * 관대함 위에서 도는 셈이 되어, 실제 DB 에서만 깨지는 구현을 통과시킨다.
 *
 * <p>대역이 지키지 <b>못하는</b> 것도 분명히 해 둔다 — 실제 {@code INSERT ... ON CONFLICT}
 * SQL 이 맞게 쓰였는지, TIMESTAMPTZ 왕복에서 시간대가 유지되는지는 이 대역으로 검증되지
 * 않는다. 그쪽은 {@code ItineraryActualTimeIntegrationTest} 가 실제 PostgreSQL 에서 본다.
 */
public class FakeItineraryItemActualRepository implements ItineraryItemActualRepository {

	private final Map<String, ItineraryItemActual> rows = new LinkedHashMap<>();

	@Override
	public ItineraryItemActual upsert(ItineraryItemActual actual) {
		this.rows.put(key(actual.itineraryId(), actual.itemKey()), actual);
		return actual;
	}

	@Override
	public List<ItineraryItemActual> findByItineraryId(String itineraryId) {
		List<ItineraryItemActual> found = new ArrayList<>();
		for (ItineraryItemActual actual : this.rows.values()) {
			if (actual.itineraryId().equals(itineraryId)) {
				found.add(actual);
			}
		}
		return List.copyOf(found);
	}

	/** 저장된 행 수 — "덮어썼는가" 를 재는 테스트가 행이 늘지 않았음을 확인한다. */
	public int rowCount() {
		return this.rows.size();
	}

	/** 두 값을 잇는 자리에 {@code /} 를 쓴다 — UUID 문자열에는 들어올 수 없는 글자다. */
	private static String key(String itineraryId, String itemKey) {
		return itineraryId + "/" + itemKey;
	}
}
