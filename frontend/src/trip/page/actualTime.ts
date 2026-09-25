// 여행 중 도착·출발 기록의 규칙 — 화면과 떨어진 계산만 둔다(S15P21E201-1690). 시험이 화면을 띄우지 않고 규칙을 본다.
//
// 🔴 서버는 도착과 출발이 «둘 다» 있어야 그곳을 「다녀옴」으로 본다(ItineraryDelayProjector.isVisited).
//    도착만 적힌 곳은 「아직 거기 있다」 — 그곳이 지금 머무는 곳이다.
// 🔴 출발을 보낼 때는 이미 적힌 도착을 함께 싣는다. 보낸 것이 그 방문지의 최종 상태라서, 출발만 보내면 도착이 지워진다(07 계약).
import type { StopOutcome } from '@/plan/tripProgress';

const MINUTE = 60_000;

/**
 * 지금 머무는 곳 — 가장 나중에 도착한 곳 «하나». 그곳의 출발이 적혔으면 머무는 곳은 없다.
 *
 * 🔴 그 뒤 방문지에 도착이 적힌 앞 곳들은 출발이 비어 있어도 「다녀옴」이다(조율 세션 결정). 출발 시각은
 *    지어내지 않고 비워 둔다 — 다음 도착으로 채우면 머문 시간이 실제보다 길게 적히고, 그 값이 체류 시간 보정으로 들어간다.
 * @param departed 출발까지 적힌 곳(서버의 「다녀옴」, 또는 방금 이 화면에서 출발을 적은 곳).
 */
export function stayingStopId(stopIds: string[], outcomes: Record<string, StopOutcome>, departed: ReadonlySet<string>): string | null {
  for (let index = stopIds.length - 1; index >= 0; index -= 1) {
    const id = stopIds[index];
    if (outcomes[id]?.kind !== 'ARRIVED') continue;
    return departed.has(id) ? null : id;
  }
  return null;
}

/** 적힌 도착 시각. 도착이 아니면(안 갔거나 건너뜀) null. */
export function arrivedAtOf(outcomes: Record<string, StopOutcome>, stopId: string): string | null {
  const outcome = outcomes[stopId];
  return outcome?.kind === 'ARRIVED' && outcome.at ? outcome.at : null;
}

/** 시각 고치기의 빠른 고르기 — 지금 · 5분 전 · 15분 전. */
export const QUICK_OFFSETS_MIN = [0, 5, 15] as const;
/** 「직접」에서 한 번 누를 때 움직이는 양. */
export const STEP_MIN = 5;

/**
 * 고를 수 있는 범위 안으로 넣는다 — 미래는 안 되고(아직 일어나지 않은 일), 아래 한계보다 이르면 안 된다
 * (출발은 도착보다 이를 수 없다 — 서버가 400 을 준다. 도착은 그날 0시보다 이를 수 없다).
 */
export function clampActualTime(ms: number, bounds: { nowMs: number; minMs: number }): number {
  return Math.max(bounds.minMs, Math.min(bounds.nowMs, ms));
}

/** 그날 0시(이 기기의 시간대). 도착 시각 고치기의 아래 한계. */
export function startOfLocalDay(ms: number): number {
  const date = new Date(ms);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

/** 지금에서 N분 전 — 범위 안으로. */
export function minutesBefore(nowMs: number, minutes: number, minMs: number): number {
  return clampActualTime(nowMs - minutes * MINUTE, { nowMs, minMs });
}

/** 「직접」의 −/+ — 범위 안으로. */
export function stepTime(ms: number, deltaMin: number, bounds: { nowMs: number; minMs: number }): number {
  return clampActualTime(ms + deltaMin * MINUTE, bounds);
}

/** 서버로 보낼 모양 — 기기 시간대의 오프셋을 붙인 ISO-8601(부산이면 +09:00). */
export function isoWithOffset(ms: number): string {
  const date = new Date(ms);
  const pad = (value: number, size = 2) => String(value).padStart(size, '0');
  const offset = -date.getTimezoneOffset();
  const sign = offset < 0 ? '-' : '+';
  const abs = Math.abs(offset);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`;
}

/** 「11:02」 — 기기 시간대. */
export function clockOf(iso: string | number): string {
  const date = new Date(iso);
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}
