// 댓글 조회·작성 — S15P21E201-1197.
//
// 🔴 여기서 재는 것은 「댓글이 보이나」가 아니라 **두 가지 조용한 실패**다.
//
//   ① 못 불러온 것을 「댓글이 없다」로 바꾸지 않는가
//      실패를 빈 목록으로 돌려주면 화면은 「아직 댓글이 없어요」를 그린다. 사용자는
//      없는 줄 믿고 나가고, 아무 오류도 안 뜬다. 이 앱이 오늘 하루 종일 데인 모양이다
//      — 안 재 본 것을 「없다」로 말하는 것.
//
//   ② 부모 id 가 요청에 실제로 실리는가
//      안 실리면 **댓글이 원글로 올라간다.** 화면에는 아무 오류가 없고, 피드에 뜬금없는
//      글이 하나 생긴다. 칸을 더해 놓고 값이 안 옮겨지는 것은 이 저장소에서 같은 밤에
//      세 번 난 병이다(S15P21E201-1120 · -1194 · -1195).

import { createStory, getStoryReplies } from '../stories';

const STORY_ID = '11111111-1111-1111-1111-111111111111';

function reply(overrides: Record<string, unknown> = {}) {
  return {
    id: '22222222-2222-2222-2222-222222222222',
    author: { id: '33333333-3333-3333-3333-333333333333', displayName: '이예승' },
    body: '여기 좋네요',
    parentId: STORY_ID,
    images: [],
    visibility: 'PUBLIC',
    publishAt: '2026-09-18T00:00:00Z',
    createdAt: '2026-09-18T00:00:00Z',
    updatedAt: '2026-09-18T00:00:00Z',
    mine: false,
    published: true,
    replyCount: 0,
    ...overrides,
  };
}

/** 마지막으로 서버에 나간 요청. 부모 id 가 실렸는지 여기서 본다. */
let lastRequest: { url: string; init?: RequestInit } | null = null;

function respond(make: (url: string) => { status: number; payload: unknown }) {
  lastRequest = null;
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    // 익명 출입증 발급은 이 시험의 관심사가 아니다 — 늘 성공시킨다.
    if (url.includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    lastRequest = { url, init };
    const { status, payload } = make(url);
    return new Response(JSON.stringify(payload), { status, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

describe('getStoryReplies', () => {
  it('서버가 준 댓글을 그대로 돌려준다', async () => {
    respond(() => ({ status: 200, payload: { data: [reply()], error: null, meta: { requestId: 'r1' } } }));

    const outcome = await getStoryReplies(STORY_ID, 'token');

    expect(outcome.state).toBe('success');
    if (outcome.state !== 'success') return;
    expect(outcome.replies).toHaveLength(1);
    expect(outcome.replies[0].parentId).toBe(STORY_ID);
  });

  it('댓글이 없으면 빈 목록이다 — 그건 실패가 아니다', async () => {
    respond(() => ({ status: 200, payload: { data: [], error: null, meta: { requestId: 'r1' } } }));

    const outcome = await getStoryReplies(STORY_ID, 'token');

    expect(outcome.state).toBe('success');
    if (outcome.state !== 'success') return;
    expect(outcome.replies).toEqual([]);
  });

  it('🔴 못 불러오면 실패를 돌려준다 — 빈 목록으로 바꾸지 않는다', async () => {
    respond(() => ({ status: 500, payload: { data: null, error: { code: 'INTERNAL', message: '서버 오류' }, meta: { requestId: 'r1' } } }));

    const outcome = await getStoryReplies(STORY_ID, 'token');

    // 이 한 줄이 이 파일의 이유다. success + [] 로 오면 화면이 「댓글이 없어요」를 그린다.
    expect(outcome.state).not.toBe('success');
  });

  it('사진 주소를 화면이 쓸 수 있는 모양으로 바꾼다 — 원글과 같은 규칙이다', async () => {
    respond(() => ({ status: 200, payload: { data: [reply({ images: [{ url: '/uploads/a.jpg', position: 0 }] })], error: null, meta: { requestId: 'r1' } } }));

    const outcome = await getStoryReplies(STORY_ID, 'token');

    expect(outcome.state).toBe('success');
    if (outcome.state !== 'success') return;
    // 상대 주소가 그대로 나가면 사진이 안 뜬다. 원글(getStory)과 같은 변환을 거쳐야 한다.
    expect(outcome.replies[0].images[0].url).toMatch(/^https?:\/\//);
  });
});

describe('createStory 로 댓글 쓰기', () => {
  it('🔴 부모 id 가 요청에 실린다 — 안 실리면 댓글이 원글로 올라간다', async () => {
    respond(() => ({ status: 201, payload: { data: reply(), error: null, meta: { requestId: 'r1' } } }));

    await createStory({ body: '좋아요', imageUrls: [], parentStoryId: STORY_ID, accessToken: 'token' });

    expect(lastRequest).not.toBeNull();
    const sent = JSON.parse(String(lastRequest?.init?.body ?? '{}'));
    expect(sent.parentStoryId).toBe(STORY_ID);
  });

  it('부모 id 를 안 주면 원글이다 — 기존 글쓰기가 안 바뀐다', async () => {
    respond(() => ({ status: 201, payload: { data: reply({ parentId: null }), error: null, meta: { requestId: 'r1' } } }));

    await createStory({ body: '오늘의 기록', imageUrls: [], accessToken: 'token' });

    const sent = JSON.parse(String(lastRequest?.init?.body ?? '{}'));
    expect(sent.parentStoryId).toBeUndefined();
  });
});
