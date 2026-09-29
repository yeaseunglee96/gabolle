// 경사·그늘 겹의 기준 한 줄 — 파일에는 한국어 원문뿐이라 다른 언어 화면에 한국어가 그대로 끼던 것.
//
// 🔴 이 시험이 지키는 것: 아는 원문은 고른 언어로 옮기고(그늘 기준은 날짜·시각만 끼운다), 모르는 원문은
//    한국어 화면에서만 낸다 — 영어·일본어 화면 한가운데 한국어 한 줄을 끼우지 않는다.
import { pickLanguage } from '@/i18n/pick';
import type { LanguageCode } from '@/i18n/languages';
import { layerBasisText, layerNote } from '@/map/MobilityLayerToggle';


const txIn = (language: LanguageCode) => (ko: string, en: string) => pickLanguage(language, { ko, en });
const SLOPE = '경사 중앙값 · 30m 이상 길 · 고도 자료로 잰 추정치';
const SHADOW = '건물 그림자 · 2026-07-15 · 9–18시 평균';
const HANGUL = /[가-힣]/;

describe('겹의 기준 한 줄', () => {
  it('🔴 영어 화면에서는 영어로 — 한국어가 끼지 않는다', () => {
    const tx = txIn('en');
    expect(layerBasisText(SLOPE, tx)).toBe('Median slope · paths 30 m or longer · estimated from elevation data');
    expect(layerBasisText(SHADOW, tx)).toBe('Building shadows · 2026-07-15 · 9:00–18:00 average');
    const note = layerNote('shade', { status: 'ready', basis: SHADOW, partial: false, drawn: true }, tx)!;
    expect(note).toBe('Darker blue = more shade · Building shadows · 2026-07-15 · 9:00–18:00 average');
    expect(note).not.toMatch(HANGUL);
    expect(layerNote('slope', { status: 'ready', basis: SLOPE, partial: false, drawn: true }, tx)).not.toMatch(HANGUL);
  });

  it('일본어 화면에서도 번역표로 옮긴다 — 날짜·시각은 그대로 끼운다', () => {
    const tx = txIn('ja');
    expect(layerBasisText(SLOPE, tx)).toBe('勾配の中央値 · 30m以上の道 · 標高データによる推定値');
    expect(layerBasisText(SHADOW, tx)).toBe('建物の影 · 2026-07-15 · 9–18時の平均');
  });

  it('한국어 화면은 원문 그대로', () => {
    const tx = txIn('ko');
    expect(layerBasisText(SLOPE, tx)).toBe(SLOPE);
    expect(layerBasisText(SHADOW, tx)).toBe(SHADOW);
  });

  it('🔴 모르는 원문은 한국어 화면에서만 — 다른 언어에서는 뺀다', () => {
    const unknown = '새 기준 · 2027년 자료';
    expect(layerBasisText(unknown, txIn('ko'))).toBe(unknown);
    expect(layerBasisText(unknown, txIn('en'))).toBeNull();
    expect(layerBasisText(unknown, txIn('ja'))).toBeNull();
    expect(layerNote('slope', { status: 'ready', basis: unknown, partial: false, drawn: false }, txIn('en'))).not.toMatch(HANGUL);
    expect(layerBasisText(null, txIn('en'))).toBeNull();
  });
});
