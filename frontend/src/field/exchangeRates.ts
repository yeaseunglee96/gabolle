// 오늘의 환율 — 현장 도구가 쓴다 (S15P21E201-1137).
//
// 🔴 외국인이 부산에서 가장 자주 하는 계산이 "이 가격이 내 돈으로 얼마?" 다. 메뉴판·택시비·
//    숙소 값을 볼 때마다 한다. 이게 없으면 앱을 나가서 다른 앱을 켜야 하고, 나간 사람은
//    잘 안 돌아온다.
//
// 🔴 서버(GET /api/v1/exchange-rates, S15P21E201-1079)가 이미 있는데 프론트가 한 번도
//    안 불렀다. 우리가 만드는 것은 화면뿐이다 — 환율을 어디서 가져올지는 서버가 이미 정했다.
import { apiRequest, ApiClientError } from '@/api/client';
import { isVendorNotReady } from '@/api/vendorReady';

/**
 * 서버가 주는 한 통화의 값.
 *
 * 🔴 `baseRate` 와 `sellingRate` 는 다른 값이고 **둘 다 보여줘야 한다.**
 * 매매기준율만 크게 보여주면 사용자가 환전소에 가서 그 값을 못 받고 "앱이 틀렸다" 고 한다.
 * 기준율로 계산하되 매도율을 같이 적어 차이를 숨기지 않는다.
 */
export type ExchangeRate = {
  currencyCode: string;
  currencyName: string;
  /** 매매기준율 — 흔히 말하는 "환율". 1단위당 원. */
  baseRate: number;
  /** 전신환매입율 — 은행이 외화를 사들일 때. */
  buyingRate: number;
  /** 전신환매도율 — 은행이 외화를 팔 때. 환전할 때 실제로 내는 값에 더 가깝다. */
  sellingRate: number;
};

export type ExchangeRatesDto = { asOf: string; rates: ExchangeRate[] };

/** 환율을 못 가져오는 이유. 화면이 이유마다 다르게 말해야 해서 뭉뚱그리지 않는다. */
export type ExchangeBlockedReason =
  /** 로그인해야 부를 수 있다 — 우리 인증키로 남이 호출을 돌리는 것을 막으려는 것이다. */
  | 'signed-out'
  /** 서버에 그 경로가 아직 없다(404·501). 기다리면 생긴다. */
  | 'not-built'
  /** 바깥 업체 열쇠가 서버에 안 꽂혔다. **다시 시도해도 매한가지다** (S15P21E201-1200). */
  | 'not-ready'
  /** 환율 업체 쪽이 실패했다(5xx). 잠시 뒤 될 수 있다. */
  | 'vendor'
  /** 그 밖 — 끊김 등. */
  | 'error';

export type ExchangeRatesOutcome =
  | { state: 'ready'; asOf: string; rates: ExchangeRate[] }
  | { state: 'blocked'; reason: ExchangeBlockedReason };

/**
 * 고른 언어에 맞는 기본 통화.
 *
 * 🔴 언어는 나라가 아니다. 영어를 쓰지만 유로를 쓰는 사람이 있다 — 그래서 이것은 **기본값일
 * 뿐이고 화면에서 바꿀 수 있어야 한다.** 다만 아무것도 안 고른 채로 시작하는 것보다는,
 * 여기까지 온 맥락(언어)을 써서 한 번의 탭을 줄여 주는 것이 낫다.
 *
 * 🔴 매개변수를 언어 타입이 아니라 문자열로 받는다. 이 함수가 하는 일은 "언어 코드를 보고
 * 통화를 고르는 것" 이라 코드값만 있으면 충분하고, 언어 목록이 늘거나 줄 때마다 이 파일이
 * 따라 바뀌지 않아야 한다. 모르는 값은 USD 로 떨어진다 — 빈 화면보다 낫다.
 *
 * 🔴 한국어는 USD 다. 한국인은 이 화면을 반대 방향(원 → 외화)으로 보고, 그때 가장 먼저
 * 떠올리는 것이 달러다.
 */
export function defaultCurrencyFor(language: string): string {
  if (language === 'ja') return 'JPY';
  if (language === 'zh-Hans') return 'CNY';
  if (language === 'zh-Hant') return 'TWD';
  return 'USD';
}

/**
 * 100단위로 고시되는 통화인가.
 *
 * 🔴 한국은행·수출입은행 고시는 엔과 동남아 통화 일부를 **100단위**로 낸다. 그대로 1단위인
 * 줄 알고 계산하면 엔화가 100배로 틀린다 — 사용자가 알아채기 어렵고, 알아챘을 땐 이미
 * 잘못된 값으로 판단한 뒤다. 통화 코드에 그 사실이 붙어 온다(예: `JPY(100)`).
 */
export function unitsPerQuote(currencyCode: string): number {
  const match = currencyCode.match(/\((\d+)\)/);
  const parsed = match ? Number(match[1]) : NaN;
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 1;
}

/** 코드에서 괄호를 걷어낸 표기 — `JPY(100)` → `JPY`. */
export function displayCode(currencyCode: string): string {
  return currencyCode.replace(/\s*\(\d+\)\s*/, '').trim();
}

/**
 * 외화 → 원. 1 JPY 가 몇 원인가를 곱한다.
 *
 * 🔴 고시가 100단위면 고시값을 100으로 나눠야 1단위 값이 된다.
 */
export function foreignToKrw(amount: number, rate: ExchangeRate): number {
  if (!Number.isFinite(amount)) return 0;
  return amount * (rate.baseRate / unitsPerQuote(rate.currencyCode));
}

/** 원 → 외화. */
export function krwToForeign(amount: number, rate: ExchangeRate): number {
  if (!Number.isFinite(amount)) return 0;
  const perUnit = rate.baseRate / unitsPerQuote(rate.currencyCode);
  if (!perUnit) return 0;
  return amount / perUnit;
}

function blockedReason(error: unknown): ExchangeBlockedReason {
  // 🔴 열쇠가 안 꽂힌 것도 5xx 로 온다 — 숫자만 보면 몸 가른다 (S15P21E201-1200).
  if (isVendorNotReady(error)) return 'not-ready';
  if (!(error instanceof ApiClientError)) return 'error';
  if (error.status === 401 || error.status === 403) return 'signed-out';
  if (error.status === 404 || error.status === 501) return 'not-built';
  if (error.status >= 500) return 'vendor';
  return 'error';
}

export async function loadExchangeRates(
  accessToken: string | null,
  signal?: AbortSignal,
): Promise<ExchangeRatesOutcome> {
  // 로그인 없이 부르면 서버가 401 을 준다. 갔다 와서 알기보다 여기서 바로 말해 준다 —
  // 기다렸다가 "안 됐어요" 를 듣는 것이 제일 나쁘다.
  if (!accessToken) return { state: 'blocked', reason: 'signed-out' };
  try {
    const dto = await apiRequest<ExchangeRatesDto>('/api/v1/exchange-rates', { accessToken, signal });
    const rates = (dto?.rates ?? []).filter((r) => r?.currencyCode && Number.isFinite(r.baseRate) && r.baseRate > 0);
    // 빈 목록을 성공이라고 하지 않는다 — 화면이 고를 것이 없는 선택기를 그리게 된다.
    if (!rates.length) return { state: 'blocked', reason: 'vendor' };
    return { state: 'ready', asOf: dto.asOf, rates };
  } catch (error) {
    return { state: 'blocked', reason: blockedReason(error) };
  }
}

/** 기본 통화를 목록에서 찾는다. 없으면 첫 번째 — 고를 것이 있는데 빈 화면을 주지 않는다. */
export function pickRate(rates: ExchangeRate[], wantedCode: string): ExchangeRate | null {
  if (!rates.length) return null;
  const want = displayCode(wantedCode).toUpperCase();
  return rates.find((r) => displayCode(r.currencyCode).toUpperCase() === want) ?? rates[0];
}
