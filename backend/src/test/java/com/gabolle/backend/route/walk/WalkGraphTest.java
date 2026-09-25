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
}
