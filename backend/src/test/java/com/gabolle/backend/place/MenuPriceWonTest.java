package com.gabolle.backend.place;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.service.MenuPriceWon;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 값 하나를 읽는 규칙만 검사한다 — DB 없이 돈다.
 *
 * <p>여기서 지키는 것은 하나다. <b>읽을 수 없는 것은 전부 {@code null}</b> 이고
 * {@code 0} 이 아니다. 0 은 화면에서 「무료」로 그려져서, 모름을 0 으로 접으면 조사가 안 된
 * 곳이 공짜로 보인다.
 */
class MenuPriceWonTest {

	@Test
	@DisplayName("적재기가 낸 모양에서 원 단위 값을 꺼낸다")
	void readsPriceWon() {
		String value = "{\"priceWon\":39000,\"menu\":\"스텔라마리스 굴 플레이트 39,000원\"}";

		assertThat(MenuPriceWon.wonOf(value)).isEqualTo(39_000);
	}

	@Test
	@DisplayName("menu 가 없어도 값만 있으면 읽는다 — menu 는 사람에게 보여 줄 글일 뿐이다")
	void readsWithoutMenuText() {
		assertThat(MenuPriceWon.wonOf("{\"priceWon\":17000}")).isEqualTo(17_000);
	}

	@Test
	@DisplayName("값이 없거나 비었거나 깨졌으면 null — 던지지 않는다")
	void unreadableIsNull() {
		assertThat(MenuPriceWon.wonOf(null)).isNull();
		assertThat(MenuPriceWon.wonOf("")).isNull();
		assertThat(MenuPriceWon.wonOf("   ")).isNull();
		assertThat(MenuPriceWon.wonOf("{\"priceWon\":")).isNull();
		assertThat(MenuPriceWon.wonOf("{}")).isNull();
	}

	@Test
	@DisplayName("숫자가 아닌 priceWon 은 null — 문자열로 온 값을 몰래 고쳐 읽지 않는다")
	void nonNumberIsNull() {
		assertThat(MenuPriceWon.wonOf("{\"priceWon\":\"39000\"}")).isNull();
		assertThat(MenuPriceWon.wonOf("{\"priceWon\":null}")).isNull();
	}

	@Test
	@DisplayName("🔴 0 과 음수는 null 이다 — 0 을 그대로 내보내면 화면이 「무료」로 그린다")
	void nonPositiveIsNull() {
		assertThat(MenuPriceWon.wonOf("{\"priceWon\":0}")).isNull();
		assertThat(MenuPriceWon.wonOf("{\"priceWon\":-1}")).isNull();
	}

	@Test
	@DisplayName("운영에 실제로 실린 범위를 읽는다 — 2026-09-22 실측 2,050원~182,000원")
	void readsProductionRange() {
		assertThat(MenuPriceWon.wonOf("{\"priceWon\":2050}")).isEqualTo(2_050);
		assertThat(MenuPriceWon.wonOf("{\"priceWon\":182000}")).isEqualTo(182_000);
	}

	// ── 1인분이 아닌 값은 모름 (S15P21E201-1615) ─────────────────────────────
	// 아래 문구는 전부 운영에 실린 그대로다(2026-09-25). 지어낸 예로 검사하면 수집 문구가 실제로
	// 어떻게 섞여 오는지를 놓친다.

	private static String value(int won, String menu) {
		return "{\"priceWon\":" + won + ",\"menu\":\"" + menu + "\"}";
	}

	@Test
	@DisplayName("🔴 2인·N인분·1~2인 값은 모름이다 — 인원수를 곱하는 예산이 부풀려진다")
	void multiPersonPricesAreUnknown() {
		assertThat(MenuPriceWon.wonOf(value(56000,
				"숙성 모듬 사시미 (2인) 56,000원 / 1인 혼술 사시미 30,000원 / 고등어 봉초밥 30,000원 / 오리 가슴살 스테이크 25,000원")))
			.isNull();
		assertThat(MenuPriceWon.wonOf(value(31000, "2인 B세트(월남쌈 M + 차돌 쌀국수) 기준 약 31,000원"))).isNull();
		assertThat(MenuPriceWon.wonOf(value(39800, "참치회 뱃살 모듬 (1~2인)"))).isNull();
	}

	@Test
	@DisplayName("🔴 세트 값은 모름이다 — 단 「1인」이 적힌 세트는 1인분이다")
	void setPricesAreUnknownUnlessMarkedForOne() {
		assertThat(MenuPriceWon.wonOf(value(9000, "메밀세트, 우동세트"))).isNull();
		assertThat(MenuPriceWon.wonOf(value(14900, "클래식 치즈버거 세트 (기본)"))).isNull();

		assertThat(MenuPriceWon.wonOf(value(18000, "1인세트 탕수육+잡채밥"))).isEqualTo(18_000);
		assertThat(MenuPriceWon.wonOf(value(25000, "모둠회 세트 (1인)"))).isEqualTo(25_000);
	}

	/**
	 * 한 줄에 여러 메뉴가 섞여 온다. 「2인」이 어딘가 있다고 통째로 버리면 멀쩡한 1인분 값이 사라진다 —
	 * 그 값이 적힌 토막만 본다.
	 */
	@Test
	@DisplayName("🔴 같은 줄의 다른 메뉴가 2인·세트여도, 이 값이 적힌 메뉴가 1인분이면 읽는다")
	void onlyThePieceCarryingThePriceIsJudged() {
		assertThat(MenuPriceWon.wonOf(value(14000, "특미초밥 14,000원, 특선초밥 19,000원, 참치 모듬 2인 60,000원")))
			.isEqualTo(14_000);
		assertThat(MenuPriceWon.wonOf(value(40000, "염소(2인분) 70,000원, 오리 40,000원, 닭 40,000원, 옻닭 50,000원")))
			.isEqualTo(40_000);
		assertThat(MenuPriceWon.wonOf(value(12000, "나가하마 라멘 12,000원 / 나가하마 라멘 교자 세트 19,000원")))
			.isEqualTo(12_000);
	}

	@Test
	@DisplayName("값이 문구에 없으면 모든 메뉴가 1인분이 아닐 때만 모름이다 — 추측으로 멀쩡한 값을 버리지 않는다")
	void withoutThePriceInTheTextOnlyAllNonSinglePiecesMakeItUnknown() {
		assertThat(MenuPriceWon.wonOf(value(14000, "단품 파스타/피자 1만 원대 초중반, 세트 메뉴 약 59,000원~65,000원 선")))
			.isEqualTo(14_000);
		assertThat(MenuPriceWon.wonOf(value(80000, "오마카세 코스 1인"))).isEqualTo(80_000);
	}
}
