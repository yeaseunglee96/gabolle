import { pickHeroStories } from '@/home/useHomeData';
import type { StoryDto } from '@/social/stories';

const story = (id: string, image = false): StoryDto => ({
  id,
  body: id,
  region: 'BUSAN',
  visibility: 'PUBLIC',
  mine: false,
  published: true,
  author: { id: 'author', displayName: '여행자' },
  images: image ? [{ url: `https://example.com/${id}.jpg`, position: 0 }] : [],
  place: null,
  tripId: null,
  publishAt: '2026-09-15T12:00:00Z',
  createdAt: '2026-09-15T12:00:00Z',
  updatedAt: '2026-09-15T12:00:00Z',
});

describe('home guest feed', () => {
  // 개수를 시험에 박지 않는다 — 줄 배치로 3에서 8로 늘었고, 다음에 또 바뀐다.
  // 지켜야 하는 것은 「사진 있는 글이 먼저」이지 몇 장이냐가 아니다.
  it('사진 있는 기록을 먼저 고른다 — 사진 없는 글이 앞자리를 차지하지 않는다', () => {
    const picked = pickHeroStories([
      story('text-1'), story('photo-1', true), story('text-2'), story('photo-2', true), story('photo-3', true), story('photo-4', true),
    ]);
    const ids = picked.map((item) => item.id);
    const firstTextAt = ids.findIndex((id) => id.startsWith('text-'));
    const lastPhotoAt = ids.map((id) => id.startsWith('photo-')).lastIndexOf(true);

    // 사진 넷이 전부 텍스트보다 앞에 있다.
    expect(ids.filter((id) => id.startsWith('photo-'))).toEqual(['photo-1', 'photo-2', 'photo-3', 'photo-4']);
    expect(lastPhotoAt).toBeLessThan(firstTextAt);
  });

  it('상한을 넘으면 자른다 — 그리고 자를 때도 사진이 먼저다', () => {
    // 사진 하나에 글 스무 개. 상한이 몇이든 첫 장은 사진이어야 한다.
    const many = [...Array(20)].map((_, index) => story(`text-${index}`));
    const picked = pickHeroStories([...many, story('photo-1', true)]);

    expect(picked[0].id).toBe('photo-1');
    expect(picked.length).toBeLessThan(21);
  });
});
