// 알림 — 서버에 알림 API 가 없어서 화면이 늘 비어 있었다(2026-09-21 실기, S15P21E201-1380).
//
// 그런데 「일정이 만들어졌어요」「동행이 순서를 바꿨어요」는 서버가 이미 여행 활동
// (GET /trips/{id}/activity)으로 준다. 내 여행을 돌며 그것을 모아 시간순으로 세우면 알림이다.
// 지어내는 것이 아니라 있는 것을 모으는 것이다. 읽음 여부는 「마지막으로 본 시각」 하나로 기기에 남긴다.
import AsyncStorage from '@react-native-async-storage/async-storage';

import { getTripActivity, type TripActivityOperation } from '@/trip/collaboration';
import { loadTrips, tripDisplayTitle, type TripSummaryDto } from '@/trip/trips';

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

/** 여행 카드와 같은 제목 규칙 — 이름이 없으면 날짜. */
function titleOf(trip: TripSummaryDto, tx: (ko: string, en: string) => string): string {
  const human = tripDisplayTitle(trip, '');
  if (human) return human;
  if (trip.startDate) {
    const end = trip.endDate && trip.endDate !== trip.startDate ? ` – ${trip.endDate.slice(5).replace('-', '.')}` : '';
    return `${trip.startDate.slice(5).replace('-', '.')}${end} ${tx('여행', 'trip')}`;
  }
  return tx('부산 여행', 'Busan trip');
}

/**
 * @param options.trips 이미 받은 여행 목록. 주면 목록을 다시 부르지 않는다 — 홈 종 점이 홈 카드의 목록을 쓴다(S15P21E201-1686).
 * @param options.maxTrips 활동을 볼 여행 수, 가장 최근에 바뀐 것부터. 주지 않으면 알림 화면의 수(12).
 */
export async function loadActivityFeed(
  accessToken: string | null,
  tx: (ko: string, en: string) => string,
  options: { trips?: TripSummaryDto[]; maxTrips?: number } = {},
): Promise<ActivityFeedResult> {
  let list = options.trips;
  if (!list) {
    const trips = await loadTrips(accessToken);
    if (trips.state !== 'success') return { state: 'error', message: trips.message };
    list = trips.trips;
  }
  const recent = [...list].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, options.maxTrips ?? MAX_TRIPS);
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
        tripTitle: titleOf(trip, tx),
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
  switch (notice.operation) {
    case 'CREATE':
      return { title: tx('일정이 만들어졌어요', 'Your itinerary is ready'), body: `「${notice.tripTitle}」 — ${tx('확인하고 저장해 주세요.', 'Take a look and save it.')}` };
    case 'REGENERATE':
    case 'REGENERATE_DAY':
    // 남은 하루 재계획(장소·순서는 두고 아직 안 지난 방문지의 시각만 다시) — 뜻이 같아 같은 문구. 빠져서 「일정이 바뀌었어요」로 뭉개졌다(S15P21E201-1443).
    case 'REPLAN_DAY':
      return { title: tx('남은 일정을 다시 계획했어요', 'The rest of the day was replanned'), body: `「${notice.tripTitle}」${who ? ` — ${by}${tx('다시 계획했어요.', 'replanned it.')}` : ''}` };
    case 'REVERT':
      return { title: tx('변경을 되돌렸어요', 'A change was undone'), body: `「${notice.tripTitle}」${who ? ` — ${by}${tx('되돌렸어요.', 'undid it.')}` : ''}` };
    case 'REORDER':
      return { title: tx('일정 순서가 바뀌었어요', 'Stops were reordered'), body: `「${notice.tripTitle}」${who ? ` — ${by}${tx('순서를 바꿨어요.', 'reordered the stops.')}` : ''}` };
    case 'LOCK_ITEM':
      return { title: tx('장소가 고정됐어요', 'A stop was pinned'), body: `「${notice.tripTitle}」${who ? ` — ${by}${tx('장소를 고정했어요.', 'pinned a stop.')}` : ''}` };
    case 'REMOVE_ITEM':
      return { title: tx('장소가 빠졌어요', 'A stop was removed'), body: `「${notice.tripTitle}」${who ? ` — ${by}${tx('장소를 뺐어요.', 'removed a stop.')}` : ''}` };
    case 'ADD_ITEM':
    case 'REPLACE_ITEM':
      return { title: tx('장소가 더해졌어요', 'A stop was added'), body: `「${notice.tripTitle}」${who ? ` — ${by}${tx('장소를 더했어요.', 'added a stop.')}` : ''}` };
    default:
      return { title: tx('일정이 바뀌었어요', 'Your itinerary changed'), body: `「${notice.tripTitle}」` };
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
