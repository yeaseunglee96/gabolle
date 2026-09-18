package com.gabolle.backend.tools.domain;

/**
 * 번역 결과 하나 — S15P21E201-343.
 *
 * @param translatedText 번역된 문장
 * @param cached 캐시에서 왔는가 — 참이면 이번 요청에서 업체를 부르지 않았다
 * @param provider 값을 만든 곳. 캐시 히트면 캐시에 처음 담길 때의 업체 이름을 모르므로
 *        {@code null} 이다 — 화면은 캐시 여부만 보면 되고 이 칸은 진단용이다
 */
public record TranslationResult(String translatedText, boolean cached, String provider) {
}
