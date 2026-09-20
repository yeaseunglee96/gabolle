package com.gabolle.backend.place;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.domain.FeaturePresence;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FeaturePresence} 진리표 — evidenceStatus 값 × value 네 모양({@code null} ·
 * {@code "true"} · {@code "false"} · 공백)의 조합을 전부 못 박는다. DB 없이 돈다.
 */
class FeaturePresenceTest {

	// ── indicatesPresence — "있다고 확인됨". UNKNOWN 이면 false. ──────────────

	@Test
	@DisplayName("VERIFIED + 값 없음 → 있다 (확인했고 해당 없음이 아니다)")
	void verifiedWithoutValueIndicatesPresence() {
		assertThat(FeaturePresence.indicatesPresence("VERIFIED", null)).isTrue();
	}

	@Test
	@DisplayName("VERIFIED + true → 있다")
	void verifiedTrueIndicatesPresence() {
		assertThat(FeaturePresence.indicatesPresence("VERIFIED", "true")).isTrue();
	}

	@Test
	@DisplayName("🔴 VERIFIED + false → 확인된 해당 없음이라 있다가 아니다")
	void verifiedFalseDoesNotIndicatePresence() {
		assertThat(FeaturePresence.indicatesPresence("VERIFIED", "false")).isFalse();
	}

	@Test
	@DisplayName("VERIFIED + 공백 값 → trim 후 false 가 아니므로 있다")
	void verifiedBlankValueIndicatesPresence() {
		assertThat(FeaturePresence.indicatesPresence("VERIFIED", "  ")).isTrue();
	}

	@Test
	@DisplayName("ESTIMATED + 값 없음 → 있다")
	void estimatedWithoutValueIndicatesPresence() {
		assertThat(FeaturePresence.indicatesPresence("ESTIMATED", null)).isTrue();
	}

	@Test
	@DisplayName("ESTIMATED + true → 있다")
	void estimatedTrueIndicatesPresence() {
		assertThat(FeaturePresence.indicatesPresence("ESTIMATED", "true")).isTrue();
	}

	@Test
	@DisplayName("ESTIMATED + false → 있다가 아니다")
	void estimatedFalseDoesNotIndicatePresence() {
		assertThat(FeaturePresence.indicatesPresence("ESTIMATED", "false")).isFalse();
	}

	@Test
	@DisplayName("🔴 UNKNOWN 은 값이 무엇이든 있다가 아니다 — 모른다를 있다로 뭉개지 않는다")
	void unknownNeverIndicatesPresence() {
		assertThat(FeaturePresence.indicatesPresence("UNKNOWN", null)).isFalse();
		assertThat(FeaturePresence.indicatesPresence("UNKNOWN", "true")).isFalse();
		assertThat(FeaturePresence.indicatesPresence("UNKNOWN", "false")).isFalse();
		assertThat(FeaturePresence.indicatesPresence("UNKNOWN", "")).isFalse();
	}

	// ── cannotRuleOutPresence — "없다고 확인되지 않음". UNKNOWN 이면 true. ────

	@Test
	@DisplayName("🔴 UNKNOWN 은 없다고 단정할 수 없다 — 모르는 것을 있는 것으로 취급한다")
	void unknownCannotRuleOutPresence() {
		assertThat(FeaturePresence.cannotRuleOutPresence("UNKNOWN", null)).isTrue();
	}

	@Test
	@DisplayName("VERIFIED + 값 없음 → 없다고 단정할 수 없다")
	void verifiedWithoutValueCannotRuleOutPresence() {
		assertThat(FeaturePresence.cannotRuleOutPresence("VERIFIED", null)).isTrue();
	}

	@Test
	@DisplayName("VERIFIED + true → 없다고 단정할 수 없다")
	void verifiedTrueCannotRuleOutPresence() {
		assertThat(FeaturePresence.cannotRuleOutPresence("VERIFIED", "true")).isTrue();
	}

	@Test
	@DisplayName("🔴 VERIFIED + false → 확인된 해당 없음이라 단정할 수 있다(=false)")
	void verifiedFalseRulesOutPresence() {
		assertThat(FeaturePresence.cannotRuleOutPresence("VERIFIED", "false")).isFalse();
	}

	@Test
	@DisplayName("VERIFIED + 공백 값 → false 리터럴이 아니므로 단정할 수 없다")
	void verifiedBlankValueCannotRuleOutPresence() {
		assertThat(FeaturePresence.cannotRuleOutPresence("VERIFIED", "  ")).isTrue();
	}

	@Test
	@DisplayName("ESTIMATED + 값 없음 → 단정할 수 없다")
	void estimatedWithoutValueCannotRuleOutPresence() {
		assertThat(FeaturePresence.cannotRuleOutPresence("ESTIMATED", null)).isTrue();
	}

	@Test
	@DisplayName("ESTIMATED + true → 단정할 수 없다")
	void estimatedTrueCannotRuleOutPresence() {
		assertThat(FeaturePresence.cannotRuleOutPresence("ESTIMATED", "true")).isTrue();
	}

	@Test
	@DisplayName("ESTIMATED + false → 확인된 해당 없음이라 단정할 수 있다")
	void estimatedFalseRulesOutPresence() {
		assertThat(FeaturePresence.cannotRuleOutPresence("ESTIMATED", "false")).isFalse();
	}

	@Test
	@DisplayName("UNKNOWN 은 값이 무엇이든(정상적으로는 항상 null) 단정할 수 없다")
	void unknownAlwaysCannotRuleOutPresenceRegardlessOfValue() {
		assertThat(FeaturePresence.cannotRuleOutPresence("UNKNOWN", "true")).isTrue();
		assertThat(FeaturePresence.cannotRuleOutPresence("UNKNOWN", "false")).isTrue();
		assertThat(FeaturePresence.cannotRuleOutPresence("UNKNOWN", "")).isTrue();
	}

	@Test
	@DisplayName("🔴 NOT_COLLECTED 는 있다가 아니다 — 수집조차 안 한 것을 확인된 사실로 읽으면 안 된다")
	void notCollectedNeverIndicatesPresence() {
		assertThat(FeaturePresence.indicatesPresence("NOT_COLLECTED", null)).isFalse();
		assertThat(FeaturePresence.indicatesPresence("NOT_COLLECTED", "true")).isFalse();
	}

	@Test
	@DisplayName("🔴 NOT_COLLECTED 는 없다고도 단정할 수 없다 — 값이 false 로 와도 마찬가지다")
	void notCollectedCannotRuleOutPresence() {
		assertThat(FeaturePresence.cannotRuleOutPresence("NOT_COLLECTED", null)).isTrue();
		assertThat(FeaturePresence.cannotRuleOutPresence("NOT_COLLECTED", "false")).isTrue();
	}

	@Test
	@DisplayName("🔴 처음 보는 상태 문자열도 확인된 사실이 아니다 — 부정이 아니라 허용 목록으로 판정한다")
	void unrecognisedStatusIsNeverConfirmed() {
		assertThat(FeaturePresence.indicatesPresence("SOMETHING_NEW", "true")).isFalse();
		assertThat(FeaturePresence.cannotRuleOutPresence("SOMETHING_NEW", "false")).isTrue();
	}

	@Test
	@DisplayName("🔴 두 판정은 서로의 반대가 아니다 — UNKNOWN 에서 갈린다")
	void theTwoPredicatesDivergeExactlyAtUnknown() {
		// 안전 제약(cannotRuleOutPresence)은 모르는 것도 있는 것으로 봐야 하므로 서로의 반대가 아니다.
		assertThat(FeaturePresence.indicatesPresence("UNKNOWN", null)).isFalse();
		assertThat(FeaturePresence.cannotRuleOutPresence("UNKNOWN", null)).isTrue();
	}
}
