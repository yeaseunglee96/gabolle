// 기록 속 코스 링크 · 코스 카드 — S15P21E201-1593.
//
// 🔴 이 시험이 지키는 것:
//    ① 우리 웹 주소의 /s/<토큰> 만 코스로 본다 — 다른 사이트의 /s/ 는 그냥 글자다.
//    ② 코스 카드로 그린 링크는 본문 글자에서 뺀다 — 같은 것을 두 번 그리지 않는다.
//    ③ 만료된 링크는 「만료된 코스」이고 누를 수 없다. 살아 있는 링크는 이름·일수·장소 수, 누르면 공유 화면.
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react-native';

import { APP_WEB_BASE_URL } from '@/api/client';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { appendCourseLink, findCourseLink, withoutCourseLink } from '@/social/courseLink';
import { CourseLinkCard } from '@/social/CourseLinkCard';

const mockPush = jest.fn();
const mockGetShared = jest.fn();
jest.mock('expo-router', () => ({ useRouter: () => ({ push: mockPush }) }));
jest.mock('@/share/sharedItinerary', () => ({ ...jest.requireActual('@/share/sharedItinerary'), getSharedItinerary: (...args: unknown[]) => mockGetShared(...args) }));

const url = `${APP_WEB_BASE_URL}/s/abc_123-XYZ`;
const WAIT = { timeout: 5000 };
// 🔴 기다림(WAIT 5초)보다 시험 제한 시간이 길어야 기다림이 먹힌다. jest 기본 제한 시간도 5초라, 부하가 걸리면 기다림이
//    끝나기 전에 시험이 먼저 끝났다(「Exceeded timeout of 5000 ms」 — S15P21E201-1665). 파일 전체에 넉넉히 준다.
jest.setTimeout(20000);
const Providers = ({ children }: { children: ReactNode }) => (
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })}>
    <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>
  </QueryClientProvider>
);

describe('본문 속 코스 링크', () => {
  it('우리 웹 주소의 /s/<토큰> 을 찾는다', () => {
    expect(findCourseLink(`광안리 다녀왔어요\n\n${url}`)).toEqual({ token: 'abc_123-XYZ', url });
  });

  it('🔴 다른 사이트의 /s/ 는 코스가 아니다', () => {
    expect(findCourseLink('https://example.com/s/abc123 여기 좋아요')).toBeNull();
    expect(findCourseLink('링크 없음')).toBeNull();
  });

  it('본문 끝에 빈 줄 하나를 두고 붙인다', () => {
    expect(appendCourseLink('  광안리 다녀왔어요  ', url)).toBe(`광안리 다녀왔어요\n\n${url}`);
  });

  it('🔴 코스 카드로 그리는 링크는 본문 글자에서 뺀다 — 링크만 있던 줄은 줄째', () => {
    const body = appendCourseLink('광안리 다녀왔어요', url);
    expect(withoutCourseLink(body, findCourseLink(body))).toBe('광안리 다녀왔어요');
    expect(withoutCourseLink('링크 없음', null)).toBe('링크 없음');
  });
});

describe('코스 카드', () => {
  beforeEach(() => { mockPush.mockReset(); mockGetShared.mockReset(); });

  it('여행 이름 · 일수 · 장소 수를 그리고, 누르면 공유 화면으로 간다', async () => {
    mockGetShared.mockResolvedValue({ state: 'success', data: {
      title: '광안리 야경 여행', startDate: '2026-10-03', finishDate: '2026-10-04', version: 1, expiresAt: '2026-11-01T00:00:00Z', notShared: [],
      days: [
        { date: '2026-10-03', items: [{ sequence: 1, placeName: 'a', category: null, startsAt: null, endsAt: null, stayMinutes: null }, { sequence: 2, placeName: 'b', category: null, startsAt: null, endsAt: null, stayMinutes: null }] },
        { date: '2026-10-04', items: [{ sequence: 1, placeName: 'c', category: null, startsAt: null, endsAt: null, stayMinutes: null }] },
      ],
    } });
    render(<CourseLinkCard token="abc_123-XYZ" />, { wrapper: Providers });

    expect(await screen.findByText('광안리 야경 여행', {}, WAIT)).toBeTruthy();
    expect(screen.getByText('2일 · 장소 3곳')).toBeTruthy();
    fireEvent.press(screen.getByLabelText('광안리 야경 여행 코스 보기'));
    expect(mockPush).toHaveBeenCalledWith('/s/abc_123-XYZ');
  });

  it('🔴 만료된 링크는 「만료된 코스」 — 누를 곳이 없다', async () => {
    mockGetShared.mockResolvedValue({ state: 'expired' });
    render(<CourseLinkCard token="old" />, { wrapper: Providers });

    expect(await screen.findByText('만료된 코스', {}, WAIT)).toBeTruthy();
    expect(screen.queryByRole('link')).toBeNull();
  });
});
