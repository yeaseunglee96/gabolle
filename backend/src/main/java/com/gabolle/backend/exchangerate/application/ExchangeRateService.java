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
 * 가장 최근 영업일 환율을 조회해 답한다.
 * 은행 환율은 영업일에 한 번만 갱신되고 벤더는 하루 1000회 호출 제한이 있어, 같은 KST 날짜에
 * 다시 물으면 메모리 캐시를 그대로 쓴다. 재시작으로 캐시가 비어도 그날 첫 요청 하나만 벤더를
 * 다시 부른다.
 * 오늘 값이 없으면 하루씩 뒤로 되감는다 — 주말·공휴일, 그리고 평일이라도 KST 자정부터 은행이
 * 그날 값을 낼 때까지는 오늘 값이 없다.
 * 되감기는 「그날 값이 없다」({@code EXCHANGE_RATE_NO_DATA_FOR_DATE})일 때만 한다. 인증키
 * 오류나 일일 한도 초과까지 되감으면 실패한 요청 하나가 벤더를 {@link #MAX_LOOKBACK_DAYS} 번
 * 더 부른다.
 * 그 밖의 이유로 벤더 호출이 실패하면 {@link ExchangeRateVendorException} 이 그대로 위로
 * 올라간다 — 여기서 잡아 지어낸 값으로 대신 답하지 않는다.
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
	 * @param askedOn 이 값을 받아 온 KST 날짜. 캐시 열쇠는 물어본 날이지 {@code asOf} 가 아니다 —
	 *     토요일에 금요일 값을 받아 오면 둘이 다르고, {@code asOf} 로 비교하면 토요일 내내
	 *     요청마다 벤더를 다시 부른다.
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
