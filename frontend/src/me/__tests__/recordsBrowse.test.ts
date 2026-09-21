import { extractTags, filterStories, groupByDay, latestMonth, monthCells, regionsOf, shiftMonth, tagsOf } from '../recordsBrowse';
import type { StoryDto } from '@/social/stories';

const story = (id: string, over: Partial<StoryDto> = {}): StoryDto => ({
  id, author: { id: 'u', displayName: '나' }, body: '', images: [], visibility: 'PUBLIC',
  publishAt: '2026-09-20T10:00:00+09:00', createdAt: '2026-09-20T10:00:00+09:00', updatedAt: '2026-09-20T10:00:00+09:00',
  mine: true, published: true, ...over,
});

describe('extractTags — 본문의 #해시태그', () => {
  it('글자·숫자·_ 로 된 태그를 본문 순으로, 중복 없이 뽑는다', () => {
    expect(extractTags('광안리 밤바다 #야경 #광안리 #야경 #busan_2026')).toEqual(['야경', '광안리', 'busan_2026']);
  });
  it('문장 안의 #과 ##는 태그가 아니다', () => {
    expect(extractTags('전화#1 은 태그가 아니다. ##둘 도 아니다. #끝')).toEqual(['끝']);
  });
  it('태그가 없으면 빈 배열', () => {
    expect(extractTags('그냥 글')).toEqual([]);
  });
});

describe('filterStories · regionsOf · tagsOf', () => {
  const list = [
    story('a', { region: '수영구', body: '#야경 #바다' }),
    story('b', { region: '해운대구', body: '#바다' }),
    story('c', { region: '수영구', body: '태그 없음' }),
    story('d', { region: null, body: '#야경' }),
  ];
  it('지역과 태그를 같이 건다', () => {
    expect(filterStories(list, { region: '수영구', tag: null }).map((s) => s.id)).toEqual(['a', 'c']);
    expect(filterStories(list, { region: null, tag: '야경' }).map((s) => s.id)).toEqual(['a', 'd']);
    expect(filterStories(list, { region: '수영구', tag: '야경' }).map((s) => s.id)).toEqual(['a']);
    expect(filterStories(list, { region: null, tag: null })).toHaveLength(4);
  });
  it('칩은 많이 쓴 순, 빈 지역은 뺀다', () => {
    expect(regionsOf(list)).toEqual(['수영구', '해운대구']);
    expect(tagsOf(list)).toEqual(['바다', '야경']);
  });
});

describe('달력', () => {
  it('groupByDay 는 기기 날짜로 묶고 못 읽는 날짜는 뺀다', () => {
    const groups = groupByDay([story('a'), story('b', { publishAt: '2026-09-21T01:00:00+09:00' }), story('x', { publishAt: 'nope', createdAt: 'nope' })]);
    expect([...groups.keys()].sort()).toEqual(['2026-09-20', '2026-09-21']);
    expect(groups.get('2026-09-20')?.map((s) => s.id)).toEqual(['a']);
  });
  it('monthCells 는 일요일 시작 42칸, 이웃 달은 inMonth=false', () => {
    const cells = monthCells(2026, 8); // 2026-09 — 1일이 화요일
    expect(cells).toHaveLength(42);
    expect(cells[0]).toEqual({ key: '2026-08-30', day: 30, inMonth: false });
    expect(cells[2]).toEqual({ key: '2026-09-01', day: 1, inMonth: true });
    expect(cells.filter((c) => c.inMonth)).toHaveLength(30);
  });
  it('latestMonth 는 가장 최근 기록의 달, 없으면 이번 달', () => {
    expect(latestMonth([story('a'), story('b', { publishAt: '2026-07-01T00:00:00+09:00' })])).toEqual({ year: 2026, month0: 8 });
    expect(latestMonth([], new Date(2026, 0, 15))).toEqual({ year: 2026, month0: 0 });
  });
  it('shiftMonth 는 해를 넘긴다', () => {
    expect(shiftMonth({ year: 2026, month0: 11 }, 1)).toEqual({ year: 2027, month0: 0 });
    expect(shiftMonth({ year: 2026, month0: 0 }, -1)).toEqual({ year: 2025, month0: 11 });
  });
});
