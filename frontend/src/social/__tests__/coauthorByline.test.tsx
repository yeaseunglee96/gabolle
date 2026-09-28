// 작성자 이름 뒤의 공동 작성자 「· 이예승」 — S15P21E201-1583.
//
// 🔴 이 시험이 지키는 것은 둘이다.
//    ① 서버가 배포되기 전(coauthors 칸 없음)에는 **아무것도 안 그린다** — 빈 「·」 하나 남기지 않는다.
//    ② 탈퇴한 사람(displayName null)은 이름에서도 「외 N명」에서도 빠진다.
import type { ReactNode } from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { CoauthorByline, coauthorNames } from '@/social/CoauthorByline';
import type { StoryDto } from '@/social/stories';

const mockPush = jest.fn();
jest.mock('expo-router', () => ({ useRouter: () => ({ push: mockPush }) }));

const tx = (ko: string) => ko;
const person = (id: string, displayName: string | null) => ({ id, displayName });

const story = (coauthors?: StoryDto['coauthors']): StoryDto => ({
  id: 'story-1', author: { id: 'author-1', displayName: '장효준' }, body: '광안리', images: [], visibility: 'PUBLIC',
  publishAt: '2026-09-24T10:00:00Z', createdAt: '2026-09-24T10:00:00Z', updatedAt: '2026-09-24T10:00:00Z', mine: false, published: true,
  ...(coauthors ? { coauthors } : {}),
});

const Providers = ({ children }: { children: ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;

describe('공동 작성자 이름 추리기', () => {
  it('칸이 없거나 비어 있으면 적을 것이 없다', () => {
    expect(coauthorNames(undefined, tx)).toBeNull();
    expect(coauthorNames([], tx)).toBeNull();
  });

  it('두 명까지는 이름을 적는다 — 합류 순서 그대로', () => {
    expect(coauthorNames([person('a', '이예승')], tx)).toBe('이예승');
    expect(coauthorNames([person('a', '이예승'), person('b', '진미리')], tx)).toBe('이예승, 진미리');
  });

  it('넘으면 「외 N명」', () => {
    expect(coauthorNames([person('a', '이예승'), person('b', '진미리'), person('c', '고지혁'), person('d', '모진성')], tx)).toBe('이예승, 진미리 외 2명');
  });

  it('🔴 탈퇴한 사람(null)은 이름에서도 「외 N명」에서도 뺀다', () => {
    expect(coauthorNames([person('x', null), person('a', '이예승')], tx)).toBe('이예승');
    expect(coauthorNames([person('a', '이예승'), person('x', null), person('b', '진미리'), person('c', '고지혁')], tx)).toBe('이예승, 진미리 외 1명');
    expect(coauthorNames([person('x', null), person('y', null)], tx)).toBeNull();
  });
});

describe('공동 작성자 줄', () => {
  beforeEach(() => mockPush.mockReset());

  it('🔴 서버가 칸을 안 주면(배포 전) 아무것도 안 그린다', () => {
    render(<CoauthorByline story={story()} />, { wrapper: Providers });
    expect(screen.toJSON()).toBeNull();
  });

  it('작성자 이름 뒤에 「· 이름」, 누르면 참여자 화면으로 간다', () => {
    render(<CoauthorByline story={story([person('a', '이예승')])} />, { wrapper: Providers });

    fireEvent.press(screen.getByText('· 이예승'));
    expect(mockPush).toHaveBeenCalledWith('/feed/story-1/coauthors');
  });
});
