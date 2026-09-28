// iOS 심사 공지의 알려진 문제 2번(뒷부분)·3번 — S15P21E201-1684.
//
// 🔴 이 시험이 지키는 것:
//    2. 여행 이름 바꾸기 창(아래에서 올라오는 판, Modal)에 키보드를 피하는 장치가 없어 키보드가 입력칸을 가렸다.
//       Modal 은 앱 화면(Screen) 바깥이라 거기의 장치가 안 먹는다 — 창에도 같은 규칙(iOS 는 padding, 안드로이드는 시스템 pan).
//    3. 다른 사람 프로필은 이메일을 비워 넘기는데, 프로필 카드가 이메일이 없으면 무조건
//       「로그인 없이 앱을 둘러보는 중이에요」라고 적었다 — 그 말은 로그인 안 한 내 마이페이지에서만.
import { KeyboardAvoidingView, Platform } from 'react-native';
import { render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', desktop: false, width: 390, height: 844, isLandscape: false }) }));

import { ProfileCard } from '@/me/ProfileCard';
import { TripNameSheet } from '@/trip/TripNameSheet';

const tx = (ko: string) => ko;
const GUEST = '로그인 없이 앱을 둘러보는 중이에요';

describe('2. 여행 이름 바꾸기 창과 키보드', () => {
  it('🔴 창이 키보드를 피한다 — 앱 화면과 같은 규칙(iOS 는 밀어 올림)', () => {
    const view = render(
      <OnboardingPreferencesProvider>
        <TripNameSheet tripId="t1" currentTitle="부산 가을 바다" dateLabel="10월 3일" accessToken="tok" onClose={jest.fn()} onSaved={jest.fn()} />
      </OnboardingPreferencesProvider>,
    );
    const avoiding = view.UNSAFE_getByType(KeyboardAvoidingView);
    expect(avoiding.props.behavior).toBe(Platform.OS === 'ios' ? 'padding' : undefined);
    // 입력칸이 그 안에 있다 — 밖에 있으면 밀려 올라가지 않는다.
    expect(avoiding.findAll((node) => node.props.value === '부산 가을 바다').length).toBeGreaterThan(0);
  });
});

describe('3. 프로필 카드의 둘러보기 문구', () => {
  const card = (props: { email: string | null; guest?: boolean }) =>
    render(<ProfileCard name="이예승" avatarUri={null} coverUri={null} counts={[]} actions={null} tx={tx} {...props} />);

  it('🔴 다른 사람 프로필(이메일 없음)에는 둘러보기 문구가 없다', () => {
    card({ email: null });
    expect(screen.queryByText(GUEST)).toBeNull();
  });

  it('로그인 안 한 내 마이페이지에서만 둘러보기 문구', () => {
    card({ email: null, guest: true });
    expect(screen.getByText(GUEST)).toBeTruthy();
  });

  it('이메일이 있으면 이메일', () => {
    card({ email: 'busan@example.test' });
    expect(screen.getByText('busan@example.test')).toBeTruthy();
    expect(screen.queryByText(GUEST)).toBeNull();
  });
});
