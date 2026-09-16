import AsyncStorage from '@react-native-async-storage/async-storage';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { Button, Text } from 'react-native';
import { getApiLanguage } from '@/api/client';
import { OnboardingPreferencesProvider, useOnboardingPreferences } from '../OnboardingPreferences';

const KEY = 'gabolle:onboarding-preferences';
function Probe() {
  const p = useOnboardingPreferences();
  return <><Text>{`${p.hydrated}:${p.language}:${p.hasEnteredApp}`}</Text><Button title="English" onPress={() => p.setLanguage('en')} /><Button title="Enter" onPress={p.markEnteredApp} /><Button title="Reset" onPress={p.reset} /></>;
}
const mount = () => render(<OnboardingPreferencesProvider><Probe /></OnboardingPreferencesProvider>);
beforeEach(async () => { jest.clearAllMocks(); await AsyncStorage.clear(); });
// CI 러너가 붐빌 때(다른 스위트와 동시 실행) 마운트 두 번 + AsyncStorage 왕복 두 번이 기본
// 5000ms 를 넘겨 이 테스트만 간헐적으로 타임아웃났다(로컬 단독 실행 533ms, MR !906 파이프라인
// #197590). 15000ms로 한 번 올렸는데 파이프라인 #197736에서 또 타임아웃났다 — 이 저장소는
// CI 러너가 한 대뿐이라(CONTRIBUTING.md 5절) 여러 MR 파이프라인이 겹치면 컨테이너가 실제로
// 받는 CPU 시간이 크게 흔들린다. 로직은 그대로 가볍기 때문에(로컬 533ms) 여유를 30000ms로
// 더 크게 잡는다 — 이래도 또 타임아웃나면 타임아웃 숫자 문제가 아니라 러너 자체를 봐야 한다.
it('restores English and the home-entry flag after an app restart', async () => {
  const first = mount();
  await first.findByText('true:ko:false');
  fireEvent.press(first.getByText('English'));
  expect(getApiLanguage()).toBe('en');
  fireEvent.press(first.getByText('Enter'));
  await waitFor(async () => expect(JSON.parse((await AsyncStorage.getItem(KEY))!)).toEqual({ language: 'en', mobility: 'none', hasEnteredApp: true }));
  first.unmount();
  const second = mount();
  await second.findByText('true:en:true');
}, 30000);
it('accepts old saved preferences without skipping onboarding', async () => {
  await AsyncStorage.setItem(KEY, JSON.stringify({ language: 'en', mobility: 'slow' }));
  await mount().findByText('true:en:false');
});
it('does not erase the app-entry flag when clearing account preferences', async () => {
  await AsyncStorage.setItem(KEY, JSON.stringify({ language: 'en', mobility: 'slow', hasEnteredApp: true }));
  const view = mount();
  await view.findByText('true:en:true');
  fireEvent.press(view.getByText('Reset'));
  await view.findByText('true:ko:true');
  await waitFor(async () => expect(JSON.parse((await AsyncStorage.getItem(KEY))!).hasEnteredApp).toBe(true));
});
it('finishes hydration even if device storage is unavailable', async () => {
  jest.mocked(AsyncStorage.getItem).mockRejectedValueOnce(new Error('unavailable'));
  await mount().findByText('true:ko:false');
});
