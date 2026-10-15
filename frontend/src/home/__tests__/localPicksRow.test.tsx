// 홈 로컬 탐색 줄의 맛집·카페 카드 (S15P21E201-2059) — 이름표 · 누르면 가는 곳 · 불러오는 동안의 빈 칸.
//
// 🔴 이 시험이 지키는 것: 새로 더한 맛집·카페 카드가 다른 카드와 «같은 규칙»으로 눌린다 —
//    사진을 누르면 그 장소의 상세, 「더 보기」를 누르면 로컬 탐색의 그 갈래(?facet=LOCAL_RESTAURANT · LOCAL_CAFE).
//    그리고 불러오는 동안의 빈 칸 수가 갈래 수와 같다(전에는 숫자 6 이 따로 적혀 있어 갈래를 더하면 어긋났다).
import { fireEvent, render, screen } from '@testing-library/react-native';

import { LocalPicksRow } from '@/home/HomeBlocks';
import { LOCAL_PICK_ORDER } from '@/home/homeCuration';
import type { HomeLocalPick } from '@/home/useHomeData';

const mockPush = jest.fn();
let mockLang: 'ko' | 'en' = 'ko';
jest.mock('expo-router', () => ({ useRouter: () => ({ push: mockPush }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: null }) }));
jest.mock('@/i18n', () => ({
  useI18n: () => ({ tx: (ko: string, en: string) => (mockLang === 'ko' ? ko : en), language: mockLang, locale: mockLang === 'ko' ? 'ko-KR' : 'en-US' }),
}));

const RESTAURANT_ID = 'fe2f8382-a076-3173-8605-777a4c500702';
const CAFE_ID = '5416ddfb-0d2b-3eac-aaf6-d965f59add30';

const place = (placeId: string, nameKo: string, address: string): HomeLocalPick['place'] => ({
  placeId, nameKo, nameEn: null, address, photoUrl: 'https://example.test/photo.jpg', photoSource: '출처 : 시험', photoSubject: null, photoLicense: null,
});
const RESTAURANT: HomeLocalPick = { key: 'LOCAL_RESTAURANT', place: place(RESTAURANT_ID, '수변최고돼지국밥', '부산광역시 수영구 광안해변로370번길 9-32'), href: '/explore?facet=LOCAL_RESTAURANT' };
const CAFE: HomeLocalPick = { key: 'LOCAL_CAFE', place: place(CAFE_ID, '모모스커피 부산본점', '부산 금정구 오시게로 20 (부곡동)'), href: '/explore?facet=LOCAL_CAFE' };
const MARKET: HomeLocalPick = { key: 'TRADITIONAL_MARKET', place: place('f548797c-361a-3b3a-8210-616be343a29d', '국제시장', '부산 중구 신창동'), href: '/explore?facet=TRADITIONAL_MARKET' };

const row = (picks: HomeLocalPick[] | null) => render(
  <LocalPicksRow picks={picks} width={390} cardWidth={160} gutter={24} arrows={false} likedIds={new Set<string>()} onToggleLike={jest.fn()} />,
);

beforeEach(() => { mockPush.mockClear(); mockLang = 'ko'; });

describe('홈 로컬 탐색 줄 — 맛집·카페 카드', () => {
  it('맛집·카페 카드가 갈래 이름표와 서버가 준 장소 이름·주소와 함께 맨 앞에 선다', () => {
    row([RESTAURANT, CAFE, MARKET]);
    expect(screen.getByText('맛집')).toBeTruthy();
    expect(screen.getByText('카페')).toBeTruthy();
    expect(screen.getByText('수변최고돼지국밥')).toBeTruthy();
    expect(screen.getByText('모모스커피 부산본점')).toBeTruthy();
    expect(screen.getByText('부산 금정구 오시게로 20 (부곡동)')).toBeTruthy();
    const opens = screen.getAllByLabelText(/상세 보기$/).map((node) => node.props.accessibilityLabel);
    expect(opens).toEqual(['맛집 · 수변최고돼지국밥 상세 보기', '카페 · 모모스커피 부산본점 상세 보기', '전통시장 · 국제시장 상세 보기']);
  });

  it('🔴 사진을 누르면 그 장소의 상세로, 「더 보기」를 누르면 로컬 탐색의 그 갈래로 간다 — 다른 카드와 같은 규칙', () => {
    row([RESTAURANT, CAFE, MARKET]);
    fireEvent.press(screen.getByLabelText('맛집 · 수변최고돼지국밥 상세 보기'));
    expect(mockPush).toHaveBeenLastCalledWith(`/place/${RESTAURANT_ID}`);
    fireEvent.press(screen.getByLabelText('카페 · 모모스커피 부산본점 상세 보기'));
    expect(mockPush).toHaveBeenLastCalledWith(`/place/${CAFE_ID}`);
    fireEvent.press(screen.getByLabelText('맛집 · 더 보기'));
    expect(mockPush).toHaveBeenLastCalledWith('/explore?facet=LOCAL_RESTAURANT');
    fireEvent.press(screen.getByLabelText('카페 · 더 보기'));
    expect(mockPush).toHaveBeenLastCalledWith('/explore?facet=LOCAL_CAFE');
    // 기존 카드의 길은 그대로다.
    fireEvent.press(screen.getByLabelText('전통시장 · 더 보기'));
    expect(mockPush).toHaveBeenLastCalledWith('/explore?facet=TRADITIONAL_MARKET');
  });

  it('영어 화면에서는 이름표가 Restaurants · Cafes 이다', () => {
    mockLang = 'en';
    row([RESTAURANT, CAFE]);
    expect(screen.getByText('Restaurants')).toBeTruthy();
    expect(screen.getByText('Cafes')).toBeTruthy();
    fireEvent.press(screen.getByLabelText('Cafes · More'));
    expect(mockPush).toHaveBeenLastCalledWith('/explore?facet=LOCAL_CAFE');
  });

  it('한 쪽이 빠져도(서버 조회 실패) 남은 카드는 그대로 서고, 줄이 깨지지 않는다', () => {
    row([CAFE, MARKET]);
    expect(screen.queryByText('맛집')).toBeNull();
    expect(screen.getByText('카페')).toBeTruthy();
    expect(screen.getByText('전통시장')).toBeTruthy();
  });

  it('불러오는 동안은 갈래 수만큼 빈 칸을 둔다', () => {
    row(null);
    expect(screen.getAllByTestId('local-pick-skeleton')).toHaveLength(LOCAL_PICK_ORDER.length);
    expect(LOCAL_PICK_ORDER).toHaveLength(8);
  });

  it('한 곳도 못 받았으면 줄을 통째로 접는다', () => {
    expect(row([]).toJSON()).toBeNull();
  });
});
