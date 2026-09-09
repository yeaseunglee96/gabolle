package com.gabolle.backend.place.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 택시 목적지 카드 (S15P21E201-217).
 *
 * <p>한국어를 못 하는 여행자가 택시 기사에게 화면을 보여주면 목적지가 전달되는 화면에 쓴다. FE 와
 * 이미 합의한 계약이라 필드 이름을 바꾸지 않는다.
 *
 * <h2>🔴 {@code addressEn} 이 없으면 키 자체가 빠진다</h2>
 *
 * 빈 문자열도 {@code null} 도 아니다 — 값이 아예 없다는 뜻으로 <b>키를 뺀다.</b> 화면이 영문 주소
 * 칸을 아예 안 그리기 위해서다. 전역 Jackson 설정을 바꾸지 않고 이 record 에만
 * {@link JsonInclude}({@code NON_NULL})을 붙였다 — 다른 응답의 직렬화 방식에 영향을 주지 않기
 * 위해서다.
 *
 * @param addressKo 한국어 주소. 없을 수 있다({@code place.address} 가 nullable) — 그 경우
 *        {@link #driverSentence()} 는 장소 이름으로 대신한다
 * @param addressEn 영문 주소. 없으면 응답에서 키 자체가 빠진다
 * @param resolvedLanguage 실제로 어느 언어로 답했는가(S15P21E201-430, 부분). {@code "ko"} 또는
 *        {@code "en"}. 영어를 요청했는데 영문 이름이 없어 한국어로 되돌린(fallback) 경우를 화면이
 *        알아야 "번역이 없습니다" 안내를 할 수 있어서 둔 칸이다
 * @param driverSentence 기사에게 보여줄 한국어 한 문장. 형식은 "이 주소로 가주세요, {한국어 주소}" 고
 *        한국어 주소가 없으면 장소 이름을 쓴다. 🔴 <b>화면에서 조립하지 않고 서버가 준다.</b>
 *        문구가 어색하면 고쳐야 하는데, 화면(앱)에서 조립하면 앱을 다시 배포해야 하고 배포된 앱은
 *        스토어 심사 때문에 즉시 못 고친다. 서버가 문장 전체를 주면 문구만 바꿔 서버만 다시
 *        배포하면 된다. 🔴 이 문장은 {@link #resolvedLanguage()} 와 무관하게 <b>항상 한국어</b>다 —
 *        읽는 사람이 여행자가 아니라 한국어를 쓰는 택시 기사이기 때문이다
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
