package com.gabolle.backend.tools.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.gabolle.backend.tools.domain.TranslationDirection.Language;

/**
 * 번역 방향의 이름과, 그 방향이 들고 있는 언어가 서로 맞는가 — S15P21E201-1363.
 *
 * <p>🔴 이름({@code JA_TO_KO})은 앱이 보내는 값이고, 언어({@code JAPANESE → KOREAN})는 모델에게 주는 지시다.
 * 둘이 어긋나면 앱은 일본어를 보냈다고 믿는데 모델은 다른 언어로 지시받는다. 모델은 그래도 대개 그럴듯한
 * 한국어를 내놓아서 <b>아무도 모른다</b>. 그래서 이름을 쪼개 언어와 직접 대조한다.
 */
class TranslationDirectionTest {

	/** 이름의 언어 조각 → 언어. 새 언어를 더하면 여기에도 한 줄 더한다 — 안 더하면 아래 시험이 빨개진다. */
	private static final Map<String, Language> CODE = Map.of(
			"KO", Language.KOREAN,
			"EN", Language.ENGLISH,
			"JA", Language.JAPANESE,
			"ZH_HANS", Language.CHINESE_SIMPLIFIED,
			"ZH_HANT", Language.CHINESE_TRADITIONAL);

	@ParameterizedTest
	@EnumSource(TranslationDirection.class)
	@DisplayName("🔴 이름의 앞 조각이 원문 언어, 뒤 조각이 대상 언어다")
	void nameMatchesItsLanguages(TranslationDirection direction) {
		String[] parts = direction.name().split("_TO_");

		assertThat(parts).hasSize(2);
		assertThat(CODE.get(parts[0])).as("원문 언어 — %s", direction).isEqualTo(direction.source());
		assertThat(CODE.get(parts[1])).as("대상 언어 — %s", direction).isEqualTo(direction.target());
	}

	@ParameterizedTest
	@EnumSource(TranslationDirection.class)
	@DisplayName("원문과 대상이 같은 방향은 없다")
	void neverTranslatesIntoTheSameLanguage(TranslationDirection direction) {
		assertThat(direction.source()).isNotEqualTo(direction.target());
	}

	@Test
	@DisplayName("🔴 앱이 보내는 다섯 이름이 다 있다 — frontend/src/field/translate.ts 의 TranslationDirection")
	void coversEveryDirectionTheAppSends() {
		for (String name : new String[] { "EN_TO_KO", "KO_TO_EN", "JA_TO_KO", "ZH_HANS_TO_KO", "ZH_HANT_TO_KO" }) {
			assertThat(TranslationDirection.valueOf(name)).isNotNull();
		}
	}

	@Test
	@DisplayName("간체와 번체는 지시문에서 다른 이름이다 — 「중국어」 하나로 쓰면 모델이 대개 간체로 답한다")
	void simplifiedAndTraditionalAreNamedApart() {
		assertThat(Language.CHINESE_SIMPLIFIED.promptName()).isNotEqualTo(Language.CHINESE_TRADITIONAL.promptName());
		assertThat(Language.CHINESE_TRADITIONAL.promptName()).contains("번체");
	}
}
