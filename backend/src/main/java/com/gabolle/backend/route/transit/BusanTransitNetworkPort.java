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
 * 부산 대중교통 노선망을 자원 파일에서 올린다 — S15P21E201-1123 · S15P21E201-1250.
 *
 * <h2>어디서 온 자료인가</h2>
 *
 * <b>버스</b>는 BIMS(부산 버스 정보) 수집본 셋을 합쳐 압축한 것이다 — 노선 목록(번호·종류·
 * 배차간격·첫차·막차), 노선별 정류장 순번, 정류장 좌표. 노선 <b>290개</b>가 방향별로 갈려
 * <b>367갈래</b>가 되고, 정류장은 <b>8,315곳</b>이다.
 *
 * <p><b>지하철</b>은 열차시각표에서 나왔다(2026-09-19). <b>114역 · 4호선</b>이고 방향별로
 * 갈려 8갈래다. 만든 방법과 원본 경로는 두 파일 각각의 {@code source} 에 적혀 있다.
 *
 * <h2>🔴 왜 한 클래스가 둘을 읽나 — 합쳐야 환승이 잡힌다</h2>
 *
 * 둘을 <b>따로 올려 두면 갈아탈 수가 없다.</b> {@link TransitNetwork} 하나가 정류장·노선을
 * 전부 알아야 탐색기가 「버스로 가다 지하철로 갈아탄다」를 찾는다. 요금 쪽은 이미 준비돼
 * 있다 — {@link TransitFareCalculator} 가 도시철도↔버스 환승 할인을 계산하는데,
 * <b>지금까지 잴 대상이 없었을 뿐이다.</b>
 *
 * <p>그래서 빈을 둘로 나누지 않았다. 나누면 「둘을 합치는 세 번째 빈」이 필요하고, 그 빈은
 * 하는 일이 <b>목록 두 개를 이어 붙이는 것</b>뿐이다.
 *
 * <h2>🔴 식별자가 안 겹치는 것을 확인하고 합친다</h2>
 *
 * 버스 정류장은 9자리 BIMS 코드({@code 163900101}), 지하철 역은 3자리 역번호({@code 101})라
 * 지금은 안 겹친다. 노선도 마찬가지다({@code 5200002000} 대 {@code SUBWAY-1-0-1}).
 *
 * <p><b>그런데 그건 우연이다.</b> 원천이 바뀌면 겹칠 수 있고, 겹치면 {@link TransitNetwork}
 * 가 한쪽을 <b>조용히 덮어쓴다</b> — 「갈 수는 있는데 못 찾는」 경로가 생기고 아무 오류도 안
 * 난다. 그래서 겹치면 <b>기동 로그에 경고를 남긴다.</b> 시험이 같은 것을 잰다.
 *
 * <h2>🔴 지하철은 <b>평일</b> 것만 올린다</h2>
 *
 * 시각표는 요일별로 갈려 있다(평일·토·일·공휴일, 합쳐 24갈래). 셋을 다 올리면 같은 구간에
 * <b>나란한 노선이 셋</b> 생기고, 탐색기는 요일을 모르므로 <b>그중 제일 빠른 것을 고른다</b> —
 * 화요일 일정에 일요일 배차가 섞이는 식이다.
 *
 * <p>평일을 고른 근거는 <b>재 본 것</b>이다. 요일 사이 배차 차이가 <b>최대 1.5분</b>이고
 * 방향도 일정하지 않다(1·2호선은 주말이 오히려 짧다). 즉 어느 쪽을 골라도 결과가 거의 같아서,
 * 요일을 아는 구조를 지금 만들 이유가 없다.
 *
 * <pre>
 *   1호선  평일 6.5분  토 6.0  일 6.0
 *   2호선  평일 7.5분  토 6.0  일 7.0
 *   3호선  평일 7.0분  토 7.0  일 7.0
 *   4호선  평일 8.0분  토 8.0  일 9.0
 * </pre>
 *
 * <p>요일을 봐야 할 만큼 차이가 큰 원천이 생기면 그때 이 자리에 축을 하나 더한다.
 *
 * <h2>🔴 운행(시각표)은 넣지 않는다 — 지하철도 마찬가지다</h2>
 *
 * BIMS 는 시각표를 주지 않는다. 배차간격과 첫차·막차로 시각표를 <b>지어낼</b> 수는 있지만
 * 그러지 않는다. 이유가 둘이다.
 * <ol>
 * <li>노선당 약 100편 × 367갈래 × 정류장 수십 개 = <b>수백만 개의 시각</b>이 메모리에 올라간다</li>
 * <li>더 나쁜 것은 <b>그럴듯해진다</b>는 점이다. "18시 12분 차" 는 구체적이라서 더 믿게
 *     만드는데 근거가 없다</li>
 * </ol>
 *
 * <p>🔴 <b>지하철은 진짜 시각표가 있는데도 안 넣는다.</b> 106,828줄이 들어오면 위 1번이 그대로
 * 생기고, 무엇보다 <b>버스와 지하철이 서로 다른 자로 재게 된다</b> — 한쪽은 시각표로, 한쪽은
 * 배차간격으로. 환승 시간이 두 자의 차이만큼 틀어진다. 대신 그 시각표에서 뽑은
 * <b>역간 소요시간과 배차간격</b>을 쓴다. 관측값이라는 점은 그대로 남는다.
 *
 * <p>{@link HeadwayJourneyPlanner} 가 배차간격으로 <b>걸리는 시간만</b> 낸다.
 *
 * <h2>파일이 없거나 깨져도 기동은 안 막는다</h2>
 *
 * 그 파일 몫만 빈 채로 간다 — <b>버스가 없어도 지하철은 올라오고, 그 반대도 된다.</b>
 * 부르는 쪽({@code TransitRouteAdapter})이 빈 노선망일 때 어림값으로 가고, 그건 고장이 아니라
 * 정상 흐름의 한 갈래다. <b>경로 하나 때문에 서버 전체가 안 뜨는 것이 훨씬 나쁘다.</b>
 */
@Primary
@Component
public class BusanTransitNetworkPort implements TransitNetworkPort {

	/**
	 * 🔴 {@code @Primary} 를 붙인 이유 — {@link EmptyTransitNetworkPort} 의 javadoc 이
	 * "진짜 노선망 구현을 더할 때 이렇게 하라" 고 적어 둔 그대로다. 둘 다 조건 없이
	 * 등록되므로 이게 없으면 스프링이 "어느 것을 쓸지 모르겠다" 로 기동을 실패시킨다.
	 */
	static final String BUS_RESOURCE_PATH = "transit/busan-bus-network.json";

	/** 지하철 노선망 — S15P21E201-1250 에 들어왔다. 114역 · 4호선. */
	static final String SUBWAY_RESOURCE_PATH = "transit/busan-subway-network.json";

	/**
	 * 지하철에서 올릴 요일 — 클래스 주석의 「평일 것만 올린다」가 이 값이다.
	 *
	 * <p>🔴 <b>원천의 글자 그대로다.</b> 「1」 같은 코드가 아니라 사람이 읽는 말로 들어 있어서
	 * ({@code "평일"}·{@code "토요일"}·{@code "일요일·공휴일"}), 대응표를 따로 안 둔다 —
	 * 표를 두면 원천이 말을 바꿨을 때 <b>아무것도 안 걸리고 노선이 0개가 된다.</b>
	 * 여기서는 0개가 되면 기동 로그가 말한다.
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

		// 🔴 운행도 환승도 비운다. 위 javadoc 참고 — 운행은 지어내지 않기 때문이고,
		//    걸어서 갈아타는 통로는 아직 아무도 재지 않았다(S15P21E201-1248).
		TransitNetwork built = TransitNetwork.of(stops, routes, List.of(), List.of());
		log.info("대중교통 노선망을 올렸다 — 정류장·역 {}곳 · 노선 갈래 {}개", stops.size(), routes.size());
		return built;
	}

	/**
	 * 자원 파일 하나를 읽어 목록에 <b>더한다.</b>
	 *
	 * <p>🔴 <b>한 파일이 없거나 깨져도 다른 파일은 올라간다.</b> 예외를 위로 던지면 버스 파일
	 * 하나 때문에 지하철까지 같이 사라진다 — 그러면 「대중교통이 통째로 안 된다」가 되고,
	 * 원인이 어느 쪽인지도 로그에 안 남는다.
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
				// 🔴 파일은 멀쩡한데 노선이 0개면 거른 조건이 원천과 안 맞는 것이다
				//    (요일 이름이 바뀌었다든지). 조용히 0개로 두면 그 수단이 그냥 사라진다.
				log.warn("{} 에서 노선을 하나도 못 골랐다 (요일 거름 «{}»). 그 수단은 경로에서 빠진다.",
						resourcePath, dayType);
			}
			stops.addAll(readStops);
			routes.addAll(readRoutes);
			log.info("{} — 정류장·역 {}곳 · 노선 갈래 {}개", resourcePath, readStops.size(), readRoutes.size());
		}
		catch (IOException | RuntimeException failure) {
			// 🔴 삼키지 않고 무엇이 잘못됐는지 남긴다. 다만 기동은 계속한다.
			log.warn("노선망을 못 읽었다 ({} — {}). 그 수단은 경로에서 빠진다.", resourcePath, failure.toString());
		}
	}

	/**
	 * 두 원천의 식별자가 겹치면 <b>말한다.</b>
	 *
	 * <p>🔴 겹치면 {@link TransitNetwork} 가 한쪽을 <b>조용히 덮어쓴다.</b> 「갈 수는 있는데
	 * 못 찾는」 경로가 생기고 아무 오류도 안 난다 — 노선을 방향별로 가른 이유와 같은 종류의
	 * 사고다. 지금은 0건이지만 그건 <b>원천이 그렇게 생겼기 때문</b>이지 누가 막고 있어서가
	 * 아니다.
	 *
	 * <p>기동을 막지는 않는다. 겹치는 몇 개보다 <b>나머지 전부가 도는 것</b>이 낫다.
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

	/**
	 * {@code {"정류장id": [위도, 경도, 이름, …]}}. 배열로 둔 것은 0.8MB 를 지키기 위해서다.
	 *
	 * <p>🔴 <b>앞 세 칸만 읽는다.</b> 뒤는 원천마다 다르다 — 버스는 정류장 종류, 지하철은
	 * 호선과 환승역 표시가 붙는다. 뒤 칸이 늘어도 여기는 안 고쳐도 된다.
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
				// 🔴 요일 칸이 아예 없으면 거르지 않고 넣는다. 「칸이 없다」와 「다른 요일이다」는
				//    다른 사실이고, 없는 것을 「아니다」로 읽으면 그 원천이 통째로 사라진다.
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
			// 🔴 첫차·막차가 없는 노선은 하루 종일 다니는 것으로 본다. 모르는 것을 "안 다닌다"
			//    로 두면 그 노선이 조용히 사라진다 — BIMS 수집본에서는 290개 중 288개에 값이 있다.
			int first = minuteOfDay(route.get("first"), 0);
			int last = minuteOfDay(route.get("last"), TransitNetwork.MINUTES_PER_DAY - 1);
			// 🔴 S15P21E201-1291 — 원천의 type 을 그대로 실어 나른다("일반버스"·"마을버스"…).
			//    요금표의 종류 이름과 글자가 같아서 그대로 열쇠가 된다. 없으면 null 이고,
			//    그 노선이 낀 여정은 요금을 못 낸다 — 아무 줄이나 고르지 않는다.
			//    지하철("도시철도")은 이 값을 안 쓴다 — 요금을 종류가 아니라 거리로 가른다.
			JsonNode type = route.get("type");
			String fareType = (type == null || type.isNull()) ? null : type.asString();
			try {
				routes.add(new TransitNetwork.Route(route.get("id").asString(), nameOf(route, kind), kind,
						stopIds, headwayMinutes(route.get("headway")), first, last, fareType));
			}
			catch (IllegalArgumentException rejected) {
				// 🔴 줄 하나가 파일 전체를 못 날리게 한다. 위에서 통째로 잡으면 이상한 노선
				//    하나 때문에 그 수단이 전부 빠지고, 로그에는 그 사실이 안 남는다.
				log.warn("노선 한 줄을 건너뛴다 ({}): {}", route.get("id"), rejected.getMessage());
			}
		}
		return routes;
	}

	/**
	 * 화면에 보일 노선 이름.
	 *
	 * <p>버스는 번호라서 <b>「번」</b>을 붙여야 말이 된다({@code 1003} → {@code 1003번}).
	 * 지하철은 이미 이름이라 붙이면 <b>「1호선번」</b>이 된다. 방향은 이름에 안 넣는다 —
	 * 버스도 방향별로 갈려 있지만 이름은 하나이고, 타는 사람에게 「상행」은 노선 이름이 아니다.
	 */
	private static String nameOf(JsonNode route, TransitNetwork.Kind kind) {
		String num = route.get("num").asString();
		return (kind == TransitNetwork.Kind.BUS) ? num + "번" : num;
	}

	/**
	 * 배차간격(분).
	 *
	 * <p>🔴 지하철은 <b>소수</b>다({@code 6.5}). 정수로 잘라 버리면 <b>언제나 짧은 쪽</b>으로
	 * 떨어져 기다리는 시간을 낮잡는다 — BIMS 실측이 계획 배차보다 1.5~3.2배 길었던 것과 같은
	 * 방향의 오차를 우리 손으로 하나 더 만드는 셈이다. 그래서 <b>반올림</b>한다. 0.5분
	 * 해상도를 잃지만 한쪽으로 기울지는 않는다.
	 */
	private static int headwayMinutes(JsonNode node) {
		return (int) Math.round(node.asDouble());
	}
}
