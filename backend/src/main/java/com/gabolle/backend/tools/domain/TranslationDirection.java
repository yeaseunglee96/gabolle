package com.gabolle.backend.tools.domain;

/**
 * 번역 방향. 원문 언어와 대상 언어를 함께 들고 있다.
 *
 * <p>🔴 <b>언어 이름을 방향 안에 두는 이유 (S15P21E201-1363).</b> 전에는 방향이 둘뿐이라 번역 지시문을
 * {@code KO_TO_EN 이면 「한국어에서 영어로」, 아니면 「영어에서 한국어로」} 로 골랐다. 그 자리에 방향만
 * 늘리면 {@code JA_TO_KO} 가 「영어에서 한국어로」로 지시되고, 모델은 일본어를 받고도 대개 한국어를
 * 잘 내놓기 때문에 <b>아무 오류 없이</b> 지나간다. 방향이 자기 언어를 알면 그런 «나머지 전부» 분기가
 * 생길 자리가 없다.
 *
 * <p>이름 형식은 {@code 원문_TO_대상}. 앱이 이 이름 그대로 보낸다
 * ({@code frontend/src/field/translate.ts} 의 {@code TranslationDirection}). 캐시 열쇠에도 이름이 들어가므로
 * ({@link TranslationHash}) 이미 있는 이름을 바꾸면 쌓아 둔 번역이 전부 새로 불린다.
 */
public enum TranslationDirection {

	KO_TO_EN(Language.KOREAN, Language.ENGLISH),

	EN_TO_KO(Language.ENGLISH, Language.KOREAN),

	/** 일본인 손님이 일본어로 적은 것을 가게에 한국어로 보여 준다. */
	JA_TO_KO(Language.JAPANESE, Language.KOREAN),

	ZH_HANS_TO_KO(Language.CHINESE_SIMPLIFIED, Language.KOREAN),

	ZH_HANT_TO_KO(Language.CHINESE_TRADITIONAL, Language.KOREAN),

	/** 가게가 한국어로 답한 것을 손님 언어로 돌려준다 — 대화가 양쪽으로 오간다. */
	KO_TO_JA(Language.KOREAN, Language.JAPANESE),

	KO_TO_ZH_HANS(Language.KOREAN, Language.CHINESE_SIMPLIFIED),

	KO_TO_ZH_HANT(Language.KOREAN, Language.CHINESE_TRADITIONAL);

	private final Language source;

	private final Language target;

	TranslationDirection(Language source, Language target) {
		this.source = source;
		this.target = target;
	}

	public Language source() {
		return this.source;
	}

	public Language target() {
		return this.target;
	}

	/**
	 * 번역 지시문에 들어가는 언어 이름.
	 *
	 * <p>중국어는 간체·번체를 이름에서 가른다. 「중국어」라고만 쓰면 모델이 대개 간체로 답해서, 대만·홍콩
	 * 손님에게 간체 글자가 나간다 — 읽을 수는 있어도 «남의 글자»다.
	 */
	public enum Language {
		KOREAN("한국어"),
		ENGLISH("영어"),
		JAPANESE("일본어"),
		CHINESE_SIMPLIFIED("간체 중국어"),
		CHINESE_TRADITIONAL("번체 중국어");

		private final String promptName;

		Language(String promptName) {
			this.promptName = promptName;
		}

		public String promptName() {
			return this.promptName;
		}
	}
}
