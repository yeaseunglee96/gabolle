// 앱 안(탭의 넓은 화면 판)에서는 「앱 받기」 띠를 숨긴다(S15P21E201-1964).
// 이미 앱을 쓰는 사람에게 앱을 받으라고 하고, 안드로이드 앱 안에서 「Google Play 심사 중」이 보였다.
import { Linking, Platform } from 'react-native';
import { fireEvent, render, screen } from '@testing-library/react-native';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn() }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'desktop', desktop: true, width: 1280, height: 800, isLandscape: true }) }));
jest.mock('@/components/DongbaekMascot', () => ({ GabolleMascot: () => null }));

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { WebFooter } from '../WebFooter';

const mount = () => render(<OnboardingPreferencesProvider><WebFooter /></OnboardingPreferencesProvider>);

const original = Platform.OS;
afterEach(() => { Platform.OS = original; });

describe('WebFooter — 앱과 웹', () => {
  it('🔴 앱(안드로이드)에서는 앱 받기 띠가 없다 — 메뉴·약관은 남는다', () => {
    Platform.OS = 'android';
    mount();
    expect(screen.queryByText('여행 중에는 앱이 더 편해요')).toBeNull();
    expect(screen.queryByText('Google Play 심사 중')).toBeNull();
    expect(screen.getByLabelText('약관·고지')).toBeTruthy();
  });

  it('웹에서는 앱 받기 띠가 그대로다', () => {
    Platform.OS = 'web';
    mount();
    expect(screen.getByText('여행 중에는 앱이 더 편해요')).toBeTruthy();
  });

  it('🔴 웹에서 Google Play 는 눌러서 받는 링크다 — 「심사 중」은 없다, QR 은 둘(S15P21E201-2037)', () => {
    Platform.OS = 'web';
    const openURL = jest.spyOn(Linking, 'openURL').mockResolvedValue(true);
    mount();
    expect(screen.queryByText(/심사 중/)).toBeNull();
    fireEvent.press(screen.getByLabelText('Google Play에서 가볼래 받기'));
    expect(openURL).toHaveBeenCalledWith('https://play.google.com/store/apps/details?id=com.gabolle.app');
    expect(screen.getByText('App Store')).toBeTruthy();
    expect(screen.getByText('Google Play')).toBeTruthy();
    expect(screen.getByText('iOS')).toBeTruthy();
    expect(screen.getByLabelText('Google Play 내려받기 QR 코드')).toBeTruthy();
  });

  it('바닥 언어 목록에 고를 수 있는 여섯 언어가 다 있다 — Tiếng Việt 포함', () => {
    Platform.OS = 'web';
    mount();
    expect(screen.getByText(/Tiếng Việt/)).toBeTruthy();
  });
});
