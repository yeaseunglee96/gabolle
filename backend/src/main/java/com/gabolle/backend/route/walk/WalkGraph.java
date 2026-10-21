package com.gabolle.backend.route.walk;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import com.gabolle.backend.route.domain.RouteLeg;

/**
 * 걷는 길 그래프와 그 위의 최단 경로 — S15P21E201-1630.
 *
 * <p>파일은 {@code bigData/process/walk-graph.mjs} 가 만든다(GBWG v1 — 그 머리말에 모양이 있다). 점은 교차점·꺾인
 * 점이고, 길은 점 번호의 줄이며 길마다 경사(천분율, -1 = 모름)와 계단 표시를 든다. 이 클래스는 읽은 뒤 바뀌지 않아
 * 여러 요청이 함께 써도 된다.
 *
 * <p>GBWG v2(S15P21E201-1895)는 v1 에 <b>길마다 그늘 한 바이트</b>가 경사 뒤에 더해진 것이다 — 0~100 은 건물 그림자
 * 하루 평균(%), 255 는 모름. {@code bigData/process/walk-graph-shade.mjs} 가 v1 에 그늘을 붙여 만든다. 이 클래스는
 * v1 도 읽고, v1 이면 그늘은 전부 모름이다.
 *
 * <p>🔴 붙일 점은 <b>한 덩어리(가장 큰 연결 성분)</b>에서만 찾는다. 섬처럼 떨어진 작은 덩어리(아파트 단지 안 길 등)에
 * 붙으면 어디로도 못 가서, 가까운 큰 길에 붙었으면 찾았을 길을 못 찾는다.
 *
 * <p>찾기는 A*(목적지까지의 직선거리를 어림으로 쓰는 최단 경로 찾기)다. 거리는 두 점을 평면으로 펴서 잰다 — 부산
 * 안에서는 하버사인과 0.1% 안쪽으로 같다.
 */
public final class WalkGraph {

	/** 출발·도착 좌표를 길에 붙일 때 이보다 멀면 못 붙인다(m) — 그때는 직선 어림으로 떨어진다. */
	static final double MAX_SNAP_M = 300;

	/** 한 번 찾기에서 확정하는 점 수 상한 — 못 이을 때 그래프 전체를 훑지 않게. */
	static final int MAX_SETTLED = 400_000;

	/**
	 * 계단을 피하는 길(stepFree)에서 계단 길 1m 를 몇 m 로 칠까. 휠체어·유모차는 계단을 못 지나므로 사실상 「다른
	 * 길이 아예 없을 때만」 쓰게 크게 둔다. 끝없이(∞) 두지 않는 것은, 계단 말고 이을 길이 없는 곳에서 경로가 통째로
	 * 사라지면 직선 어림으로 떨어져 오히려 계단이 있는지조차 안 보이기 때문이다 — 계단 표시가 조각에 남는다.
	 */
	static final double STAIRS_COST_FACTOR = 25;

	/**
	 * 계단을 피하는 길에서 가파른 길 1m 를 몇 m 로 칠까. 4배 안쪽으로 돌아가는 평지 길이 있으면 그쪽으로 간다.
	 * 경사를 모르는 길(-1)은 1배다 — 모르는 것을 가파르다고 치면 자료가 빈 동네 전체를 피하게 된다.
	 */
	static final double STEEP_COST_FACTOR = 4;

	/**
	 * 가파르다고 보는 경사(천분율). 83‰ = 8.33% 는 휠체어 경사로 기준(1:12)이고, 추천의 경사 상한
	 * {@code gabolle.recommendation.mobility.max-slope-percent=8.33}(application.properties)과 같은 값이다 —
	 * 장소는 들어갈 수 있다고 해 놓고 가는 길은 못 가게 되면 안 된다.
	 */
	static final int STEEP_PERMILLE = 83;

	private static final double M_PER_DEG_LAT = 110_574;

	/** 붙일 점을 찾는 칸의 크기(도). 약 220m. */
	private static final double CELL_DEG = 0.002;

	private final int[] latE7;

	private final int[] lonE7;

	/** 점 u 에서 나가는 길 조각은 {@code adjTo[adjStart[u] .. adjStart[u+1])} 이다. */
	private final int[] adjStart;

	private final int[] adjTo;

	private final float[] adjMeters;

	private final int[] adjWay;

	private final short[] waySlopePermille;

	private final boolean[] wayStairs;

	/** 길마다 그늘(건물 그림자 하루 평균, 0~100 %). -1 = 모름 — v1 파일이거나 그늘을 재 두지 않은 길이다. */
	private final short[] wayShadePercent;

	private final Map<Long, int[]> cells;

	private final int mainNodes;

	/** 한 번 찾기에서 확정하는 점 수 상한. 운영은 {@link #MAX_SETTLED} 이고, 시험만 {@link #withMaxSettled} 로 줄인다. */
	private final int maxSettled;

	private WalkGraph(int[] latE7, int[] lonE7, int[] adjStart, int[] adjTo, float[] adjMeters, int[] adjWay,
			short[] waySlopePermille, boolean[] wayStairs, short[] wayShadePercent, Map<Long, int[]> cells,
			int mainNodes) {
		this(latE7, lonE7, adjStart, adjTo, adjMeters, adjWay, waySlopePermille, wayStairs, wayShadePercent, cells,
				mainNodes, MAX_SETTLED);
	}

	private WalkGraph(int[] latE7, int[] lonE7, int[] adjStart, int[] adjTo, float[] adjMeters, int[] adjWay,
			short[] waySlopePermille, boolean[] wayStairs, short[] wayShadePercent, Map<Long, int[]> cells,
			int mainNodes, int maxSettled) {
		this.maxSettled = maxSettled;
		this.wayShadePercent = wayShadePercent;
		this.latE7 = latE7;
		this.lonE7 = lonE7;
		this.adjStart = adjStart;
		this.adjTo = adjTo;
		this.adjMeters = adjMeters;
		this.adjWay = adjWay;
		this.waySlopePermille = waySlopePermille;
		this.wayStairs = wayStairs;
		this.cells = cells;
		this.mainNodes = mainNodes;
	}

	/** 점 수 · 길 수 · 한 덩어리에 든 점 수 — 기동 로그에 남긴다. */
	public int nodeCount() {
		return this.latE7.length;
	}

	public int wayCount() {
		return this.waySlopePermille.length;
	}

	public int mainNodeCount() {
		return this.mainNodes;
	}

	/** 그늘 값이 있는 길 수 — v1 파일이면 0 이다. 기동 로그에 남겨 그늘 없는 파일이 배포됐는지 바로 보이게 한다. */
	public int shadedWayCount() {
		int count = 0;
		for (short shade : this.wayShadePercent) {
			if (shade >= 0) {
				count++;
			}
		}
		return count;
	}

	/** 같은 그래프를 점 수 상한만 바꿔 든다 — 배열은 나눠 쓴다. 상한을 넘는 경우를 작은 그래프로 재 보려는 시험용이다. */
	WalkGraph withMaxSettled(int limit) {
		return new WalkGraph(this.latE7, this.lonE7, this.adjStart, this.adjTo, this.adjMeters, this.adjWay,
				this.waySlopePermille, this.wayStairs, this.wayShadePercent, this.cells, this.mainNodes, limit);
	}

	/**
	 * 찾은 길.
	 *
	 * @param path {@code [경도, 위도]} 목록 — 출발 좌표, 길 위의 점들, 도착 좌표 순이다
	 * @param pieces 경로를 경사·계단이 같은 조각으로 나눈 것. 번호는 {@code path} 의 자리다
	 * @param meters 출발·도착을 길에 붙이는 거리까지 더한 길이
	 * @param stepFreeHonored 계단·급경사를 피해 달라는 부탁을 들어준 길인가. 피하는 찾기가 상한에 걸리거나 못 이어
	 *        가장 짧은 길로 대신 답했으면 {@code false} 다 — 그 길에는 계단이 있을 수 있다(조각의 계단 표시를 본다).
	 *        피해 달라고 안 했으면 늘 {@code true}(들어줄 부탁이 없었다)
	 */
	public record Route(List<double[]> path, List<RouteLeg.Piece> pieces, double meters, boolean stepFreeHonored) {

		/** 부탁을 그대로 들어준 길 — 보통 길과 피하기에 성공한 길. */
		public Route(List<double[]> path, List<RouteLeg.Piece> pieces, double meters) {
			this(path, pieces, meters, true);
		}
	}

	public static WalkGraph read(InputStream gzip) throws IOException {
		try (DataInputStream in = new DataInputStream(
				new BufferedInputStream(new GZIPInputStream(gzip, 1 << 16), 1 << 16))) {
			byte[] magic = in.readNBytes(4);
			int version = in.readInt();
			if (!"GBWG".equals(new String(magic, java.nio.charset.StandardCharsets.US_ASCII))
					|| (version != 1 && version != 2)) {
				throw new IOException("보행 그래프 파일이 아니거나 판이 다르다 — GBWG v1 또는 v2 여야 한다");
			}
			int n = in.readInt();
			int w = in.readInt();
			int refs = in.readInt();
			int[] lat = new int[n];
			int[] lon = new int[n];
			for (int i = 0; i < n; i++) {
				lat[i] = in.readInt();
				lon[i] = in.readInt();
			}
			int edges = refs - w;
			int[] from = new int[edges];
			int[] to = new int[edges];
			int[] way = new int[edges];
			short[] slope = new short[w];
			boolean[] stairs = new boolean[w];
			short[] shade = new short[w];
			Arrays.fill(shade, (short) -1);
			int e = 0;
			for (int k = 0; k < w; k++) {
				int count = in.readInt();
				stairs[k] = in.readUnsignedByte() == 1;
				slope[k] = in.readShort();
				if (version >= 2) {
					// 0~100 은 그림자 %, 255 는 모름. 그 밖의 값은 깨진 것이므로 모름으로 읽는다 — 틀린 그늘을 칠하지 않는다.
					int percent = in.readUnsignedByte();
					shade[k] = (percent <= 100) ? (short) percent : -1;
				}
				int prev = in.readInt();
				for (int i = 1; i < count; i++) {
					int cur = in.readInt();
					from[e] = prev;
					to[e] = cur;
					way[e] = k;
					e++;
					prev = cur;
				}
			}
			return build(lat, lon, from, to, way, slope, stairs, shade);
		}
	}

	private static WalkGraph build(int[] lat, int[] lon, int[] from, int[] to, int[] way, short[] slope,
			boolean[] stairs, short[] shade) {
		int n = lat.length;
		int[] start = new int[n + 1];
		for (int i = 0; i < from.length; i++) {
			start[from[i] + 1]++;
			start[to[i] + 1]++;
		}
		for (int i = 0; i < n; i++) {
			start[i + 1] += start[i];
		}
		int[] fill = Arrays.copyOf(start, n);
		int[] adjTo = new int[from.length * 2];
		float[] adjMeters = new float[from.length * 2];
		int[] adjWay = new int[from.length * 2];
		for (int i = 0; i < from.length; i++) {
			float m = (float) planarMeters(lat[from[i]], lon[from[i]], lat[to[i]], lon[to[i]]);
			int a = fill[from[i]]++;
			adjTo[a] = to[i];
			adjMeters[a] = m;
			adjWay[a] = way[i];
			int b = fill[to[i]]++;
			adjTo[b] = from[i];
			adjMeters[b] = m;
			adjWay[b] = way[i];
		}

		// 가장 큰 덩어리를 찾는다.
		int[] component = new int[n];
		Arrays.fill(component, -1);
		int[] stack = new int[n];
		int best = -1;
		int bestSize = 0;
		int next = 0;
		for (int s = 0; s < n; s++) {
			if (component[s] >= 0) {
				continue;
			}
			int top = 0;
			stack[top++] = s;
			component[s] = next;
			int size = 0;
			while (top > 0) {
				int u = stack[--top];
				size++;
				for (int a = start[u]; a < start[u + 1]; a++) {
					if (component[adjTo[a]] < 0) {
						component[adjTo[a]] = next;
						stack[top++] = adjTo[a];
					}
				}
			}
			if (size > bestSize) {
				bestSize = size;
				best = next;
			}
			next++;
		}

		// 한 덩어리의 점만 칸에 넣는다.
		Map<Long, List<Integer>> byCell = new HashMap<>();
		for (int i = 0; i < n; i++) {
			if (component[i] == best) {
				byCell.computeIfAbsent(cellOf(lat[i] / 1e7, lon[i] / 1e7), k -> new ArrayList<>()).add(i);
			}
		}
		Map<Long, int[]> cells = new HashMap<>(byCell.size() * 2);
		byCell.forEach((k, v) -> cells.put(k, v.stream().mapToInt(Integer::intValue).toArray()));
		return new WalkGraph(lat, lon, start, adjTo, adjMeters, adjWay, slope, stairs, shade, cells, bestSize);
	}

	/** 두 좌표 사이 최단 보행 경로. 붙일 길이 없거나 못 이으면 빈 값이다. 계단은 가리지 않는다. */
	public Optional<Route> route(double originLat, double originLng, double destLat, double destLng) {
		return route(originLat, originLng, destLat, destLng, false);
	}

	/**
	 * 두 좌표 사이 보행 경로.
	 *
	 * <p>🔴 {@code stepFree} 면 가장 <b>짧은</b> 길이 아니라 계단·급경사를 치른 값이 가장 <b>싼</b> 길을 찾는다 —
	 * 계단 길은 길이 × {@link #STAIRS_COST_FACTOR}, 가파른 길은 × {@link #STEEP_COST_FACTOR} 로 친다. 전에는 경사와
	 * 계단을 지도에 칠하기만 하고 길 고르기에는 안 써서, 휠체어 사용자에게도 계단 지름길을 냈다.
	 *
	 * <p>돌려주는 {@link Route#meters()} 는 치른 값이 아니라 <b>실제 길이</b>다 — 걷는 시간이 거기서 나온다.
	 *
	 * <p>🔴 피하는 찾기가 못 끝나면 가장 짧은 길로 대신 답한다. 배수를 치르면 A* 의 어림(직선거리)이 실제 값보다
	 * 한참 작아져 확정하는 점이 늘고, 먼 구간은 {@link #MAX_SETTLED} 에 걸린다. 예전에는 그때 빈 값을 줘서 직선
	 * 어림으로 떨어졌고, 길 모양도 계단 표시도 사라진 채 「계단 없는 길」처럼 보였다. 이제는 가장 짧은 길을 내고
	 * {@link Route#stepFreeHonored()} 를 거짓으로 둔다 — 계단은 조각에 그대로 칠해진다.
	 *
	 * @param stepFree 계단과 급경사를 피할까. {@code false} 면 전과 똑같이 가장 짧은 길이다
	 */
	public Optional<Route> route(double originLat, double originLng, double destLat, double destLng,
			boolean stepFree) {
		int s = nearest(originLat, originLng);
		int g = nearest(destLat, destLng);
		if (s < 0 || g < 0) {
			return Optional.empty();
		}
		int[] nodes = search(s, g, stepFree);
		boolean honored = true;
		if (nodes == null && stepFree) {
			nodes = search(s, g, false);
			honored = false;
		}
		if (nodes == null) {
			return Optional.empty();
		}
		// 아래 조각 고르기도 실제로 찾은 값으로 해야 찾은 길과 조각이 어긋나지 않는다(edgeBetween 설명).
		boolean searchedStepFree = stepFree && honored;

		List<double[]> path = new ArrayList<>(nodes.length + 2);
		List<RouteLeg.Piece> pieces = new ArrayList<>();
		double meters = 0;
		path.add(new double[] { originLng, originLat });
		double snapIn = planarMeters(originLat, originLng, this.latE7[s] / 1e7, this.lonE7[s] / 1e7);
		meters += snapIn;
		for (int i = 0; i < nodes.length; i++) {
			path.add(new double[] { this.lonE7[nodes[i]] / 1e7, this.latE7[nodes[i]] / 1e7 });
		}
		// 출발 좌표 → 첫 점: 길 밖이라 경사·그늘을 모른다.
		addPiece(pieces, 0, 1, null, false, null);
		for (int i = 1; i < nodes.length; i++) {
			int a = edgeBetween(nodes[i - 1], nodes[i], searchedStepFree);
			meters += this.adjMeters[a];
			int wayIndex = this.adjWay[a];
			short permille = this.waySlopePermille[wayIndex];
			addPiece(pieces, i, i + 1, permille < 0 ? null : permille / 10.0, this.wayStairs[wayIndex],
					shadeOf(this.wayShadePercent[wayIndex]));
		}
		path.add(new double[] { destLng, destLat });
		meters += planarMeters(this.latE7[g] / 1e7, this.lonE7[g] / 1e7, destLat, destLng);
		addPiece(pieces, nodes.length, nodes.length + 1, null, false, null);
		return Optional.of(new Route(List.copyOf(path), List.copyOf(pieces), meters, honored));
	}

	/**
	 * 그늘(%)을 조각에 싣는 값으로 — 0.1 단위(0.0, 0.1, … 1.0)로 묶는다. 모르면({@code < 0}) {@code null}.
	 *
	 * <p>10% 단위로 묶는 것은 조각이 잘게 쪼개지지 않게 하려는 것이다. 그늘은 길마다 조금씩 달라(37%, 41%, 38%…) 그대로
	 * 두면 경사가 같은 이웃 길도 매번 새 조각이 되어 지도 선이 수십 토막으로 나뉜다. 10% 차이는 색 단계(세 단계)보다
	 * 작아 눈에 보이지 않는다.
	 */
	static Double shadeOf(short percent) {
		return (percent < 0) ? null : Math.round(percent / 10.0) / 10.0;
	}

	/** 앞 조각과 경사·계단·그늘이 같으면 이어 붙이고, 다르면 새 조각을 연다. */
	private static void addPiece(List<RouteLeg.Piece> pieces, int from, int to, Double slopePercent, boolean stairs,
			Double shade) {
		if (!pieces.isEmpty()) {
			RouteLeg.Piece last = pieces.get(pieces.size() - 1);
			if (last.to() == from && java.util.Objects.equals(last.slopePercent(), slopePercent)
					&& last.stairs() == stairs && java.util.Objects.equals(last.shade(), shade)) {
				pieces.set(pieces.size() - 1, new RouteLeg.Piece(last.from(), to, slopePercent, stairs, shade));
				return;
			}
		}
		pieces.add(new RouteLeg.Piece(from, to, slopePercent, stairs, shade));
	}

	/**
	 * 두 점을 잇는 조각 중 가장 싼 것 — 같은 두 점을 잇는 길이 둘일 수 있다(예: 계단과 옆 경사로).
	 *
	 * <p>찾기({@link #search})와 같은 값({@link #cost})으로 고른다. 길이로만 고르면 찾기는 경사로를 골랐는데 조각에는
	 * 옆의 계단이 실려, 지도가 「계단으로 가라」고 칠하고 거리도 다른 길의 것이 된다.
	 */
	private int edgeBetween(int u, int v, boolean stepFree) {
		int best = -1;
		for (int a = this.adjStart[u]; a < this.adjStart[u + 1]; a++) {
			if (this.adjTo[a] == v && (best < 0 || cost(a, stepFree) < cost(best, stepFree))) {
				best = a;
			}
		}
		return best;
	}

	/**
	 * 조각 하나를 지나는 값. 보통은 길이(m) 그대로이고, stepFree 면 계단·급경사에 배수를 곱한다.
	 *
	 * <p>배수는 전부 1 이상이다 — 그래서 값이 실제 길이보다 작아지는 일이 없고, 직선거리로 잡은 어림
	 * ({@link #heuristic})이 여전히 남은 값을 넘지 않아 A* 가 가장 싼 길을 놓치지 않는다.
	 */
	private double cost(int a, boolean stepFree) {
		double meters = this.adjMeters[a];
		if (!stepFree) {
			return meters;
		}
		int wayIndex = this.adjWay[a];
		if (this.wayStairs[wayIndex]) {
			return meters * STAIRS_COST_FACTOR;
		}
		// -1(모름)은 83 을 넘지 않으므로 1배다.
		return (this.waySlopePermille[wayIndex] > STEEP_PERMILLE) ? meters * STEEP_COST_FACTOR : meters;
	}

	/** 한 덩어리 안에서 가장 가까운 점. {@link #MAX_SNAP_M} 안에 없으면 -1. */
	int nearest(double lat, double lng) {
		long ci = (long) Math.floor(lat / CELL_DEG);
		long cj = (long) Math.floor(lng / CELL_DEG);
		int best = -1;
		double bestM = MAX_SNAP_M;
		for (long i = ci - 2; i <= ci + 2; i++) {
			for (long j = cj - 2; j <= cj + 2; j++) {
				int[] bucket = this.cells.get((i << 32) | (j & 0xffffffffL));
				if (bucket == null) {
					continue;
				}
				for (int node : bucket) {
					double m = planarMeters(lat, lng, this.latE7[node] / 1e7, this.lonE7[node] / 1e7);
					if (m <= bestM) {
						bestM = m;
						best = node;
					}
				}
			}
		}
		return best;
	}

	/** A*. 점 번호 줄(출발 → 도착)을 돌려주고, 못 이으면 {@code null}. 값은 {@link #cost} 로 센다. */
	private int[] search(int s, int g, boolean stepFree) {
		double goalLat = this.latE7[g] / 1e7;
		double goalLng = this.lonE7[g] / 1e7;
		Map<Integer, Double> dist = new HashMap<>();
		Map<Integer, Integer> prev = new HashMap<>();
		MinHeap open = new MinHeap();
		dist.put(s, 0.0);
		open.push(heuristic(s, goalLat, goalLng), s);
		int settled = 0;
		while (!open.isEmpty()) {
			double f = open.peekKey();
			int u = open.pop();
			double du = dist.get(u);
			if (f > du + heuristic(u, goalLat, goalLng) + 1e-6) {
				continue; // 더 짧은 길로 이미 확정된 낡은 항목
			}
			if (u == g) {
				return trace(prev, s, g);
			}
			if (++settled > this.maxSettled) {
				return null;
			}
			for (int a = this.adjStart[u]; a < this.adjStart[u + 1]; a++) {
				int v = this.adjTo[a];
				double nd = du + cost(a, stepFree);
				Double old = dist.get(v);
				if (old == null || nd < old) {
					dist.put(v, nd);
					prev.put(v, u);
					open.push(nd + heuristic(v, goalLat, goalLng), v);
				}
			}
		}
		return null;
	}

	/** 남은 거리의 어림 — 직선거리보다 조금 작게(0.99) 잡아 넘치지 않게 한다. 넘치면 가장 짧은 길을 놓친다. */
	private double heuristic(int node, double goalLat, double goalLng) {
		return 0.99 * planarMeters(this.latE7[node] / 1e7, this.lonE7[node] / 1e7, goalLat, goalLng);
	}

	private static int[] trace(Map<Integer, Integer> prev, int s, int g) {
		List<Integer> back = new ArrayList<>();
		for (Integer cur = g; cur != null; cur = (cur == s) ? null : prev.get(cur)) {
			back.add(cur);
		}
		int[] nodes = new int[back.size()];
		for (int i = 0; i < nodes.length; i++) {
			nodes[i] = back.get(back.size() - 1 - i);
		}
		return nodes;
	}

	private static long cellOf(double lat, double lng) {
		long i = (long) Math.floor(lat / CELL_DEG);
		long j = (long) Math.floor(lng / CELL_DEG);
		return (i << 32) | (j & 0xffffffffL);
	}

	private static double planarMeters(int lat1E7, int lon1E7, int lat2E7, int lon2E7) {
		return planarMeters(lat1E7 / 1e7, lon1E7 / 1e7, lat2E7 / 1e7, lon2E7 / 1e7);
	}

	static double planarMeters(double lat1, double lng1, double lat2, double lng2) {
		double midLat = Math.toRadians((lat1 + lat2) / 2);
		double dx = (lng2 - lng1) * 111_320 * Math.cos(midLat);
		double dy = (lat2 - lat1) * M_PER_DEG_LAT;
		return Math.hypot(dx, dy);
	}

	/** 작은 최소 힙 — 열쇠(어림 포함 거리)와 점 번호를 원시 배열로 든다. */
	private static final class MinHeap {

		private double[] keys = new double[256];

		private int[] values = new int[256];

		private int size;

		boolean isEmpty() {
			return this.size == 0;
		}

		double peekKey() {
			return this.keys[0];
		}

		void push(double key, int value) {
			if (this.size == this.keys.length) {
				this.keys = Arrays.copyOf(this.keys, this.size * 2);
				this.values = Arrays.copyOf(this.values, this.size * 2);
			}
			int i = this.size++;
			while (i > 0) {
				int p = (i - 1) >>> 1;
				if (this.keys[p] <= key) {
					break;
				}
				this.keys[i] = this.keys[p];
				this.values[i] = this.values[p];
				i = p;
			}
			this.keys[i] = key;
			this.values[i] = value;
		}

		int pop() {
			int top = this.values[0];
			double key = this.keys[--this.size];
			int value = this.values[this.size];
			int i = 0;
			while (true) {
				int l = 2 * i + 1;
				if (l >= this.size) {
					break;
				}
				int c = (l + 1 < this.size && this.keys[l + 1] < this.keys[l]) ? l + 1 : l;
				if (this.keys[c] >= key) {
					break;
				}
				this.keys[i] = this.keys[c];
				this.values[i] = this.values[c];
				i = c;
			}
			this.keys[i] = key;
			this.values[i] = value;
			return top;
		}
	}
}
