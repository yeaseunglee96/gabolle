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
});
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
