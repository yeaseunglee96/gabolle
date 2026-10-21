// 일정 조정 — 머무는 시간 바꾸기 · 고른 자리에 더하기 · 순서 고정/시각 고정 (S15P21E201-2017).
// 화면이 쓰는 순수 계산만 둔다. 서버 요청은 plan/itinerary.ts 에 있다.
import { txf } from '@/i18n/format';
import type { ItineraryDto, ItineraryItemDto, ItineraryLockMode, ItineraryOpeningHoursWarning } from '@/plan/itinerary';

type Tx = (ko: string, en: string) => string;

/** 머무는 시간의 눈금 — 서버 ItineraryEditService.STAY_STEP_MINUTES 와 같다. */
export const STAY_STEP_MINUTES = 15;
/** 머무는 시간의 최소 — 서버와 같다. */
export const MIN_STAY_MINUTES = 15;
/** 머무는 시간의 최대(12시간) — 서버와 같다. */
export const MAX_STAY_MINUTES = 12 * 60;

/** 시작·끝에서 머무는 분. 하나라도 없거나 읽을 수 없으면 null — 모르는 것을 지어내지 않는다. */
export function stayMinutesOf(item: Pick<ItineraryItemDto, 'startsAt' | 'endsAt'>): number | null {
  if (!item.startsAt || !item.endsAt) return null;
  const start = Date.parse(item.startsAt);
  const end = Date.parse(item.endsAt);
  if (Number.isNaN(start) || Number.isNaN(end) || end <= start) return null;
  return Math.round((end - start) / 60000);
}

/**
 * 한 눈금 늘리거나 줄인 값. 눈금에 안 맞는 값(하루를 처음 깔 때 47분 같은 값이 나온다)은 그 방향의 가까운 눈금으로 간다.
 * 15분 밑, 12시간 위로는 안 간다.
 */
export function steppedStay(current: number, direction: 1 | -1): number {
  const next = direction > 0
    ? Math.floor(current / STAY_STEP_MINUTES) * STAY_STEP_MINUTES + STAY_STEP_MINUTES
    : Math.ceil(current / STAY_STEP_MINUTES) * STAY_STEP_MINUTES - STAY_STEP_MINUTES;
  return Math.min(MAX_STAY_MINUTES, Math.max(MIN_STAY_MINUTES, next));
}

/** 「45분」 · 「1시간」 · 「1시간 45분」. */
export function formatStay(minutes: number, tx: Tx): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return txf(tx, '%s분', '%s min', rest);
  if (rest === 0) return txf(tx, '%s시간', '%s h', hours);
  return txf(tx, '%s시간 %s분', '%s h %s min', hours, rest);
}

/** 고정 방식. 옛 서버(칸 없음)의 고정은 순서 고정이다 — 그때의 고정이 하던 일이 그것이다. */
export function lockModeOf(item: Pick<ItineraryItemDto, 'locked' | 'lockMode'>): ItineraryLockMode | null {
  if (!item.locked) return null;
  return item.lockMode ?? 'ORDER';
}

/** 장소를 넣을 자리 — 「맨 앞」, 「○○ 다음」. 자리 번호는 서버와 같이 1부터. */
export function insertPositionOptions(items: Pick<ItineraryItemDto, 'title'>[], tx: Tx, nameOf: (item: Pick<ItineraryItemDto, 'title'>) => string = (item) => item.title): { position: number; label: string }[] {
  return [
    { position: 1, label: tx('맨 앞', 'First') },
    ...items.map((item, index) => ({ position: index + 2, label: txf(tx, '%s 다음', 'After %s', nameOf(item)) })),
  ];
}

/** 일정 조정이 새로 내는 경고 둘을 사람 말로. 영업시간 경고는 따로 그린다(describeOpeningHoursIssues). */
export function describeAdjustWarnings(itinerary: ItineraryDto, warnings: ItineraryOpeningHoursWarning[], tx: Tx, nameOf: (item: ItineraryItemDto) => string = (item) => item.title): string[] {
  const itemsById = new Map(itinerary.days.flatMap((day) => day.items).map((item) => [item.id, item]));
  const titleOf = (id: string) => {
    const item = itemsById.get(id);
    return item ? nameOf(item) : tx('이 장소', 'this place');
  };
  const messages = warnings.flatMap((warning) => {
    if (warning.code === 'DAY_END_EXCEEDED') return [txf(tx, '%s에서 하루 활동 시간을 넘겨요. 다른 곳을 줄이거나 빼 보세요.', 'The day runs past its end at %s. Try shortening or removing another stop.', titleOf(warning.itemId))];
    if (warning.code === 'TIME_FIX_MISSED') return [txf(tx, '%s은(는) 시각 고정이지만 앞 일정이 길어 제 시각에 갈 수 없어요.', "%s has a fixed time, but the stops before it run too long to arrive on time.", titleOf(warning.itemId))];
    return [];
  });
  return [...new Set(messages)];
}
