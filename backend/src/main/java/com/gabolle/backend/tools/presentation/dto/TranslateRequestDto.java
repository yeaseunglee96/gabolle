package com.gabolle.backend.tools.presentation.dto;

/** {@code POST /api/v1/tools/translate} 요청 본문 — S15P21E201-343. */
public record TranslateRequestDto(String sourceText, String direction) {
}
