package com.gabolle.backend.route.walk;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.service.GeoDistance;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제로 싣는 그래프 파일({@value WalkGraphRouteProvider#RESOURCE})로 잰다 — S15P21E201-1630.
 *
 * <p>표본 셋의 출발은 지하철 역(bigData {@code busan-subway-network.json}), 도착은 운영 장소 행의 좌표다
 * (2026-09-25). 파일이 빠지거나 깨지면 서버는 조용히 직선 어림으로 떨어지므로, 그것을 여기서 막는다.
 */
class WalkGraphResourceTest {

	/** 이름 · 출발(위도, 경도) · 도착(위도, 경도). */
	private static final List<Object[]> SAMPLES = List.of(
			new Object[] { "해운대역→해운대해수욕장", 35.163672, 129.158908, 35.1585232170784, 129.159854668484 },
			new Object[] { "토성역→감천문화마을", 35.100727, 129.019776, 35.09740872250286, 129.01056080474402 },
			new Object[] { "남포역→흰여울문화마을", 35.097953, 129.034869, 35.0783, 129.0453 });

	private static WalkGraph graph;

	private static long loadMs;

	private static long heapMb;

	@BeforeAll
	static void load() throws Exception {
		Runtime runtime = Runtime.getRuntime();
		System.gc();
		long before = runtime.totalMemory() - runtime.freeMemory();
		long started = System.nanoTime();
		try (InputStream in = WalkGraphResourceTest.class.getClassLoader()
				.getResourceAsStream(WalkGraphRouteProvider.RESOURCE)) {
			assertThat(in).as("그래프 파일이 자원에 있어야 한다").isNotNull();
			graph = WalkGraph.read(in);
		}
		loadMs = (System.nanoTime() - started) / 1_000_000;
		System.gc();
		heapMb = (runtime.totalMemory() - runtime.freeMemory() - before) / 1_000_000;
		System.out.println("BENCH load ms=" + loadMs + " heapMB≈" + heapMb + " nodes=" + graph.nodeCount()
				+ " ways=" + graph.wayCount() + " main=" + graph.mainNodeCount());
	}

	@Test
	@DisplayName("🔴 실어 둔 그래프가 부산 걷는 길 전체다 — 점 47만 · 한 덩어리 95% 이상")
	void theShippedGraphIsWhole() {
		assertThat(graph.nodeCount()).isGreaterThan(400_000);
		assertThat((double) graph.mainNodeCount() / graph.nodeCount()).isGreaterThan(0.95);
	}

	@Test
	@DisplayName("🔴 실어 둔 그래프에 그늘이 붙어 있다 — 그늘 없는 v1 로 되돌리면 지도가 경사만 칠하게 된다 (S15P21E201-1895)")
	void theShippedGraphCarriesShade() {
		// 2026-09-30 실측 14,102 길. walk-graph.mjs 로 그래프를 다시 만들면 v1(그늘 0)이 나오므로
		// walk-graph-shade.mjs 를 다시 돌려야 한다 — 그걸 빠뜨리면 여기서 걸린다.
		assertThat(graph.shadedWayCount()).isGreaterThan(10_000);
	}

	@Test
	@DisplayName("그늘을 재 둔 지역(해운대·남포)의 표본 길은 그늘 있는 조각을 갖고, 값은 0~1 의 0.1 단위다")
	void shadeReachesRealRoutesInMeasuredAreas() {
		for (int index : new int[] { 0, 2 }) {
			Object[] s = SAMPLES.get(index);
			WalkGraph.Route route = graph.route((double) s[1], (double) s[2], (double) s[3], (double) s[4])
					.orElseThrow(() -> new AssertionError(s[0]));
			List<Double> known = route.pieces().stream().map(piece -> piece.shade())
					.filter(java.util.Objects::nonNull).toList();
			assertThat(known).as((String) s[0] + " — 그늘 있는 조각").isNotEmpty();
			assertThat(known).as((String) s[0] + " — 값은 0.1 단위")
					.allSatisfy(v -> assertThat(v * 10).isBetween(0.0, 10.0).isCloseTo(Math.rint(v * 10),
							org.assertj.core.data.Offset.offset(1e-9)));
		}
	}

	@Test
	@DisplayName("🔴 표본 세 길을 찾고, 한 번에 0.2초 안이다 — 길이는 직선거리보다 길다")
	void samplesAreFoundQuickly() {
		for (Object[] s : SAMPLES) {
			double oLat = (double) s[1], oLng = (double) s[2], dLat = (double) s[3], dLng = (double) s[4];
			long cold = System.nanoTime();
			WalkGraph.Route route = graph.route(oLat, oLng, dLat, dLng).orElseThrow(() -> new AssertionError(s[0]));
			long coldMs = (System.nanoTime() - cold) / 1_000_000;
			long warm = System.nanoTime();
			graph.route(oLat, oLng, dLat, dLng);
			double warmMs = (System.nanoTime() - warm) / 1e6;
			double straight = GeoDistance.meters(oLat, oLng, dLat, dLng);
			System.out.printf("BENCH %s straight=%.0fm straightX1.3=%.0fm graph=%.0fm points=%d pieces=%d coldMs=%d warmMs=%.1f%n",
					s[0], straight, straight * 1.3, route.meters(), route.path().size(), route.pieces().size(), coldMs, warmMs);
			assertThat(route.meters()).as((String) s[0]).isGreaterThan(straight);
			assertThat(warmMs).as((String) s[0]).isLessThan(200);
		}
	}
}
