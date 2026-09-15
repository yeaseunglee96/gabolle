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
  it('공개 피드에서 사진 있는 기록을 우선해 홈 카드 3개를 고른다', () => {
    const picked = pickHeroStories([
      story('text-1'), story('photo-1', true), story('text-2'), story('photo-2', true), story('photo-3', true), story('photo-4', true),
    ]);
    expect(picked.map((item) => item.id)).toEqual(['photo-1', 'photo-2', 'photo-3']);
  });
});
