package com.gabolle.backend.tools.domain;

/**
 * 번역 요청 하나 — S15P21E201-343.
 *
 * <p>🔴 길이 검사를 <b>여기서</b> 한다. 컨트롤러에만 두면 이 서비스를 직접 부르는 다른
 * 자리(있다면)가 검사를 지나치게 된다 — {@code RouteQuery} 가 같은 이유로 자기 생성자에서
 * 좌표를 검사하는 것과 같은 판단이다.
 */
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

	/** 캐시 열쇠 — 원문 자체가 아니라 해시다({@link TranslationHash} javadoc 참고). */
	public String sourceHash() {
		return TranslationHash.of(this.sourceText, this.direction);
	}
}
