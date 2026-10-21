// 사진 출처 영어판 — 「Photo: 한국관광공사 공공누리 제1유형」이 영어판에서도 한국어였다(S15P21E201-1705, 조율 세션 결정 A).
//
// 🔴 기준(조율 세션):
//    - 운영 자료의 출처 글자 다섯 가지(2026-09-26 읽기만 해서 확인)만 영어로 바꾼다. 그 밖의 글자는 한국어 그대로.
//    - 촬영자 이름(「촬영 ○○」의 ○○)은 로마자로 바꾸지 않는다 — 본인이 쓰는 철자가 따로 있을 수 있다. 이름표만 영어.
//    - 공공누리 유형 번호는 그대로 옮긴다. 한국어판은 그대로.
//    - 「KOGL Type 1」은 줄바꿈 안 되는 공백(\u00A0)으로 붙는다 — 카드 띠에서 번호만 둘째 줄로 떨어지지 않게.
import { isSelfLabelledSource, photoLabels, photoSourceEnglish, photoSourceShortText } from '../places';

// tsconfig 가 node 타입을 안 들고 있어서 import 로 쓰면 타입 검사가 막힌다 — 이 저장소의 다른 파일 검사 시험과 같은 방식.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

const tx = (ko: string) => ko;
const txEn = (_ko: string, en: string) => en;
const KOGL = (type: number) => `KOGL\u00A0Type\u00A0${type}`;

describe('사진 출처를 영어로', () => {
  it.each([
    ['한국관광공사 공공누리 제3유형', `Korea Tourism Organization · ${KOGL(3)}`],
    ['한국관광공사 공공누리 제1유형', `Korea Tourism Organization · ${KOGL(1)}`],
    ['한국관광공사 관광사진갤러리 공공누리 제1유형 · 촬영 부산관광공사', `Korea Tourism Organization Photo Gallery · ${KOGL(1)} · Photographer: 부산관광공사`],
    ['한국관광공사 관광사진갤러리 공공누리 제1유형 · 촬영 부산울산지사 디자인글꼴', `Korea Tourism Organization Photo Gallery · ${KOGL(1)} · Photographer: 부산울산지사 디자인글꼴`],
    ['한국관광공사 관광사진갤러리 공공누리 제1유형 · 촬영 한국관광공사 이범수', `Korea Tourism Organization Photo Gallery · ${KOGL(1)} · Photographer: 한국관광공사 이범수`],
  ])('운영 자료의 출처 「%s」', (source, english) => {
    expect(photoSourceEnglish(source)).toBe(english);
  });

  it.each(['한국관광공사 관광사진갤러리', '부산광역시 공공누리 제1유형', 'Wikimedia Commons', ''])('🔴 다섯 모양 밖의 글자는 그대로 — 「%s」', (source) => {
    expect(photoSourceEnglish(source)).toBe(source);
  });

  it('번호는 줄바꿈 안 되는 공백(160)으로 붙고, 역슬래시(92) 글자가 화면에 새지 않는다', () => {
    const text = photoSourceEnglish('한국관광공사 공공누리 제1유형');
    expect(text.endsWith(['KOGL', 'Type', '1'].join(String.fromCharCode(160)))).toBe(true);
    expect(text).not.toContain(String.fromCharCode(92));
  });

  it('영어판 출처 줄 — 「Photo: Korea Tourism Organization · KOGL Type 1」', () => {
    expect(photoLabels({ photoSource: '한국관광공사 공공누리 제1유형' }, txEn).credit).toBe(`Photo: Korea Tourism Organization · ${KOGL(1)}`);
  });

  it('한국어판은 그대로', () => {
    expect(photoLabels({ photoSource: '한국관광공사 공공누리 제1유형' }, tx).credit).toBe('사진: 한국관광공사 공공누리 제1유형');
  });

  it.each(['src/components/PhotoCreditBar.tsx', 'src/plan/CourseCard.tsx'])('%s 도 같은 것을 쓴다 — 카드 띠·코스 표지', (file) => {
    const source = readFileSync(join(__dirname, '..', '..', '..', file), 'utf8');
    expect(source).toContain("txf(tx, '사진: %s', 'Photo: %s', photoSourceText(");
  });
});

// 5개 언어 점검(2026-09-29) — 장소 사진 여러 장의 구글 출처가 영어·일본어·중국어 화면에서 한국어로 떴다.
describe('구글 지도 출처', () => {
  const en = (_ko: string, english: string) => english;
  it('🔴 이름표만 영어로, 촬영자 이름은 그대로', () => {
    expect(photoSourceEnglish('Google 지도 · 사진 박대규')).toBe('Google Maps · Photo: 박대규');
    expect(photoSourceShortText('Google 지도 · 사진 박대규', en)).toBe('Google Maps · Photo: 박대규');
  });
  it('🔴 이름표를 달고 오므로 「사진: 」을 또 붙이지 않는다', () => {
    expect(isSelfLabelledSource('Google 지도 · 사진 박대규')).toBe(true);
    expect(isSelfLabelledSource('한국관광공사 공공누리 제1유형')).toBe(false);
  });
});
