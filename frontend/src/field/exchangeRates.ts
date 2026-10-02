// 오늘의 환율 — 현장 도구가 쓴다.
import { apiRequest, ApiClientError } from '@/api/client';
import { isVendorNotReady } from '@/api/vendorReady';

/** 서버가 주는 한 통화의 값. */
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
  /** 바깥 업체 열쇠가 서버에 안 꽂혔다. 다시 시도해도 매한가지다. */
  | 'not-ready'
  /** 환율 업체 쪽이 실패했다(5xx). 잠시 뒤 될 수 있다. */
  | 'vendor'
  /** 그 밖 — 끊김 등. */
  | 'error';

export type ExchangeRatesOutcome =
  | { state: 'ready'; asOf: string; rates: ExchangeRate[] }
  | { state: 'blocked'; reason: ExchangeBlockedReason };

/** 고른 언어에 맞는 기본 통화. */
export function defaultCurrencyFor(language: string): string {
  if (language === 'ja') return 'JPY';
  if (language === 'zh-Hans') return 'CNY';
  if (language === 'zh-Hant') return 'TWD';
  return 'USD';
}

/** 100단위로 고시되는 통화인가. */
export function unitsPerQuote(currencyCode: string): number {
  const match = currencyCode.match(/\((\d+)\)/);
  const parsed = match ? Number(match[1]) : NaN;
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 1;
}

/** 코드에서 괄호를 걷어낸 표기 — `JPY(100)` → `JPY`. */
export function displayCode(currencyCode: string): string {
  return currencyCode.replace(/\s*\(\d+\)\s*/, '').trim();
}

/** 외화 → 원. 1 JPY 가 몇 원인가를 곱한다. */
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
  // 열쇠가 안 꽂힌 것도 5xx 로 온다 — 숫자만 보면 몸 가른다.
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
  // 로그인 없이 부르면 서버가 401 을 준다. 갔다 와서 알기보다 여기서 바로 말해 준다
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
  const find = (code: string) => rates.find((r) => displayCode(r.currencyCode).toUpperCase() === code);
  const want = displayCode(wantedCode).toUpperCase();
  // 서버(수출입은행)는 위안을 CNH 로만 주고 TWD 는 아예 없다. 못 찾았을 때 목록 첫 통화(알파벳순이라 AED)로
  // 떨어지면 중국어 화면이 디르함으로 시작한다(S15P21E201-1920) — 위안은 CNH 로 잇고, 그래도 없으면 USD.
  return find(want) ?? (want === 'CNY' ? find('CNH') : undefined) ?? find('USD') ?? rates[0];
}
