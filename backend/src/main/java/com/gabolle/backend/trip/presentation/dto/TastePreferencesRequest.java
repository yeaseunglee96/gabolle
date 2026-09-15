package com.gabolle.backend.trip.presentation.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/v1/me/preferences/taste} 요청 본문 — S15P21E201-639.
 *
 * <p>온보딩 첫 실행과 마이페이지가 같은 모양을 쓴다. 답 하나의 모양은 여행 만들기와
 * 글자 그대로 같다({@link CreateTripRequest.PreferenceAnswerInput}) — 화면이 이미 그
 * 모양으로 취향을 다루고 있어서, 여기만 다른 모양을 쓰면 같은 값을 두 벌로 만들게 된다.
 *
 * <h2>🔴 보낸 차원만 바뀐다</h2>
 *
 * <p>안 보낸 차원은 <b>그대로 남는다.</b> 다섯 중 하나만 고쳤으면 그 하나만 보내면 된다.
 *
 * <h2>🔴 지우려면 {@code answerStatus} 를 {@code UNKNOWN} 으로 보낸다</h2>
 *
 * <p>마이페이지에서 <i>"이 취향 잊어 주세요"</i> 를 누른 경우다. {@code SKIPPED} 는
 * 지우기가 <b>아니다</b> — 그건 "물어봤는데 안 답했다" 라서 계정을 안 건드린다.
 * 둘을 뭉개면 지우기가 아예 불가능해진다.
 *
 * @param answers 바꿀 차원만. 빈 목록이면 아무 일도 일어나지 않는다
 */
public record TastePreferencesRequest(
		@NotNull @Valid List<CreateTripRequest.PreferenceAnswerInput> answers) {
}
