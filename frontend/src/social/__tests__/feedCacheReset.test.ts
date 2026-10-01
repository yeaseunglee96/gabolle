// 팔로우·글쓰기 뒤 피드 보관소 — S15P21E201-1778(고지혁 QA).
//
// 🔴 이 시험이 지키는 것: 팔로우한 뒤 팔로잉 피드가 한참 「팔로우한 사람의 기록이 없어요」로 남았고,
//    글을 쓴 뒤 「내 피드」에 방금 쓴 글이 바로 안 떴다. 보관소(30초)가 팔로우·글쓰기 전 목록을 그대로 내줬다.
import { queryClient } from '@/api/queryClient';
import { createStory, deleteStory, feedQueryKey, setBlocked, setFollowing } from '../stories';

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

// 차단·차단 해제 뒤 — S15P21E201-1787(QA). 차단하고 피드로 돌아가면 새로고침 전까지 그 사람 글이 남아 있었다.
describe('차단 뒤 피드 보관소', () => {
  it('차단하면 내 기록을 뺀 모든 갈래의 옛 목록을 버린다 — 다음에 열 때 서버가 거른 목록을 새로 받는다', async () => {
    respond({ userId: 'x', blocked: true });
    const outcome = await setBlocked('x', true, 'token');
    expect(outcome.state).toBe('success');
    expect(queryClient.getQueryData(feedQueryKey('FOLLOWING', true))).toBeUndefined();
    expect(queryClient.getQueryData(feedQueryKey('FOR_YOU', true))).toBeUndefined();
    expect(queryClient.getQueryData(feedQueryKey('ALL', true, 'POPULAR'))).toBeUndefined();
    // 내 기록은 내 글뿐이라 차단과 상관없다.
    expect(queryClient.getQueryData(feedQueryKey('MINE', true))).toEqual(empty);
  });

  it('차단을 풀어도 비운다 — 그 사람 글이 다시 나와야 한다', async () => {
    respond({ userId: 'x', blocked: false });
    await setBlocked('x', false, 'token');
    expect(queryClient.getQueryData(feedQueryKey('ALL', true, 'POPULAR'))).toBeUndefined();
  });

  it('서버가 거절하면 보관소를 건드리지 않는다', async () => {
    globalThis.fetch = jest.fn(async () => new Response(JSON.stringify({ data: null, error: { code: 'INTERNAL', message: 'x' }, meta: { requestId: 'r1' } }), { status: 500, headers: { 'content-type': 'application/json' } })) as unknown as typeof fetch;
    const outcome = await setBlocked('x', true, 'token');
    expect(outcome.state).not.toBe('success');
    expect(queryClient.getQueryData(feedQueryKey('ALL', true, 'POPULAR'))).toEqual(empty);
  });

  it('🔴 글을 쓰거나 지우면 마이페이지 「기록 N」 숫자(프로필)를 다시 받는다 — S15P21E201-1909', async () => {
    queryClient.setQueryData(['user-profile', 'u'], { state: 'success', profile: { storyCount: 1 } });
    respond(story);
    await createStory({ body: 'b', imageUrls: [], accessToken: 'token' });
    await Promise.resolve();
    expect(queryClient.getQueryState(['user-profile', 'u'])?.isInvalidated).toBe(true);
    queryClient.setQueryData(['user-profile', 'u'], { state: 'success', profile: { storyCount: 2 } });
    globalThis.fetch = jest.fn(async () => new Response(null, { status: 204 })) as unknown as typeof fetch;
    expect((await deleteStory('st-1', 'token')).state).toBe('success');
    expect(queryClient.getQueryState(['user-profile', 'u'])?.isInvalidated).toBe(true);
  });
});
