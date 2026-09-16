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
