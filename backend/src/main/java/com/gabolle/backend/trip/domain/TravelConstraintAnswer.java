package com.gabolle.backend.trip.domain;

/**
 * 여행 조건 모달에 사람이 준 답 한 벌. 「한 번도 안 물어봄」은 값이 아니라 이 객체가 없는 것이다.
 *
 * @param valueJson {@link TravelConstraintStatus#SAVED} 일 때만 채워진다. JSON 글자 한 덩어리다
 */
public record TravelConstraintAnswer(TravelConstraintStatus status, String valueJson) {
}
