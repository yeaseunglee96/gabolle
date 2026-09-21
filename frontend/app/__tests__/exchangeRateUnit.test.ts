// 환율 화면이 「100엔 기준」이라는 말을 안 하던 것 — S15P21E201-1449.
//
// 🔴 2026-09-21 실기(APK versionCode 29). 계산은 늘 맞았다 — 10,000원을 넣으면 1,135엔이
//    나온다(10000 / 881 × 100). 틀린 것은 «보여줄 때»다. displayCode 가 `JPY(100)` 에서
//    괄호를 걷어내면서 100 이 화면에서 사라져, 목록 줄이 「JPY  일본 엔     ₩881」이 됐다.
//    1엔이 881원으로 읽힌다 — 100배다.
//
// 🔴 부산은 일본·중국 여행객이 주 대상이라 JPY 는 대표 통화다. 100배 틀린 값으로 읽히는
//    숫자를 가격표 옆에 두면 이 화면이 하려던 일과 정반대가 된다.
//
// 이 시험은 «화면이 단위를 적는가»를 잰다. 숫자 자체는 exchangeRates.test.ts 가 이미 잰다.
import { displayCode, unitsPerQuote } from '@/field/exchangeRates';

// tsconfig 가 node 타입을 안 들고 있어서 import 로 쓰면 타입 검사가 막힌다 —
// 이 저장소의 다른 파일 검사 시험과 같은 방식이다(app/__tests__/homeHeaderBell.test.ts).
declare const require: (id: string) => any;
declare const __dirname: string;

// 화면과 같은 규칙. 화면의 quotedCode 와 어긋나면 아래 파일 검사가 잡는다.
function quotedCode(currencyCode: string): string {
  const units = unitsPerQuote(currencyCode);
  const code = displayCode(currencyCode);
  return units > 1 ? `${units} ${code}` : code;
}

describe('quoted currency label', () => {
  it('says how many units the rate is for', () => {
    expect(quotedCode('JPY(100)')).toBe('100 JPY');
  });

  it('leaves per-one currencies alone — 「1 USD」는 군더더기다', () => {
    expect(quotedCode('USD')).toBe('USD');
    expect(quotedCode('EUR')).toBe('EUR');
  });

  it('uses whatever number the notice carries, not a hard-coded 100', () => {
    expect(quotedCode('IDR(1000)')).toBe('1000 IDR');
  });
});

describe('the exchange screen prints the unit', () => {
  const source = (() => {
    const { readFileSync } = require('fs');
    const { join } = require('path');
    return readFileSync(join(__dirname, '..', 'field', 'exchange-rate.tsx'), 'utf8') as string;
  })();

  it('labels the currency picker row with the unit', () => {
    expect(source).toContain('function quotedCode(');
    expect(source).toContain('<Text variant="body" weight="bold">{quotedCode(item.currencyCode)}</Text>');
    // 되돌리면 바로 이 줄로 돌아간다.
    expect(source).not.toContain('<Text variant="body" weight="bold">{displayCode(item.currencyCode)}</Text>');
  });

  it('labels the base-rate line with the unit', () => {
    expect(source).toContain('unitsPerQuote(rate.currencyCode) > 1');
    expect(source).toContain('${quotedCode(rate.currencyCode)} · ');
  });

  it('reads the unit out loud too — 눈으로만 고치면 소리로는 그대로다', () => {
    expect(source).toContain('accessibilityLabel={`${quotedCode(item.currencyCode)} ${name}`}');
  });
});
