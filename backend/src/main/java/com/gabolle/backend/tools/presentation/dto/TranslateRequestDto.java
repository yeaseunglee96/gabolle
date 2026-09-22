package com.gabolle.backend.tools.presentation.dto;

/** {@code POST /api/v1/tools/translate} 요청 본문. */
public record TranslateRequestDto(String sourceText, String direction) {
}
