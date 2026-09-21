// 댓글에 달린 댓글을 펼치는 단추 — 그려서 본다.
//
// 🔴 이 시험이 못 잡는 것 (초록이어도 확인 안 된 것):
//   · 들여쓰기가 폰 폭에서 실제로 읽히는가. 깊은 답글이 찌그러지는 것은 실기기에서만 보인다
//   · 서버가 정말 replyCount 를 채워 보내는가. 여기서는 우리가 넣어 준 값을 쓴다
//   · 답글을 쓰는 입력창 — 이 단계에서는 안 만들었다. 펼쳐 보는 것까지다
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { getStoryReplies, type StoryDto } from '@/social/stories';

import { ReplyCard } from '../../../app/feed/[id]';

jest.mock('@/social/stories', () => ({
  ...jest.requireActual('@/social/stories'),
  getStoryReplies: jest.fn(),
}));

const asked = getStoryReplies as jest.MockedFunction<typeof getStoryReplies>;

const PARENT_ID = '11111111-1111-1111-1111-111111111111';
const REPLY_ID = '22222222-2222-2222-2222-222222222222';

function story(over: Partial<StoryDto> = {}): StoryDto {
  return {
    id: REPLY_ID,
    author: { id: '33333333-3333-3333-3333-333333333333', displayName: '이예승' },
    body: '여기 좋네요',
    parentId: PARENT_ID,
    images: [],
    visibility: 'PUBLIC',
    publishAt: '2026-09-21T00:00:00Z',
    createdAt: '2026-09-21T00:00:00Z',
    updatedAt: '2026-09-21T00:00:00Z',
    mine: false,
    published: true,
    replyCount: 0,
    ...over,
  } as StoryDto;
}

function draw(reply: StoryDto) {
  return render(
    <OnboardingPreferencesProvider>
      <ReplyCard reply={reply} accessToken="token" onUpdated={() => {}} onDeleted={() => {}} onReport={() => {}} />
    </OnboardingPreferencesProvider>,
  );
}

beforeEach(() => asked.mockReset());

describe('댓글에 달린 댓글', () => {
  it('답글이 없으면 펼치기 단추를 안 그린다 — 눌러도 빈 목록만 나온다', () => {
    draw(story({ replyCount: 0 }));
    expect(screen.queryByText(/답글/)).toBeNull();
  });

  it('답글 수를 단추에 적는다', () => {
    draw(story({ replyCount: 2 }));
    expect(screen.getByText('답글 2개')).toBeTruthy();
  });

  it('🔴 펼치면 그 댓글의 id 로 묻는다 — 원글 id 로 물으면 형제 댓글이 답글로 그려진다', async () => {
    asked.mockResolvedValue({ state: 'success', replies: [] });
    draw(story({ replyCount: 1 }));

    fireEvent.press(screen.getByText('답글 1개'));

    // 이 한 줄이 이 파일의 이유다. 부모 id 를 넘기면 화면은 멀쩡해 보이는데 내용이 남의 것이다.
    await waitFor(() => expect(asked).toHaveBeenCalledWith(REPLY_ID, 'token'));
  });

  it('펼치면 답글 본문이 보인다', async () => {
    asked.mockResolvedValue({ state: 'success', replies: [story({ id: 'child-1', parentId: REPLY_ID, body: '저도 갔어요' })] });
    draw(story({ replyCount: 1 }));

    fireEvent.press(screen.getByText('답글 1개'));

    expect(await screen.findByText('저도 갔어요')).toBeTruthy();
  });

  it('🔴 못 불러오면 그렇다고 말한다 — 「답글이 없다」로 바꾸지 않는다', async () => {
    asked.mockResolvedValue({ state: 'error', message: '서버 오류' });
    draw(story({ replyCount: 3 }));

    fireEvent.press(screen.getByText('답글 3개'));

    expect(await screen.findByText('답글을 불러오지 못했어요.')).toBeTruthy();
    expect(screen.getByText('다시 시도')).toBeTruthy();
  });

  it('서버가 잘라 주면 잘렸다고 말한다 — 상한과 「더 있다」는 언제나 짝이다', async () => {
    asked.mockResolvedValue({ state: 'success', replies: [story({ id: 'child-1', body: '하나' })] });
    draw(story({ replyCount: 60 }));

    fireEvent.press(screen.getByText('답글 60개'));

    expect(await screen.findByText('답글 60개 중 1개를 보여드렸어요.')).toBeTruthy();
  });

  it('접었다 다시 펴도 서버에 또 묻지 않는다', async () => {
    asked.mockResolvedValue({ state: 'success', replies: [story({ id: 'child-1', body: '하나' })] });
    draw(story({ replyCount: 1 }));

    fireEvent.press(screen.getByText('답글 1개'));
    expect(await screen.findByText('하나')).toBeTruthy();

    fireEvent.press(screen.getByText('답글 접기'));
    fireEvent.press(screen.getByText('답글 1개'));

    expect(await screen.findByText('하나')).toBeTruthy();
    expect(asked).toHaveBeenCalledTimes(1);
  });
});
