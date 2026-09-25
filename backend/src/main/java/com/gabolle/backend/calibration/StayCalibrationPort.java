package com.gabolle.backend.calibration;

import java.util.OptionalInt;

/**
 * 실제로 머문 시간으로 고친 갈래별 체류 시간(분) — S15P21E201-1692.
 *
 * <p>계산은 저장소의 {@code backend/calibration} 작업(PySpark)이 하고 결과는 {@code calibration_value} 표에 판마다 쌓인다.
 * 이 창구는 갈래마다 가장 최근에 검사를 통과한 값만 준다. 값이 없는 갈래(표본이 모자라거나 아직 한 번도 안 돈 갈래)는 비어
 * 있고, 그러면 일정 조립은 자기 기본값을 쓴다.
 */
public interface StayCalibrationPort {

	/**
	 * @param category 장소 갈래. {@code null} 이면 빈 값
	 * @return 고친 체류 시간(분). 없으면 빈 값
	 */
	OptionalInt minutesFor(String category);
}
