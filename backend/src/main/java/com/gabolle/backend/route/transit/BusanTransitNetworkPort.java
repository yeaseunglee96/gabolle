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
 * 부산 대중교통 노선망을 자원 파일에서 올린다.
 *
 * 버스는 BIMS(부산 버스 정보) 수집본 셋을 합쳐 압축한 것이고(노선 목록·노선별 정류장 순번·
 * 정류장 좌표), 지하철은 열차시각표에서 나왔다. 만든 방법과 원본 경로는 두 파일 각각의
 * {@code source} 에 적혀 있다.
 *
 * 한 클래스가 둘을 읽는 것은 합쳐야 환승이 잡히기 때문이다. {@link TransitNetwork} 하나가
 * 정류장·노선을 전부 알아야 탐색기가 「버스로 가다 지하철로 갈아탄다」를 찾는다.
 *
 * 버스 정류장은 9자리 BIMS 코드({@code 163900101}), 지하철 역은 3자리 역번호({@code 101})라
 * 지금은 식별자가 안 겹친다. 그건 우연이라 원천이 바뀌면 겹칠 수 있고, 겹치면
 * {@link TransitNetwork} 가 한쪽을 조용히 덮어쓴다 — 「갈 수는 있는데 못 찾는」 경로가 생기고
 * 아무 오류도 안 난다. 그래서 겹치면 기동 로그에 경고를 남긴다.
 *
 * 지하철은 평일 것만 올린다. 시각표가 요일별로 갈려 있어 다 올리면 같은 구간에 나란한 노선이
 * 여럿 생기고, 탐색기는 요일을 모르므로 그중 제일 빠른 것을 고른다 — 화요일 일정에 일요일
 * 배차가 섞인다. 평일을 고른 근거는 요일 사이 배차 차이가 최대 1.5분이고 방향도 일정하지
 * 않다는 것이다(1·2호선은 주말이 오히려 짧다).
 *
 * 운행(시각표)은 넣지 않는다. BIMS 는 시각표를 안 주고, 배차간격으로 지어내면 수백만 개의
 * 시각이 메모리에 올라갈뿐더러 "18시 12분 차" 는 근거 없이 구체적이라 더 믿게 만든다.
 * 지하철은 진짜 시각표가 있는데도 안 넣는다 — 버스와 서로 다른 자로 재게 되고 환승 시간이
 * 그 차이만큼 틀어진다. 대신 그 시각표에서 뽑은 역간 소요시간과 배차간격을 쓰고,
 * {@link HeadwayJourneyPlanner} 가 배차간격으로 걸리는 시간만 낸다.
 *
 * 파일이 없거나 깨져도 기동은 안 막는다. 그 파일 몫만 빈 채로 간다 — 버스가 없어도 지하철은
 * 올라오고 그 반대도 된다. 부르는 쪽({@code TransitRouteAdapter})이 빈 노선망일 때 어림값으로
 * 가는 것은 고장이 아니라 정상 흐름의 한 갈래다.
 */
@Primary
@Component
public class BusanTransitNetworkPort implements TransitNetworkPort {

	/**
	 * {@code @Primary} 가 필요한 이유 — {@link EmptyTransitNetworkPort} 도 조건 없이 등록되므로,
	 * 없으면 스프링이 어느 빈을 쓸지 몰라 기동에 실패한다.
	 */
	static final String BUS_RESOURCE_PATH = "transit/busan-bus-network.json";

	/** 지하철 노선망 자원. */
	static final String SUBWAY_RESOURCE_PATH = "transit/busan-subway-network.json";

	/**
	 * 지하철에서 올릴 요일. 원천의 글자 그대로다({@code "평일"}·{@code "토요일"}·
	 * {@code "일요일·공휴일"}) — 대응표를 두면 원천이 말을 바꿨을 때 아무것도 안 걸리고 노선이
	 * 0개가 된다. 여기서는 0개가 되면 기동 로그가 말한다.
	 */
	private static final String SUBWAY_DAY_TYPE = "평일";

	private static final Logger log = LoggerFactory.getLogger(BusanTransitNetworkPort.class);

	private final TransitNetwork network;

	public BusanTransitNetworkPort(ObjectMapper objectMapper) {
		this.network = load(objectMapper);
	}

	@Override
	public TransitNetwork network() {
		return this.network;
	}

	private static TransitNetwork load(ObjectMapper objectMapper) {
		List<TransitNetwork.Stop> stops = new ArrayList<>();
		List<TransitNetwork.Route> routes = new ArrayList<>();

		readInto(objectMapper, BUS_RESOURCE_PATH, TransitNetwork.Kind.BUS, null, stops, routes);
		readInto(objectMapper, SUBWAY_RESOURCE_PATH, TransitNetwork.Kind.SUBWAY, SUBWAY_DAY_TYPE, stops, routes);

		warnOnDuplicateIds(stops, routes);

		// 운행도 환승도 비운다. 운행은 지어내지 않기 때문이고, 걸어서 갈아타는 통로는 아직
		// 아무도 재지 않았다.
		TransitNetwork built = TransitNetwork.of(stops, routes, List.of(), List.of());
		log.info("대중교통 노선망을 올렸다 — 정류장·역 {}곳 · 노선 갈래 {}개", stops.size(), routes.size());
		return built;
	}

	/**
	 * 자원 파일 하나를 읽어 목록에 더한다. 한 파일이 없거나 깨져도 다른 파일은 올라간다 —
	 * 예외를 위로 던지면 버스 파일 하나 때문에 지하철까지 같이 사라지고, 원인이 어느 쪽인지도
	 * 로그에 안 남는다.
	 *
	 * @param dayType 지하철처럼 요일별로 갈린 원천에서 고를 요일. {@code null} 이면 전부
	 */
	private static void readInto(ObjectMapper objectMapper, String resourcePath, TransitNetwork.Kind kind,
			String dayType, List<TransitNetwork.Stop> stops, List<TransitNetwork.Route> routes) {

		ClassPathResource resource = new ClassPathResource(resourcePath);
		if (!resource.exists()) {
			log.warn("노선망 파일이 없다 ({}). 그 수단은 경로에서 빠진다.", resourcePath);
			return;
		}
		try (InputStream in = resource.getInputStream()) {
			JsonNode root = objectMapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			List<TransitNetwork.Stop> readStops = readStops(root.get("stops"), kind);
			List<TransitNetwork.Route> readRoutes = readRoutes(root.get("routes"), kind, dayType);
			if (readRoutes.isEmpty()) {
				// 파일은 멀쩡한데 노선이 0개면 거른 조건이 원천과 안 맞는 것이다(요일 이름이
				// 바뀌었다든지). 조용히 0개로 두면 그 수단이 그냥 사라진다.
				log.warn("{} 에서 노선을 하나도 못 골랐다 (요일 거름 «{}»). 그 수단은 경로에서 빠진다.",
						resourcePath, dayType);
			}
			stops.addAll(readStops);
			routes.addAll(readRoutes);
			log.info("{} — 정류장·역 {}곳 · 노선 갈래 {}개", resourcePath, readStops.size(), readRoutes.size());
		}
		catch (IOException | RuntimeException failure) {
			// 삼키지 않고 무엇이 잘못됐는지 남긴다. 다만 기동은 계속한다.
			log.warn("노선망을 못 읽었다 ({} — {}). 그 수단은 경로에서 빠진다.", resourcePath, failure.toString());
		}
	}

	/**
	 * 두 원천의 식별자가 겹치면 말한다. 겹치면 {@link TransitNetwork} 가 한쪽을 조용히 덮어쓰고,
	 * 「갈 수는 있는데 못 찾는」 경로가 생기는데 아무 오류도 안 난다.
	 * 기동을 막지는 않는다 — 겹치는 몇 개보다 나머지 전부가 도는 것이 낫다.
	 */
	private static void warnOnDuplicateIds(List<TransitNetwork.Stop> stops, List<TransitNetwork.Route> routes) {
		long uniqueStops = stops.stream().map(TransitNetwork.Stop::id).distinct().count();
		if (uniqueStops != stops.size()) {
			log.warn("🔴 정류장·역 식별자가 {}개 겹친다 — 겹친 쪽은 한쪽만 남는다. 원천의 id 규칙을 확인하라",
					stops.size() - uniqueStops);
		}
		long uniqueRoutes = routes.stream().map(TransitNetwork.Route::id).distinct().count();
		if (uniqueRoutes != routes.size()) {
			log.warn("🔴 노선 식별자가 {}개 겹친다 — 겹친 쪽은 한쪽만 남는다. 원천의 id 규칙을 확인하라",
					routes.size() - uniqueRoutes);
		}
	}

	/**
	 * {@code "04:20"} 을 자정부터의 분으로. 값이 없거나 모양이 다르면 {@code fallback}.
	 * {@code 24:00} 이 넘는 값을 쓰는 원천이 있어(새벽 1시를 25:00 으로 적는 식) 하루 범위로
	 * 되접는다 — 안 그러면 노선 하나가 통째로 안 실린다.
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

	/**
	 * {@code {"정류장id": [위도, 경도, 이름, …]}}. 배열로 둔 것은 파일 크기를 지키기 위해서다.
	 * 앞 세 칸만 읽는다 — 뒤는 원천마다 다르다(버스는 정류장 종류, 지하철은 호선·환승역 표시).
	 */
	private static List<TransitNetwork.Stop> readStops(JsonNode node, TransitNetwork.Kind kind) {
		List<TransitNetwork.Stop> stops = new ArrayList<>();
		if (node == null) {
			return stops;
		}
		for (Map.Entry<String, JsonNode> entry : node.properties()) {
			JsonNode value = entry.getValue();
			stops.add(new TransitNetwork.Stop(entry.getKey(), value.get(2).asString(),
					value.get(0).asDouble(), value.get(1).asDouble(), kind));
		}
		return stops;
	}

	/**
	 * 노선 목록.
	 *
	 * @param dayType 요일별로 갈린 원천에서 고를 요일. {@code null} 이면 전부 (버스가 그렇다)
	 */
	private static List<TransitNetwork.Route> readRoutes(JsonNode node, TransitNetwork.Kind kind, String dayType) {
		List<TransitNetwork.Route> routes = new ArrayList<>();
		if (node == null) {
			return routes;
		}
		for (JsonNode route : node) {
			if (dayType != null) {
				JsonNode day = route.get("dayType");
				// 요일 칸이 아예 없으면 거르지 않고 넣는다. 「칸이 없다」와 「다른 요일이다」는
				// 다른 사실이고, 없는 것을 「아니다」로 읽으면 그 원천이 통째로 사라진다.
				if (day != null && !day.isNull() && !dayType.equals(day.asString())) {
					continue;
				}
			}
			List<String> stopIds = new ArrayList<>();
			for (JsonNode stopId : route.get("stops")) {
				stopIds.add(stopId.asString());
			}
			if (stopIds.size() < 2) {
				continue;
			}
			// 첫차·막차가 없는 노선은 하루 종일 다니는 것으로 본다. 모르는 것을 "안 다닌다" 로
			// 두면 그 노선이 조용히 사라진다.
			int first = minuteOfDay(route.get("first"), 0);
			int last = minuteOfDay(route.get("last"), TransitNetwork.MINUTES_PER_DAY - 1);
			// 원천의 type 을 그대로 실어 나른다("일반버스"·"마을버스"…). 요금표의 종류 이름과
			// 글자가 같아서 그대로 열쇠가 된다. 없으면 null 이고, 그 노선이 낀 여정은 요금을 못
			// 낸다. 지하철("도시철도")은 이 값을 안 쓴다 — 요금을 종류가 아니라 거리로 가른다.
			JsonNode type = route.get("type");
			String fareType = (type == null || type.isNull()) ? null : type.asString();
			try {
				routes.add(new TransitNetwork.Route(route.get("id").asString(), nameOf(route, kind), kind,
						stopIds, headwayMinutes(route.get("headway")), first, last, fareType,
						hopMinutesOf(route.get("hopMinutes"), stopIds.size())));
			}
			catch (IllegalArgumentException rejected) {
				// 줄 하나가 파일 전체를 못 날리게 한다. 위에서 통째로 잡으면 이상한 노선 하나
				// 때문에 그 수단이 전부 빠지고, 로그에는 그 사실이 안 남는다.
				log.warn("노선 한 줄을 건너뛴다 ({}): {}", route.get("id"), rejected.getMessage());
			}
		}
		return routes;
	}

	/**
	 * 역 사이 소요 시간(분) — 지하철 원천에만 있다. 같은 열차가 역마다 찍은 도착 시각에서 잰 값이다
	 * (busan-subway-network.json 의 note). 칸이 없거나, 개수가 「정류장 수 − 1」과 다르거나,
	 * 0 이하·숫자가 아닌 값이 끼면 {@code null} — 없는 것으로 보고 거리·속도 어림으로 돌아간다.
	 * 반쯤 맞는 값을 쓰는 것보다 정직한 어림이 낫다 (S15P21E201-1757).
	 */
	private static List<Double> hopMinutesOf(JsonNode node, int stopCount) {
		if (node == null || !node.isArray() || node.size() != stopCount - 1) {
			return null;
		}
		List<Double> hops = new ArrayList<>(node.size());
		for (JsonNode hop : node) {
			if (!hop.isNumber() || hop.asDouble() <= 0) {
				return null;
			}
			hops.add(hop.asDouble());
		}
		return List.copyOf(hops);
	}

	/**
	 * 화면에 보일 노선 이름. 버스는 번호라서 「번」을 붙여야 말이 되고({@code 1003} →
	 * {@code 1003번}) 지하철은 이미 이름이라 붙이면 「1호선번」이 된다. 방향은 이름에 안 넣는다 —
	 * 타는 사람에게 「상행」은 노선 이름이 아니다.
	 */
	private static String nameOf(JsonNode route, TransitNetwork.Kind kind) {
		String num = route.get("num").asString();
		return (kind == TransitNetwork.Kind.BUS) ? num + "번" : num;
	}

	/**
	 * 배차간격(분). 지하철 원천은 소수다({@code 6.5}) — 정수로 잘라 버리면 언제나 짧은 쪽으로
	 * 떨어져 기다리는 시간을 낮잡으므로 반올림한다. 0.5분 해상도를 잃지만 한쪽으로 기울지는 않는다.
	 */
	private static int headwayMinutes(JsonNode node) {
		return (int) Math.round(node.asDouble());
	}
}
