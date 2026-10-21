// 환율 화면 통화 이름 — 안드로이드 앱(Intl.DisplayNames 없음)에서도 화면 언어로 — S15P21E201-1913(실기기 10/2).
import { currencyDisplayName } from '@/field/CurrencyBadge';

const real = Intl.DisplayNames;
afterEach(() => { (Intl as { DisplayNames: unknown }).DisplayNames = real; });

it('🔴 Intl.DisplayNames 가 없으면 서버의 한국어 이름 대신 앱 이름표를 쓴다', () => {
  (Intl as { DisplayNames: unknown }).DisplayNames = undefined;
  expect(currencyDisplayName('USD', 'en-US', '미국 달러')).toBe('US Dollar');
  expect(currencyDisplayName('JPY(100)', 'ja-JP', '일본 옌')).toBe('日本円');
  expect(currencyDisplayName('HKD', 'zh-Hans', '홍콩 달러')).toBe('港元');
  expect(currencyDisplayName('HKD', 'zh-Hant', '홍콩 달러')).toBe('港幣');
});

it('한국어 화면과 표에 없는 통화는 서버 이름 그대로', () => {
  (Intl as { DisplayNames: unknown }).DisplayNames = undefined;
  expect(currencyDisplayName('USD', 'ko-KR', '미국 달러')).toBe('미국 달러');
  expect(currencyDisplayName('XYZ', 'en-US', '어떤 돈')).toBe('어떤 돈');
});
