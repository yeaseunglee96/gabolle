package com.gabolle.backend.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link KmaGridConverter} 검증 — S15P21E201-366.
 *
 * <p>기상청이 공개 예시로 드는 격자값(서울시청 부근 (60,127), 부산시청 부근 (98,76))으로
 * 변환이 정확한지 검증한다 — 이 값은 기상청 단기예보 조회서비스 문서·공공데이터포털 자료가
 * 공통으로 드는 값이다.
 */
class KmaGridConverterTest {

	@Test
	@DisplayName("서울시청 좌표는 격자 (60, 127) 이다")
	void seoulCityHallConvertsToKnownGrid() {
		KmaGridCoordinate grid = KmaGridConverter.toGrid(37.5665, 126.9780);

		assertThat(grid.nx()).isEqualTo(60);
		assertThat(grid.ny()).isEqualTo(127);
	}

	@Test
	@DisplayName("🔴 부산 좌표는 격자 (98, 76) 이다 — 완료 기준의 부산 좌표 검증")
	void busanConvertsToKnownGrid() {
		KmaGridCoordinate grid = KmaGridConverter.toGrid(35.1796, 129.0756);

		assertThat(grid.nx()).isEqualTo(98);
		assertThat(grid.ny()).isEqualTo(76);
	}
}
