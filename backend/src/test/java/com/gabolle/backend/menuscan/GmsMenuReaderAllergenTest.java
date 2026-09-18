package com.gabolle.backend.menuscan;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.gabolle.backend.menuscan.adapter.GmsMenuReader;

/**
 * 사진에 없는 알레르기 재료를 지어내던 것 — S15P21E201-1332.
 *
 * <p>🔴 2026-09-19 실측. 재료 낱말이 <b>한 글자도 없는</b> 메뉴판(음식 이름과 가격뿐)을
 * 운영에 올렸더니 이렇게 나왔다.
 *
 * <pre>
 *   제육볶음    11,000 원   allergens=[돼지고기]
 *   불고기정식  14,000 원   allergens=[돼지고기]   &lt;-- 불고기는 소고기다
 * </pre>
 *
 * 프롬프트 규칙 9가 「그 음식에 무엇이 들어가는지 짐작해서 적지 않는다」라고 이미 막고
 * 있는데도 샜고, 같은 사진인데 실행마다 붙는 낱말이 달랐다. 그래서 프롬프트에 맡기지 않고
 * <b>서버가 자른다</b> — 그 줄에서 읽어 낸 글자에 없는 낱말은 버린다.
 *
 * <p>판별은 {@code GmsMenuReader.appearsIn} 이 한다. 바깥을 안 부르는 순수 함수라
 * 리플렉션으로 직접 부른다 — 이 규칙 하나만 재는 것이 목적이라 모델을 띄우지 않는다.
 */
class GmsMenuReaderAllergenTest {

	private static boolean appearsIn(String text, String word) {
		try {
			Method method = GmsMenuReader.class.getDeclaredMethod("appearsIn", String.class, String.class);
			method.setAccessible(true);
			return (boolean) method.invoke(null, text, word);
		}
		catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("appearsIn 을 못 찾았다 — 이름이 바뀌었으면 이 시험도 함께 고친다", exception);
		}
	}

	@ParameterizedTest
	@CsvSource({
			"제육볶음 11000원, 돼지고기",
			"불고기정식 14000원, 돼지고기",
			"갈비탕 13000원, 소고기",
			"돌솥비빔밥 10500원, 계란",
			"모둠회 45000원, 새우",
	})
	@DisplayName("🔴 사진에 그 글자가 없으면 버린다 — 음식 이름에서 재료를 짐작하지 않는다")
	void dropsWordsThatAreNotInThePhoto(String text, String word) {
		assertThat(appearsIn(text, word)).isFalse();
	}

	@ParameterizedTest
	@CsvSource({
			"돼지고기 김치찜 12000원, 돼지고기",
			"새우튀김 15000원, 새우",
			"'계란말이 8000원', 계란",
			"'우유 들어감 · 티라미수 7000원', 우유",
	})
	@DisplayName("사진에 적혀 있으면 그대로 남긴다 — 걸러내기가 너무 세지 않다")
	void keepsWordsThatAreInThePhoto(String text, String word) {
		assertThat(appearsIn(text, word)).isTrue();
	}

	@Test
	@DisplayName("띄어쓰기가 달라도 사진에 있으면 남긴다 — 모델이 「돼지 고기」로 적어도")
	void ignoresWhitespaceDifferences() {
		assertThat(appearsIn("돼지고기 두루치기 14000원", "돼지 고기")).isTrue();
		assertThat(appearsIn("돼지 고기 두루치기", "돼지고기")).isTrue();
	}

	@Test
	@DisplayName("빈 값은 남기지 않는다")
	void dropsEmpties() {
		assertThat(appearsIn("", "돼지고기")).isFalse();
		assertThat(appearsIn("   ", "돼지고기")).isFalse();
		assertThat(appearsIn(null, "돼지고기")).isFalse();
		assertThat(appearsIn("돼지고기 김치찜", "   ")).isFalse();
	}

	@Test
	@DisplayName("🔴 이 시험이 실제로 무언가를 가르고 있다 — 전부 참도, 전부 거짓도 아니다")
	void theRuleActuallySeparates() {
		List<Boolean> verdicts = List.of(
				appearsIn("제육볶음 11000원", "돼지고기"),
				appearsIn("돼지고기 김치찜 12000원", "돼지고기"));
		assertThat(verdicts).containsExactly(false, true);
	}
}
