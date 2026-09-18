// 반응 토글과 낙관적 수 계산 —.
import { applyReaction, nextReaction, type ReactableStory } from '@/social/StoryReactionRow';

function story(over: Partial<ReactableStory> = {}): ReactableStory {
  return { myReaction: null, likeCount: 0, dislikeCount: 0, ...over };
}

describe('반응 토글 판정', () => {
  it('안 누른 상태에서 누르면 그것이 켜진다', () => {
    expect(nextReaction(null, 'LIKE')).toBe('LIKE');
    expect(nextReaction(null, 'DISLIKE')).toBe('DISLIKE');
  });

  it('같은 것을 다시 누르면 꺼진다 — 서버로는 DELETE 가 간다', () => {
    expect(nextReaction('LIKE', 'LIKE')).toBeNull();
    expect(nextReaction('DISLIKE', 'DISLIKE')).toBeNull();
  });

  it('다른 것을 누르면 바꾼다 — 두 번 누르게 하지 않는다', () => {
    expect(nextReaction('LIKE', 'DISLIKE')).toBe('DISLIKE');
    expect(nextReaction('DISLIKE', 'LIKE')).toBe('LIKE');
  });

  it('값이 없는 것과 null 을 같게 다룬다 — 서버가 칸을 빼고 줄 수 있다', () => {
    expect(nextReaction(undefined, 'LIKE')).toBe('LIKE');
  });
});

describe('낙관적 수 계산', () => {
  it('좋아요를 켜면 좋아요만 오른다', () => {
    expect(applyReaction(story({ likeCount: 3, dislikeCount: 1 }), 'LIKE'))
      .toEqual({ myReaction: 'LIKE', likeCount: 4, dislikeCount: 1 });
  });

  it('좋아요에서 싫어요로 바꾸면 한쪽이 내리고 한쪽이 오른다', () => {
    expect(applyReaction(story({ myReaction: 'LIKE', likeCount: 4, dislikeCount: 1 }), 'DISLIKE'))
      .toEqual({ myReaction: 'DISLIKE', likeCount: 3, dislikeCount: 2 });
  });

  it('끄면 그 칸만 내린다', () => {
    expect(applyReaction(story({ myReaction: 'DISLIKE', likeCount: 2, dislikeCount: 5 }), null))
      .toEqual({ myReaction: null, likeCount: 2, dislikeCount: 4 });
  });

  it('껐다 켜기를 반복해도 처음 수로 돌아온다', () => {
    const start = story({ likeCount: 7, dislikeCount: 2 });
    let current = start;
    for (let i = 0; i < 5; i += 1) {
      current = applyReaction(current, nextReaction(current.myReaction, 'LIKE'));
      current = applyReaction(current, nextReaction(current.myReaction, 'LIKE'));
    }
    expect(current).toEqual(start);
  });

  it('서버가 칸을 안 준 글도 0 에서 센다 — NaN 으로 그리지 않는다', () => {
    expect(applyReaction({ myReaction: null }, 'LIKE'))
      .toEqual({ myReaction: 'LIKE', likeCount: 1, dislikeCount: 0 });
  });

  it('원본을 고치지 않는다 — 목록 캐시를 제자리에서 바꾸면 리렌더가 안 걸린다', () => {
    const before = story({ likeCount: 1 });
    applyReaction(before, 'LIKE');
    expect(before).toEqual({ myReaction: null, likeCount: 1, dislikeCount: 0 });
  });
});
