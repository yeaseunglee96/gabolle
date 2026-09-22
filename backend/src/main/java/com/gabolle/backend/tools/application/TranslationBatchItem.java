package com.gabolle.backend.tools.application;

/**
 * 일괄 번역에서 문장 하나의 결과. 받은 순서 그대로 돌려준다.
 *
 * <p>🔴 실패를 번역문으로 위장하지 않는다. 못 옮긴 문장은 {@code translatedText} 가 {@code null} 이고
 * {@code status} 가 이유를 말한다 — 원문을 번역문 자리에 넣어 주면 화면은 «이미 번역됐다» 로 읽고 다시 부르지 않는다.
 *
 * @param cached 참이면 업체를 부르지 않고 저장해 둔 번역을 꺼냈다
 */
public record TranslationBatchItem(String translatedText, boolean cached, Status status) {

	public enum Status {
		/** 옮겼다(캐시든 새로 불렀든). */
		TRANSLATED,
		/** 업체가 실패했다. 같은 요청 안의 나머지 미번역 문장도 부르지 않고 이것이 된다 — 업체 장애는 대개 한꺼번에 온다. */
		FAILED,
		/** 시간 예산을 넘겨 부르지 않았다. 실패가 아니다 — 다시 부르면 된다. */
		SKIPPED
	}

	static TranslationBatchItem translated(String text, boolean cached) {
		return new TranslationBatchItem(text, cached, Status.TRANSLATED);
	}

	static TranslationBatchItem failed() {
		return new TranslationBatchItem(null, false, Status.FAILED);
	}

	static TranslationBatchItem skipped() {
		return new TranslationBatchItem(null, false, Status.SKIPPED);
	}
}
