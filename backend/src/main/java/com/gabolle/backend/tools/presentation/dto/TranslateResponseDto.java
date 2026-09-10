package com.gabolle.backend.tools.presentation.dto;

import com.gabolle.backend.tools.domain.TranslationResult;

/**
 * {@code POST /api/v1/tools/translate} 응답 본문 — S15P21E201-343.
 *
 * @param cached 참이면 이번 요청에서 업체를 부르지 않았다 — 완료 기준의 "캐시 히트" 를
 *        화면·테스트가 확인할 수 있는 칸이다
 */
public record TranslateResponseDto(String translatedText, boolean cached, String provider) {

	public static TranslateResponseDto from(TranslationResult result) {
		return new TranslateResponseDto(result.translatedText(), result.cached(), result.provider());
	}
}
