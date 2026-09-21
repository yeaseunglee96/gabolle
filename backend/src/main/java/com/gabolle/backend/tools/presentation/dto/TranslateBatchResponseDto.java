package com.gabolle.backend.tools.presentation.dto;

import java.util.List;

import com.gabolle.backend.tools.application.TranslationBatchItem;

/**
 * {@code POST /api/v1/tools/translate/batch} 응답 본문. {@code items[i]} 가 요청의 {@code texts[i]} 다.
 *
 * <p>{@code status} 가 {@code TRANSLATED} 가 아니면 {@code translatedText} 는 {@code null} 이다. 그때 화면은
 * 원문을 그대로 보여 주고, {@code SKIPPED} 는 나중에 다시 부른다.
 */
public record TranslateBatchResponseDto(List<Item> items) {

	public record Item(String translatedText, boolean cached, String status) {
	}

	public static TranslateBatchResponseDto from(List<TranslationBatchItem> items) {
		return new TranslateBatchResponseDto(items.stream()
				.map((item) -> new Item(item.translatedText(), item.cached(), item.status().name()))
				.toList());
	}
}
