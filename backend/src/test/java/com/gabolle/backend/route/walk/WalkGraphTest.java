package com.gabolle.backend.route.walk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.route.domain.RouteLeg;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보행 그래프의 규칙 — S15P21E201-1630. 작은 가짜 그래프를 파일 모양(GBWG v1) 그대로 써서 읽힌다.
 *
 * <pre>
 *   A ── B ── C          A-B 평지 길(경사 2.0%) · B-C 계단 · A-D-C 는 돌아가는 길(경사 모름)
 *   │         │
 *   D ─────── E? (없음)  ISLAND 는 떨어진 작은 덩어리
 * </pre>
 */
class WalkGraphTest {

	// 동서 100m ≈ 0.0011°, 남북 100m ≈ 0.0009°
	private static final double[] A = { 35.1000, 129.0000 };

	private static final double[] B = { 35.1000, 129.0011 };

	private static final double[] C = { 35.1000, 129.0022 };

	private static final double[] D = { 35.0990, 129.0011 };

	private static final double[] ISLAND_1 = { 35.1005, 129.0040 };

	private static final double[] ISLAND_2 = { 35.1006, 129.0040 };

	private static WalkGraph sample() throws IOException {
		double[][] nodes = { A, B, C, D, ISLAND_1, ISLAND_2 };
		// { 점 번호들 }, 계단, 경사 천분율
		Object[][] ways = {
				{ new int[] { 0, 1 }, false, 20 },
				{ new int[] { 1, 2 }, true, -1 },
				{ new int[] { 0, 3, 2 }, false, -1 },
				{ new int[] { 4, 5 }, false, 10 },
		};
		return graph(nodes, ways);
	}

	/** 점과 길로 GBWG v1 파일을 써서 읽힌다. 길은 {@code { 점 번호들, 계단, 경사 천분율 }} 이다. */
	private static WalkGraph graph(double[][] nodes, Object[][] ways) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
			out.writeBytes("GBWG");
			out.writeInt(1);
			out.writeInt(nodes.length);
			out.writeInt(ways.length);
			int refs = 0;
			for (Object[] w : ways) {
				refs += ((int[]) w[0]).length;
			}
			out.writeInt(refs);
			for (double[] n : nodes) {
				out.writeInt((int) Math.round(n[0] * 1e7));
				out.writeInt((int) Math.round(n[1] * 1e7));
			}
			for (Object[] w : ways) {
				int[] r = (int[]) w[0];
				out.writeInt(r.length);
				out.writeByte((Boolean) w[1] ? 1 : 0);
				out.writeShort((Integer) w[2]);
				for (int x : r) {
					out.writeInt(x);
				}
			}
		}
		return WalkGraph.read(new ByteArrayInputStream(bytes.toByteArray()));
	}

	@Test
	@DisplayName("🔴 가장 짧은 길로 잇고, 조각마다 경사·계단을 싣는다 — 계단은 길로 쓰되 표시가 남는다")
	void shortestPathWithPieces() throws IOException {
		WalkGraph.Route route = sample().route(A[0], A[1], C[0], C[1]).orElseThrow();

		// A → B → C (약 200m) 가 A → D → C 보다 짧다.
		assertThat(route.path()).hasSize(5); // 출발 · A · B · C · 도착
		assertThat(route.meters()).isBetween(190.0, 215.0);
		assertThat(route.pieces()).containsExactly(
				new RouteLeg.Piece(0, 1, null, false), // 출발 좌표 → A: 길 밖
				new RouteLeg.Piece(1, 2, 2.0, false),
				new RouteLeg.Piece(2, 3, null, true),
				new RouteLeg.Piece(3, 4, null, false));
		assertThat(route.path().get(0)).as("[경도, 위도] 순서").containsExactly(A[1], A[0]);
	}

	@Test
	@DisplayName("경사·계단이 같은 이웃 조각은 하나로 잇는다")
	void mergesEqualPieces() throws IOException {
		// D 에서 C 로: D-C 한 조각(모름) + 길 밖 두 토막(모름) → 전부 모름이라 한 조각
		WalkGraph.Route route = sample().route(D[0], D[1], C[0], C[1]).orElseThrow();

		assertThat(route.pieces()).containsExactly(new RouteLeg.Piece(0, 3, null, false));
	}

	@Test
	@DisplayName("🔴 출발·도착에서 300m 안에 걷는 길이 없으면 빈 값 — 그러면 직선 어림으로 떨어진다")
	void tooFarToSnap() throws IOException {
		assertThat(sample().route(35.2, 129.2, C[0], C[1])).isEmpty();
	}

	@Test
	@DisplayName("🔴 떨어진 작은 덩어리에는 안 붙는다 — 붙으면 어디로도 못 간다")
	void doesNotSnapToIslands() throws IOException {
		WalkGraph graph = sample();

		int snapped = graph.nearest(ISLAND_1[0], ISLAND_1[1]);

		assertThat(snapped).as("섬 대신 한 덩어리의 점(C)").isEqualTo(2);
		assertThat(graph.route(ISLAND_1[0], ISLAND_1[1], A[0], A[1])).isPresent();
	}

	@Test
	@DisplayName("다른 판의 파일은 읽지 않는다")
	void rejectsOtherFormats() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
			out.writeBytes("GBWG");
			out.writeInt(2);
		}
		catch (IOException ex) {
			throw new AssertionError(ex);
		}
		org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> WalkGraph.read(new ByteArrayInputStream(bytes.toByteArray())))
				.isInstanceOf(IOException.class);
	}

	@Test
	@DisplayName("평면 거리는 부산 안에서 하버사인과 0.5% 안쪽으로 같다")
	void planarMatchesHaversine() {
		double planar = WalkGraph.planarMeters(35.1632, 129.1584, 35.0974, 129.0106);
		double haversine = com.gabolle.backend.place.service.GeoDistance.meters(35.1632, 129.1584, 35.0974, 129.0106);

		assertThat(planar / haversine).isBetween(0.995, 1.005);
		assertThat(List.of(planar)).isNotEmpty();
	}

	// ── 계단·급경사를 피하는 길(stepFree) ──────────────────────────────────────

	private static boolean anyStairs(WalkGraph.Route route) {
		return route.pieces().stream().anyMatch(RouteLeg.Piece::stairs);
	}

	@Test
	@DisplayName("보통 길은 전과 같다 — 짧은 계단 길(A-B-C 약 200m)을 탄다")
	void defaultTakesShortStairs() throws IOException {
		WalkGraph.Route route = sample().route(A[0], A[1], C[0], C[1], false).orElseThrow();

		assertThat(anyStairs(route)).isTrue();
		assertThat(route.meters()).isBetween(190.0, 215.0);
	}

	@Test
	@DisplayName("🔴 계단을 피하는 길은 더 길어도 계단 없는 A-D-C 로 돌아간다 — 거리는 치른 값이 아니라 실제 길이다")
	void stepFreeTakesLongerDetour() throws IOException {
		WalkGraph.Route route = sample().route(A[0], A[1], C[0], C[1], true).orElseThrow();

		assertThat(anyStairs(route)).as("휠체어에 계단을 내지 않는다").isFalse();
		assertThat(route.path()).hasSize(5); // 출발 · A · D · C · 도착
		assertThat(route.path().get(2)).as("D 를 지난다").containsExactly(D[1], D[0]);
		// A-D·D-C 는 각각 약 149m. 계단 배수(×25)가 섞였다면 수천 m 가 된다.
		assertThat(route.meters()).isBetween(280.0, 320.0);
	}

	@Test
	@DisplayName("🔴 계단 말고 이을 길이 없으면 계단 길이라도 낸다 — 경로가 사라지면 계단이 있는지조차 안 보인다")
	void stepFreeStillUsesStairsWhenOnlyWay() throws IOException {
		double[] p = { 35.2000, 129.0000 };
		double[] q = { 35.2000, 129.0011 };
		double[] r = { 35.2000, 129.0022 };
		WalkGraph graph = graph(new double[][] { p, q, r }, new Object[][] {
				{ new int[] { 0, 1 }, false, 10 },
				{ new int[] { 1, 2 }, true, -1 },
		});

		WalkGraph.Route route = graph.route(p[0], p[1], r[0], r[1], true).orElseThrow();

		assertThat(anyStairs(route)).as("계단 표시가 조각에 남는다").isTrue();
		assertThat(route.meters()).isBetween(190.0, 215.0);
	}

	@Test
	@DisplayName("🔴 계단을 피하는 길은 4배 안쪽의 평지 길이 있으면 8.33% 넘는 가파른 길을 피한다")
	void stepFreeAvoidsSteepWay() throws IOException {
		// A-B-C 한 길이 120‰(12%) 로 가파르다(약 200m). A-D-C 는 경사 30‰ 평지(약 298m) — 4배(800m) 안쪽이다.
		WalkGraph graph = graph(new double[][] { A, B, C, D }, new Object[][] {
				{ new int[] { 0, 1, 2 }, false, 120 },
				{ new int[] { 0, 3, 2 }, false, 30 },
		});

		WalkGraph.Route plain = graph.route(A[0], A[1], C[0], C[1], false).orElseThrow();
		WalkGraph.Route stepFree = graph.route(A[0], A[1], C[0], C[1], true).orElseThrow();

		assertThat(plain.pieces()).anyMatch((piece) -> Double.valueOf(12.0).equals(piece.slopePercent()));
		assertThat(stepFree.pieces()).noneMatch((piece) -> Double.valueOf(12.0).equals(piece.slopePercent()));
		assertThat(stepFree.path().get(2)).as("D 를 지난다").containsExactly(D[1], D[0]);
	}

	@Test
	@DisplayName("🔴 같은 두 점을 잇는 길이 둘이면 조각에도 찾기가 고른 쪽이 실린다 — 계단 옆 경사로")
	void parallelEdgeReportsTheOneSearched() throws IOException {
		double[] x = { 35.3000, 129.0000 };
		double[] y = { 35.3000, 129.0011 };
		// 둘은 길이가 같다. 먼저 적힌 쪽이 계단이다.
		WalkGraph graph = graph(new double[][] { x, y }, new Object[][] {
				{ new int[] { 0, 1 }, true, -1 },
				{ new int[] { 0, 1 }, false, 40 },
		});

		WalkGraph.Route route = graph.route(x[0], x[1], y[0], y[1], true).orElseThrow();

		assertThat(anyStairs(route)).isFalse();
		assertThat(route.pieces()).anyMatch((piece) -> Double.valueOf(4.0).equals(piece.slopePercent()));
	}
}
