package com.gabolle.backend.dish.presentation.dto;

/**
 * @param name 메뉴판 응답의 {@code name} 을 그대로 보낸다 — 사용자가 친 값이 아니라 사진에서 읽은
 *     값이다. 그래도 신뢰하지는 않는다. 막는 것은 {@code GmsDishDescriber} 의 몫이다
 * @param language 앱 언어({@code ko}·{@code en}·{@code ja}·{@code zh-Hans}·{@code zh-Hant}).
 *     없거나 모르는 값이면 한국어로 답한다
 */
public record DishRequest(String name, String language) {
}
