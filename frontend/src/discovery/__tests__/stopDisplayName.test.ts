// 영어 화면 일정·여행 중 카드의 장소 이름 — 로마자를 붙인다(S15P21E201-1735).
//
// 🔴 2026-09-26 발표 시연 점검. 영어로 보면 일정 카드·「Heading to 돈반」·코스 줄의 장소 이름이 한국어 그대로였다.
//    영어 이름이 있는 장소는 6,933곳 중 4곳뿐(운영도 같음). 장소 상세만 「국제시장 (Gukjesijang)」처럼 붙였다.
//    사용자 결정: 로마자를 옆에 붙인다 · 장소 상세의 그 함수를 다시 쓴다 · 영어 이름이 있으면 영어 먼저 ·
//    좁은 한 줄에서는 로마자 쪽만 줄임표로 자르고 한글은 자르지 않는다(택시·길 묻기에 필요) · 한국어 화면은 그대로.
declare const require: (id: string) => any;
declare const __dirname: string;

const mockGetPlace = jest.fn();
jest.mock('@/discovery/places', () => ({ ...jest.requireActual('@/discovery/places'), getPlace: (id: string) => mockGetPlace(id) }));

import { placeNameForLanguage, stopNameForLanguage, stopNameParts } from '@/discovery/romanize';
import { clearPlacePhotoCache, loadPlacePhoto } from '@/plan/placePhotos';
import { buildTripPassDetails } from '@/plan/tripPassData';

describe('일정에 적을 장소 이름', () => {
  it('한국어 화면은 일정 제목 그대로 — 영어 이름이 있어도 덧붙이지 않는다', () => {
    expect(stopNameForLanguage('돈반', null, 'ko')).toBe('돈반');
    expect(stopNameForLanguage('광안리해수욕장', 'Gwangalli Beach', 'ko')).toBe('광안리해수욕장');
  });

  it('🔴 영어 — 영어 이름이 없으면 「한글 (로마자)」', () => {
    expect(stopNameForLanguage('돈반', null, 'en')).toBe('돈반 (Donban)');
  });

  it('🔴 영어 — 영어 이름이 있으면 영어 먼저', () => {
    expect(stopNameForLanguage('광안리해수욕장', 'Gwangalli Beach', 'en')).toBe('Gwangalli Beach (광안리해수욕장)');
  });

  it('일·중도 장소 상세와 같은 규칙 — 장소 상세 함수 그대로', () => {
    for (const language of ['ja', 'zh-Hans', 'zh-Hant'] as const) {
      expect(stopNameForLanguage('카페오뜨', null, language)).toBe(placeNameForLanguage('카페오뜨', null, language));
    }
  });

  it('한글이 없는 이름은 괄호를 만들지 않는다', () => {
    expect(stopNameForLanguage('One Way Bread', null, 'en')).toBe('One Way Bread');
  });
});

describe('좁은 한 줄로 쪼개기 — 한글은 안 자르고 나머지만 자른다', () => {
  it('영어 이름이 없으면 한글이 앞, 로마자가 뒤(잘리는 쪽)', () => {
    expect(stopNameParts('돈반', null, 'en')).toEqual({ hangul: '돈반', other: 'Donban', otherFirst: false });
  });
  it('영어 이름이 있으면 영어가 앞(잘리는 쪽), 한글이 뒤', () => {
    expect(stopNameParts('광안리해수욕장', 'Gwangalli Beach', 'en')).toEqual({ hangul: '광안리해수욕장', other: 'Gwangalli Beach', otherFirst: true });
  });
  it('한국어 화면은 한글만', () => {
    expect(stopNameParts('돈반', 'Donban', 'ko')).toEqual({ hangul: '돈반', other: null, otherFirst: false });
  });
});

describe('장소 사진 조회가 영어 이름도 싣는다 — 일정 항목에는 영어 이름 칸이 없다', () => {
  beforeEach(() => { clearPlacePhotoCache(); mockGetPlace.mockReset(); });
  it('있으면 싣고, 없으면 null', async () => {
    mockGetPlace.mockResolvedValueOnce({ placeId: 'a', nameKo: '광안리해수욕장', nameEn: 'Gwangalli Beach', photoUrl: null, photoSource: null, category: 'SEA_BEACH' });
    mockGetPlace.mockResolvedValueOnce({ placeId: 'b', nameKo: '돈반', nameEn: null, photoUrl: null, photoSource: null, category: 'FOOD' });
    expect((await loadPlacePhoto('a')).nameEn).toBe('Gwangalli Beach');
    expect((await loadPlacePhoto('b')).nameEn).toBeNull();
  });
});

describe('승차권 첫·마지막 일정', () => {
  const itinerary = { days: [{ date: '2026-09-27', items: [
    { id: '1', placeId: 'a', title: '광안리해수욕장', startsAt: '2026-09-27T09:01:00+09:00', locked: false },
    { id: '2', placeId: 'b', title: '마끼몬스타', startsAt: '2026-09-27T19:00:00+09:00', locked: false },
  ] }] } as never;
  const base = { itinerary, origin: null, startDate: null, endDate: null, transport: null, ownerName: null };

  it('🔴 영어 — 영어 이름 먼저, 없으면 로마자', () => {
    const rows = buildTripPassDetails({ ...base, language: 'en', nameEnByPlaceId: { a: 'Gwangalli Beach' } });
    expect(rows.find((row) => row.key === 'First stop')?.value).toBe('09:01 · Gwangalli Beach (광안리해수욕장)');
    expect(rows.find((row) => row.key === 'Last stop')?.value).toBe('19:00 · 마끼몬스타 (Makkimonseuta)');
  });

  it('한국어는 그대로', () => {
    const rows = buildTripPassDetails({ ...base, language: 'ko', nameEnByPlaceId: { a: 'Gwangalli Beach' } });
    expect(rows.find((row) => row.key === '첫 일정')?.value).toBe('09:01 · 광안리해수욕장');
  });
});

// 화면이 이 규칙을 «쓰는지» — 되돌려도 화면은 그려지고 이름만 한국어로 돌아간다.
describe('화면이 이 규칙을 쓴다', () => {
  const { readFileSync } = require('fs');
  const { join } = require('path');
  const read = (...p: string[]) => readFileSync(join(__dirname, '..', '..', ...p), 'utf8') as string;

  it('🔴 폰 여행 화면 — 지금 카드·코스 줄·일정 카드', () => {
    const page = read('trip', 'page', 'TripPageMobile.tsx');
    expect(page).toContain("'Heading to %s', nameOf(currentStop)");
    expect(page).toContain("'Next: %s', nameOf(currentStop)");
    expect(page).toContain('items.map((item) => nameOf(item)).join');
    expect(page).toContain('<StopName');
  });

  it('🔴 넓은 여행 화면 — 카드', () => {
    expect(read('trip', 'page', 'TripPageDesktop.tsx')).toContain('<StopName');
  });

  it('🔴 생성 완료 승차권 — 영어 이름을 넘긴다', () => {
    const screen = readFileSync(join(__dirname, '..', '..', '..', 'app', '(plan)', 'generating.tsx'), 'utf8') as string;
    expect(screen).toContain('nameEnByPlaceId');
  });
});
