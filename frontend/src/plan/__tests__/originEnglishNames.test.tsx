// 영어 화면의 출발지·숙소 검색 — S15P21E201-1781(고지혁 QA).
//
// 🔴 이 시험이 지키는 것: 출발지·숙소는 카카오(한국어 이름만)를, 꼭 갈 곳은 우리 장소 목록(name_en 있음)을 불러
//    영어 화면에서 출발지·숙소만 한국어였다. 아래 값은 2026-09-27 운영 서버가 「Haeundae」에 돌려준 그대로다.
import type { ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { toCreateTripPayload } from '@/api/tripApi';
import { PlanStartBar } from '@/home/PlanStartBar';
import { EMPTY_START_BAR, START_BAR_PRESETS, englishNameOf, placeEnglishOf, startBarChips, startBarFromDraft, summarizeStartBar } from '@/home/startBarValue';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { EMPTY_PLAN } from '@/plan/PlanProvider';
import { attachEnglishNames, MAJOR_BUSAN_ORIGINS, type OriginCandidate } from '@/plan/origins';

const kakao: OriginCandidate[] = [
  { name: '해운대해수욕장', address: '부산 해운대구 우동', lat: 35.1585232170784, lng: 129.159854668484, externalId: '7913306', source: 'KAKAO_LOCAL' },
  { name: '해운대블루라인파크 미포정거장', address: '부산 해운대구 달맞이길62번길 13', lat: 35.15815259880406, lng: 129.17281473802996, externalId: '188403018', source: 'KAKAO_LOCAL' },
];
const places = [
  { nameKo: 'Haeundae accommodation', nameEn: null, lat: 35.1602484, lng: 129.1543148 },
  { nameKo: '해운대해수욕장', nameEn: 'Haeundae Beach', lat: 35.1585232170784, lng: 129.159854668484 },
];

jest.mock('@/plan/origins', () => {
  const actual = jest.requireActual('@/plan/origins');
  return { ...actual, searchOrigins: jest.fn(async () => ({ state: 'success', items: kakao, degraded: false })) };
});
jest.mock('@/discovery/places', () => {
  const actual = jest.requireActual('@/discovery/places');
  return { ...actual, searchPlacesByName: jest.fn(async () => places) };
});

describe('카카오 결과에 영어 이름 붙이기', () => {
  it('🔴 한국어 이름이 같은 우리 장소가 있으면 그 영어 이름을 붙인다', () => {
    const [beach, stop] = attachEnglishNames(kakao, places);
    expect(beach.nameEn).toBe('Haeundae Beach');
    expect(beach.name).toBe('해운대해수욕장'); // 서버로 가는 이름은 그대로 한국어
    expect(stop.nameEn).toBeUndefined(); // 못 찾으면 지어내지 않는다
  });

  it('이름이 한쪽을 품어도 멀면(80m 넘게) 같은 장소로 안 본다', () => {
    const [far] = attachEnglishNames([{ ...kakao[0], name: '해운대' }], [{ nameKo: '해운대해수욕장', nameEn: 'Haeundae Beach', lat: 35.17, lng: 129.2 }]);
    expect(far.nameEn).toBeUndefined();
    const [near] = attachEnglishNames([{ ...kakao[0], name: '해운대해수욕장 입구' }], [places[1]]);
    expect(near.nameEn).toBe('Haeundae Beach');
  });
});

const Providers = ({ children }: { children: ReactNode }) => (
  <SafeAreaProvider initialMetrics={{ frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } }}>
    <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>
  </SafeAreaProvider>
);

it('🔴 영어 화면의 출발지 검색은 영어 이름을 먼저, 없으면 읽는 법을 붙여 보인다', async () => {
  await AsyncStorage.setItem('gabolle:onboarding-preferences', JSON.stringify({ language: 'en', mobility: 'none', hasEnteredApp: true }));
  const view = render(<PlanStartBar wide={false} sheet accessToken={null} onSubmit={jest.fn()} onClose={jest.fn()} today={new Date('2026-09-24T09:00:00')} initialSection="origin" initialValue={EMPTY_START_BAR} />, { wrapper: Providers });
  await waitFor(() => expect(screen.getByLabelText('Search starting point')).toBeTruthy());
  jest.useFakeTimers();
  fireEvent.changeText(screen.getByLabelText('Search starting point'), 'Haeundae');
  await act(async () => { jest.advanceTimersByTime(1000); });
  jest.useRealTimers();
  await waitFor(() => expect(screen.getByText('Haeundae Beach (해운대해수욕장)')).toBeTruthy());
  expect(screen.getByText(/^해운대블루라인파크 미포정거장 \(/)).toBeTruthy();
  view.unmount();
  await AsyncStorage.clear();
});

// ── 추천 목록과 고른 뒤의 이름 — S15P21E201-1795(고지혁 QA) ─────────────────────────
//
// 🔴 이 시험이 지키는 것: 1781 은 «검색 결과»에만 영어 이름을 붙였다. 검색 전에 보이는 추천 목록은 앱에 박힌 상수라
//    영어 화면에 「부산역 (Busanyeok)」이 나왔고, 검색해서 「Busan Station」을 골라도 알약은 「From 부산역」이었다.
describe('추천 출발지·숙소 지역과 고른 뒤의 이름', () => {
  const renderBar = async (language: 'ko' | 'en' | 'ja', section: 'origin' | 'lodging') => {
    await AsyncStorage.setItem('gabolle:onboarding-preferences', JSON.stringify({ language, mobility: 'none', hasEnteredApp: true }));
    const view = render(<PlanStartBar wide={false} sheet accessToken={null} onSubmit={jest.fn()} onClose={jest.fn()} today={new Date('2026-09-24T09:00:00')} initialSection={section} initialValue={EMPTY_START_BAR} />, { wrapper: Providers });
    return view;
  };
  // 가짜 시계로 돈다 — 패널이 내려오는 움직임(requestAnimationFrame)이 시험 파일이 끝난 뒤에 불려
  // 「Jest 환경이 이미 내려갔다」 오류로 종료 코드를 1로 만들었다. 가짜 시계의 남은 호출은 버려진다.
  beforeEach(() => { jest.useFakeTimers(); });
  afterEach(async () => { jest.useRealTimers(); await AsyncStorage.clear(); });

  it('🔴 영어 화면의 추천 출발지는 영어 이름을 먼저 보인다 — DB 의 place.name_en 과 같은 철자', async () => {
    const view = await renderBar('en', 'origin');
    await waitFor(() => expect(screen.getByText('Busan Station (부산역)')).toBeTruthy());
    expect(screen.getByText('Haeundae Beach (해운대해수욕장)')).toBeTruthy();
    expect(screen.getByText('Gwangalli Beach (광안리해수욕장)')).toBeTruthy();
    expect(screen.queryByText(/Busanyeok/)).toBeNull();
    view.unmount();
  });

  it('한국어 화면의 추천 출발지는 예전 그대로 한국어 이름만', async () => {
    const view = await renderBar('ko', 'origin');
    await waitFor(() => expect(screen.getByText('부산역')).toBeTruthy());
    expect(screen.queryByText(/Busan Station/)).toBeNull();
    view.unmount();
  });

  it('🔴 추천 숙소 지역은 이름과 설명 줄이 둘 다 그 언어로 나온다', async () => {
    const view = await renderBar('en', 'lodging');
    await waitFor(() => expect(screen.getByText('Haeundae (해운대)')).toBeTruthy());
    expect(screen.getByText('Beachfront hotels and resorts')).toBeTruthy();
    expect(screen.queryByText('바다 앞 호텔·리조트가 모여 있어요')).toBeNull();
    view.unmount();
  });

  it('일본어 화면의 설명 줄은 번역표를 거친다 — 영어로 떨어지지 않는다', async () => {
    const view = await renderBar('ja', 'lodging');
    await waitFor(() => expect(screen.getByText('海沿いにホテル・リゾートが集まっています')).toBeTruthy());
    view.unmount();
  });

  it('한국어 화면의 설명 줄은 예전 문장 그대로', async () => {
    const view = await renderBar('ko', 'lodging');
    await waitFor(() => expect(screen.getByText('바다 앞 호텔·리조트가 모여 있어요')).toBeTruthy());
    view.unmount();
  });
});

describe('고른 출발지·숙소의 이름 — 알약과 요약', () => {
  const en = (_korean: string, english: string) => english;
  const ko = (korean: string) => korean;
  const busan = MAJOR_BUSAN_ORIGINS.find((origin) => origin.externalId === 'major-busan-station')!;
  const picked = { ...EMPTY_START_BAR, origin: busan.name, originEnglish: placeEnglishOf(busan), originLat: busan.lat, originLng: busan.lng, startDate: '2026-10-03', endDate: '2026-10-03' };

  it('🔴 영어 화면의 알약은 「From Busan Station (부산역)」— 「From 부산역」이 아니다', () => {
    expect(startBarChips(picked, en, 'en')[0]).toBe('From Busan Station (부산역)');
  });

  it('🔴 한 줄 요약(알약)은 영어 이름만 짧게 — 괄호까지 넣으면 폰 폭에서 날짜가 잘린다', () => {
    const withLodging = { ...picked, lodging: '해운대', lodgingEnglish: { name: '해운대', nameEn: 'Haeundae' } };
    expect(summarizeStartBar(withLodging, en, 'en').startsWith('Busan Station · Haeundae · ')).toBe(true);
    // 영어 이름이 없으면 예전처럼 한국어 이름 그대로 — 읽는 법도 안 붙인다.
    expect(summarizeStartBar({ ...withLodging, lodgingEnglish: null }, en, 'en').startsWith('Busan Station · 해운대 · ')).toBe(true);
    // 한국어 화면은 예전 그대로.
    expect(summarizeStartBar(withLodging, ko, 'ko').startsWith('부산역 · 해운대 · ')).toBe(true);
  });

  it('한국어 화면의 알약은 예전 그대로다', () => {
    expect(startBarChips(picked, ko, 'ko')[0]).toBe('부산역 출발');
    // 언어를 안 넘기는 예전 호출도 그대로다.
    expect(startBarChips(picked, ko)[0]).toBe('부산역 출발');
  });

  it('영어 이름이 없는 곳은 다른 화면과 같은 규칙 — 한글 옆에 읽는 법', () => {
    const hotel = { ...EMPTY_START_BAR, lodging: '하운드호텔 부산역점', lodgingEnglish: placeEnglishOf({ name: '하운드호텔 부산역점', nameEn: null }), origin: '부산역' };
    expect(hotel.lodgingEnglish).toBeNull(); // 지어내지 않는다
    expect(startBarChips(hotel, en, 'en')[1]).toMatch(/^Staying in 하운드호텔 부산역점 \(.+\)$/);
  });

  it('🔴 한국어 이름이 바뀌면 옛 영어 이름은 쓰지 않는다 — 다른 곳의 이름을 보이지 않게', () => {
    const moved = { ...picked, origin: '서면역' };
    expect(englishNameOf(moved.origin, moved.originEnglish)).toBeNull();
    expect(startBarChips(moved, en, 'en')[0]).not.toContain('Busan Station');
  });

  it('부산역 출발 프리셋도 영어 이름을 든다', () => {
    const applied = START_BAR_PRESETS.find((preset) => preset.id === 'from-station')!.apply(new Date('2026-09-24T09:00:00'));
    expect(startBarChips({ ...EMPTY_START_BAR, ...applied }, en, 'en')[0]).toBe('From Busan Station (부산역)');
  });

  it('「날짜 정하기」로 다녀와도 영어 이름이 남는다', () => {
    expect(startBarFromDraft(picked).originEnglish).toEqual({ name: '부산역', nameEn: 'Busan Station' });
  });

  it('🔴 서버로 가는 요청은 한 글자도 안 바뀐다 — 영어 이름은 화면용이다', () => {
    const withoutEnglish = toCreateTripPayload({ ...EMPTY_PLAN, origin: '부산역', originLat: busan.lat, originLng: busan.lng });
    const withEnglish = toCreateTripPayload({ ...EMPTY_PLAN, origin: '부산역', originLat: busan.lat, originLng: busan.lng, originEnglish: { name: '부산역', nameEn: 'Busan Station' } });
    expect(JSON.stringify(withEnglish)).toBe(JSON.stringify(withoutEnglish));
    expect(JSON.stringify(withEnglish)).not.toContain('Busan Station');
  });
});
