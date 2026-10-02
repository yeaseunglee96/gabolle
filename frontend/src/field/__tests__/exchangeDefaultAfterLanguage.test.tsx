declare const require: (id: string) => any; declare const __dirname: string; const { readFileSync } = require('fs');

import { currencyDisplayName } from '@/field/CurrencyBadge';

// 🔴 S15P21E201-1941 — 운영 웹(10/2) 중국어 환율 화면이 USD 로 시작했고, 목록의 CNH 칸에는 이름이 비었다.
//    ① 웹은 고른 언어가 저장소에서 늦게 와서, 첫 그림(영어)으로 정한 기본 통화가 그대로 남았다.
//    ② 브라우저 Intl.DisplayNames 는 모르는 코드(CNH)를 코드 그대로 돌려준다 — 앱의 이름표로 넘어가지 않았다.
const screen: string = readFileSync(`${__dirname}/../../../app/field/exchange-rate.tsx`, 'utf8');

describe('환율 화면 — 언어가 늦게 와도 기본 통화가 맞다', () => {
  it('🔴 언어가 바뀌면, 직접 고르기 전까지 기본 통화·방향을 다시 맞춘다', () => {
    expect(screen).toMatch(/if \(!touched\.current\.code\) setCode\(defaultCurrencyFor\(language\)\);/);
    expect(screen).toMatch(/\}, \[language\]\);/);
    expect(screen).toMatch(/touched\.current\.code = true; setCode\(item\.currencyCode\);/);
  });
  it('🔴 브라우저가 모르는 CNH 도 이름이 나온다', () => {
    // Edge 는 CNH 를 모른다(코드 그대로 돌려준다). 노드는 알아서, 그 브라우저처럼 흉내 낸다.
    const Real = Intl.DisplayNames;
    class EdgeLike extends Real { of(code: string) { return code === 'CNH' ? 'CNH' : super.of(code); } }
    (Intl as { DisplayNames: unknown }).DisplayNames = EdgeLike;
    try {
    expect(currencyDisplayName('CNH', 'zh-Hans', '')).toBe('离岸人民币');
    expect(currencyDisplayName('CNH', 'zh-Hant', '')).toBe('離岸人民幣');
    expect(currencyDisplayName('CNH', 'en', '')).toBe('Chinese Yuan (offshore)');
    } finally {
      (Intl as { DisplayNames: unknown }).DisplayNames = Real;
    }
  });
  it('아는 통화는 그대로 브라우저 이름', () => {
    expect(currencyDisplayName('USD', 'en', '')).toMatch(/US Dollar/);
  });
});
