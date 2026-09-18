// 온보딩에서 지금 보이는 페이지만 접근성 트리에 남는가 —.
import { fireEvent, render } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

jest.mock('expo-router', () => ({
  useRouter: () => ({ replace: jest.fn(), push: jest.fn() }),
  useLocalSearchParams: () => ({}),
}));

import AppIntro from '../app-intro';

/** useI18n 이 이 제공자 안에서만 동작한다 — AppLanguageSetting 시험과 같은 방식이다. */
const mount = () => render(<OnboardingPreferencesProvider><AppIntro /></OnboardingPreferencesProvider>);

/** 한 페이지 분량의 글이 트리에 있는가 — 눈썹 문구로 센다. */
const EYEBROWS = ['AI 여행', '부산 둘러보기', '현장 말하기'];

function visibleEyebrows(view: ReturnType<typeof render>): string[] {
  return EYEBROWS.filter((text) => view.queryAllByText(text).length > 0);
}

describe('온보딩 — 보이는 페이지만 읽힌다', () => {
  it('처음에는 1페이지만 트리에 있다', () => {
    const view = mount();
    expect(visibleEyebrows(view)).toEqual(['AI 여행']);
  });

  it('🔴 안 보이는 페이지의 글자는 트리에 없다 — 그것이 이 시험의 전부다', () => {
    const view = mount();
    // 3페이지의 「현장 말하기」가 1페이지에서 잡히면, 실기기에서 겪은 그 일이 다시 난다.
    expect(view.queryAllByText('현장 말하기')).toHaveLength(0);
    expect(view.queryAllByText('부산 둘러보기')).toHaveLength(0);
  });

  it('다음으로 넘기면 그 페이지만 남는다', () => {
    const view = mount();
    fireEvent.press(view.getByTestId('app-intro-primary'));
    expect(visibleEyebrows(view)).toEqual(['부산 둘러보기']);
  });

  it('마지막까지 가면 3페이지만 남는다', () => {
    const view = mount();
    fireEvent.press(view.getByTestId('app-intro-primary'));
    fireEvent.press(view.getByTestId('app-intro-primary'));
    expect(visibleEyebrows(view)).toEqual(['현장 말하기']);
  });

  it('주 버튼과 건너뛰기는 글자가 아니라 testID 로 찾을 수 있다', () => {
    // 글자로 찾으면 English·日本語 로 바꾸는 순간 깨진다. 5개국어를 지원하는 앱이다.
    const view = mount();
    expect(view.getByTestId('app-intro-primary')).toBeTruthy();
    expect(view.getByTestId('app-intro-skip')).toBeTruthy();
  });

  it('마지막 페이지에서만 주 버튼 글자가 「시작하기」가 된다', () => {
    const view = mount();
    expect(view.getByText('다음')).toBeTruthy();
    fireEvent.press(view.getByTestId('app-intro-primary'));
    fireEvent.press(view.getByTestId('app-intro-primary'));
    expect(view.getByText('시작하기')).toBeTruthy();
  });
});
