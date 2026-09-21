// 마이페이지 「기록」을 달력으로 보고 지역·#태그로 거르는 순수 함수들 — S15P21E201-1444.
//
// 🔴 전부 프론트에서 한다. 서버 기록(StoryDto)에는 태그도, 날짜별 묶음도 없다.
//    태그는 본문의 «#해시태그»에서 뽑는다(인스타처럼 본문에 적는 것) — 서버에 태그 칸이
//    생기면 그때 extractTags 만 바꾸면 된다. 부르는 쪽은 모른다.
import type { StoryDto } from '@/social/stories';

/** 「#광안리 #야경」 → ['광안리', '야경']. 순서는 본문 순, 중복은 하나로, 글자·숫자·_ 만. */
export function extractTags(body: string): string[] {
  const found: string[] = [];
  for (const match of body.matchAll(/(?:^|[^\p{L}\p{N}_#])#([\p{L}\p{N}_]+)/gu)) {
    const tag = match[1];
    if (!found.includes(tag)) found.push(tag);
  }
  return found;
}

/** 기기 시간대 기준 「2026-09-21」. 못 읽으면 null — 달력에 안 놓는다. */
export function dayKeyOf(iso: string): string | null {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return keyOfDate(date);
}

function keyOfDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

/** 기록의 날 — 예약 발행이면 publishAt, 아니면 createdAt. 화면에서 「그날」이라고 부르는 것과 같다. */
export function recordDay(story: StoryDto): string | null {
  return dayKeyOf(story.publishAt || story.createdAt);
}

export type RecordsFilter = { region: string | null; tag: string | null };

export function filterStories(stories: readonly StoryDto[], filter: RecordsFilter): StoryDto[] {
  return stories.filter((story) => {
    if (filter.region && (story.region ?? '') !== filter.region) return false;
    if (filter.tag && !extractTags(story.body).includes(filter.tag)) return false;
    return true;
  });
}

/** 칩에 늘어놓을 지역 — 많이 쓴 순, 같으면 이름순. 빈 지역은 뺀다. */
export function regionsOf(stories: readonly StoryDto[]): string[] {
  return rankByCount(stories.map((story) => story.region ?? '').filter(Boolean));
}

/** 칩에 늘어놓을 태그 — 많이 쓴 순. */
export function tagsOf(stories: readonly StoryDto[]): string[] {
  return rankByCount(stories.flatMap((story) => extractTags(story.body)));
}

function rankByCount(values: string[]): string[] {
  const count = new Map<string, number>();
  for (const value of values) count.set(value, (count.get(value) ?? 0) + 1);
  return [...count.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).map(([value]) => value);
}

/** 날짜 → 그날 기록(최신이 앞). 날짜를 못 읽은 기록은 빠진다. */
export function groupByDay(stories: readonly StoryDto[]): Map<string, StoryDto[]> {
  const groups = new Map<string, StoryDto[]>();
  for (const story of stories) {
    const key = recordDay(story);
    if (!key) continue;
    const list = groups.get(key) ?? [];
    list.push(story);
    groups.set(key, list);
  }
  return groups;
}

export type MonthCell = { key: string; day: number; inMonth: boolean };

/**
 * 달력 한 달의 칸 — 일요일 시작, 6줄 42칸 고정. 앞뒤 빈칸은 이웃 달의 날짜로 채우되 inMonth=false.
 * 6줄로 고정하는 이유: 달마다 5줄·6줄이 오가면 아래 내용이 위아래로 뛴다.
 */
export function monthCells(year: number, month0: number): MonthCell[] {
  const first = new Date(year, month0, 1);
  const start = new Date(year, month0, 1 - first.getDay());
  const cells: MonthCell[] = [];
  for (let i = 0; i < 42; i += 1) {
    const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i);
    cells.push({ key: keyOfDate(date), day: date.getDate(), inMonth: date.getMonth() === month0 });
  }
  return cells;
}

/** 처음 열었을 때 보여 줄 달 — 가장 최근 기록의 달. 기록이 없으면 이번 달. */
export function latestMonth(stories: readonly StoryDto[], now: Date = new Date()): { year: number; month0: number } {
  let best: string | null = null;
  for (const story of stories) {
    const key = recordDay(story);
    if (key && (!best || key > best)) best = key;
  }
  if (!best) return { year: now.getFullYear(), month0: now.getMonth() };
  return { year: Number(best.slice(0, 4)), month0: Number(best.slice(5, 7)) - 1 };
}

export function shiftMonth(value: { year: number; month0: number }, by: number): { year: number; month0: number } {
  const date = new Date(value.year, value.month0 + by, 1);
  return { year: date.getFullYear(), month0: date.getMonth() };
}
