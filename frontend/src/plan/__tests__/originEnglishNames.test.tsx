// 영어 화면의 출발지·숙소 검색 — S15P21E201-1781(고지혁 QA).
//
// 🔴 이 시험이 지키는 것: 출발지·숙소는 카카오(한국어 이름만)를, 꼭 갈 곳은 우리 장소 목록(name_en 있음)을 불러
//    영어 화면에서 출발지·숙소만 한국어였다. 아래 값은 2026-09-27 운영 서버가 「Haeundae」에 돌려준 그대로다.
import type { ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { PlanStartBar } from '@/home/PlanStartBar';
import { EMPTY_START_BAR } from '@/home/startBarValue';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { attachEnglishNames, type OriginCandidate } from '@/plan/origins';

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
