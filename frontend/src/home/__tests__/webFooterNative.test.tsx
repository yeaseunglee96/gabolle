// 앱 안(탭의 넓은 화면 판)에서는 「앱 받기」 띠를 숨긴다(S15P21E201-1964).
// 이미 앱을 쓰는 사람에게 앱을 받으라고 하고, 안드로이드 앱 안에서 「Google Play 심사 중」이 보였다.
import { Platform } from 'react-native';
import { render, screen } from '@testing-library/react-native';

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
    expect(screen.getByLabelText('약관')).toBeTruthy();
  });

  it('웹에서는 앱 받기 띠가 그대로다', () => {
    Platform.OS = 'web';
    mount();
    expect(screen.getByText('여행 중에는 앱이 더 편해요')).toBeTruthy();
  });
});
