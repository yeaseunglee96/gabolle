// 반응 줄에 무엇이 보이는가 — 그려서 본다.
//
// 순수 함수 시험(storyReaction.test.ts)은 수 계산만 본다. 「👎 가 사라졌는가」와
// 「수가 없을 때 칸을 안 그리는가」는 그려 보지 않으면 알 수 없다.
import { render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { StoryReactionRow, type ReactableStory } from '@/social/StoryReactionRow';

// 한국어/영어를 고르는 자리가 이 공급자 안에 있다 — 없으면 부품이 글자를 못 고른다.
function row(over: Partial<ReactableStory> = {}) {
  const story: ReactableStory = { myReaction: null, likeCount: 12, dislikeCount: 3, ...over };
  return render(
    <OnboardingPreferencesProvider>
      <StoryReactionRow story={story} reacting={false} onReact={() => {}} />
    </OnboardingPreferencesProvider>,
  );
}

describe('반응 줄', () => {
  it('싫어요를 안 그린다 — 서버에 수가 남아 있어도', () => {
    // dislikeCount 3 을 일부러 넣는다. 데이터가 있어도 화면에 나오면 안 된다.
    row({ dislikeCount: 3 });
    expect(screen.queryByText(/👎/)).toBeNull();
    expect(screen.queryByLabelText(/싫어요/)).toBeNull();
  });

  it('좋아요 수를 그린다', () => {
    row({ likeCount: 12 });
    expect(screen.getByText('👍 12')).toBeTruthy();
  });

  it('인용 수가 오면 그린다', () => {
    row({ linkCopyCount: 4 });
    expect(screen.getByText('인용 4')).toBeTruthy();
  });

  it('🔴 인용 수가 0 이어도 그린다 — 0 은 「아무도 안 했다」라는 사실이다', () => {
    row({ linkCopyCount: 0 });
    expect(screen.getByText('인용 0')).toBeTruthy();
  });

  it('🔴 인용 수가 안 오면 칸 자체를 안 그린다 — 0 을 지어내지 않는다', () => {
    row({ linkCopyCount: undefined });
    expect(screen.queryByText(/인용/)).toBeNull();
  });

  it('찾는 방법 자체가 살아 있다 — 없는 글자는 없다고 말한다', () => {
    // 이 줄이 없으면 queryByText 가 늘 null 을 돌려줘도 위 두 시험이 통과한다.
    row({ linkCopyCount: 4 });
    expect(screen.queryByText('이런 글자는 없다')).toBeNull();
    expect(screen.queryByText('인용 4')).not.toBeNull();
  });
});
