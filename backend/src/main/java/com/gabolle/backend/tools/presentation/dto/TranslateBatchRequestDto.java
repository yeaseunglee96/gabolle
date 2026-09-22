package com.gabolle.backend.tools.presentation.dto;

import java.util.List;

/**
 * {@code POST /api/v1/tools/translate/batch} 요청 본문.
 *
 * @param direction 단건 번역과 같은 값({@code KO_TO_JA} 등). 서버 데이터는 한국어라 대개 {@code KO_TO_*} 다
 * @param texts 옮길 문장. 받은 순서 그대로 결과가 온다
 */
public record TranslateBatchRequestDto(String direction, List<String> texts) {
}
