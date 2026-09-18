package com.gabolle.backend.exchangerate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.gabolle.backend.exchangerate.domain.ExchangeRatesResult;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link ExchangeRateService} 검증 — S15P21E201-1079.
 *
 * <p>실제 벤더 호출·JSON 파싱은 {@code KoreaeximExchangeRateVendorAdapterTest}·{@code
 * KoreaeximExchangeRateJsonParserTest} 몫이라 여기서는 벤더를 스텁으로 대신해 <b>캐시 동작</b>
 * 만 잰다.
 */
class ExchangeRateServiceTest {

	private static final String SAMPLE_JSON = """
			[{"result":1,"cur_unit":"USD","cur_nm":"미국 달러","deal_bas_r":"1,378.60","ttb":"1,364.81","tts":"1,392.39"}]""";

	@Test
	@DisplayName("같은 날짜(KST) 안에서는 벤더를 다시 부르지 않고 캐시를 쓴다")
	void reusesCacheWithinSameKstDate() {
		CountingVendor vendor = new CountingVendor();
		Clock clock = Clock.fixed(Instant.parse("2026-09-16T02:00:00Z"), ZoneOffset.UTC); // KST 11:00
		ExchangeRateService service = new ExchangeRateService(vendor, clock, new ObjectMapper());

		ExchangeRatesResult first = service.getLatestRates();
		ExchangeRatesResult second = service.getLatestRates();

		assertThat(vendor.callCount).isEqualTo(1);
		assertThat(first.asOf()).isEqualTo(LocalDate.of(2026, 9, 16));
		assertThat(second).isSameAs(first);
	}

	@Test
	@DisplayName("🔴 KST 날짜가 바뀌면 캐시를 버리고 벤더를 다시 부른다")
	void refetchesWhenKstDateChanges() {
		CountingVendor vendor = new CountingVendor();
		MutableClock clock = new MutableClock(Instant.parse("2026-09-16T02:00:00Z")); // KST 9/16 11:00
		ExchangeRateService service = new ExchangeRateService(vendor, clock, new ObjectMapper());

		service.getLatestRates();
		clock.instant = Instant.parse("2026-09-17T02:00:00Z"); // KST 9/17 11:00 — 날짜가 바뀜
		service.getLatestRates();

		assertThat(vendor.callCount).isEqualTo(2);
	}

	@Test
	@DisplayName("🔴 벤더 호출이 실패하면 캐시를 건드리지 않고 그대로 위로 올라간다")
	void vendorFailurePropagatesWithoutCorruptingCache() {
		FailingVendor vendor = new FailingVendor();
		Clock clock = Clock.fixed(Instant.parse("2026-09-16T02:00:00Z"), ZoneOffset.UTC);
		ExchangeRateService service = new ExchangeRateService(vendor, clock, new ObjectMapper());

		assertThatThrownBy(service::getLatestRates).isInstanceOf(ExchangeRateVendorException.class);
	}

	private static final class CountingVendor implements ExchangeRateVendorPort {

		int callCount = 0;

		@Override
		public String fetchRatesJson(LocalDate searchDate) {
			this.callCount++;
			return SAMPLE_JSON;
		}

		@Override
		public String providerName() {
			return "TEST_VENDOR";
		}
	}

	private static final class FailingVendor implements ExchangeRateVendorPort {

		@Override
		public String fetchRatesJson(LocalDate searchDate) {
			throw new ExchangeRateVendorException("EXCHANGE_RATE_VENDOR_UNAVAILABLE", "실패", HttpStatus.BAD_GATEWAY);
		}

		@Override
		public String providerName() {
			return "TEST_VENDOR";
		}
	}

	/** {@link Clock#fixed}는 값을 못 바꾼다 — 테스트 중간에 "날짜가 바뀐 것"을 흉내 내려고 직접 만든다. */
	private static final class MutableClock extends Clock {

		Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Instant instant() {
			return this.instant;
		}
	}
}
