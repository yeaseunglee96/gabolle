// 글(원글·댓글 공통) 본문 수정 — S15P21E201-1239.
//
// 🔴 여기서 재는 것은 「PATCH 가 body 만 보내는가」다. 댓글에는 visibility·publishAt
// 을 고를 화면이 없다 — 실수로 그 칸을 채워 보내면 서버가 StoryForbiddenException 으로
// 막긴 하지만(작성자만 바꿀 수 있다), 안 보내는 편이 「막힐 걸 알고 보낸다」보다 낫다.

import { updateStory } from '../stories';

const STORY_ID = '11111111-1111-1111-1111-111111111111';

function story(overrides: Record<string, unknown> = {}) {
  return {
    id: STORY_ID,
    author: { id: '33333333-3333-3333-3333-333333333333', displayName: '이예승' },
    body: '고친 글',
    parentId: null,
    images: [],
    visibility: 'PUBLIC',
    publishAt: '2026-09-18T00:00:00Z',
    createdAt: '2026-09-18T00:00:00Z',
    updatedAt: '2026-09-18T01:00:00Z',
    mine: true,
    published: true,
    ...overrides,
  };
}

let lastRequest: { url: string; init?: RequestInit } | null = null;

function respond(make: (url: string) => { status: number; payload: unknown }) {
  lastRequest = null;
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    lastRequest = { url, init };
    const { status, payload } = make(url);
    return new Response(JSON.stringify(payload), { status, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

describe('updateStory', () => {
  it('🔴 PATCH 요청에 body 만 실린다 — visibility·publishAt 은 안 보낸다', async () => {
    respond(() => ({ status: 200, payload: { data: story(), error: null, meta: { requestId: 'r1' } } }));

    await updateStory(STORY_ID, '고친 글', 'token');

    expect(lastRequest).not.toBeNull();
    expect(lastRequest?.init?.method).toBe('PATCH');
    const sent = JSON.parse(String(lastRequest?.init?.body ?? '{}'));
    expect(sent).toEqual({ body: '고친 글' });
  });

  it('성공하면 고친 글을 그대로 돌려준다', async () => {
    respond(() => ({ status: 200, payload: { data: story({ body: '두 번째로 고침' }), error: null, meta: { requestId: 'r1' } } }));

    const outcome = await updateStory(STORY_ID, '두 번째로 고침', 'token');

    expect(outcome.state).toBe('success');
    if (outcome.state !== 'success') return;
    expect(outcome.story.body).toBe('두 번째로 고침');
  });

  it('🔴 실패를 조용히 삼키지 않는다', async () => {
    respond(() => ({ status: 403, payload: { data: null, error: { code: 'FORBIDDEN', message: '남의 글은 못 고쳐요' }, meta: { requestId: 'r1' } } }));

    const outcome = await updateStory(STORY_ID, '남의 글 고치기', 'token');

    expect(outcome.state).not.toBe('success');
  });
});
