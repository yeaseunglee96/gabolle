// 여행 화면 창 안의 글쓰기 — S15P21E201-1760.
//
// 🔴 이 시험이 지키는 것(사용자: 「기록 남기기만 연속성이 유지가 안 된다」):
//    ① 창 안(panel)에서는 뒤로 가기·제목을 스스로 그리지 않는다 — 창이 이미 그린다. 두 번 나오지 않는다.
//    ② 다 올리면 창을 닫아 일정으로 돌아간다(onClose). 화면을 옮기지 않는다.
//    ③ 공개 전이면 곧장 닫지 않고 언제 공개되는지 창 안에서 말한 뒤, 「확인」으로 돌아간다.
//    ④ 여행을 단 글이다 — tripId 가 서버로 간다.
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

const mockCreateStory = jest.fn();
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', user: { userId: 'me' } }) }));
jest.mock('@/social/stories', () => ({ ...jest.requireActual('@/social/stories'), createStory: (...args: unknown[]) => mockCreateStory(...args) }));
jest.mock('@/onboarding/firstRun', () => ({ markChecklistStep: jest.fn() }));

import { StoryComposeForm } from '../StoryComposeForm';

const WAIT = { timeout: 5000 };
jest.setTimeout(20000);
const METRICS = { frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } };
const Providers = ({ children }: { children: ReactNode }) => (
  <SafeAreaProvider initialMetrics={METRICS}>
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })}>
      <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>
    </QueryClientProvider>
  </SafeAreaProvider>
);

const story = (published: boolean) => ({
  id: 's1', author: { id: 'me', displayName: '나' }, body: '광안리', images: [], visibility: 'PUBLIC',
  publishAt: '2026-09-27T00:00:00+09:00', createdAt: '2026-09-26T07:00:00Z', updatedAt: '2026-09-26T07:00:00Z',
  mine: true, published,
});

async function post(onClose: () => void) {
  render(<StoryComposeForm variant="panel" tripId="trip-1" onClose={onClose} />, { wrapper: Providers });
  fireEvent.changeText(await screen.findByTestId('compose-body', {}, WAIT), '광안리 다녀왔어요');
  fireEvent.press(screen.getByTestId('compose-submit'));
}

beforeEach(() => { mockCreateStory.mockReset(); });

describe('여행 화면 창 안의 글쓰기', () => {
  it('🔴 창이 그리는 뒤로 가기·제목을 다시 그리지 않는다', async () => {
    render(<StoryComposeForm variant="panel" tripId="trip-1" onClose={jest.fn()} />, { wrapper: Providers });
    expect(await screen.findByTestId('compose-body', {}, WAIT)).toBeTruthy();
    expect(screen.queryByLabelText('뒤로 가기')).toBeNull();
    expect(screen.queryByText('기록 남기기')).toBeNull();
    expect(screen.getByText('✓ 이 여행과 연결됨')).toBeTruthy();
  }, 20000);

  it('🔴 다 올리면 창을 닫아 일정으로 돌아간다 — 여행을 단 글이다', async () => {
    mockCreateStory.mockResolvedValue({ state: 'success', story: story(true) });
    const onClose = jest.fn();
    await post(onClose);
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
    expect(mockCreateStory.mock.calls[0][0]).toEqual(expect.objectContaining({ tripId: 'trip-1', body: '광안리 다녀왔어요' }));
    expect(screen.queryByTestId('compose-scheduled')).toBeNull();
  }, 20000);

  it('🔴 공개 전이면 창 안에서 공개 시점을 말하고, 「확인」으로 돌아간다', async () => {
    mockCreateStory.mockResolvedValue({ state: 'success', story: story(false) });
    const onClose = jest.fn();
    await post(onClose);
    expect(await screen.findByTestId('compose-scheduled', {}, WAIT)).toBeTruthy();
    expect(onClose).not.toHaveBeenCalled();
    fireEvent.press(screen.getByText('확인'));
    expect(onClose).toHaveBeenCalledTimes(1);
  }, 20000);

  it('올리지 못하면 창을 닫지 않고 까닭을 보여 준다 — 쓴 글이 사라지지 않는다', async () => {
    mockCreateStory.mockResolvedValue({ state: 'error', message: '잠시 뒤 다시 시도해 주세요.' });
    const onClose = jest.fn();
    await post(onClose);
    expect(await screen.findByRole('alert', {}, WAIT)).toBeTruthy();
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByDisplayValue('광안리 다녀왔어요')).toBeTruthy();
  }, 20000);
});
