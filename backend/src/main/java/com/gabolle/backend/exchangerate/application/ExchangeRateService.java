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
 * 가장 최근 영업일 환율을 조회해 답한다 — S15P21E201-1079.
 *
 * <p>🔴 <b>은행 환율은 영업일에 한 번만 갱신되고, 벤더는 하루 호출 1000회 제한이 있다.</b>
 * 그래서 같은 KST 날짜에 다시 물으면 메모리 캐시를 그대로 쓴다 — {@code WeatherService}는 DB에
 * 캐시하지만, 여기서는 인스턴스가 재시작되면 캐시가 비어도 문제없다(그날 첫 요청 하나만 벤더를
 * 다시 부르고 끝이라, 날씨처럼 격자·회차 조합이 여러 개라 DB로 공유해야 할 이유가 없다).
 *
 * <p>🔴 <b>오늘 값이 없으면 하루씩 뒤로 되감는다</b> — S15P21E201-1300. 예전에는 오늘 하루만
 * 묻고 없으면 그대로 실패했다. 그래서 <b>토요일·일요일·공휴일, 그리고 평일이라도 KST 자정부터
 * 은행이 그날 값을 낼 때까지</b> 환율 기능이 통째로 죽었다. 2026-09-19 토요일 운영에서
 * {@code 502 EXCHANGE_RATE_VENDOR_ERROR "그 날짜의 환율 정보가 없습니다"} 로 실측했다.
 *
 * <p>되감기는 <b>「그날 값이 없다」({@code EXCHANGE_RATE_NO_DATA_FOR_DATE})일 때만</b> 한다.
 * 인증키 오류나 일일 한도 초과까지 되감으면 실패한 요청 하나가 벤더를 {@link #MAX_LOOKBACK_DAYS}
 * 번 더 부른다.
 *
 * <p>🔴 <b>벤더 호출이 그 밖의 이유로 실패하면 {@link ExchangeRateVendorException}이 그대로 위로
 * 올라간다.</b> 여기서 잡아 지어낸 값으로 대신 답하지 않는다.
 */
@Service
@Profile({ "db", "dev" })
public class ExchangeRateService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/**
	 * 며칠까지 되감아 보나. 설·추석 연휴가 주말에 붙으면 은행이 닷새까지 쉰다. 이레면 그런 해에도
	 * 직전 영업일에 닿는다. 그보다 길게 잡으면 벤더가 정말 고장 났을 때 헛호출만 늘어난다.
	 */
	static final int MAX_LOOKBACK_DAYS = 7;

	private final ExchangeRateVendorPort vendor;

	private final Clock clock;

	private final ObjectMapper objectMapper;

	private final AtomicReference<CachedRates> cache = new AtomicReference<>();

	/**
	 * @param askedOn 이 값을 받아 온 KST 날짜. 캐시 열쇠는 <b>물어본 날</b>이지 {@code asOf}가
	 *     아니다 — 토요일에 금요일 값을 받아 오면 둘이 다르고, {@code asOf}로 비교하면 토요일
	 *     내내 요청마다 벤더를 다시 부른다.
	 */
	private record CachedRates(LocalDate askedOn, ExchangeRatesResult result) {
	}

	public ExchangeRateService(ExchangeRateVendorPort vendor, Clock clock, ObjectMapper objectMapper) {
		this.vendor = vendor;
		this.clock = clock;
		this.objectMapper = objectMapper;
	}

	public ExchangeRatesResult getLatestRates() {
		LocalDate today = ZonedDateTime.now(this.clock).withZoneSameInstant(KST).toLocalDate();

		CachedRates cached = this.cache.get();
		if (cached != null && cached.askedOn().equals(today)) {
			return cached.result();
		}

		ExchangeRateVendorException lastNoData = null;
		for (int back = 0; back < MAX_LOOKBACK_DAYS; back++) {
			LocalDate searchDate = today.minusDays(back);
			try {
				String rawJson = this.vendor.fetchRatesJson(searchDate);
				List<ExchangeRate> rates = KoreaeximExchangeRateJsonParser.parse(rawJson, this.objectMapper);
				ExchangeRatesResult result = new ExchangeRatesResult(rates, searchDate);
				this.cache.set(new CachedRates(today, result));
				return result;
			}
			catch (ExchangeRateVendorException exception) {
				// 그날 값이 없는 것만 하루 뒤로 물러서 다시 묻는다. 나머지(인증·한도·해석 실패)는
				// 되감아도 같은 답이 오므로 즉시 위로 올린다 — 캐시는 건드리지 않는다.
				if (!KoreaeximExchangeRateJsonParser.NO_DATA_FOR_DATE.equals(exception.getCode())) {
					throw exception;
				}
				lastNoData = exception;
			}
		}

		// 이레를 되감아도 없다 = 주말이 아니라 벤더 쪽이 이상하다. 마지막 실패를 그대로 올린다.
		throw lastNoData;
	}
}
