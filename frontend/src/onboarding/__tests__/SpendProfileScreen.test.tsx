import { fireEvent, render, waitFor } from '@testing-library/react-native';
import SpendProfileScreen from '../../../app/(onboarding)/spend-profile';
import { getSpendProfile, putSpendProfile, SPEND_QUESTIONS } from '../spendProfile';

const mockRouter = { replace: jest.fn() };
let mockLanguage: 'ko' | 'en' = 'en';
jest.mock('expo-router', () => ({ useRouter: () => mockRouter }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'test', ready: true }) }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string, en: string) => mockLanguage === 'en' ? en : ko }) }));
jest.mock('@/components/Screen', () => ({ Screen: require('react-native').View }));
jest.mock('@/components/BrandLogoLink', () => ({ BrandLogoLink: () => null }));
jest.mock('@/onboarding/spendProfile', () => ({ ...jest.requireActual('@/onboarding/spendProfile'), getSpendProfile: jest.fn(), putSpendProfile: jest.fn() }));

beforeEach(() => {
  jest.clearAllMocks();
  mockLanguage = 'en';
  jest.mocked(getSpendProfile).mockResolvedValue({ status: 'UNKNOWN', answers: null });
  jest.mocked(putSpendProfile).mockResolvedValue({ status: 'SELECTED', value: '{}' });
});
it.each(['en', 'ko'] as const)('shows every question in %s and saves the same answer codes', async (language) => {
  mockLanguage = language;
  const view = render(<SpendProfileScreen />);
  for (const question of SPEND_QUESTIONS) {
    await view.findByText(language === 'en' ? question.titleEn('USER') : question.titleKo('USER'));
    expect(view.queryByText(language === 'en' ? question.titleKo('USER') : question.titleEn('USER'))).toBeNull();
    fireEvent.press(view.getByLabelText(language === 'en' ? question.options[0].labelEn : question.options[0].labelKo));
  }
  await waitFor(() => expect(putSpendProfile).toHaveBeenCalledWith({ transport: 'PRICE_FIRST', stay: 'KRW_UNDER_50K', meal: 'EVERYDAY_LOCAL' }, 'test'));
  await waitFor(() => expect(mockRouter.replace).toHaveBeenCalledWith('/taste-profile'));
});
it.each(['SELECTED', 'SKIPPED'] as const)('does not repeat questions for a %s profile', async (status) => {
  jest.mocked(getSpendProfile).mockResolvedValue({ status, answers: null });
  const view = render(<SpendProfileScreen />);
  await waitFor(() => expect(mockRouter.replace).toHaveBeenCalledWith('/home'));
  expect(view.queryByText(SPEND_QUESTIONS[0].titleEn('USER'))).toBeNull();
});
it('keeps answers after a failed save and retries without repeating questions', async () => {
  jest.mocked(putSpendProfile).mockRejectedValueOnce(new Error('offline'));
  const view = render(<SpendProfileScreen />);
  for (const question of SPEND_QUESTIONS) {
    await view.findByText(question.titleEn('USER'));
    fireEvent.press(view.getByLabelText(question.options[0].labelEn));
  }
  await view.findByRole('alert');
  expect(mockRouter.replace).not.toHaveBeenCalled();
  fireEvent.press(view.getByText('Retry saving'));
  await waitFor(() => expect(mockRouter.replace).toHaveBeenCalledWith('/taste-profile'));
  expect(jest.mocked(putSpendProfile).mock.calls[1]).toEqual(jest.mocked(putSpendProfile).mock.calls[0]);
// 🔴 이 테스트만 제한을 15초로 올린다 (S15P21E201-1076).
//
// 설문 전체를 한 번 돌고, 저장이 실패한 뒤 다시 한 번 돈다 — 이 파일에서 유일하게 두 바퀴
// 도는 테스트라 5초 기본값이 원래 빠듯했다. 2코어 CI 러너에서 43개 스위트를 동시에 돌리면
// 넘는다(실측: 잡 #551782, 이 스위트 혼자 19.758초).
//
// 🔴 성능 문제를 가리려고 올리는 것이 아니다. 진짜 원인은 먼저 잡았다 — Button 의
//    interpolate() 가 렌더마다 새 애니메이션 노드를 만들던 것을 useMemo 로 묶었고, 그것만으로
//    726ms -> 326ms 가 나왔다(이예승 님 A/B). **그 수정을 넣고 파이프라인이 또 걸린 뒤에**
//    올리는 것이라, 이 숫자가 무엇을 덮고 있는지는 안다. 순서를 반대로 했으면 몰랐다.
//
// 이 제한이 다시 모자라면 그때는 시간을 더 올릴 것이 아니라 테스트를 쪼개야 한다.
}, 15_000);
it('offers an explicit exit after a failed skip save', async () => {
  jest.mocked(putSpendProfile).mockRejectedValueOnce(new Error('offline'));
  const view = render(<SpendProfileScreen />);
  await view.findByText(SPEND_QUESTIONS[0].titleEn('USER'));
  fireEvent.press(view.getByLabelText('Skip all'));
  await view.findByRole('alert');
  expect(mockRouter.replace).not.toHaveBeenCalled();
  fireEvent.press(view.getByText('Leave without saving'));
  expect(mockRouter.replace).toHaveBeenCalledWith('/home');
});
it('does not mistake a failed profile lookup for an unanswered survey', async () => {
  jest.mocked(getSpendProfile).mockRejectedValueOnce(new Error('offline'));
  const view = render(<SpendProfileScreen />);
  await view.findByRole('alert');
  expect(view.queryByText(SPEND_QUESTIONS[0].titleEn('USER'))).toBeNull();
  jest.mocked(getSpendProfile).mockResolvedValue({ status: 'SELECTED', answers: null });
  fireEvent.press(view.getByText('Check again'));
  await waitFor(() => expect(mockRouter.replace).toHaveBeenCalledWith('/home'));
  expect(putSpendProfile).not.toHaveBeenCalled();
});
