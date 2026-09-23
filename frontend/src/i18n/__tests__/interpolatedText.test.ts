import { fillValues, txf } from '@/i18n/format';
import { pickLanguage } from '@/i18n';
import { TRANSLATIONS } from '@/i18n/translations';
import type { LanguageCode } from '@/i18n/languages';

// 표는 «원문 파일»로도 봐야 한다 — 같은 열쇠를 두 번 적으면 읽어 들인 객체에는
// 하나만 남아서, 객체만 보면 중복을 영영 못 본다.
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

/**
 * 🔴 **이름·장소가 끼는 문구가 일본어·중국어에서 영어로 떨어지던 것** — S15P21E201-1352.
 *
 * 숫자가 끼는 자리는 「모양」으로 찾아 해결했다(S15P21E201-1344). 하지만 글자가 끼는
 * 자리는 다 그려진 뒤에 값과 문구를 못 가른다. 그래서 값을 «따로» 받는다.
 *
 * <p>이 시험이 붙드는 것은 셋이다.
 * <ol>
 *   <li>틀이 표에서 찾히고, 값이 제자리에 들어간다</li>
 *   <li>🔴 <b>값의 순서가 안 뒤집힌다</b> — 표를 쓸 때 가장 내기 쉬운 실수다.
 *       실제로 이 작업 중에 중국어를 「用&lt;언어&gt;收听&lt;글자&gt;」로 적었다가
 *       두 값이 뒤바뀌는 것을 여기서 잡았다</li>
 *   <li>🔴 <b>자리 수가 틀과 번역에서 같다</b> — 번역에서 %s 를 하나 빠뜨리면 값이
 *       조용히 사라지고, 하나 더 적으면 「%s」 가 화면에 그대로 뜬다</li>
 * </ol>
 */
describe('값이 끼는 문구 — 글자 자리(%s)', () => {
  const tx = (language: LanguageCode) => (ko: string, en: string) => pickLanguage(language, { ko, en });

  it('틀을 표에서 찾아 값을 끼운다', () => {
    expect(txf(tx('ja'), '%s 상세 보기', 'View details for %s', '海雲台')).toBe('海雲台の詳細を見る');
    expect(txf(tx('zh-Hans'), '사진: %s', 'Photo: %s', '韩国观光公社')).toBe('照片: 韩国观光公社');
    expect(txf(tx('zh-Hant'), '%s 빼기', 'Remove %s', '甘川洞')).toBe('移除 甘川洞');
  });

  it('한국어·영어는 원래대로다', () => {
    expect(txf(tx('ko'), '%s 빼기', 'Remove %s', '감천문화마을')).toBe('감천문화마을 빼기');
    expect(txf(tx('en'), '%s 빼기', 'Remove %s', 'Gamcheon')).toBe('Remove Gamcheon');
  });

  it('🔴 값이 둘일 때 순서가 안 뒤집힌다', () => {
    // [읽을 글자][언어 이름] 순서다. 뒤집히면 「日本語 を 김치 で聞く」가 된다.
    expect(txf(tx('ja'), '%s %s로 듣기', 'Hear %s in %s', '김치', '日本語')).toBe('김치を 日本語で聞く');
    expect(txf(tx('zh-Hans'), '%s %s로 듣기', 'Hear %s in %s', '김치', '中文')).toBe('收听 김치（中文）');
  });

  /**
   * 🔴 틀과 번역의 «자리 수»가 다르면 조용히 값이 사라지거나 %s 가 화면에 뜬다.
   * 표를 손으로 고치는 일이 앞으로도 있으니, 전수로 센다.
   */
  it('🔴 표의 모든 %s 틀에서 자리 수가 언어마다 같다', () => {
    const count = (text: string) => (text.match(/%s/g) ?? []).length;
    const wrong: string[] = [];
    for (const [ko, row] of Object.entries(TRANSLATIONS)) {
      const expected = count(ko);
      if (expected === 0) continue;
      for (const field of ['ja', 'zhHans', 'zhHant'] as const) {
        const translated = row[field];
        if (translated && count(translated) !== expected) {
          wrong.push(`${field} ${JSON.stringify(ko)} — 틀 ${expected}자리 · 번역 ${count(translated)}자리`);
        }
      }
    }
    expect(wrong).toEqual([]);
  });

  it('🔴 %d 틀도 자리 수가 언어마다 같다', () => {
    const count = (text: string) => (text.match(/%d/g) ?? []).length;
    const wrong: string[] = [];
    for (const [ko, row] of Object.entries(TRANSLATIONS)) {
      const expected = count(ko);
      if (expected === 0) continue;
      for (const field of ['ja', 'zhHans', 'zhHant'] as const) {
        const translated = row[field];
        if (translated && count(translated) !== expected) {
          wrong.push(`${field} ${JSON.stringify(ko)} — 틀 ${expected}자리 · 번역 ${count(translated)}자리`);
        }
      }
    }
    expect(wrong).toEqual([]);
  });

  /**
   * 🔴 같은 열쇠를 두 번 적으면 **뒤엣것이 조용히 이긴다.** 타입 검사는 안 잡고,
   * 읽어 들인 객체에도 하나만 남아서 «객체만 보면» 영영 안 보인다.
   * S15P21E201-1354 에서 실제로 「출발지에서 %d분」을 두 번 넣었다. 그래서 원문으로 센다.
   */
  it('🔴 표에 같은 열쇠가 두 번 있지 않다', () => {
    const source: string = readFileSync(join(__dirname, '..', 'translations.ts'), 'utf8');
    const keys = [...source.matchAll(/^ {2}'((?:[^'\\]|\\.)*)':\s*\{/gm)].map((m) => m[1]);
    const seen = new Set<string>();
    const duplicated: string[] = [];
    for (const key of keys) {
      if (seen.has(key)) duplicated.push(key);
      seen.add(key);
    }
    expect(duplicated).toEqual([]);
    expect(keys.length).toBeGreaterThan(1000);   // 정규식이 헛돌면 0개가 나온다
  });

  it('🔴 자리 지정(%1$s)은 쓰지 않는다 — fillValues 가 못 읽는다', () => {
    const positional: string[] = [];
    for (const [ko, row] of Object.entries(TRANSLATIONS)) {
      for (const text of [ko, row.ja, row.zhHans, row.zhHant]) {
        if (text && /%\d+\$/.test(text)) positional.push(JSON.stringify(text));
      }
    }
    expect(positional).toEqual([]);
  });

  describe('fillValues 는 어긋나도 안 터진다', () => {
    it('값이 모자라면 그 자리를 그대로 둔다 — 엉뚱한 값을 지어내지 않는다', () => {
      expect(fillValues('%s 와 %s', ['하나'])).toBe('하나 와 %s');
    });
    it('값이 남으면 버린다', () => {
      expect(fillValues('%s 만', ['하나', '둘'])).toBe('하나 만');
    });
    it('숫자도 글자로 끼운다', () => {
      expect(fillValues('%s개', [3])).toBe('3개');
    });
  });

  /**
   * 🔴 **일본어·중국어는 어절을 띄우지 않는다** — S15P21E201-1489(B-18).
   *
   * <p>실기기(iOS build 39)에서 마이페이지 숫자 칸이 이렇게 읽혔다.
   *
   * <pre>
   *   記録 を見る      ← 조사(を) 앞에 공백
   *   查看 记录
   * </pre>
   *
   * <p>QA 는 iOS 가 자동 생성한 라벨이라고 봤지만 아니었다 — 표의 번역값
   * (`'%s 보기'` → `'%s を見る'`)에 **공백이 박혀 있었다.** 한국어 원문에 공백이 있다고
   * 번역까지 따라 적으면 이렇게 된다.
   *
   * <p>🔴 **일본어 조사만 본다.** 처음에 「값 자리 뒤의 공백」을 통째로 잡아 봤더니
   * 중국어까지 걸렸는데, 중국어는 사정이 다르다 — 라틴 문자나 값을 사이에 둘 때 앞뒤로
   * 공백을 두는 것이 실제로 흔한 표기다(「查看 %s 详情」). 언어마다 다른 관습을 한 자로
   * 재면 정당한 번역이 빨개지고, 그러면 사람이 검사를 지운다.
   *
   * <p>일본어 조사는 예외가 없다 — 앞 낱말에 **반드시 붙여 쓴다.** 그래서 이것만 막는다.
   * 이 검사를 처음 켰을 때 표에서 54자리가 걸렸고, 전부 같은 결함이라 함께 고쳤다.
   */
  it('🔴 일본어 조사는 %s 에 붙여 쓴다 — 「%s を見る」 같은 공백이 없다', () => {
    const spaced: string[] = [];
    for (const [ko, row] of Object.entries(TRANSLATIONS)) {
      if (row.ja && /%s (を|の|で|に|が|は|と|へ|から|まで|より)/.test(row.ja)) {
        spaced.push(`${JSON.stringify(ko)} › ja: ${JSON.stringify(row.ja)}`);
      }
    }
    expect(spaced).toEqual([]);
  });
});
