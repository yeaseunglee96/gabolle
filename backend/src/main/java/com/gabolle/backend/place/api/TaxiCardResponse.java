package com.gabolle.backend.place.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 택시 목적지 카드. FE 와 이미 합의한 계약이라 필드 이름을 바꾸지 않는다.
 *
 * <p>값이 없는 칸은 빈 문자열이 아니라 키 자체가 빠진다. 전역 Jackson 설정을 건드리지 않으려고
 * 이 record 에만 {@link JsonInclude}({@code NON_NULL})을 붙였다.
 *
 * <p>{@code addressKo} 는 없을 수 있고({@code place.address} 가 nullable) 그때
 * {@code driverSentence} 는 장소 이름을 쓴다. {@code resolvedLanguage} 는 실제로 답한 언어
 * ({@code "ko"}/{@code "en"})이고, 영어를 요청했는데 영문 이름이 없어 한국어로 되돌린 경우를
 * 화면이 알아야 "번역이 없습니다" 안내를 할 수 있어 둔 칸이다.
 *
 * <p>{@code driverSentence} 는 {@code resolvedLanguage} 와 무관하게 항상 한국어다 — 읽는 사람이
 * 여행자가 아니라 택시 기사다. 화면에서 조립하지 않고 서버가 문장 전체를 주는 것은, 배포된 앱은
 * 스토어 심사 때문에 문구를 즉시 못 고치기 때문이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaxiCardResponse(
		UUID placeId,
		String nameKo,
		String addressKo,
		String addressEn,
		String resolvedLanguage,
		String driverSentence) {
}
