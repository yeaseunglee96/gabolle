// 내 기록 여러 개를 골라 한 번에 지우기 — S15P21E201-1907.
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react-native';

const mockDelete = jest.fn(async (id: string) => (id === 's3' ? { state: 'error', message: 'x' } : { state: 'success' }));
const story = (id: string) => ({ id, body: `기록 ${id}`, createdAt: '2026-09-30T00:00:00Z', publishAt: '2026-09-30T00:00:00Z', published: true, visibility: 'PUBLIC', images: [], place: null, region: null });
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 't', user: { userId: 'u1' } }) }));
jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn() }), useFocusEffect: (cb: () => void) => { const r = jest.requireActual('react') as typeof import('react'); r.useEffect(() => cb(), [cb]); } }));
jest.mock('@/social/stories', () => ({
  ...jest.requireActual('@/social/stories'),
  loadUserStories: jest.fn(async () => ({ state: 'success', items: [story('s1'), story('s2'), story('s3')], nextCursor: null })),
  deleteStory: (id: string) => mockDelete(id),
}));
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { MyPostsBody } from '@/me/panels/MyPostsBody';

it('🔴 여러 개를 골라 확인 뒤 한 번에 지우고, 못 지운 것은 남기고 알린다', async () => {
  render(<OnboardingPreferencesProvider><MyPostsBody /></OnboardingPreferencesProvider>);
  fireEvent.press(await screen.findByText('여러 개 지우기'));
  const boxes = screen.getAllByLabelText('이 기록 고르기');
  fireEvent.press(boxes[1]);
  fireEvent.press(boxes[2]);
  expect(screen.getByText('2개 골랐어요')).toBeTruthy();
  fireEvent.press(screen.getByText('골라서 지우기'));
  await act(async () => { fireEvent.press(screen.getByText('지우기')); });
  await waitFor(() => expect(screen.getByText('1개는 지우지 못했어요. 다시 시도해 주세요.')).toBeTruthy());
  expect(mockDelete).toHaveBeenCalledTimes(2);
  expect(screen.queryByText('기록 s2')).toBeNull();
  expect(screen.getByText('기록 s3')).toBeTruthy();
  expect(screen.getByText('기록 s1')).toBeTruthy();
});
