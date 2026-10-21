// 지운 알림 — S15P21E201-1907(팀원 요청 「알림 지우는 버튼」).
//
// 🔴 알림은 서버에 저장된 행이 아니라 여행 활동에서 그때그때 셈한 요약이다(activityFeed.ts) — 서버에 지울 대상이 없다.
//    그래서 지운 것을 «이 기기에» 계정별로 기억한다. 다른 기기에서는 다시 보이고, 새로 생긴 알림은 다시 뜬다.
//    「모두 지우기」는 그 시각까지를 지운 것으로 본다(이후 알림만 남는다). 하나씩 지운 것은 id 로 기억한다(최근 300개).
import AsyncStorage from '@react-native-async-storage/async-storage';

export type DismissedNotices = { clearedBefore: string | null; ids: string[] };

const EMPTY: DismissedNotices = { clearedBefore: null, ids: [] };
const MAX_IDS = 300;
const keyFor = (userId: string) => `gabolle:notifications-dismissed:${userId}`;

export async function loadDismissed(userId: string | null | undefined): Promise<DismissedNotices> {
  if (!userId) return EMPTY;
  try {
    const raw = await AsyncStorage.getItem(keyFor(userId));
    if (!raw) return EMPTY;
    const parsed = JSON.parse(raw) as Partial<DismissedNotices>;
    return { clearedBefore: typeof parsed.clearedBefore === 'string' ? parsed.clearedBefore : null, ids: Array.isArray(parsed.ids) ? parsed.ids.filter((id) => typeof id === 'string') : [] };
  } catch {
    return EMPTY;
  }
}

async function save(userId: string, value: DismissedNotices): Promise<DismissedNotices> {
  try { await AsyncStorage.setItem(keyFor(userId), JSON.stringify(value)); } catch { /* 기기 저장이 안 되면 다음에 다시 보일 뿐이다 */ }
  return value;
}

/** 몇 개를 지운다 — 묶음 하나를 지우면 그 안의 알림 id 를 모두 넘긴다. */
export async function dismissNotices(userId: string, current: DismissedNotices, ids: string[]): Promise<DismissedNotices> {
  const merged = [...ids, ...current.ids.filter((id) => !ids.includes(id))].slice(0, MAX_IDS);
  return save(userId, { ...current, ids: merged });
}

/** 지금 보이는 것을 모두 지운다 — 가장 최근 알림 시각까지. 그 뒤에 생기는 알림은 다시 뜬다. */
export async function dismissAllUpTo(userId: string, latestAt: string): Promise<DismissedNotices> {
  return save(userId, { clearedBefore: latestAt, ids: [] });
}

/**
 * 「모두 지우기」 — 받은 알림 «전부»(이미 하나씩 지운 것 포함)에서 가장 최근 시각까지 지운다(S15P21E201-1909).
 * 🔴 전에는 화면에 남은 것만 보고 시각을 정했다. 가장 최근 알림을 먼저 ✕ 로 지운 뒤 모두 지우면 기준이 그보다
 *    앞이 되고 id 목록은 비워져, 지운 알림이 되살아났다(실기기 10/1).
 */
export async function dismissAll<T extends { at: string }>(userId: string, items: T[]): Promise<DismissedNotices | null> {
  if (!items.length) return null;
  const latest = items.reduce((max, item) => (item.at > max ? item.at : max), items[0].at);
  return dismissAllUpTo(userId, latest);
}

/** 지운 것을 뺀 알림. */
export function visibleNotices<T extends { id: string; at: string }>(items: T[], dismissed: DismissedNotices): T[] {
  return items.filter((item) => (!dismissed.clearedBefore || item.at > dismissed.clearedBefore) && !dismissed.ids.includes(item.id));
}
