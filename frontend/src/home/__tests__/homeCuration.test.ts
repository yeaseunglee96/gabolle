// S15P21E201-2021 — 홈 정리: 기록 줄은 고른 글이 먼저, 로컬 탐색은 갈래마다 한 칸씩.
// S15P21E201-2059 — 로컬 탐색 줄에 맛집·카페 두 칸을 더했다(여섯 칸 → 여덟 칸). 2026-10-15 사용자 지시로 맨 앞 → 맨 뒤.
jest.mock('@/discovery/places', () => ({ getPlace: jest.fn(), getPlacesByFacet: jest.fn() }));
jest.mock('@/discovery/festivals', () => ({ getFestivals: jest.fn(), festivalDisplayTitle: (f: { title?: string; nameKo: string }) => f.title ?? f.nameKo }));
import { getPlace } from '@/discovery/places';
import { getFestivals } from '@/discovery/festivals';
import { localFacetLabel } from '@/discovery/localExplore';
import { currentFeaturedFestival, FEATURED_STORY_IDS, LOCAL_PICK_ORDER, LOCAL_PICK_PLACES, pickFestival, pickLabel, type LocalPickKey } from '@/home/homeCuration';
import { loadLocalPicks, pickHeroStories } from '@/home/useHomeData';
import { LANGUAGE_CODES } from '@/i18n/languages';
import { pickLanguage } from '@/i18n/pick';
import { getTranslation } from '@/i18n/translations';
import type { StoryDto } from '@/social/stories';

const story = (id: string, image = true) => ({ id, images: image ? [{ url: 'x', position: 0 }] : [] }) as unknown as StoryDto;

// 맛집·카페 칸에 고정한 장소 — 바꾸라는 지시가 없으면 바뀌지 않아야 하는 값이라 시험에도 적어 둔다.
const RESTAURANT_ID = 'd330bc4c-3a6c-3d05-8e66-13ba3707b1d0'; // 이재모피자(중구 광복중앙로) — 2026-10-17 지시
const CAFE_ID = '5416ddfb-0d2b-3eac-aaf6-d965f59add30'; // 모모스커피 부산본점(금정구 오시게로 20)

describe('기록 줄 — 고른 글이 먼저', () => {
  it('고정 글이 원래 순서와 상관없이 맨 앞에 서고, 같은 글은 두 번 안 나온다', () => {
    const pinned = story(FEATURED_STORY_IDS[0]);
    const picked = pickHeroStories([story('a'), story('b'), pinned], [pinned]);
    expect(picked.map((s) => s.id)).toEqual([FEATURED_STORY_IDS[0], 'a', 'b']);
  });
});

describe('로컬 탐색 여덟 칸', () => {
  it('예전 여섯 칸이 앞이고 맛집·카페가 맨 뒤이며, 이름표가 여섯 언어 다 있다', () => {
    expect(LOCAL_PICK_ORDER).toEqual(['FESTIVAL', 'LOCAL_RESTAURANT', 'LOCAL_CAFE', 'NIGHT_VIEW', 'TRADITIONAL_MARKET', 'NATURE', 'WALK', 'ACTIVITY']);
    for (const key of LOCAL_PICK_ORDER) for (const lang of LANGUAGE_CODES) expect(pickLabel(key, lang)).toBeTruthy();
  });

  it('맛집은 수변최고돼지국밥, 카페는 모모스커피 부산본점(영도점이 아니다)으로 고정이고, 칸마다 다른 장소다', () => {
    expect(LOCAL_PICK_PLACES.LOCAL_RESTAURANT).toBe(RESTAURANT_ID);
    expect(LOCAL_PICK_PLACES.LOCAL_CAFE).toBe(CAFE_ID);
    expect(LOCAL_PICK_PLACES.LOCAL_CAFE).not.toBe('a6e61415-5b19-3e1a-b069-a7876fbb92c6'); // 모모스커피 영도점
    const ids = Object.values(LOCAL_PICK_PLACES);
    for (const id of ids) expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
    expect(new Set(ids).size).toBe(ids.length);
    // 축제를 뺀 모든 칸에 장소가 있다 — 빠진 칸은 카드가 조용히 사라진다.
    for (const key of LOCAL_PICK_ORDER) if (key !== 'FESTIVAL') expect(LOCAL_PICK_PLACES[key]).toBeTruthy();
  });

  it('🔴 맛집·카페 이름표는 로컬 탐색 화면의 갈래 칩과 같은 말이다 — 칩 이름을 만드는 localFacetLabel 과 번역표를 따른다', () => {
    const chips: Array<[LocalPickKey, string]> = [['LOCAL_RESTAURANT', '맛집'], ['LOCAL_CAFE', '카페']];
    for (const [featureKey, labelKo] of chips) {
      const chip = { featureKey, labelKo, labelEn: null, placeCount: 1 }; // 서버는 갈래 이름을 한국어로만 준다
      for (const lang of ['ko', 'en', 'ja', 'zh-Hans', 'zh-Hant', 'vi'] as const) {
        const tx = (ko: string, en: string) => pickLanguage(lang, { ko, en });
        expect(pickLabel(featureKey, lang)).toBe(localFacetLabel(chip, lang, tx));
      }
      // 🔴 베트남어도 번역표의 말이다 — 전에는 칩 쪽이 localFacetLabel 이 일본어·중국어만 번역표로 보내서 영어로 나왔다(S15P21E201-2060).
      expect(pickLabel(featureKey, 'vi')).toBe(getTranslation(labelKo, 'vi'));
    }
  });

  it('BIFF 는 기간 안에만 축제 칸에 선다', () => {
    expect(currentFeaturedFestival('2026-10-09')?.title.ko).toContain('부산국제영화제');
    // 2026-10-17 지시 — 영화제가 끝난 뒤 홈 축제 칸 표지는 부산불꽃축제.
    expect(currentFeaturedFestival('2026-10-17')?.title.ko).toBe('부산불꽃축제');
    expect(currentFeaturedFestival('2026-10-17')?.placeId).toBe('68c965a4-9b99-331b-a8f1-d7f0a31ba419');
    expect(currentFeaturedFestival('2028-01-10')).toBeNull();
  });

  it('축제 데이터에서는 오늘 열리는 것 중 짧은 것, 없으면 가장 먼저 열리는 것', () => {
    const year = { startDate: '2026-01-01', endDate: '2026-12-31' };
    const week = { startDate: '2026-10-05', endDate: '2026-10-11' };
    const later = { startDate: '2026-10-20', endDate: '2026-10-21' };
    expect(pickFestival([year, week, later], '2026-10-09')).toBe(week);
    expect(pickFestival([later], '2026-10-09')).toBe(later);
  });

  it('여덟 칸을 만들고, BIFF 기간에는 축제 칸이 영화의전당·BIFF 이름이다. 한 곳이 실패하면 그 칸만 빠진다', async () => {
    (getPlace as jest.Mock).mockImplementation(async (id: string) => {
      if (id === 'd7d68934-1a4e-31c5-836a-0f94134e81e4') throw new Error('boom');
      return { placeId: id, nameKo: `place-${id.slice(0, 4)}`, nameEn: null, address: '부산' };
    });
    (getFestivals as jest.Mock).mockResolvedValue([]);
    const picks = await loadLocalPicks(new Date(2026, 9, 9));
    expect(picks.map((p) => p.key)).toEqual(['FESTIVAL', 'LOCAL_RESTAURANT', 'LOCAL_CAFE', 'TRADITIONAL_MARKET', 'NATURE', 'WALK', 'ACTIVITY']);
    const festival = picks.find((p) => p.key === 'FESTIVAL');
    expect(festival?.place.placeId).toBe('d8836d09-0c5c-4d15-a962-95894af98d2e');
    expect(festival?.title?.ko).toContain('부산국제영화제');
  });

  it('맛집·카페 칸은 고정한 장소를 서버에서 받아 그리고, 「더 보기」는 로컬 탐색의 맛집·카페 갈래로 간다', async () => {
    (getPlace as jest.Mock).mockClear();
    (getPlace as jest.Mock).mockImplementation(async (id: string) => ({ placeId: id, nameKo: `place-${id.slice(0, 4)}`, nameEn: null, address: '부산' }));
    (getFestivals as jest.Mock).mockResolvedValue([]);
    const picks = await loadLocalPicks(new Date(2026, 9, 9));
    expect(picks.slice(1, 3).map((p) => [p.key, p.place.placeId, p.href])).toEqual([
      ['LOCAL_RESTAURANT', RESTAURANT_ID, '/explore?facet=LOCAL_RESTAURANT'],
      ['LOCAL_CAFE', CAFE_ID, '/explore?facet=LOCAL_CAFE'],
    ]);
    // 이름·주소는 서버 답을 그대로 쓴다 — 코드에 적어 둔 것은 placeId 뿐이다.
    expect(picks.find((p) => p.key === 'LOCAL_RESTAURANT')!.place.nameKo).toBe(`place-${RESTAURANT_ID.slice(0, 4)}`);
    const asked = (getPlace as jest.Mock).mock.calls.map(([id]) => id);
    expect(asked).toEqual(expect.arrayContaining([RESTAURANT_ID, CAFE_ID]));
  });

  it('🔴 맛집이나 카페를 못 불러오면 그 카드만 빠지고, 줄 전체는 그대로 선다', async () => {
    (getFestivals as jest.Mock).mockResolvedValue([]);
    const failing = (ids: string[]) => async (id: string) => {
      if (ids.includes(id)) throw new Error('boom');
      return { placeId: id, nameKo: `place-${id.slice(0, 4)}`, nameEn: null, address: '부산' };
    };

    (getPlace as jest.Mock).mockImplementation(failing([RESTAURANT_ID]));
    expect((await loadLocalPicks(new Date(2026, 9, 9))).map((p) => p.key)).toEqual(['FESTIVAL', 'LOCAL_CAFE', 'NIGHT_VIEW', 'TRADITIONAL_MARKET', 'NATURE', 'WALK', 'ACTIVITY']);

    (getPlace as jest.Mock).mockImplementation(failing([CAFE_ID]));
    expect((await loadLocalPicks(new Date(2026, 9, 9))).map((p) => p.key)).toEqual(['FESTIVAL', 'LOCAL_RESTAURANT', 'NIGHT_VIEW', 'TRADITIONAL_MARKET', 'NATURE', 'WALK', 'ACTIVITY']);

    (getPlace as jest.Mock).mockImplementation(failing([RESTAURANT_ID, CAFE_ID]));
    expect((await loadLocalPicks(new Date(2026, 9, 9))).map((p) => p.key)).toEqual(['FESTIVAL', 'NIGHT_VIEW', 'TRADITIONAL_MARKET', 'NATURE', 'WALK', 'ACTIVITY']);
  });

  it('대표 축제 기간이 아니면 축제 데이터의 축제가 칸을 채운다', async () => {
    (getPlace as jest.Mock).mockImplementation(async (id: string) => ({ placeId: id, nameKo: id, nameEn: null, address: '부산' }));
    (getFestivals as jest.Mock).mockResolvedValue([{ placeId: 'fest', nameKo: '불꽃축제', address: '부산', startDate: '2028-02-07', endDate: '2028-02-07', overlapDates: [] }]);
    // 부산불꽃축제 대표 칸(2026-10-16 ~ 2027-12-31)이 끝난 뒤의 날짜로 본다.
    const picks = await loadLocalPicks(new Date(2028, 1, 1));
    expect(picks.find((p) => p.key === 'FESTIVAL')).toMatchObject({ key: 'FESTIVAL', place: { placeId: 'fest', nameKo: '불꽃축제' } });
  });
});
