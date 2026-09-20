package com.gabolle.backend.tools.domain;

/** 번역 요청 하나. 길이 검사를 컨트롤러가 아니라 여기서 한다 — 서비스를 직접 부르는 자리도 걸리게. */
public record TranslationRequest(String sourceText, TranslationDirection direction) {

	/** 잰 값이 아니라 고른 값이다 — 문장 하나를 번역하는 자리이지 문서를 번역하는 자리가 아니다. */
	private static final int MAX_SOURCE_LENGTH = 2000;

	public TranslationRequest {
		if (sourceText == null || sourceText.isBlank()) {
			throw new IllegalArgumentException("sourceText 가 비어 있습니다.");
		}
		if (sourceText.length() > MAX_SOURCE_LENGTH) {
			throw new IllegalArgumentException(
					"sourceText 가 너무 깁니다: " + sourceText.length() + "자 (최대 " + MAX_SOURCE_LENGTH + "자)");
		}
		if (direction == null) {
			throw new IllegalArgumentException("direction 이 필요합니다.");
		}
	}

	/** 캐시 열쇠 — 원문이 아니라 해시다. */
	public String sourceHash() {
		return TranslationHash.of(this.sourceText, this.direction);
	}
}
