package com.gabolle.backend.route.transit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 부산 시내버스 노선망을 자원 파일에서 올린다 — S15P21E201-1123.
 *
 * <h2>어디서 온 자료인가</h2>
 *
 * BIMS(부산 버스 정보) 수집본 셋을 합쳐 압축한 것이다 — 노선 목록(번호·종류·배차간격·
 * 첫차·막차), 노선별 정류장 순번, 정류장 좌표. 만든 방법과 원본 경로는 파일 안의
 * {@code source} 에 적혀 있다.
 *
 * <p>노선 <b>290개</b>가 방향별로 갈려 <b>367갈래</b>가 되고, 정류장은 <b>8,315곳</b>이다.
 *
 * <h2>🔴 왜 방향별로 갈라 두었나</h2>
 *
 * 버스는 왕복이라 <b>같은 정류장을 두 번 지난다</b>(1003번의 부산역은 46번째이자 65번째다).
 * 그런데 {@link TransitNetwork} 는 {@code (노선, 정류장) → 순번} 을 <b>하나만</b> 기억한다.
 * 안 가르고 넣으면 한쪽 방향이 조용히 지워지고, "갈 수는 있는데 못 찾는" 경로가 생긴다.
 * 그래서 만들 때 갈랐다 — id 뒤의 {@code #1}·{@code #2} 가 그것이다.
 *
 * <h2>🔴 운행(시각표)은 넣지 않는다</h2>
 *
 * BIMS 는 시각표를 주지 않는다. 배차간격과 첫차·막차로 시각표를 <b>지어낼</b> 수는 있지만
 * 그러지 않는다. 이유가 둘이다.
 *
 * <ol>
 * <li>노선당 약 100편 × 367갈래 × 정류장 수십 개 = <b>수백만 개의 시각</b>이 메모리에 올라간다</li>
 * <li>더 나쁜 것은 <b>그럴듯해진다</b>는 점이다. "18시 12분 차" 는 구체적이라서 더 믿게
 *     만드는데 근거가 없다</li>
 * </ol>
 *
 * <p>대신 {@link HeadwayJourneyPlanner} 가 배차간격으로 <b>걸리는 시간만</b> 낸다.
 * 시각표가 생기면(지하철 API) 그때 {@link RaptorPlanner} 가 맡으면 된다 — 그쪽을 지우지
 * 않은 이유다.
 *
 * <h2>파일이 없거나 깨져도 기동은 안 막는다</h2>
 *
 * 빈 노선망으로 떨어진다. 부르는 쪽({@code TransitRouteAdapter})이 그때 어림값으로 가고,
 * 그건 고장이 아니라 정상 흐름의 한 갈래다. <b>경로 하나 때문에 서버 전체가 안 뜨는 것이
 * 훨씬 나쁘다.</b>
 */
@Primary
@Component
public class BusanBusNetworkPort implements TransitNetworkPort {

	/**
	 * 🔴 {@code @Primary} 를 붙인 이유 — {@link EmptyTransitNetworkPort} 의 javadoc 이
	 * "진짜 노선망 구현을 더할 때 이렇게 하라" 고 적어 둔 그대로다. 둘 다 조건 없이
	 * 등록되므로 이게 없으면 스프링이 "어느 것을 쓸지 모르겠다" 로 기동을 실패시킨다.
	 */
	static final String RESOURCE_PATH = "transit/busan-bus-network.json";

	private static final Logger log = LoggerFactory.getLogger(BusanBusNetworkPort.class);

	private final TransitNetwork network;

	public BusanBusNetworkPort(ObjectMapper objectMapper) {
		this.network = load(objectMapper);
	}

	@Override
	public TransitNetwork network() {
		return this.network;
	}

	private static TransitNetwork load(ObjectMapper objectMapper) {
		ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
		if (!resource.exists()) {
			log.warn("대중교통 노선망 파일이 없다 ({}). 대중교통 경로는 어림값으로 답한다.", RESOURCE_PATH);
			return TransitNetwork.empty();
		}
		try (InputStream in = resource.getInputStream()) {
			JsonNode root = objectMapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			List<TransitNetwork.Stop> stops = readStops(root.get("stops"));
			List<TransitNetwork.Route> routes = readRoutes(root.get("routes"));
			// 🔴 운행도 환승도 비운다. 위 javadoc 참고 — 환승은 배차간격 탐색기가 아직
			//    한 번 타는 길만 보기 때문이고(S15P21E201-1123 의 다음 걸음), 운행은
			//    지어내지 않기 때문이다.
			TransitNetwork built = TransitNetwork.of(stops, routes, List.of(), List.of());
			log.info("대중교통 노선망을 올렸다 — 정류장 {}곳 · 노선 갈래 {}개", stops.size(), routes.size());
			return built;
		}
		catch (IOException | RuntimeException failure) {
			// 🔴 삼키지 않고 무엇이 잘못됐는지 남긴다. 다만 기동은 계속한다.
			log.warn("대중교통 노선망을 못 읽었다 ({}). 대중교통 경로는 어림값으로 답한다.",
					failure.toString());
			return TransitNetwork.empty();
		}
	}

	/**
	 * {@code "04:20"} 을 자정부터의 분으로. 값이 없거나 모양이 다르면 {@code fallback}.
	 *
	 * <p>🔴 {@code 24:00} 이 넘는 값을 쓰는 원천이 있다(새벽 1시를 25:00 으로 적는 식).
	 * 하루 범위로 되접는다 — 안 그러면 노선 하나가 통째로 안 실린다.
	 */
	private static int minuteOfDay(JsonNode node, int fallback) {
		if (node == null || node.isNull()) {
			return fallback;
		}
		String raw = node.asString();
		int colon = (raw == null) ? -1 : raw.indexOf(':');
		if (colon <= 0) {
			return fallback;
		}
		try {
			int hour = Integer.parseInt(raw.substring(0, colon).trim());
			int minute = Integer.parseInt(raw.substring(colon + 1).trim());
			if (minute < 0 || minute > 59) {
				return fallback;
			}
			return Math.floorMod(hour * 60 + minute, TransitNetwork.MINUTES_PER_DAY);
		}
		catch (NumberFormatException notATime) {
			return fallback;
		}
	}

	/** {@code {"정류장id": [위도, 경도, 이름, 종류]}}. 배열로 둔 것은 0.8MB 를 지키기 위해서다. */
	private static List<TransitNetwork.Stop> readStops(JsonNode node) {
		List<TransitNetwork.Stop> stops = new ArrayList<>();
		if (node == null) {
			return stops;
		}
		for (Map.Entry<String, JsonNode> entry : node.properties()) {
			JsonNode value = entry.getValue();
			stops.add(new TransitNetwork.Stop(entry.getKey(), value.get(2).asString(),
					value.get(0).asDouble(), value.get(1).asDouble(), TransitNetwork.Kind.BUS));
		}
		return stops;
	}

	private static List<TransitNetwork.Route> readRoutes(JsonNode node) {
		List<TransitNetwork.Route> routes = new ArrayList<>();
		if (node == null) {
			return routes;
		}
		for (JsonNode route : node) {
			List<String> stopIds = new ArrayList<>();
			for (JsonNode stopId : route.get("stops")) {
				stopIds.add(stopId.asString());
			}
			if (stopIds.size() < 2) {
				continue;
			}
			// 🔴 첫차·막차가 없는 노선은 하루 종일 다니는 것으로 본다. 모르는 것을 "안 다닌다"
			//    로 두면 그 노선이 조용히 사라진다 — BIMS 수집본에서는 290개 중 288개에 값이 있다.
			int first = minuteOfDay(route.get("first"), 0);
			int last = minuteOfDay(route.get("last"), TransitNetwork.MINUTES_PER_DAY - 1);
			// 🔴 S15P21E201-1291 — 원천의 type 을 그대로 실어 나른다("일반버스"·"마을버스"…).
			//    요금표의 종류 이름과 글자가 같아서 그대로 열쇠가 된다. 없으면 null 이고,
			//    그 노선이 낀 여정은 요금을 못 낸다 — 아무 줄이나 고르지 않는다.
			JsonNode type = route.get("type");
			String fareType = (type == null || type.isNull()) ? null : type.asString();
			routes.add(new TransitNetwork.Route(route.get("id").asString(),
					route.get("num").asString() + "번", TransitNetwork.Kind.BUS, stopIds,
					route.get("headway").asInt(), first, last, fareType));
		}
		return routes;
	}
}
