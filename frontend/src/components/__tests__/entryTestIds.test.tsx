// 앱에 들어가는 길의 선택자 이름이 그대로인가 —.
import { render } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { TabBar } from '../TabBar';

const mount = (active: 'home' | 'feed' | 'schedule' | 'map' | 'me') =>
  render(<OnboardingPreferencesProvider><TabBar active={active} /></OnboardingPreferencesProvider>);

// TabBar 는 폰 폭에서만 그린다 — 넓은 폭이면 null 을 돌려준다(md 부터는 상단 바가
// 맡을 자리라 없는 것을 지어내지 않는다). 시험 환경의 기본 폭은 그보다 넓으므로
// 폰 폭으로 고정한다. 이 시험이 재는 것은 선택자 이름이지 반응형 분기가 아니다.
jest.mock('@/layout/useLayout', () => ({
  useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }),
}));

// TabBar 는 하단 안전영역을 직접 읽는다. 제공자를 세우는 대신 그 훅만 흉내낸다
// 이 시험이 재는 것은 선택자 이름이지 여백 계산이 아니다.
jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

jest.mock('expo-router', () => ({
  useRouter: () => ({ replace: jest.fn(), push: jest.fn() }),
  useLocalSearchParams: () => ({}),
  usePathname: () => '/home',
}));

/** 맥북 자동화 스크립트가 쓰는 이름. 바꾸면 그쪽도 같이 바꿔야 한다. */
const TAB_IDS = ['tab-home', 'tab-feed', 'tab-schedule', 'tab-map', 'tab-me'];

describe('탭바 — 언어와 무관하게 찾을 수 있다', () => {
  it('다섯 탭 전부 testID 로 찾힌다', () => {
    const view = mount('home');
    for (const id of TAB_IDS) expect(view.getByTestId(id)).toBeTruthy();
  });

  it('🔴 이름이 다섯 개 그대로다 — 늘지도 줄지도 않는다', () => {
    const view = mount('home');
    // 탭이 늘거나 이름이 바뀌면 여기서 걸린다. 그때 자동화 스크립트도 같이 고쳐야 한다.
    expect(view.getAllByTestId(/^tab-/).map((node) => node.props.testID).sort())
      .toEqual([...TAB_IDS].sort());
  });

  it('탭 이름은 화면 글자와 무관하다 — 영어로 바뀌어도 testID 는 같다', () => {
    // 글자는 5개국어로 바뀌지만 testID 는 안 바뀐다는 것이 이 선택자의 존재 이유다.
    const view = mount('feed');
    expect(view.getByTestId('tab-feed')).toBeTruthy();
    expect(view.getByTestId('tab-me')).toBeTruthy();
  });
});
