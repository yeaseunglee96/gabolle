// 팔로우·글쓰기 뒤 피드 보관소 — S15P21E201-1778(고지혁 QA).
//
// 🔴 이 시험이 지키는 것: 팔로우한 뒤 팔로잉 피드가 한참 「팔로우한 사람의 기록이 없어요」로 남았고,
//    글을 쓴 뒤 「내 피드」에 방금 쓴 글이 바로 안 떴다. 보관소(30초)가 팔로우·글쓰기 전 목록을 그대로 내줬다.
import { queryClient } from '@/api/queryClient';
import { createStory, feedQueryKey, setFollowing } from '../stories';

const empty = { state: 'success' as const, items: [], nextCursor: null };
const story = { id: 'st-1', author: { id: 'u', displayName: '진미리' }, body: 'b', images: [], visibility: 'PUBLIC', publishAt: '', createdAt: '', updatedAt: '', mine: true, published: true, replyCount: 0 };

function respond(data: unknown) {
  globalThis.fetch = jest.fn(async () => new Response(JSON.stringify({ data, error: null, meta: { requestId: 'r1' } }), { status: 200, headers: { 'content-type': 'application/json' } })) as unknown as typeof fetch;
}

beforeEach(() => {
  queryClient.clear();
  queryClient.setQueryData(feedQueryKey('FOLLOWING', true), empty);
  queryClient.setQueryData(feedQueryKey('FOR_YOU', true), empty);
  queryClient.setQueryData(feedQueryKey('MINE', true), empty);
  queryClient.setQueryData(feedQueryKey('ALL', true, 'POPULAR'), empty);
  queryClient.setQueryData(['me', 'stories', 'u'], empty);
});

afterAll(() => queryClient.clear());

describe('팔로우·글쓰기 뒤 피드 보관소', () => {
  it('🔴 팔로우하면 팔로잉·추천 갈래의 옛 빈 목록을 버린다 — 다음에 열 때 「없어요」 대신 새로 불러온다', async () => {
    respond({ userId: 'x', following: true, followerCount: 1, followingCount: 1 });
    const outcome = await setFollowing('x', true, 'token');
    expect(outcome.state).toBe('success');
    expect(queryClient.getQueryData(feedQueryKey('FOLLOWING', true))).toBeUndefined();
    expect(queryClient.getQueryData(feedQueryKey('FOR_YOU', true))).toBeUndefined();
    // 팔로우와 상관없는 갈래는 그대로 둔다.
    expect(queryClient.getQueryData(feedQueryKey('ALL', true, 'POPULAR'))).toEqual(empty);
  });

  it('🔴 글을 쓰면 「내 피드」와 마이페이지 기록의 옛 목록을 버린다', async () => {
    respond(story);
    const outcome = await createStory({ body: 'b', imageUrls: [], accessToken: 'token' });
    expect(outcome.state).toBe('success');
    await Promise.resolve();
    expect(queryClient.getQueryData(feedQueryKey('MINE', true))).toBeUndefined();
    expect(queryClient.getQueryData(['me', 'stories', 'u'])).toBeUndefined();
    expect(queryClient.getQueryState(feedQueryKey('ALL', true, 'POPULAR'))?.isInvalidated).toBe(true);
  });

  it('댓글은 피드 목록에 안 나오므로 비우지 않는다', async () => {
    respond(story);
    await createStory({ body: 'b', imageUrls: [], parentStoryId: 'p', accessToken: 'token' });
    expect(queryClient.getQueryData(feedQueryKey('MINE', true))).toEqual(empty);
  });
});
