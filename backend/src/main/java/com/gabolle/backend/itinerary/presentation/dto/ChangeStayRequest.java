package com.gabolle.backend.itinerary.presentation.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 한 곳에 머무는 시간을 바꾸는 요청(S15P21E201-2017).
 * 값의 규칙(15분 단위·15분 이상·12시간 이하)은 여기 Bean Validation 이 아니라 서비스가 본다 — 어긴 이유를 한 코드
 * ({@code ITINERARY_STAY_INVALID})로 내야 화면이 같은 안내를 띄울 수 있다.
 *
 * @param stayMinutes 새로 머무를 분
 * @param baseVersion 화면이 보고 있던 판
 */
public record ChangeStayRequest(
		@NotNull(message = "머무는 시간을 지정해 주세요.") Integer stayMinutes,
		@NotNull(message = "바탕 판 번호가 필요합니다.") Integer baseVersion) {
}
