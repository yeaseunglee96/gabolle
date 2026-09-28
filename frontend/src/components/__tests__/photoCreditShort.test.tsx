// 카드 사진 띠·코스 표지의 짧은 출처 — S15P21E201-1705 (사용자 결정 (나)).
//
// 🔴 폰 두 칸 카드의 띠는 두 줄까지(글자 폭 147px, 2026-09-26 실측)다. 긴 출처는 영어 세 줄
//    「Photo: Korea Tourism / Organization · …」, 갤러리 사진은 한국어도 세 줄이라 이용 조건(공공누리 유형)이 잘렸다.
//    - 좁은 자리에는 기관과 이용 조건만: 한국어 「한국관광공사 공공누리 제1유형」, 영어 「KTO · KOGL Type 1」.
//    - 보이는 글자만 줄인다. 화면 낭독에는 긴 출처 전체를 붙인다.
//    - 다섯 모양 밖의 글자는 그대로.
import { render, screen } from '@testing-library/react-native';

import { PhotoCreditBar } from '@/components/PhotoCreditBar';
import { photoSourceShortText } from '@/discovery/places';
import { pickLanguage } from '@/i18n/pick';

// 소스를 읽는 시험은 이 저장소의 방식대로 require 로 부른다 — 앱 tsconfig 에 node 타입이 없다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

const tx = (ko: string) => ko;
const txEn = (_ko: string, en: string) => en;
// 줄바꿈 안 되는 공백(160) — 「KOGL Type 1」이 한 덩어리로 넘어가게.
const NB = String.fromCharCode(160);
const GALLERY = '한국관광공사 관광사진갤러리 공공누리 제1유형 · 촬영 부산관광공사';

describe('좁은 자리의 짧은 출처', () => {
  it.each([
    ['한국관광공사 공공누리 제1유형', '한국관광공사 공공누리 제1유형', `KTO · KOGL${NB}Type${NB}1`],
    ['한국관광공사 공공누리 제3유형', '한국관광공사 공공누리 제3유형', `KTO · KOGL${NB}Type${NB}3`],
    [GALLERY, '한국관광공사 공공누리 제1유형', `KTO · KOGL${NB}Type${NB}1`],
    ['한국관광공사 관광사진갤러리 공공누리 제1유형 · 촬영 한국관광공사 이범수', '한국관광공사 공공누리 제1유형', `KTO · KOGL${NB}Type${NB}1`],
  ])('「%s」 → 한국어 「%s」 · 영어 「%s」', (source, ko, en) => {
    expect(photoSourceShortText(source, tx)).toBe(ko);
    expect(photoSourceShortText(source, txEn)).toBe(en);
  });

  it('일본어·중국어판은 긴 출처와 같이 영어 — 번역표의 숫자 모양으로 찾는다', () => {
    const txJa = (ko: string, en: string) => pickLanguage('ja', { ko, en });
    const txZh = (ko: string, en: string) => pickLanguage('zh-Hant', { ko, en });
    expect(photoSourceShortText('한국관광공사 공공누리 제3유형', txJa)).toBe(`KTO · KOGL${NB}Type${NB}3`);
    expect(photoSourceShortText(GALLERY, txZh)).toBe(`KTO · KOGL${NB}Type${NB}1`);
  });

  it.each(['한국관광공사 관광사진갤러리', '한국관광공사 관광사진갤러리 공공누리 제1유형', 'Wikimedia Commons'])('🔴 다섯 모양 밖의 글자는 그대로 — 「%s」', (source) => {
    expect(photoSourceShortText(source, txEn)).toBe(source);
    expect(photoSourceShortText(source, tx)).toBe(source);
  });
});

describe('카드 사진 띠 — 보이는 글자는 짧게, 화면 낭독은 긴 출처', () => {
  it('영어판 — 띠에는 「Photo: KTO · KOGL Type 1」, 낭독은 「Photo: Korea Tourism Organization · KOGL Type 1」', () => {
    render(<PhotoCreditBar source="한국관광공사 공공누리 제1유형" license={null} licenseUrl={null} tx={txEn} />);
    expect(screen.getByText(`Photo: KTO · KOGL${NB}Type${NB}1`)).toBeTruthy();
    expect(screen.getByLabelText(`Photo: Korea Tourism Organization · KOGL${NB}Type${NB}1`)).toBeTruthy();
  });

  it('🔴 갤러리 사진 — 띠에서는 「관광사진갤러리 · 촬영 ○○」를 빼고 이용 조건은 남긴다. 낭독은 전부', () => {
    render(<PhotoCreditBar source={GALLERY} license={null} licenseUrl={null} tx={tx} />);
    expect(screen.getByText('사진: 한국관광공사 공공누리 제1유형')).toBeTruthy();
    expect(screen.getByLabelText(`사진: ${GALLERY}`)).toBeTruthy();
  });

  it('코스 카드 표지도 같은 규칙 — 짧은 출처에 두 줄까지, 낭독은 긴 출처', () => {
    const source = readFileSync(join(__dirname, '..', '..', 'plan', 'CourseCard.tsx'), 'utf8') as string;
    expect(source).toContain("accessibilityLabel={txf(tx, '사진: %s', 'Photo: %s', photoSourceText(photo.source, tx))}");
    expect(source).toContain('<Text variant="micro" numberOfLines={2} color={color.text.onAction}>');
    expect(source).toContain("{txf(tx, '사진: %s', 'Photo: %s', photoSourceShortText(photo.source, tx))}");
  });
});
