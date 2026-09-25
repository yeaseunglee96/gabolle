// 마이페이지가 못 불러왔을 때 — S15P21E201-1681.
//
// 🔴 이 시험이 지키는 것(조율 세션 결정):
//    E. 「기록을 불러오지 못했어요.」 한 줄만 있고 다시 할 길이 없었다 — 「다시 시도」와 위 카드와의 간격.
//    F. 취향을 불러오는 함수가 서버 오류를 빈 답으로 삼켜서, 못 읽었을 때도 「처음에 건너뛰셨어요」라고 했다.
//       서버는 한 번도 저장 안 한 사람에게 404 가 아니라 UNKNOWN 으로 200 을 준다(SpendProfileResponse) — 오류는 진짜 실패다.
//       실패면 「불러오지 못했어요 · 다시 시도」, 정말 건너뛴(SKIPPED) 경우만 「처음에 건너뛰셨어요」.
import type { ReactNode } from 'react';
import { StyleSheet } from 'react-native';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { ApiClientError } from '@/api/client';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn() }));
const { apiRequest } = jest.requireMock('@/api/client') as { apiRequest: jest.Mock };
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', user: { userId: 'me' } }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', desktop: false, width: 390, height: 844, isLandscape: false }) }));

import { RecordsLoadFailed } from '@/me/RecordsLoadFailed';
import { PreferencesBody } from '@/me/panels/PreferencesBody';
import { loadAccountPreferences } from '@/preferences/accountPreferences';

jest.setTimeout(20000);

const tx = (ko: string) => ko;

/** 두 요청에 무엇을 돌려줄지 — 'fail' 이면 서버 오류. */
function answer(spend: { status: string; value: string | null } | 'fail', taste: { answers: unknown[] } | 'fail' = { answers: [] }) {
  apiRequest.mockImplementation(async (path: string) => {
    const reply = path.includes('/spend') ? spend : taste;
    if (reply === 'fail') throw new ApiClientError('요청을 처리하지 못했어요.', 'REQUEST_FAILED', 500);
    return reply;
  });
}

beforeEach(() => apiRequest.mockReset());

describe('E. 기록을 못 불러왔을 때', () => {
  it('🔴 「다시 시도」가 있고, 누르면 다시 부른다', () => {
    const retry = jest.fn();
    render(<RecordsLoadFailed onRetry={retry} tx={tx} />);
    expect(screen.getByText('기록을 불러오지 못했어요.')).toBeTruthy();
    fireEvent.press(screen.getByText('다시 시도'));
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('위 카드에 붙지 않는다 — 간격이 있다', () => {
    const view = render(<RecordsLoadFailed onRetry={jest.fn()} tx={tx} />);
    const root = view.toJSON() as unknown as { props: { style?: unknown } };
    const top = StyleSheet.flatten(root.props.style as never) as { marginTop?: number } | undefined;
    expect(top?.marginTop ?? 0).toBeGreaterThan(0);
  });
});

describe('F. 취향을 불러오는 함수', () => {
  it('🔴 서버 오류를 빈 답으로 삼키지 않는다 — 실패라고 표시한다', async () => {
    answer('fail');
    await expect(loadAccountPreferences('token')).resolves.toMatchObject({ spend: {}, taste: {}, loadFailed: true });
  });

  it('건너뛴(SKIPPED) 것과 한 번도 안 답한(UNKNOWN) 것을 가른다', async () => {
    answer({ status: 'SKIPPED', value: null });
    const skipped = await loadAccountPreferences('token');
    expect(skipped.spendSkipped).toBe(true);
    expect(skipped.loadFailed).toBeFalsy();

    answer({ status: 'UNKNOWN', value: null });
    const never = await loadAccountPreferences('token');
    expect(never.spendSkipped).toBeFalsy();
    expect(never.loadFailed).toBeFalsy();
  });
});

describe('F. 여행 취향 창', () => {
  function Providers({ children }: { children: ReactNode }) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } });
    return <QueryClientProvider client={client}><OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider></QueryClientProvider>;
  }
  const open = async (text: string) => {
    render(<PreferencesBody />, { wrapper: Providers });
    // 첫 화면은 부품을 처음 불러오느라 몇 초 걸린다 — 기다림보다 시험 제한 시간을 길게 둔다(S15P21E201-1665 규칙).
    await waitFor(() => expect(screen.getByText(text)).toBeTruthy(), { timeout: 10000 });
  };

  it('🔴 못 불러왔으면 「불러오지 못했어요 · 다시 시도」 — 「건너뛰셨어요」가 아니다', async () => {
    answer('fail');
    await open('취향을 불러오지 못했어요');
    expect(screen.getByText('다시 시도')).toBeTruthy();
    expect(screen.queryByText(/건너뛰셨어요/)).toBeNull();
    expect(screen.queryByText('아직 기억된 취향이 없어요')).toBeNull();
    // 모르는데 줄마다 「답 안 함」이라고 하지 않는다 — 문항 줄을 숨긴다(조율 세션 결정).
    expect(screen.queryByText('답 안 함')).toBeNull();
    expect(screen.queryByText('오는 교통')).toBeNull();
  });

  it('정말 건너뛰었으면 「처음에 건너뛰셨어요」', async () => {
    answer({ status: 'SKIPPED', value: null });
    await open('아직 기억된 취향이 없어요');
    expect(screen.getByText(/처음에 건너뛰셨어요/)).toBeTruthy();
  });

  it('한 번도 답한 적 없으면 건너뛰었다고 하지 않는다', async () => {
    answer({ status: 'UNKNOWN', value: null });
    await open('아직 기억된 취향이 없어요');
    expect(screen.queryByText(/건너뛰셨어요/)).toBeNull();
    expect(screen.getByText('지금 답하면 여행을 만들 때 미리 채워 드려요.')).toBeTruthy();
  });
});
