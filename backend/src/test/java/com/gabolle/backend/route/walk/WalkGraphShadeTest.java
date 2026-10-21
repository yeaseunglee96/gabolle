package com.gabolle.backend.route.walk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.route.domain.RouteLeg;

/**
 * 걷는 길 그래프의 「그늘」 — S15P21E201-1895.
 *
 * <p>코스 지도가 경로 선을 경사·그늘로 칠하려면 조각마다 그늘이 있어야 한다. 작은 가짜 그래프를 파일 모양 그대로(GBWG
 * v1·v2) 써서 읽힌다.
 *
 * <pre>
 *   A ── B ── C ── D      한 줄로 이은 길 셋. 길마다 경사(‰)와 그늘(%)을 바꿔 가며 조각이 어떻게 나뉘는지 본다.
 * </pre>
 */
class WalkGraphShadeTest {

	private static final double LAT = 35.1000;

	/** 동쪽으로 100m 씩 — 경도 0.0011° ≈ 100m. */
	private static final double[][] NODES = { { LAT, 129.0000 }, { LAT, 129.0011 }, { LAT, 129.0022 },
			{ LAT, 129.0033 } };

	/** 그늘 칸이 없는 옛 파일(v1) 모양의 값. */
	private static final int NO_SHADE_BYTE = -1;

	@Test
	@DisplayName("v2 파일의 길 그늘이 조각에 실린다 — 0.1 단위로 묶고, 0%(볕)는 모름이 아니라 0.0 이다")
	void v2ShadeReachesPieces() throws IOException {
		// A-B 37% · B-C 0% · C-D 100%
		WalkGraph graph = read(2, ways(way(0, 1, 20, 37), way(1, 2, 20, 0), way(2, 3, 20, 100)));

		List<RouteLeg.Piece> pieces = route(graph).pieces();

		// 출발 토막 + 길 셋 + 도착 토막 — 셋은 그늘이 달라 이어 붙지 않는다.
		assertThat(pieces).extracting(RouteLeg.Piece::shade).containsExactly(null, 0.4, 0.0, 1.0, null);
	}

	@Test
	@DisplayName("v1 파일도 읽힌다 — 그늘은 전부 모름이다")
	void v1StillReadsWithUnknownShade() throws IOException {
		WalkGraph graph = read(1, ways(way(0, 1, 20, NO_SHADE_BYTE), way(1, 2, 20, NO_SHADE_BYTE),
				way(2, 3, 20, NO_SHADE_BYTE)));

		WalkGraph.Route route = route(graph);

		assertThat(route.pieces()).allSatisfy(piece -> assertThat(piece.shade()).isNull());
		assertThat(graph.shadedWayCount()).isZero();
		// 경사·계단은 v1 그대로 읽힌다.
		assertThat(route.pieces().get(1).slopePercent()).isEqualTo(2.0);
	}

	@Test
	@DisplayName("255 는 모름이다 — 그늘을 안 잰 길은 null 이고 0.0(볕)이 아니다")
	void unknownShadeByteIsNullNotZero() throws IOException {
		WalkGraph graph = read(2, ways(way(0, 1, 20, 255), way(1, 2, 20, 0), way(2, 3, 20, 255)));

		List<RouteLeg.Piece> pieces = route(graph).pieces();

		assertThat(pieces).extracting(RouteLeg.Piece::shade).containsExactly(null, null, 0.0, null, null);
		assertThat(graph.shadedWayCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("깨진 그늘 값(101~254)은 틀린 그늘을 칠하지 않고 모름으로 읽는다")
	void corruptShadeIsTreatedAsUnknown() throws IOException {
		WalkGraph graph = read(2, ways(way(0, 1, 20, 150), way(1, 2, 20, 101), way(2, 3, 20, 254)));

		assertThat(route(graph).pieces()).allSatisfy(piece -> assertThat(piece.shade()).isNull());
		assertThat(graph.shadedWayCount()).isZero();
	}

	@Test
	@DisplayName("경사가 같고 그늘이 같은 10% 칸이면 이웃 길이 한 조각으로 이어 붙는다 — 선이 잘게 쪼개지지 않는다")
	void adjacentWaysInSameShadeBucketMerge() throws IOException {
		// 37% 와 41% 는 둘 다 0.4 칸이다. 경사도 같다.
		WalkGraph graph = read(2, ways(way(0, 1, 20, 37), way(1, 2, 20, 41), way(2, 3, 20, 44)));

		List<RouteLeg.Piece> pieces = route(graph).pieces();

		assertThat(pieces).hasSize(3); // 출발 토막 · 이어 붙은 길 하나 · 도착 토막
		RouteLeg.Piece merged = pieces.get(1);
		assertThat(merged.shade()).isEqualTo(0.4);
		assertThat(merged.from()).isEqualTo(1);
		assertThat(merged.to()).isEqualTo(4);
	}

	@Test
	@DisplayName("그늘 칸이 다르면 경사가 같아도 새 조각이다")
	void differentShadeBucketSplitsEvenWithSameSlope() throws IOException {
		WalkGraph graph = read(2, ways(way(0, 1, 20, 37), way(1, 2, 20, 56), way(2, 3, 20, 56)));

		assertThat(route(graph).pieces()).extracting(RouteLeg.Piece::shade).containsExactly(null, 0.4, 0.6, null);
	}

	@Test
	@DisplayName("10% 칸으로 묶는 규칙 — 반올림이다(5% → 0.1, 4% → 0.0, 95% → 1.0)")
	void quantizesToTenths() {
		assertThat(WalkGraph.shadeOf((short) 0)).isEqualTo(0.0);
		assertThat(WalkGraph.shadeOf((short) 4)).isEqualTo(0.0);
		assertThat(WalkGraph.shadeOf((short) 5)).isEqualTo(0.1);
		assertThat(WalkGraph.shadeOf((short) 37)).isEqualTo(0.4);
		assertThat(WalkGraph.shadeOf((short) 95)).isEqualTo(1.0);
		assertThat(WalkGraph.shadeOf((short) 100)).isEqualTo(1.0);
		assertThat(WalkGraph.shadeOf((short) -1)).isNull();
	}

	@Test
	@DisplayName("모르는 판(v3)은 거절한다 — 조용히 v2 로 읽으면 바이트가 어긋난다")
	void unknownVersionIsRejected() {
		assertThatThrownBy(() -> read(3, ways(way(0, 1, 20, 37)))).isInstanceOf(IOException.class)
				.hasMessageContaining("v1 또는 v2");
	}

	// ── 도우미 ────────────────────────────────────────────────────────────────

	private static WalkGraph.Route route(WalkGraph graph) {
		return graph.route(NODES[0][0], NODES[0][1], NODES[3][0], NODES[3][1]).orElseThrow();
	}

	/** {@code { 점 번호 둘, 경사 천분율, 그늘 바이트 }}. 그늘 -1 은 v1 처럼 칸이 없다는 뜻이다. */
	private static Object[] way(int from, int to, int slopePermille, int shade) {
		return new Object[] { new int[] { from, to }, slopePermille, shade };
	}

	private static Object[][] ways(Object[]... ways) {
		return ways;
	}

	private static WalkGraph read(int version, Object[][] ways) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
			out.writeBytes("GBWG");
			out.writeInt(version);
			out.writeInt(NODES.length);
			out.writeInt(ways.length);
			int refs = 0;
			for (Object[] w : ways) {
				refs += ((int[]) w[0]).length;
			}
			out.writeInt(refs);
			for (double[] n : NODES) {
				out.writeInt((int) Math.round(n[0] * 1e7));
				out.writeInt((int) Math.round(n[1] * 1e7));
			}
			for (Object[] w : ways) {
				int[] r = (int[]) w[0];
				out.writeInt(r.length);
				out.writeByte(0); // 계단 아님
				out.writeShort((Integer) w[1]);
				if (version >= 2) {
					out.writeByte((Integer) w[2]);
				}
				for (int x : r) {
					out.writeInt(x);
				}
			}
		}
		return WalkGraph.read(new ByteArrayInputStream(bytes.toByteArray()));
	}
}
