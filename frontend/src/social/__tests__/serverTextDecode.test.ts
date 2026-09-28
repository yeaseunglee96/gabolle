// 서버가 HTML 인코딩해 보낸 글자를 받는 곳에서 되돌린다 — S15P21E201-1657.
//
// 🔴 이 시험이 지키는 것: 운영 서버는 기록 작성자 이름·기록 본문·장소 후기 본문을 HTML 인코딩해서 보낸다
//    (& → &amp; · ' → &#39;). 그대로 두면
//    ① 이름·후기처럼 그냥 찍는 곳에 「Tom &amp; Jerry」가 보이고
//    ② 댓글 수정 창이 인코딩된 본문을 입력칸에 넣어, & 가 든 댓글을 고쳐 저장할 때마다 &amp; 가 글자로 불어난다.
//    그래서 받는 한 곳(기록·후기를 불러오는 함수)에서 되돌린다.
import { decodeHtmlText } from '../htmlText';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn() }));
const { apiRequest } = jest.requireMock('@/api/client') as { apiRequest: jest.Mock };

import { getStory, getStoryReplies } from '../stories';
import { loadPlaceReviews, submitPlaceReview } from '@/review/placeReviews';

const story = (id: string) => ({
  id, author: { id: 'u', displayName: 'Tom &amp; Jerry&#39;s' }, body: '광안리 &amp; 민락 &#39;야경&#39; &lt;3', parentId: null, images: [],
  visibility: 'PUBLIC', publishAt: '2026-09-24T10:00:00Z', createdAt: '2026-09-24T10:00:00Z', updatedAt: '2026-09-24T10:00:00Z', mine: false, published: true,
});

beforeEach(() => apiRequest.mockReset());

describe('서버가 인코딩한 글자 되돌리기', () => {
  it('HTML 인코딩 다섯 가지와 번호 표기를 되돌리고, 모르는 것은 그대로 둔다', () => {
    expect(decodeHtmlText('A &amp; B &lt;b&gt; &quot;x&quot; &#39;y&#39; &#x27;z&#x27; &apos;w&apos;')).toBe('A & B <b> "x" \'y\' \'z\' \'w\'');
    expect(decodeHtmlText('&nbsp;그대로 &unknown;')).toBe('&nbsp;그대로 &unknown;');
  });

  it('🔴 기록 하나를 불러오면 작성자 이름·본문이 되돌아온다', async () => {
    apiRequest.mockResolvedValueOnce(story('s1'));
    const result = await getStory('s1-decode', null);
    expect(result).toMatchObject({ state: 'success', story: { author: { displayName: "Tom & Jerry's" }, body: "광안리 & 민락 '야경' <3" } });
  });

  it('🔴 댓글 목록도 되돌아온다 — 수정 창이 이 본문을 입력칸에 그대로 넣는다', async () => {
    apiRequest.mockResolvedValueOnce([story('r1')]);
    const result = await getStoryReplies('s1', null);
    expect(result.state === 'success' && result.replies[0].body).toBe("광안리 & 민락 '야경' <3");
    expect(result.state === 'success' && result.replies[0].author.displayName).toBe("Tom & Jerry's");
  });

  it('🔴 장소 후기 본문도 되돌아온다 — 목록과 방금 쓴 후기 둘 다', async () => {
    const review = { id: 'v1', body: '맛있어요 &amp; 친절해요 &#39;또 올게요&#39;', mine: true };
    apiRequest.mockResolvedValueOnce({ reviews: [review], averageScore: 4 });
    const list = await loadPlaceReviews('p1', null);
    expect(list.state === 'success' && list.reviews[0].body).toBe("맛있어요 & 친절해요 '또 올게요'");
    expect(list.state === 'success' && list.mine?.body).toBe("맛있어요 & 친절해요 '또 올게요'");
    apiRequest.mockResolvedValueOnce(review);
    const posted = await submitPlaceReview({ placeId: 'p1', food: 'HIGH', price: null, accessibility: null, onsite: null, body: '맛있어요 & 친절해요', accessToken: null });
    expect(posted.state === 'success' && posted.review.body).toBe("맛있어요 & 친절해요 '또 올게요'");
  });
});
