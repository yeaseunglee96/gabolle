package com.gabolle.backend.route.walk;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실어 둔 그래프 파일의 「평지 잡음 바닥」 — S15P21E201-2023.
 *
 * <p>고도 자료(SRTM 30m, 수직 오차 ±5m)가 건물 빽빽한 평지 도심에 가짜 경사를 만든다. 2026-10-09 조사에서 서면 중심은
 * 경사를 아는 길의 길이로 27% 가 노랑·빨강이었고, 서면 대로(길 20786)는 양끝 차 0.4% 인데 7.4% 노랑이었다.
 * {@code bigData/process/walk-graph-flat-floor.mjs} 가 높낮이 폭 5m 안의 길을 평탄으로 낮춘다. 그 단계를 빠뜨린 그래프를
 * 다시 실으면 여기서 걸린다. 언덕(감천문화마을)은 그대로 빨강이 대부분이어야 한다 — 바닥이 언덕까지 지우면 안 된다.
 *
 * <p>칸 = [남쪽 위도, 북쪽 위도, 서쪽 경도, 동쪽 경도] — 조사 스크립트(flat.mjs)와 같은 칸이다.
 */
class WalkGraphFlatFloorResourceTest {

	private static final double[] SEOMYEON = { 35.152, 35.160, 129.054, 129.064 };

	private static final double[] GAMCHEON = { 35.093, 35.100, 129.007, 129.014 };

	/**
	 * 서면 중심의 길 하나 — 바닥 전 7.5% 노랑, 10m 표본의 높낮이 폭이 5m 안. 바닥이 깔렸으면 평탄이다.
	 *
	 * <p>🔴 조사에서 짚은 서면 대로(길 20786, 7.4%)는 이 바닥으로 안 낮아진다 — 양끝 차는 0.4% 지만 표본 폭이 14.2m 라
	 * 「고도 오차 5m 안」이 아니다. 건물 높이가 섞인 넓은 혹이다. 그것은 5m 지면 DEM 으로 바꿔야 풀린다(조사 4절의 2번).
	 */
	private static final int SEOMYEON_SMALL_WAY = 1694;

	private static int[] lat;

	private static int[] lon;

	private static int[][] wayNodes;

	private static short[] waySlope;

	@BeforeAll
	static void load() throws Exception {
		try (InputStream raw = WalkGraphFlatFloorResourceTest.class.getClassLoader()
				.getResourceAsStream(WalkGraphRouteProvider.RESOURCE);
				DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(raw)))) {
			byte[] magic = new byte[4];
			in.readFully(magic);
			int version = in.readInt();
			int n = in.readInt();
			int w = in.readInt();
			in.readInt();
			lat = new int[n];
			lon = new int[n];
			for (int i = 0; i < n; i++) {
				lat[i] = in.readInt();
				lon[i] = in.readInt();
			}
			wayNodes = new int[w][];
			waySlope = new short[w];
			for (int k = 0; k < w; k++) {
				int count = in.readInt();
				in.readByte();
				waySlope[k] = in.readShort();
				if (version >= 2) {
					in.readByte();
				}
				int[] nodes = new int[count];
				for (int i = 0; i < count; i++) {
					nodes[i] = in.readInt();
				}
				wayNodes[k] = nodes;
			}
		}
	}

	/** 칸 안 길이 비율 — [초록, 노랑, 빨강] (경사를 아는 길만, 경사 값만으로. 계단 표시는 따로라 안 센다). */
	private static double[] shares(double[] box) {
		double green = 0, yellow = 0, red = 0;
		for (int k = 0; k < wayNodes.length; k++) {
			short s = waySlope[k];
			if (s < 0) {
				continue;
			}
			int[] r = wayNodes[k];
			for (int i = 1; i < r.length; i++) {
				double la = (lat[r[i]] + (double) lat[r[i - 1]]) / 2e7;
				double lo = (lon[r[i]] + (double) lon[r[i - 1]]) / 2e7;
				if (la < box[0] || la >= box[1] || lo < box[2] || lo >= box[3]) {
					continue;
				}
				double d = com.gabolle.backend.place.service.GeoDistance.meters(lat[r[i - 1]] / 1e7, lon[r[i - 1]] / 1e7,
						lat[r[i]] / 1e7, lon[r[i]] / 1e7);
				double p = s / 10.0;
				if (p < 5) {
					green += d;
				}
				else if (p <= 8.33) {
					yellow += d;
				}
				else {
					red += d;
				}
			}
		}
		double total = green + yellow + red;
		return new double[] { green / total, yellow / total, red / total };
	}

	@Test
	@DisplayName("🔴 높낮이 폭 5m 안의 서면 길(1694, 바닥 전 7.5%)은 평탄(5% 미만)이다")
	void seomyeonNoiseWayIsFlat() {
		assertThat(waySlope[SEOMYEON_SMALL_WAY]).isBetween((short) 0, (short) 49);
	}

	@Test
	@DisplayName("🔴 서면 중심의 노랑·빨강이 바닥 전(길이로 27.0%)보다 줄었다")
	void seomyeonIsMostlyGreen() {
		double[] s = shares(SEOMYEON);
		System.out.printf("서면 초록 %.1f%% 노랑 %.1f%% 빨강 %.1f%%%n", s[0] * 100, s[1] * 100, s[2] * 100);
		assertThat(s[1] + s[2]).isLessThan(0.265);
	}

	@Test
	@DisplayName("감천문화마을은 그대로 빨강이 절반 넘는다 — 바닥은 진짜 언덕을 지우지 않는다")
	void gamcheonStaysSteep() {
		double[] s = shares(GAMCHEON);
		System.out.printf("감천 초록 %.1f%% 노랑 %.1f%% 빨강 %.1f%%%n", s[0] * 100, s[1] * 100, s[2] * 100);
		assertThat(s[2]).isGreaterThan(0.5);
	}
}
