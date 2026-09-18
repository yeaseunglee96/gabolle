package com.gabolle.backend.route.transit;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 수단별 기본요금표 — S15P21E201-1291.
 *
 * <p>{@code transit/transit-fare.json} 을 읽어 「종류 이름 → 어른 교통카드 요금」 하나로 만든다.
 * {@code BusanBusNetworkPort} 가 노선망을 읽는 방식과 같다.
 *
 * <h2>🔴 값이 없는 종류가 있고, 그것이 정상이다</h2>
 *
 * 급행버스·심야버스(급행)는 요금이 {@code null} 이다. <b>부산시 고시에 그 이름이 아예 없어서</b>
 * 자료를 만든 사람이 지어내지 않고 비워 둔 것이다(요금표 주석에 그 경위가 있다). 여기서도
 * 비워 둔 채로 두고, 그 노선이 낀 여정은 <b>요금을 못 낸다</b>.
 *
 * <p>🔴 <b>0 으로 메우지 않는다.</b> 0 은 「공짜다」라는 다른 사실이고, 메우면 화면이 그 여정을
 * 「무료」로 그린다 — <b>틀린 값을 자신 있게 보여주는 것</b>이다. 요금표 자신이 그 규칙을
 * 적어 두었다.
 *
 * <h2>어른·교통카드 기준이다</h2>
 *
 * 요금표에는 청소년·어린이와 현금 값도 있지만 여기서는 <b>어른 교통카드</b>만 읽는다. 화면이
 * 아직 나이를 묻지 않고, 환승 할인이 <b>교통카드에만</b> 있기 때문이다(현금은 할인이 없다 —
 * BIMS 일반 환승안내). 나이를 묻게 되면 이 클래스가 그 축을 하나 더 받으면 된다.
 */
@Component
public class TransitFareTable {

	static final String RESOURCE_PATH = "transit/transit-fare.json";

	/** 요금표에서 어른 교통카드 값을 가리키는 열쇠. 버스 절에서 쓴다. */
	private static final String ADULT_CARD = "교통카드_어른";

	private final Map<String, Integer> busFareByType;

	private final int subwayStageOneKrw;

	private final int subwayStageTwoKrw;

	private final int subwayStageBoundaryMeters;

	public TransitFareTable(ObjectMapper objectMapper) {
		JsonNode root = load(objectMapper);

		JsonNode subway = require(root, "지하철");
		JsonNode stages = require(subway, "구간");
		this.subwayStageOneKrw = require(require(stages, "1구간"), ADULT_CARD).asInt();
		this.subwayStageTwoKrw = require(require(stages, "2구간"), ADULT_CARD).asInt();
		this.subwayStageBoundaryMeters = require(subway, "구간기준거리m").asInt();

		Map<String, Integer> buses = new LinkedHashMap<>();
		JsonNode kinds = require(require(root, "버스"), "종류");
		kinds.properties().forEach(entry -> {
			JsonNode fare = entry.getValue().get(ADULT_CARD);
			// 🔴 값이 없으면 넣지 않는다. 넣으면서 0 으로 바꾸면 「공짜」가 된다.
			if (fare != null && !fare.isNull()) {
				buses.put(entry.getKey(), fare.asInt());
			}
		});
		this.busFareByType = Map.copyOf(buses);
	}

	/**
	 * 그 종류의 버스 기본요금(원).
	 *
	 * @return 모르면 {@code null}. 🔴 <b>0 이 아니다</b> — 0 은 「공짜」라는 다른 사실이다
	 */
	public Integer busFareKrw(String fareType) {
		return (fareType == null) ? null : this.busFareByType.get(fareType);
	}

	/**
	 * 그 거리의 지하철 기본요금(원).
	 *
	 * <p>{@code 구간기준거리m}(10km) 이하는 1구간, 넘으면 2구간이다. 거리로 판정하는 이유는
	 * 요금표 주석에 있다 — 역 좌표가 114역 전부 있어서 잴 수 있다.
	 */
	public int subwayFareKrw(int distanceMeters) {
		return (distanceMeters <= this.subwayStageBoundaryMeters) ? this.subwayStageOneKrw
				: this.subwayStageTwoKrw;
	}

	/** 요금을 아는 버스 종류의 수. 시험과 기동 로그가 쓴다. */
	public int knownBusTypeCount() {
		return this.busFareByType.size();
	}

	private static JsonNode require(JsonNode parent, String field) {
		JsonNode node = parent.get(field);
		if (node == null || node.isNull()) {
			// 🔴 기동할 때 죽는 편이 낫다. 요금표가 깨진 채로 뜨면 모든 여정이 조용히 요금 없이 나간다.
			throw new IllegalStateException("요금표에 «" + field + "» 가 없다: " + RESOURCE_PATH);
		}
		return node;
	}

	private static JsonNode load(ObjectMapper objectMapper) {
		ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
		try (InputStream in = resource.getInputStream()) {
			return objectMapper.readTree(in);
		}
		catch (IOException e) {
			throw new IllegalStateException("요금표를 읽지 못했다: " + RESOURCE_PATH, e);
		}
	}
}
