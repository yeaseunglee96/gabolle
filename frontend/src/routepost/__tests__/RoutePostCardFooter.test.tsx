// 루트 카드 아래 줄(S15P21E201-2027) — 좁은 카드(장소 화면 「이 장소가 들어간 루트」·폰 피드)에서
// 작성자 + 「♥ ★ 따라 간 사람」 줄이 두 줄로 꺾였다. 한 줄로 두고 이름만 말줄임으로 줄인다.
import { render } from '@testing-library/react-native';
import { StyleSheet } from 'react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

import { RoutePostCard } from '../RoutePostCard';
import type { RoutePostDto } from '../routePosts';

jest.mock('../RouteThumb', () => ({ RouteThumb: () => null }));

const post: RoutePostDto = {
  routePostId: 'r1', author: { userId: 'u1', displayName: '아주아주아주긴이름을가진여행자입니다' }, title: '광안리 한 바퀴', body: '',
  dayIndex: 0, dayCount: 1, durationMin: 120, distanceM: 4200, path: [], stops: [],
  likeCount: 12, saveCount: 3, copyCount: 7, likedByMe: false, savedByMe: false, mine: false, createdAt: '2026-10-10T00:00:00Z',
};

it('keeps the counters on one line with a short follow label', () => {
  const view = render(<OnboardingPreferencesProvider><RoutePostCard post={post} onOpen={() => {}} /></OnboardingPreferencesProvider>);
  const counters = view.getByTestId('route-post-counters');
  expect(counters.props.numberOfLines).toBe(1);
  expect(StyleSheet.flatten(counters.props.style).flexShrink).toBe(0);
  expect(counters.props.children).toBe('♥ 12  ★ 3  따라 감 7');
});

it('truncates a long author name instead of wrapping the row', () => {
  const view = render(<OnboardingPreferencesProvider><RoutePostCard post={post} onOpen={() => {}} /></OnboardingPreferencesProvider>);
  const name = view.getByText(post.author.displayName);
  expect(name.props.numberOfLines).toBe(1);
  expect(name.props.ellipsizeMode ?? 'tail').toBe('tail');
  const author = view.getByTestId('route-post-author');
  const style = StyleSheet.flatten(author.props.style);
  expect(style.flex).toBe(1);
  expect(style.minWidth).toBe(0);
});
