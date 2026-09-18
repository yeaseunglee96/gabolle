package com.gabolle.backend.trip.domain;

/**
 * 여행 조건 모달에 사람이 준 답 한 벌 — S15P21E201-1231.
 *
 * <h2>🔴 이 record 가 왜 따로 있나 — 계층 검사가 요구한다</h2>
 *
 * 처음에는 응용 계층이 <b>JPA 엔티티를 그대로 돌려주고</b> 컨트롤러가 그것을 읽었다.
 * {@code LayeringArchitectureTest} 가 빨개졌다 —
 * <i>「{@code ..presentation..} 은 {@code ..infra..}·{@code ..repository..} 에 기대지 않는다」</i>.
 *
 * <p>규칙이 막는 것은 취향이 아니다. 컨트롤러가 엔티티를 직접 들면 <b>표 모양을 바꾸는 순간
 * 화면 계약이 같이 흔들린다</b> — 칸 이름 하나를 고쳤을 뿐인데 앱이 받는 JSON 이 바뀐다.
 * 그 사이에 이 record 를 두면 표는 표대로, 응답은 응답대로 움직인다.
 *
 * <p>씀씀이가 {@code PreferenceSnapshot.PreferenceAnswer} 를 같은 자리에 두고 있다.
 *
 * @param status 사람이 준 답. 「한 번도 안 물어봄」은 여기 없다 — 그건 <b>이 객체가 없는 것</b>이다
 * @param valueJson {@link TravelConstraintStatus#SAVED} 일 때만 채워진다. JSON 글자 한 덩어리다
 */
public record TravelConstraintAnswer(TravelConstraintStatus status, String valueJson) {
}
