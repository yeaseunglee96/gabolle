// 알림 — 서버에 알림 API 가 없어서 화면이 늘 비어 있었다(2026-09-21 실기, S15P21E201-1380).
//
// 그런데 「일정이 만들어졌어요」「동행이 순서를 바꿨어요」는 서버가 이미 여행 활동
// (GET /trips/{id}/activity)으로 준다. 내 여행을 돌며 그것을 모아 시간순으로 세우면 알림이다.
// 지어내는 것이 아니라 있는 것을 모으는 것이다. 읽음 여부는 「마지막으로 본 시각」 하나로 기기에 남긴다.
import AsyncStorage from '@react-native-async-storage/async-storage';

import { getTripActivity, type TripActivityOperation } from '@/trip/collaboration';
import { loadTrips, type TripSummaryDto } from '@/trip/trips';
import { tripNameOrDates } from '@/trip/tripNaming';
import { txf } from '@/i18n/format';

export type ActivityNotice = {
  id: string;
  tripId: string;
  itineraryId: string;
  tripTitle: string;
  operation: TripActivityOperation;
  actorName: string | null;
  isMe: boolean;
  at: string;
  warningCodes: string[];
};

export type ActivityFeedResult =
  | { state: 'success'; items: ActivityNotice[] }
  | { state: 'error'; message: string };

const SEEN_KEY = 'gabolle:notifications-seen-at';
const MAX_TRIPS = 12;

/** 여행 카드와 같은 제목 규칙 — 이름이 없으면 날짜(tripNameOrDates, S15P21E201-1738). */
function titleOf(trip: TripSummaryDto, tx: (ko: string, en: string) => string, locale: string): string {
  return tripNameOrDates(trip, tx, locale);
}

export async function loadActivityFeed(accessToken: string | null, tx: (ko: string, en: string) => string, locale = 'ko-KR'): Promise<ActivityFeedResult> {
  const trips = await loadTrips(accessToken);
  if (trips.state !== 'success') return { state: 'error', message: trips.message };
  const recent = [...trips.trips].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, MAX_TRIPS);
  const views = await Promise.all(recent.map((trip) => getTripActivity(trip.tripId, accessToken, 10)));
  const items: ActivityNotice[] = [];
  views.forEach((view, i) => {
    if (view.state !== 'success') return;
    const trip = recent[i];
    for (const entry of view.entries) {
      items.push({
        id: `${entry.itineraryId}:${entry.version}`,
        tripId: trip.tripId,
        itineraryId: entry.itineraryId,
        tripTitle: titleOf(trip, tx, locale),
        operation: entry.operation,
        actorName: entry.actorName,
        isMe: entry.isMe,
        at: entry.at,
        warningCodes: entry.warningCodes ?? [],
      });
    }
  });
  items.sort((a, b) => b.at.localeCompare(a.at));
  return { state: 'success', items };
}

/** 알림 한 줄의 말 — 「무엇이」 「누가」. 서버가 준 사실만 옮긴다. */
export function noticeCopy(notice: ActivityNotice, tx: (ko: string, en: string) => string): { title: string; body: string } {
  const who = notice.isMe ? null : notice.actorName;
  const by = who ? `${who}${tx('님이', '')} ` : '';
  // 여행 이름을 감싸는 부호 — 한국어·일본어 「…」, 영어·중국어 “…”(S15P21E201-1711). 이름 자체는 건드리지 않는다.
  const trip = txf(tx, '「%s」', '“%s”', notice.tripTitle);
  switch (notice.operation) {
    case 'CREATE':
      return { title: tx('일정이 만들어졌어요', 'Your itinerary is ready'), body: `${trip} — ${tx('확인하고 저장해 주세요.', 'Take a look and save it.')}` };
    case 'REGENERATE':
    case 'REGENERATE_DAY':
    // 남은 하루 재계획(장소·순서는 두고 아직 안 지난 방문지의 시각만 다시) — 뜻이 같아 같은 문구. 빠져서 「일정이 바뀌었어요」로 뭉개졌다(S15P21E201-1443).
    case 'REPLAN_DAY':
      return { title: tx('남은 일정을 다시 계획했어요', 'The rest of the day was replanned'), body: `${trip}${who ? ` — ${by}${tx('다시 계획했어요.', 'replanned it.')}` : ''}` };
    case 'REVERT':
      return { title: tx('변경을 되돌렸어요', 'A change was undone'), body: `${trip}${who ? ` — ${by}${tx('되돌렸어요.', 'undid it.')}` : ''}` };
    case 'REORDER':
      return { title: tx('일정 순서가 바뀌었어요', 'Stops were reordered'), body: `${trip}${who ? ` — ${by}${tx('순서를 바꿨어요.', 'reordered the stops.')}` : ''}` };
    case 'LOCK_ITEM':
      return { title: tx('장소가 고정됐어요', 'A stop was pinned'), body: `${trip}${who ? ` — ${by}${tx('장소를 고정했어요.', 'pinned a stop.')}` : ''}` };
    case 'REMOVE_ITEM':
      return { title: tx('장소가 빠졌어요', 'A stop was removed'), body: `${trip}${who ? ` — ${by}${tx('장소를 뺐어요.', 'removed a stop.')}` : ''}` };
    case 'ADD_ITEM':
    case 'REPLACE_ITEM':
      return { title: tx('장소가 더해졌어요', 'A stop was added'), body: `${trip}${who ? ` — ${by}${tx('장소를 더했어요.', 'added a stop.')}` : ''}` };
    default:
      return { title: tx('일정이 바뀌었어요', 'Your itinerary changed'), body: `${trip}` };
  }
}

export async function loadSeenAt(): Promise<string | null> {
  try { return await AsyncStorage.getItem(SEEN_KEY); } catch { return null; }
}

export async function markSeenNow(): Promise<void> {
  try { await AsyncStorage.setItem(SEEN_KEY, new Date().toISOString()); } catch { /* 기기 저장이 안 되면 점이 한 번 더 뜰 뿐이다 */ }
}

/** 안 본 알림이 있나 — 마지막으로 본 시각 뒤의 것. 한 번도 안 봤으면 있는 것 전부. */
export function hasUnseen(items: ActivityNotice[], seenAt: string | null): boolean {
  if (!items.length) return false;
  return seenAt ? items[0].at > seenAt : true;
}

// ── 묶기(UI 캔버스 ⑦) ─────────────────────────────────────────────────────
//
// 🔴 동행이 장소를 빼고·고정하고·또 빼면 알림이 세 줄로 따로 쌓였고, 한 줄 한 줄이 「장소가 빠졌어요」라
//    무엇이 얼마나 바뀌었는지 모아 보기 어려웠다. 같은 여행·같은 사람·가까운 시각(1시간)의 변경은 한 장으로 묶는다.
//    일정이 새로 만들어진 것(CREATE)은 묶지 않는다 — 여행마다 한 번이고 그 자체가 소식이다.

/** 한 장으로 묶는 시간 폭 — 한 번 앉아서 고치는 동안의 변경. */
const GROUP_WINDOW_MS = 60 * 60 * 1000;

export type NoticeGroup = {
  id: string;
  /** 가장 최근 알림 — 제목·시각·이동은 이것을 따른다. */
  latest: ActivityNotice;
  /** 묶인 알림 전부(최신 먼저). */
  items: ActivityNotice[];
};

export function groupNotices(items: ActivityNotice[]): NoticeGroup[] {
  const groups: NoticeGroup[] = [];
  for (const item of items) {
    const last = groups[groups.length - 1];
    const sameWho = (a: ActivityNotice, b: ActivityNotice) => a.isMe === b.isMe && (a.isMe || a.actorName === b.actorName);
    const oldest = last?.items[last.items.length - 1];
    if (
      last && oldest && item.operation !== 'CREATE' && last.latest.operation !== 'CREATE'
      && last.latest.tripId === item.tripId && sameWho(last.latest, item)
      && Math.abs(Date.parse(oldest.at) - Date.parse(item.at)) <= GROUP_WINDOW_MS
    ) {
      last.items.push(item);
      continue;
    }
    groups.push({ id: item.id, latest: item, items: [item] });
  }
  return groups;
}

/** 묶음 안의 변경을 종류별로 센다 — 「장소 2곳을 뺐어요 · 1곳을 고정했어요」. */
export function groupLines(group: NoticeGroup, tx: (ko: string, en: string) => string): { kind: NoticeKind; text: string }[] {
  const count = new Map<NoticeKind, number>();
  for (const item of group.items) count.set(noticeKind(item.operation), (count.get(noticeKind(item.operation)) ?? 0) + 1);
  const order: NoticeKind[] = ['remove', 'lock', 'add', 'reorder', 'replan', 'revert', 'change'];
  return order.filter((kind) => count.has(kind)).map((kind) => {
    const n = String(count.get(kind));
    switch (kind) {
      case 'remove': return { kind, text: txf(tx, '장소 %s곳을 뺐어요', n === '1' ? 'Removed %s stop' : 'Removed %s stops', n) };
      case 'lock': return { kind, text: txf(tx, '장소 %s곳을 고정했어요', n === '1' ? 'Pinned %s stop' : 'Pinned %s stops', n) };
      case 'add': return { kind, text: txf(tx, '장소 %s곳을 더했어요', n === '1' ? 'Added %s stop' : 'Added %s stops', n) };
      case 'reorder': return { kind, text: tx('순서를 바꿨어요', 'Reordered the stops') };
      case 'replan': return { kind, text: tx('남은 일정을 다시 계획했어요', 'Replanned the rest of the day') };
      case 'revert': return { kind, text: tx('변경을 되돌렸어요', 'Undid a change') };
      default: return { kind, text: tx('일정을 바꿨어요', 'Changed the itinerary') };
    }
  });
}

export type NoticeKind = 'created' | 'lock' | 'remove' | 'add' | 'reorder' | 'replan' | 'revert' | 'change';

/** 알림 종류 → 아이콘 종류(NoticeIcon). */
export function noticeKind(operation: TripActivityOperation): NoticeKind {
  switch (operation) {
    case 'CREATE': return 'created';
    case 'LOCK_ITEM': return 'lock';
    case 'REMOVE_ITEM': return 'remove';
    case 'ADD_ITEM':
    case 'REPLACE_ITEM': return 'add';
    case 'REORDER': return 'reorder';
    case 'REGENERATE':
    case 'REGENERATE_DAY':
    case 'REPLAN_DAY': return 'replan';
    case 'REVERT': return 'revert';
    default: return 'change';
  }
}
