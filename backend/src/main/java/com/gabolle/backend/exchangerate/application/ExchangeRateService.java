package com.gabolle.backend.exchangerate.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.exchangerate.domain.ExchangeRate;
import com.gabolle.backend.exchangerate.domain.ExchangeRatesResult;

import tools.jackson.databind.ObjectMapper;

/**
 * 오늘(KST 기준) 환율을 조회해 답한다 — S15P21E201-1079.
 *
 * <p>🔴 <b>은행 환율은 영업일에 한 번만 갱신되고, 벤더는 하루 호출 1000회 제한이 있다.</b>
 * 그래서 KST 날짜가 같으면 메모리 캐시를 그대로 쓴다 — {@code WeatherService}는 DB에 캐시하지만,
 * 여기서는 인스턴스가 재시작되면 캐시가 비어도 문제없다(그날 첫 요청 하나만 벤더를 다시
 * 부르고 끝이라, 날씨처럼 격자·회차 조합이 여러 개라 DB로 공유해야 할 이유가 없다).
 *
 * <p>🔴 <b>벤더 호출이 실패하면 {@link ExchangeRateVendorException}이 그대로 위로 올라간다.</b>
 * 여기서 잡아 지어낸 값으로 대신 답하지 않는다.
 */
@Service
@Profile({ "db", "dev" })
public class ExchangeRateService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final ExchangeRateVendorPort vendor;

	private final Clock clock;

	private final ObjectMapper objectMapper;

	private final AtomicReference<ExchangeRatesResult> cache = new AtomicReference<>();

	public ExchangeRateService(ExchangeRateVendorPort vendor, Clock clock, ObjectMapper objectMapper) {
		this.vendor = vendor;
		this.clock = clock;
		this.objectMapper = objectMapper;
	}

	public ExchangeRatesResult getLatestRates() {
		LocalDate today = ZonedDateTime.now(this.clock).withZoneSameInstant(KST).toLocalDate();

		ExchangeRatesResult cached = this.cache.get();
		if (cached != null && cached.asOf().equals(today)) {
			return cached;
		}

		// 🔴 실패하면 여기서 던진 ExchangeRateVendorException 이 그대로 위로 올라간다 — 캐시를
		// 덮어쓰지 않는다(옛 캐시가 있다면 다음 요청이 다시 시도할 수 있게 그대로 둔다).
		String rawJson = this.vendor.fetchRatesJson(today);
		List<ExchangeRate> rates = KoreaeximExchangeRateJsonParser.parse(rawJson, this.objectMapper);

		ExchangeRatesResult result = new ExchangeRatesResult(rates, today);
		this.cache.set(result);
		return result;
	}
}
