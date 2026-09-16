package com.gabolle.backend.exchangerate.domain;

import java.time.LocalDate;
import java.util.List;

/** @param asOf 이 환율이 적용되는 영업일. 은행이 이미 게시한 값이라 사용자에게 그대로 보여준다. */
public record ExchangeRatesResult(List<ExchangeRate> rates, LocalDate asOf) {
}
