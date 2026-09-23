// 마이페이지 시안 새 판 (frontend/docs/design_handoff_mypage_v2, S15P21E201-1526).
//
// 이 시험이 지키는 것:
//   · 넓은 화면 「내 여행」 가로형 카드 — 여행이 없어도(null) 안 터진다. 홈의 세로형은 안 바뀐다
//   · 폰 프로필 카드 — 아바타와 「프로필 편집」이 한 줄에서 세로 가운데(=커버와 카드의 경계선)
// 탭 표시가 미끄러지는 것과 2열 배치는 화면을 띄워 눈으로 확인한다 — 움직임은 시험이 못 본다.
import { render } from '@testing-library/react-native';
import { StyleSheet } from 'react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import type { TripSummaryDto } from '@/trip/trips';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn() }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', user: { userId: 'me' } }) }));
jest.mock('@/home/tripNavigation', () => ({ resolveHomeTripDestination: jest.fn(async () => '/trip/t1') }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }) }));

import { MyTripCard } from '@/home/HomeBlocks';
import { ProfileCard, ProfileCardButton } from '@/me/ProfileCard';

const TRIP: TripSummaryDto = {
  tripId: 't1', title: '가을 부산 한 바퀴', startDate: '2026-10-03', endDate: '2026-10-05', dayCount: 3, partySize: 2,
  status: 'PLANNING', role: 'OWNER', createdAt: '2026-09-20T00:00:00Z', updatedAt: '2026-09-20T00:00:00Z',
} as TripSummaryDto;

const wrap = (node: React.ReactElement) => render(<OnboardingPreferencesProvider>{node}</OnboardingPreferencesProvider>);
const flat = (style: unknown) => StyleSheet.flatten(style as never) as Record<string, unknown>;
/** 위로 올라가며 스타일이 조건에 맞는 첫 상자를 찾는다 — 몇 겹 위인지를 시험이 외우지 않게. */
type Node = { props: { style?: unknown }; parent: Node | null } | null;
const upTo = (from: Node, ok: (style: Record<string, unknown>) => boolean) => {
  for (let node = from; node; node = node.parent) { const style = flat(node.props.style) ?? {}; if (ok(style)) return style; }
  throw new Error('조건에 맞는 상자를 못 찾았다');
};

describe('내 여행 카드 — layout="row" (마이페이지 넓은 화면)', () => {
  it('가로로 선다 — 글자 묶음과 「일정 보기 →」가 한 줄, 선 없는 카드', () => {
    const view = wrap(<MyTripCard layout="row" trip={TRIP} signedIn loaded />);
    const card = view.getByRole('button');
    expect(flat(card.props.style)).toMatchObject({ flexDirection: 'row', alignItems: 'center', borderWidth: 0 });
    expect(view.getByText('일정 보기 →')).toBeTruthy();
    expect(view.getByText('가을 부산 한 바퀴')).toBeTruthy();
  });

  it('🔴 여행이 없어도 터지지 않는다 — 빈 상태를 그린다', () => {
    const view = wrap(<MyTripCard layout="row" trip={null} signedIn loaded />);
    expect(view.getByText('아직 만든 여행이 없어요')).toBeTruthy();
  });

  it('기본값(홈)은 그대로 세로형이다 — 폭 360', () => {
    const view = wrap(<MyTripCard trip={TRIP} signedIn loaded />);
    expect(flat(view.getByRole('button').props.style).flexDirection).not.toBe('row');
    expect(upTo(view.getByText('내 여행') as never, (style) => style.width !== undefined)).toMatchObject({ width: 360 });
  });
});

describe('폰 프로필 카드', () => {
  it('아바타와 「프로필 편집」이 한 줄에서 세로 가운데 — 커버와 흰 카드의 경계선 위', () => {
    const view = render(
      <ProfileCard name="김민지" email="minji@example.com" avatarUri={null} coverUri={null} counts={[]} tx={(ko) => ko}
        actions={<ProfileCardButton label="프로필 편집" onPress={() => {}} />} />,
    );
    // 아바타 첫 글자(「김」 — 시안의 「민」은 더미 이름 김민지의 가운데 글자일 뿐이다)에서 위로 올라가 처음 만나는 가로 줄
    expect(upTo(view.getByText('김') as never, (style) => style.flexDirection === 'row')).toMatchObject({ alignItems: 'center', justifyContent: 'space-between' });
  });
});
