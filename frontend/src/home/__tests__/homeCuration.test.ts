// S15P21E201-2021 — 홈 정리: 기록 줄은 고른 글이 먼저, 로컬 탐색은 여섯 갈래에 한 칸씩.
jest.mock('@/discovery/places', () => ({ getPlace: jest.fn(), getPlacesByFacet: jest.fn() }));
jest.mock('@/discovery/festivals', () => ({ getFestivals: jest.fn(), festivalDisplayTitle: (f: { title?: string; nameKo: string }) => f.title ?? f.nameKo }));
import { getPlace } from '@/discovery/places';
import { getFestivals } from '@/discovery/festivals';
import { currentFeaturedFestival, FEATURED_STORY_IDS, LOCAL_PICK_ORDER, pickFestival, pickLabel } from '@/home/homeCuration';
import { loadLocalPicks, pickHeroStories } from '@/home/useHomeData';
import { LANGUAGE_CODES } from '@/i18n/languages';
import type { StoryDto } from '@/social/stories';

const story = (id: string, image = true) => ({ id, images: image ? [{ url: 'x', position: 0 }] : [] }) as unknown as StoryDto;

describe('기록 줄 — 고른 글이 먼저', () => {
  it('고정 글이 원래 순서와 상관없이 맨 앞에 서고, 같은 글은 두 번 안 나온다', () => {
    const pinned = story(FEATURED_STORY_IDS[0]);
    const picked = pickHeroStories([story('a'), story('b'), pinned], [pinned]);
    expect(picked.map((s) => s.id)).toEqual([FEATURED_STORY_IDS[0], 'a', 'b']);
  });
});

describe('로컬 탐색 여섯 칸', () => {
  it('축제·전통시장·액티비티·산책·자연·야경 순서이고 이름표가 여섯 언어 다 있다', () => {
    expect(LOCAL_PICK_ORDER).toEqual(['FESTIVAL', 'TRADITIONAL_MARKET', 'ACTIVITY', 'WALK', 'NATURE', 'NIGHT_VIEW']);
    for (const key of LOCAL_PICK_ORDER) for (const lang of LANGUAGE_CODES) expect(pickLabel(key, lang)).toBeTruthy();
  });

  it('BIFF 는 기간 안에만 축제 칸에 선다', () => {
    expect(currentFeaturedFestival('2026-10-09')?.title.ko).toContain('부산국제영화제');
    expect(currentFeaturedFestival('2026-10-16')).toBeNull();
  });

  it('축제 데이터에서는 오늘 열리는 것 중 짧은 것, 없으면 가장 먼저 열리는 것', () => {
    const year = { startDate: '2026-01-01', endDate: '2026-12-31' };
    const week = { startDate: '2026-10-05', endDate: '2026-10-11' };
    const later = { startDate: '2026-10-20', endDate: '2026-10-21' };
    expect(pickFestival([year, week, later], '2026-10-09')).toBe(week);
    expect(pickFestival([later], '2026-10-09')).toBe(later);
  });

  it('여섯 칸을 만들고, BIFF 기간에는 축제 칸이 영화의전당·BIFF 이름이다. 한 곳이 실패하면 그 칸만 빠진다', async () => {
    (getPlace as jest.Mock).mockImplementation(async (id: string) => {
      if (id === 'd7d68934-1a4e-31c5-836a-0f94134e81e4') throw new Error('boom');
      return { placeId: id, nameKo: `place-${id.slice(0, 4)}`, nameEn: null, address: '부산' };
    });
    (getFestivals as jest.Mock).mockResolvedValue([]);
    const picks = await loadLocalPicks(new Date(2026, 9, 9));
    expect(picks.map((p) => p.key)).toEqual(['FESTIVAL', 'TRADITIONAL_MARKET', 'ACTIVITY', 'WALK', 'NATURE']);
    expect(picks[0].place.placeId).toBe('d8836d09-0c5c-4d15-a962-95894af98d2e');
    expect(picks[0].title?.ko).toContain('부산국제영화제');
  });

  it('BIFF 가 끝나면 축제 데이터의 축제가 칸을 채운다', async () => {
    (getPlace as jest.Mock).mockImplementation(async (id: string) => ({ placeId: id, nameKo: id, nameEn: null, address: '부산' }));
    (getFestivals as jest.Mock).mockResolvedValue([{ placeId: 'fest', nameKo: '불꽃축제', address: '부산', startDate: '2026-11-07', endDate: '2026-11-07', overlapDates: [] }]);
    const picks = await loadLocalPicks(new Date(2026, 10, 1));
    expect(picks[0]).toMatchObject({ key: 'FESTIVAL', place: { placeId: 'fest', nameKo: '불꽃축제' } });
  });
});
