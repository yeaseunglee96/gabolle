// 행동 기록 동의를 첫 하트 때 한 번 묻는다 — S15P21E201-1644.
//
// 🔴 이 시험이 지키는 것: 가입자 81명 중 1명만 동의해서 하트를 눌러도 추천에 반영되지 않았다. 기본값은 그대로(동의 안 함)
//    두고, 알맞은 순간에 한 번만 분명하게 묻는다. 「나중에」면 다시 조르지 않는다.
import type { ReactNode } from 'react';
import { Pressable, Text } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { act, fireEvent, render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { loadBehaviorConsent, setBehaviorConsent } from '@/personalization/behaviorConsent';
import { useBehaviorConsentAsk } from '@/personalization/consentAsk';

jest.mock('@/auth/authApi', () => ({
  ...jest.requireActual('@/auth/authApi'),
  updateMyConsents: jest.fn(async () => ({})),
  getMyConsents: jest.fn(async () => ({ behaviorPersonalizationEnabled: false })),
}));
const { updateMyConsents } = jest.requireMock('@/auth/authApi') as { updateMyConsents: jest.Mock };

const QUESTION = '하트·저장한 곳을 다음 추천에 반영할까요?';
function Heart({ token }: { token: string | null }) {
  const { askOnce, prompt } = useBehaviorConsentAsk(token);
  return <>{prompt}<Pressable onPress={() => void askOnce()}><Text>하트</Text></Pressable></>;
}
const wrapper = ({ children }: { children: ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;
const pressHeart = async () => { await act(async () => { fireEvent.press(screen.getByText('하트')); }); };

beforeEach(async () => {
  await AsyncStorage.clear();
  await setBehaviorConsent(false);
  updateMyConsents.mockClear();
});

describe('행동 기록 동의 — 첫 하트 때 한 번', () => {
  it('🔴 로그인했고 꺼져 있고 처음이면 묻는다', async () => {
    render(<Heart token="token" />, { wrapper });
    await pressHeart();
    expect(screen.getByText(QUESTION)).toBeTruthy();
  });

  it('🔴 「나중에」면 다시 묻지 않는다 — 기기에 한 번 물었음을 기억', async () => {
    const first = render(<Heart token="token" />, { wrapper });
    await pressHeart();
    await act(async () => { fireEvent.press(screen.getByText('나중에')); });
    expect(screen.queryByText(QUESTION)).toBeNull();
    await pressHeart();
    expect(screen.queryByText(QUESTION)).toBeNull();
    // 앱을 다시 켜도(새로 그려도) 안 묻는다
    first.unmount();
    render(<Heart token="token" />, { wrapper });
    await pressHeart();
    expect(screen.queryByText(QUESTION)).toBeNull();
    expect(await loadBehaviorConsent()).toBe(false);
  });

  it('「반영하기」면 동의가 켜지고 계정에도 저장된다', async () => {
    render(<Heart token="token" />, { wrapper });
    await pressHeart();
    await act(async () => { fireEvent.press(screen.getByText('반영하기')); });
    expect(screen.queryByText(QUESTION)).toBeNull();
    expect(await loadBehaviorConsent()).toBe(true);
    expect(updateMyConsents).toHaveBeenCalledWith('token', { BEHAVIOR_PERSONALIZATION: true });
  });

  it('로그인 안 했거나 이미 켜져 있으면 묻지 않는다', async () => {
    const guest = render(<Heart token={null} />, { wrapper });
    await pressHeart();
    expect(screen.queryByText(QUESTION)).toBeNull();
    guest.unmount();
    await setBehaviorConsent(true);
    render(<Heart token="token" />, { wrapper });
    await pressHeart();
    expect(screen.queryByText(QUESTION)).toBeNull();
  });
});
