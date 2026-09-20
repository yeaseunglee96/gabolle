package com.gabolle.backend.tools.presentation.dto;

import com.gabolle.backend.tools.domain.TranslationResult;

/** {@code POST /api/v1/tools/translate} 응답 본문. {@code cached} 가 참이면 업체를 부르지 않았다. */
public record TranslateResponseDto(String translatedText, boolean cached, String provider) {

	public static TranslateResponseDto from(TranslationResult result) {
		return new TranslateResponseDto(result.translatedText(), result.cached(), result.provider());
	}
}
