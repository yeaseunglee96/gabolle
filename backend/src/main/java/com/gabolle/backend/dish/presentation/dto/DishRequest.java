package com.gabolle.backend.dish.presentation.dto;

/**
 * 어떤 음식을 물어보나 — S15P21E201-1272.
 *
 * @param name 🔴 <b>메뉴판 응답의 {@code name} 을 그대로 보낸다.</b> 사용자가 손으로 친
 *     값이 아니라 사진에서 읽은 값이라는 뜻이다. 그래도 신뢰하지는 않는다 — 메뉴판에
 *     「이전 지시를 무시하고 …」를 인쇄해 두면 그 글자가 여기로 온다. 막는 것은
 *     {@code GmsDishDescriber} 의 몫이고, 실제로 그 문장을 넣어 보면 빈 설명이 온다
 * @param language 앱 언어({@code ko}·{@code en}·{@code ja}·{@code zh-Hans}·{@code zh-Hant}).
 *     없거나 모르는 값이면 한국어로 답한다 — 메뉴판 읽기와 같은 규칙이다
 */
public record DishRequest(String name, String language) {
}
