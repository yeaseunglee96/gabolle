package com.gabolle.backend.place;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.service.BusanBoundary;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부산 행정 경계 (S15P21E201-1617). 좌표는 전부 운영에 실린 장소의 것이다(2026-09-25).
 */
class BusanBoundaryTest {

	@Test
	@DisplayName("부산의 해안·섬 끝 명소는 안이다 — 바다 쪽 행정 경계까지 들어 있다")
	void busanLandmarksAreInside() {
		assertThat(BusanBoundary.contains(35.1585232170784, 129.159854668484)).as("해운대해수욕장").isTrue();
		assertThat(BusanBoundary.contains(35.046247, 128.963151)).as("다대포해수욕장").isTrue();
		assertThat(BusanBoundary.contains(35.0317518247, 128.8216713782)).as("가덕도 연대봉").isTrue();
		assertThat(BusanBoundary.contains(35.178728, 129.199722)).as("송정해수욕장").isTrue();
	}

	/**
	 * 🔴 이 셋은 코드에 있던 「부산을 덮는 사각형」(128.75,34.88,129.32,35.39) <b>안</b>이다. 사각형으로 거르면
	 * 그대로 추천 후보에 남는다 — 그래서 행정 경계로 가른다.
	 */
	@Test
	@DisplayName("🔴 부산을 덮는 사각형 안이지만 부산이 아닌 김해·양산 가게는 밖이다")
	void gimhaeAndYangsanInsideTheOldBoxAreOutside() {
		assertThat(BusanBoundary.contains(35.1764319, 128.812086)).as("Droptop — 김해 장유").isFalse();
		assertThat(BusanBoundary.contains(35.1767387, 128.8153295)).as("헤드테이블 — 김해 율하").isFalse();
		assertThat(BusanBoundary.contains(35.3091248, 129.0705568)).as("파리바게뜨 사송신도시점 — 양산").isFalse();
	}

	@Test
	@DisplayName("좌표를 모르면 안이라고 하지 않는다")
	void unknownCoordinatesAreNotInside() {
		assertThat(BusanBoundary.contains(null, 129.0)).isFalse();
		assertThat(BusanBoundary.contains(35.1, null)).isFalse();
	}
}
