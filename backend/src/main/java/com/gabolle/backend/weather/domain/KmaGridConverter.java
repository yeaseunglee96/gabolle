package com.gabolle.backend.weather.domain;

/**
 * 위경도 → 기상청 격자(nx, ny) 변환 — S15P21E201-366.
 *
 * <p>🔴 <b>이 상수와 공식은 지어낸 값이 아니다.</b> 기상청이 단기예보 조회서비스 문서와 함께
 * 배포하는 위경도-격자 변환 C 코드(Lambert Conformal Conic 도법, 표준위도 30°/60°, 기준점
 * 동경 126°/북위 38°)를 그대로 옮긴 것이다 — 공공데이터포털의 "기상청_단기예보 조회서비스"
 * 문서 부록과, 그 문서를 그대로 옮긴 공개 자료(예: 기상청 API허브·데이터포털 이용자들이
 * 공유하는 변환 유틸리티)에서 반복적으로 확인되는 값이다.
 *
 * <p>기준점(서울시청 부근)이 격자 (60, 127)이고 부산 시청 부근이 (98, 76)이라는 것은 기상청이
 * 공개 예시로 드는 값이고, 이 클래스의 단위 테스트가 그 값으로 왕복 변환을 검증한다.
 *
 * <p>순수 수학 함수라 domain 계층에 둔다 — Spring·HTTP 의존이 없다.
 */
public final class KmaGridConverter {

	/** 지구 반지름(km). */
	private static final double RE = 6371.00877;

	/** 격자 간격(km). */
	private static final double GRID = 5.0;

	/** 표준위도 1(도). */
	private static final double SLAT1 = 30.0;

	/** 표준위도 2(도). */
	private static final double SLAT2 = 60.0;

	/** 기준점의 경도(도). */
	private static final double OLON = 126.0;

	/** 기준점의 위도(도). */
	private static final double OLAT = 38.0;

	/** 기준점의 X 격자 좌표. */
	private static final double XO = 43;

	/** 기준점의 Y 격자 좌표. */
	private static final double YO = 136;

	private static final double DEGRAD = Math.PI / 180.0;

	private KmaGridConverter() {
	}

	/**
	 * @param lat 위도(도)
	 * @param lon 경도(도)
	 * @return 기상청 단기예보 격자 좌표
	 */
	public static KmaGridCoordinate toGrid(double lat, double lon) {
		double re = RE / GRID;
		double slat1 = SLAT1 * DEGRAD;
		double slat2 = SLAT2 * DEGRAD;
		double olon = OLON * DEGRAD;
		double olat = OLAT * DEGRAD;

		double sn = Math.log(Math.cos(slat1) / Math.cos(slat2))
				/ Math.log(Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5));
		double sf = Math.pow(Math.tan(Math.PI * 0.25 + slat1 * 0.5), sn) * Math.cos(slat1) / sn;
		double ro = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + olat * 0.5), sn);

		double ra = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + (lat * DEGRAD) * 0.5), sn);
		double theta = lon * DEGRAD - olon;
		if (theta > Math.PI) {
			theta -= 2.0 * Math.PI;
		}
		if (theta < -Math.PI) {
			theta += 2.0 * Math.PI;
		}
		theta *= sn;

		int nx = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
		int ny = (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);
		return new KmaGridCoordinate(nx, ny);
	}
}
