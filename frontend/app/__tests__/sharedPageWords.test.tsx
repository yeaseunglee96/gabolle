// 공유 링크 화면의 말 — S15P21E201-1677.
//
// 🔴 이 시험이 지키는 것: 로그인 안 한 사람이 처음 보는 화면이다.
//    ① 장소 분류가 코드 그대로(SEA_BEACH·CULTURE_TEMPLE) 나왔다 — 서버는 장소의 분류 코드를 그대로 싣는다.
//       이름표로 바꾸고, 모르는 코드는 안 보인다. 이름표에 없던 서버 코드(CULTURE_TEMPLE 등)도 채운다.
//    ② 「인원는(은)」 — 받침에 맞는 조사를 고른다.
//    ③ 날짜가 「2026-10-03」 기계 모양이었다 — 내 여행 목록과 같은 「10월 3일 (토)」.
import { render, screen, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { koreanTopic } from '@/i18n/korean';
import type { SharedItineraryDto } from '@/share/sharedItinerary';

jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn() }),
  useLocalSearchParams: () => ({ token: 'abc123' }),
}));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 0, left: 0, right: 0, bottom: 0 }),
}));
jest.mock('@/plan/PlanProvider', () => ({ usePlan: () => ({ update: jest.fn(), clear: jest.fn(async () => {}) }) }));
jest.mock('@/share/sharedItinerary', () => ({ getSharedItinerary: jest.fn() }));
const { getSharedItinerary } = jest.requireMock('@/share/sharedItinerary') as { getSharedItinerary: jest.Mock };

import SharedItinerary from '../s/[token]';

const item = (sequence: number, placeName: string, category: string | null) => ({
  sequence, placeName, category, startsAt: `2026-10-03T${String(9 + sequence).padStart(2, '0')}:00:00+09:00`, endsAt: null, stayMinutes: 60,
});
const DATA = {
  title: '부산 가을 바다 2박 3일', startDate: '2026-10-03', finishDate: '2026-10-05', expiresAt: '2026-10-25T00:00:00+09:00',
  notShared: ['origin', 'contact', 'budget', 'partySize'],
  days: [{ date: '2026-10-03', items: [item(1, '해운대해수욕장', 'SEA_BEACH'), item(2, '감천문화마을', 'CULTURE_TEMPLE'), item(3, '어딘가', 'SOMETHING_NEW')] }],
} as unknown as SharedItineraryDto;

jest.setTimeout(20000);

beforeEach(() => getSharedItinerary.mockResolvedValue({ state: 'success', data: DATA }));

async function open() {
  render(<OnboardingPreferencesProvider><SharedItinerary /></OnboardingPreferencesProvider>);
  // 첫 화면은 부품을 처음 불러오느라 몇 초 걸린다 — 기다림보다 시험 제한 시간을 길게 둔다(S15P21E201-1665 규칙).
  await waitFor(() => expect(screen.getByText('부산 가을 바다 2박 3일')).toBeTruthy(), { timeout: 10000 });
}

describe('공유 링크 화면의 말', () => {
  it('🔴 분류는 이름표로 — 코드를 그대로 안 보이고, 모르는 코드는 빼고 머무는 시간만', async () => {
    await open();
    expect(screen.getByText('바다 · 60분 머묾')).toBeTruthy();
    expect(screen.getByText('문화 · 60분 머묾')).toBeTruthy();
    expect(screen.queryByText(/SEA_BEACH|CULTURE_TEMPLE|SOMETHING_NEW/)).toBeNull();
    expect(screen.getAllByText('60분 머묾')).toHaveLength(1);
  });

  it('🔴 조사는 받침에 맞게 — 「인원은」', async () => {
    await open();
    expect(screen.getByText('출발지 · 연락처 · 예산 · 인원은 공유되지 않아요.')).toBeTruthy();
    expect(screen.queryByText(/는\(은\)/)).toBeNull();
  });

  it('날짜는 「10월 3일 (토)」 모양이다', async () => {
    await open();
    expect(screen.getByText('10월 3일 (토) – 10월 5일 (월)')).toBeTruthy();
    expect(screen.getByText('1일차 · 10월 3일 (토)')).toBeTruthy();
    expect(screen.queryByText(/2026-10-0/)).toBeNull();
  });
});

describe('주제 조사 「은」·「는」', () => {
  it('받침이 있으면 「은」, 없거나 한글이 아니면 「는」', () => {
    expect(koreanTopic('인원')).toBe('은');
    expect(koreanTopic('예산')).toBe('은');
    expect(koreanTopic('출발지')).toBe('는');
    expect(koreanTopic('GABOLLE')).toBe('는');
  });
});
