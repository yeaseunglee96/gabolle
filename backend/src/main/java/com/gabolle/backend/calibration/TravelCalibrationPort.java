package com.gabolle.backend.calibration;

import java.util.OptionalDouble;

/**
 * 실제 이동으로 고친 수단별 배율(실제 ÷ 어림) — S15P21E201-1700.
 *
 * <p>계산은 저장소의 {@code backend/calibration/jobs/travel_multiplier} 작업이 하고 결과는 {@code calibration_value} 에 판마다
 * 쌓인다. 이 창구는 수단마다 가장 최근에 검사를 통과한 배율만 준다. 없는 수단은 비어 있고, 그러면 엔진의 어림을 그대로
 * 쓴다.
 */
public interface TravelCalibrationPort {

	/**
	 * @param travelMode 이동 수단({@code BUS} · {@code WALK} · {@code PRIVATE_CAR} …). {@code null} 이면 빈 값
	 * @return 어림에 곱할 배율. 없으면 빈 값
	 */
	OptionalDouble multiplierFor(String travelMode);
}
