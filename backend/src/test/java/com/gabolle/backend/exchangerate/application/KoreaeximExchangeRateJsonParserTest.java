package com.gabolle.backend.exchangerate.application;

// 패키지 전용(package-private) 파서와 같은 패키지에 둔다.

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.exchangerate.domain.ExchangeRate;

import tools.jackson.databind.ObjectMapper;

class KoreaeximExchangeRateJsonParserTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("통화가 여럿이면 전부 옮기고, 천 단위 쉼표는 벗겨서 숫자로 바꾼다")
	void parsesMultipleCurrenciesAndStripsCommas() {
		String json = """
				[
				{"result":1,"cur_unit":"USD","cur_nm":"미국 달러","deal_bas_r":"1,378.60","ttb":"1,364.81","tts":"1,392.39"},
				{"result":1,"cur_unit":"JPY(100)","cur_nm":"일본 옌","deal_bas_r":"935.15","ttb":"926.02","tts":"944.28"}
				]""";

		List<ExchangeRate> rates = KoreaeximExchangeRateJsonParser.parse(json, this.objectMapper);

		assertThat(rates).containsExactly(
				new ExchangeRate("USD", "미국 달러", 1378.60, 1364.81, 1392.39),
				new ExchangeRate("JPY(100)", "일본 옌", 935.15, 926.02, 944.28));
	}

	@Test
	@DisplayName("🔴 result 가 1 이 아니면(인증키 오류 등) 실패다")
	void nonSuccessResultCodeIsAFailure() {
		String json = """
				[{"result":3,"cur_unit":null,"deal_bas_r":null}]""";

		assertThatThrownBy(() -> KoreaeximExchangeRateJsonParser.parse(json, this.objectMapper))
				.isInstanceOf(ExchangeRateVendorException.class)
				.satisfies(exception -> assertThat(((ExchangeRateVendorException) exception).getCode())
						.isEqualTo("EXCHANGE_RATE_VENDOR_ERROR"));
	}

	@Test
	@DisplayName("🔴 응답이 빈 배열이면 「그날 값이 없다」로 알린다 — 다른 실패와 코드를 가른다")
	void emptyArrayIsANoDataFailure() {
		// 이 코드일 때만 ExchangeRateService 가 하루씩 뒤로 되감는다. 인증키 오류·한도 초과와
		// 같은 코드를 쓰면 그것까지 되감아 벤더를 헛되이 여러 번 부른다.
		assertThatThrownBy(() -> KoreaeximExchangeRateJsonParser.parse("[]", this.objectMapper))
				.isInstanceOf(ExchangeRateVendorException.class)
				.satisfies(exception -> assertThat(((ExchangeRateVendorException) exception).getCode())
						.isEqualTo(KoreaeximExchangeRateJsonParser.NO_DATA_FOR_DATE));
	}

	@Test
	@DisplayName("🔴 그 밖의 실패는 되감을 수 없는 코드로 남는다 — 되감기 대상과 섞이지 않는다")
	void otherFailuresKeepADifferentCode() {
		String json = """
				[{"result":3,"cur_unit":null,"deal_bas_r":null}]""";

		assertThatThrownBy(() -> KoreaeximExchangeRateJsonParser.parse(json, this.objectMapper))
				.isInstanceOf(ExchangeRateVendorException.class)
				.satisfies(exception -> assertThat(((ExchangeRateVendorException) exception).getCode())
						.isNotEqualTo(KoreaeximExchangeRateJsonParser.NO_DATA_FOR_DATE));
	}
}
