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
}
