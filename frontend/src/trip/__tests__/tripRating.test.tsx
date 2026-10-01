// 여행 별점 — S15P21E201-1908. 끝난 여행에 별 다섯 개. 누르면 저장, 같은 별을 다시 누르면 지운다.
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';

import { ApiClientError } from '@/api/client';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import type { TripSummaryDto } from '@/trip/trips';

const mockLoad = jest.fn();
const mockSave = jest.fn();
jest.mock('@/trip/tripRating', () => ({
  ...jest.requireActual('@/trip/tripRating'),
  loadTripRating: (...args: unknown[]) => mockLoad(...args),
  saveTripRating: (...args: unknown[]) => mockSave(...args),
}));

import { nextScore } from '@/trip/tripRating';
import { TripRatingStars } from '@/trip/TripRatingStars';
import { ended } from '../../../app/(tabs)/trips';

const Providers = ({ children }: { children: ReactNode }) => (
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })}>
    <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>
  </QueryClientProvider>
);

beforeEach(() => { mockLoad.mockReset(); mockSave.mockReset(); });

describe('여행 별점', () => {
  it('같은 별을 다시 누르면 지운다(null), 다른 별은 그 점수', () => {
    expect(nextScore(null, 3)).toBe(3);
    expect(nextScore(3, 5)).toBe(5);
    expect(nextScore(4, 4)).toBeNull();
  });

  it('🔴 끝난 여행만 — 일정을 못 만든 PLANNING 과 다가오는 여행은 아니다', () => {
    const now = new Date('2026-10-01T12:00:00');
    const base = { startDate: '2026-09-20', endDate: '2026-09-21', status: 'READY' } as TripSummaryDto;
    expect(ended(base, now)).toBe(true);
    expect(ended({ ...base, status: 'PLANNING' } as TripSummaryDto, now)).toBe(false);
    expect(ended({ ...base, startDate: '2026-10-10', endDate: '2026-10-11' } as TripSummaryDto, now)).toBe(false);
  });

  it('별을 누르면 저장하고, 같은 별을 다시 누르면 지운다', async () => {
    mockLoad.mockResolvedValue({ myScore: null, average: null, count: 0 });
    mockSave.mockImplementation((_trip: string, score: number | null) => Promise.resolve({ myScore: score, average: score, count: score == null ? 0 : 1 }));
    render(<TripRatingStars tripId="t1" accessToken="token" />, { wrapper: Providers });

    fireEvent.press(await screen.findByLabelText('별 4개'));
    await waitFor(() => expect(mockSave).toHaveBeenCalledWith('t1', 4, 'token'));
    fireEvent.press(await screen.findByLabelText('별 4개 — 다시 누르면 지워요'));
    await waitFor(() => expect(mockSave).toHaveBeenLastCalledWith('t1', null, 'token'));
    expect(await screen.findByText('이 여행은 어땠나요?')).toBeTruthy();
  });

  it('🔴 서버에 경로가 없으면(404) 줄을 숨긴다', async () => {
    mockLoad.mockRejectedValue(new ApiClientError('없음', 'NOT_FOUND', 404));
    render(<TripRatingStars tripId="t1" accessToken="token" />, { wrapper: Providers });
    await waitFor(() => expect(mockLoad).toHaveBeenCalled());
    await waitFor(() => expect(screen.queryByLabelText('별 1개')).toBeNull());
  });

  it('저장이 실패하면 원래 점수로 되돌리고 알린다', async () => {
    mockLoad.mockResolvedValue({ myScore: 2, average: 2, count: 1 });
    mockSave.mockRejectedValue(new Error('끊김'));
    render(<TripRatingStars tripId="t1" accessToken="token" />, { wrapper: Providers });
    fireEvent.press(await screen.findByLabelText('별 5개'));
    expect(await screen.findByText('별점을 저장하지 못했어요. 다시 시도해 주세요.')).toBeTruthy();
    expect(screen.getByLabelText('별 2개 — 다시 누르면 지워요')).toBeTruthy();
  });
});
