package com.gabolle.backend.exchangerate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

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

	// ── 주말·공휴일 되감기 (S15P21E201-1300) ────────────────────────────────

	@Test
	@DisplayName("🔴 주말이라 오늘 값이 없으면 직전 영업일 값을 준다 — 예전에는 502 로 죽었다")
	void fallsBackToLastBusinessDay() {
		// 2026-09-19 는 토요일. 은행은 금·토·일 중 금요일 것만 낸다.
		WeekendVendor vendor = new WeekendVendor(LocalDate.of(2026, 9, 18));
		Clock clock = Clock.fixed(Instant.parse("2026-09-18T15:55:00Z"), ZoneOffset.UTC); // KST 9/19 00:55 토
		ExchangeRateService service = new ExchangeRateService(vendor, clock, new ObjectMapper());

		ExchangeRatesResult result = service.getLatestRates();

		assertThat(result.asOf()).isEqualTo(LocalDate.of(2026, 9, 18));
		assertThat(result.rates()).isNotEmpty();
		assertThat(vendor.asked).containsExactly(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 18));
	}

	@Test
	@DisplayName("🔴 되감아 받은 값도 그날 안에는 캐시한다 — 토요일 내내 벤더를 다시 부르지 않는다")
	void cachesTheResultOfALookback() {
		WeekendVendor vendor = new WeekendVendor(LocalDate.of(2026, 9, 18));
		Clock clock = Clock.fixed(Instant.parse("2026-09-18T15:55:00Z"), ZoneOffset.UTC); // KST 토요일
		ExchangeRateService service = new ExchangeRateService(vendor, clock, new ObjectMapper());

		service.getLatestRates();
		service.getLatestRates();
		service.getLatestRates();

		// 첫 요청의 두 번(토·금)뿐이어야 한다. 캐시 열쇠가 asOf 면 매번 다시 불러 여섯 번이 된다.
		assertThat(vendor.asked).hasSize(2);
	}

	@Test
	@DisplayName("🔴 인증키 오류는 되감지 않는다 — 실패 한 번이 벤더를 일곱 번 더 부르면 안 된다")
	void doesNotLookBackOnAuthFailure() {
		FailingVendor vendor = new FailingVendor();
		Clock clock = Clock.fixed(Instant.parse("2026-09-18T15:55:00Z"), ZoneOffset.UTC);
		ExchangeRateService service = new ExchangeRateService(vendor, clock, new ObjectMapper());

		assertThatThrownBy(service::getLatestRates).isInstanceOf(ExchangeRateVendorException.class);

		assertThat(vendor.callCount).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 이레를 되감아도 없으면 그때는 실패로 알린다 — 조용히 빈 값을 주지 않는다")
	void givesUpAfterTheLookbackWindow() {
		WeekendVendor vendor = new WeekendVendor(LocalDate.of(1999, 1, 1)); // 무엇을 물어도 없다
		Clock clock = Clock.fixed(Instant.parse("2026-09-18T15:55:00Z"), ZoneOffset.UTC);
		ExchangeRateService service = new ExchangeRateService(vendor, clock, new ObjectMapper());

		assertThatThrownBy(service::getLatestRates)
			.isInstanceOf(ExchangeRateVendorException.class)
			.hasMessageContaining("영업일이 아닐 수 있습니다");

		assertThat(vendor.asked).hasSize(ExchangeRateService.MAX_LOOKBACK_DAYS);
	}

	/** 정해 둔 하루만 값을 내주고, 나머지 날은 은행처럼 빈 배열을 준다. */
	private static final class WeekendVendor implements ExchangeRateVendorPort {

		private final LocalDate publishedOn;

		final List<LocalDate> asked = new ArrayList<>();

		WeekendVendor(LocalDate publishedOn) {
			this.publishedOn = publishedOn;
		}

		@Override
		public String fetchRatesJson(LocalDate searchDate) {
			this.asked.add(searchDate);
			return searchDate.equals(this.publishedOn) ? SAMPLE_JSON : "[]";
		}

		@Override
		public String providerName() {
			return "TEST_VENDOR";
		}
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

		int callCount = 0;

		@Override
		public String fetchRatesJson(LocalDate searchDate) {
			this.callCount++;
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
