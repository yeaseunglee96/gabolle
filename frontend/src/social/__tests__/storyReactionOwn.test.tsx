// 내 글의 좋아요 단추 (S15P21E201-1766).
import { fireEvent, render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { StoryReactionRow } from '@/social/StoryReactionRow';

const wrapper = ({ children }: { children: React.ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;

const base = { myReaction: null, likeCount: 0, dislikeCount: 0, linkCopyCount: 0 } as const;

it('🔴 내 글이면 좋아요가 잠기고 이유를 읽어 준다', () => {
  const onReact = jest.fn();
  render(<StoryReactionRow story={{ ...base, mine: true }} reacting={false} onReact={onReact} />, { wrapper });
  const button = screen.getByLabelText('내 글에는 좋아요를 누를 수 없어요');
  fireEvent.press(button);
  expect(onReact).not.toHaveBeenCalled();
});

it('남의 글이면 지금처럼 눌린다', () => {
  const onReact = jest.fn();
  render(<StoryReactionRow story={{ ...base, mine: false }} reacting={false} onReact={onReact} />, { wrapper });
  fireEvent.press(screen.getByLabelText('좋아요'));
  expect(onReact).toHaveBeenCalledWith('LIKE');
});
