// 갤럭시 S10 QA(APK 41) 결함 넷 — S15P21E201-1868.
import { act, fireEvent, render, screen } from '@testing-library/react-native';

import { PlacePhotoGallery } from '@/components/PlacePhotoGallery';
import { photoLabels, photoSourceText } from '@/discovery/places';
import { pickLanguage } from '@/i18n/pick';

jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

declare const require: any;
declare const __dirname: string;
const { readFileSync } = require('fs');

const txIn = (language: 'ko' | 'en' | 'ja' | 'zh-Hans' | 'zh-Hant') => (ko: string, en: string) => pickLanguage(language as never, { ko, en });

describe('A. 사진 갤러리는 지금 쪽 ±1 만 그린다 — 연달아 열면 메모리가 차서 회색이 됐다', () => {
  const urls = ['https://a/1.jpg', 'https://a/2.jpg', 'https://a/3.jpg', 'https://a/4.jpg', 'https://a/5.jpg', 'https://a/6.jpg'];

  it('처음에는 1·2 쪽만 사진이고 나머지는 빈 판이다', () => {
    render(<PlacePhotoGallery urls={urls} />);
    expect(screen.getAllByTestId('place-photo-page')).toHaveLength(2);
    expect(screen.getAllByTestId('place-photo-page-placeholder')).toHaveLength(4);
  });

  it('넘기면 창이 따라온다 — 4쪽이면 3·4·5 쪽만', () => {
    render(<PlacePhotoGallery urls={urls} />);
    const gallery = screen.getByTestId('place-photo-gallery');
    act(() => { fireEvent(gallery.parent ?? gallery, 'layout', { nativeEvent: { layout: { width: 300, height: 200 } } }); });
    act(() => { fireEvent.scroll(gallery, { nativeEvent: { contentOffset: { x: 900, y: 0 } } }); });
    const pages = screen.getAllByTestId('place-photo-page');
    expect(pages.map((p) => p.props.source?.uri ?? p.findByProps({ source: { uri: expect.any(String) } }).props.source.uri)).toEqual(['https://a/3.jpg', 'https://a/4.jpg', 'https://a/5.jpg']);
  });

  it('🔴 사진 주소를 바꾸지 않는다(제3유형 변경 금지) — 축소는 디코딩(resizeMethod)으로만', () => {
    const src = readFileSync(`${__dirname}/../components/PlacePhotoGallery.tsx`, 'utf8');
    expect(src).toContain('resizeMethod="resize"');
    expect(src).toContain('source={{ uri: url }}');
  });
});

describe('B. 출처 줄에 라이선스 이름을 두 번 적지 않는다', () => {
  it.each([
    ['한국관광공사 공공누리 제1유형', '공공누리 제1유형'],
    ['홍길동 · CC BY-SA 4.0', 'CC BY-SA 4.0'],
    ['Google 지도 · 사진 M M', 'Google 지도'],
  ])('%s + %s', (source, name) => {
    const credit = photoLabels({ photoSource: source, photoLicense: { name, url: 'https://l', filePage: 'https://f' } as never }, txIn('ko')).credit!;
    expect(credit.split(name)).toHaveLength(2);
  });

  it('출처에 없으면 여전히 붙이고, 링크는 그대로', () => {
    const labels = photoLabels({ photoSource: '출처 : 부산관광아카이브', photoLicense: { name: 'CC BY 2.0', url: 'https://l', filePage: 'https://f' } as never }, txIn('ko'));
    expect(labels.credit).toBe('출처 : 부산관광아카이브 · CC BY 2.0');
    expect(labels.licenseUrl).toBe('https://f');
  });
});

describe('C. 출처 이름표를 화면 언어로', () => {
  it.each([
    ['ja', '出典: 부산관광아카이브'],
    ['zh-Hans', '来源: 부산관광아카이브'],
    ['zh-Hant', '來源: 부산관광아카이브'],
    ['en', 'Source: 부산관광아카이브'],
  ] as const)('%s', (language, expected) => {
    expect(photoSourceText('출처 : 부산관광아카이브', txIn(language))).toBe(expected);
  });

  it('한국어는 받은 글자 그대로(띄어쓰기까지)', () => {
    expect(photoSourceText('출처:부산관광아카이브', txIn('ko'))).toBe('출처:부산관광아카이브');
    expect(photoSourceText('Google 지도 · 사진 박대규', txIn('ko'))).toBe('Google 지도 · 사진 박대규');
  });

  it('Google 이름표도 일본어로', () => {
    expect(photoSourceText('Google 지도 · 사진 박대규', txIn('ja'))).toBe('Google マップ · 写真: 박대규');
  });
});

describe('D. 중국어 달력의 일요일 머리는 「日」', () => {
  it.each(['zh-Hans', 'zh-Hant', 'ja'] as const)('%s', (language) => {
    expect(txIn(language)('일', 'S')).toBe('日');
  });

  it('기간 「3일」은 여전히 「3天」', () => {
    expect(txIn('zh-Hans')('3일', '3 days')).toBe('3天');
  });
});
