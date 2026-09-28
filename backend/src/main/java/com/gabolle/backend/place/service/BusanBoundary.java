package com.gabolle.backend.place.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 좌표가 부산광역시 행정 경계 안인가 (S15P21E201-1617).
 *
 * <p>🔴 <b>사각형으로는 못 가른다.</b> 코드에 있던 부산 기준은 「부산을 덮는 사각형」뿐이었는데
 * ({@code OriginSearchProperties.searchRect}), 부산은 모양이 들쭉날쭉해서 그 사각형이 김해 장유·율하, 양산
 * 사송·물금까지 덮는다. 오픈스트리트맵 적재가 사각형으로 받아 와 운영 장소 6,931곳 중 858곳이 부산 밖이었다.
 *
 * <p>경계는 오픈스트리트맵의 부산광역시 행정 경계(relation 2396450)를 자원 파일({@value #RESOURCE})로 넣은 것이다
 * — ODbL, © OpenStreetMap 기여자. 바다 쪽 행정 경계까지 들어 있어 해안의 장소도 안에 든다. 점 730개라 매번
 * 전부 훑어도 가볍다.
 *
 * <p>판정은 반직선 교차(점에서 한쪽으로 그은 선이 테두리를 몇 번 넘는가 — 홀수면 안)다. 좌표를 모르면 안이라고
 * 하지 않는다.
 */
public final class BusanBoundary {

	static final String RESOURCE = "geo/busan-boundary.json";

	/** [경도, 위도] 쌍의 닫힌 테두리. */
	private static final double[][] RING = load();

	private BusanBoundary() {
	}

	public static boolean contains(Double lat, Double lng) {
		if (lat == null || lng == null) {
			return false;
		}
		boolean inside = false;
		for (int i = 0, j = RING.length - 1; i < RING.length; j = i++) {
			double xi = RING[i][0];
			double yi = RING[i][1];
			double xj = RING[j][0];
			double yj = RING[j][1];
			if ((yi > lat) != (yj > lat) && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
				inside = !inside;
			}
		}
		return inside;
	}

	private static double[][] load() {
		try (InputStream in = BusanBoundary.class.getClassLoader().getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("부산 경계 자원이 없다: " + RESOURCE);
			}
			JsonNode ring = JsonMapper.builder().build().readTree(in).path("ring");
			if (!ring.isArray() || ring.size() < 4) {
				throw new IllegalStateException("부산 경계 자원의 ring 이 테두리가 아니다: " + RESOURCE);
			}
			double[][] points = new double[ring.size()][];
			for (int i = 0; i < ring.size(); i++) {
				points[i] = new double[] { ring.get(i).get(0).asDouble(), ring.get(i).get(1).asDouble() };
			}
			return points;
		}
		catch (IOException ex) {
			throw new UncheckedIOException("부산 경계 자원을 못 읽었다: " + RESOURCE, ex);
		}
	}
}
