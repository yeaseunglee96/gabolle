// — 통화 이름은 Intl 에서 받는데, Intl 자료 자체가 틀린 이름이 있다(S15P21E201-1790).

import { currencyDisplayName } from '../CurrencyBadge';

describe('currencyDisplayName', () => {
  it('BND 를 한국어로 「브루나이 달러」라고 쓴다 — Intl 은 「부루나이」로 준다', () => {
    expect(currencyDisplayName('BND', 'ko', '브루나이 달러')).toBe('브루나이 달러');
    expect(currencyDisplayName('BND', 'ko-KR', '브루나이 달러')).toBe('브루나이 달러');
  });

  it('다른 언어와 다른 통화는 Intl 이름 그대로다', () => {
    expect(currencyDisplayName('BND', 'en', 'x')).toBe('Brunei Dollar');
    expect(currencyDisplayName('USD', 'ko', 'x')).toBe('미국 달러');
  });
});
