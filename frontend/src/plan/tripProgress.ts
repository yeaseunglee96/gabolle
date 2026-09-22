// 일정 진행 — 지금 어느 단계인가 (시안 ⑤, 인계 §6·§7⑤).
//
// 🔴 **서버에 이 자리가 아직 없다**(S15P21E201-1325). 그래서 진행 상태를 **이 기기에만**
//    남긴다. 다른 기기에서는 안 보이고, 앱을 지우면 사라진다. 그 한계를 화면이 말한다 —
//    조용히 기기에만 두면 사용자는 어디서나 이어지는 줄 안다.
//
// 🔴 시간을 여기서 읽지 않는다. `now` 를 받아서 쓴다. 안 그러면 시험이 실제 시각에 따라
//    통과했다 실패했다 하고, 그런 시험은 없는 것보다 나쁘다.
import AsyncStorage from '@react-native-async-storage/async-storage';

export type ProgressStatus = 'PLANNED' | 'RUNNING' | 'PAUSED' | 'DONE';

/** 정차지 하나에 일어난 일. 시안의 `trip_stop_event` 와 같은 갈래다. */
export type StopOutcome =
  | { kind: 'ARRIVED'; at: string; how: 'auto' | 'manual' }
  | { kind: 'SKIPPED'; at: string };

export type TripProgress = {
  status: ProgressStatus;
  /** 지금 향하고 있는 정차지의 자리(0부터). 다녀온 것 다음이다. */
  currentStopIndex: number;
  /** 정차지 id → 일어난 일. 안 적힌 것은 아직 안 갔다는 뜻이다. */
  outcomes: Record<string, StopOutcome>;
};

/** 이보다 크게 벌어지면 「지금」이 아니다. 열두 시간. */
const SANE_DRIFT_MINUTES = 12 * 60;

export const EMPTY_PROGRESS: TripProgress = { status: 'PLANNED', currentStopIndex: 0, outcomes: {} };

/** 단계 하나가 화면에서 어떤 모습인가. */
export type StepState = 'done' | 'current' | 'next' | 'later';

/**
 * 단계들의 모습을 정한다.
 *
 * 🔴 「다녀옴」과 「건너뜀」을 **같은 모습으로 그리지 않는다** — 건너뛴 곳은 안 간 곳이다.
 *    같이 그리면 나중에 「거기 갔었나?」를 기억으로만 풀어야 한다.
 */
export function stepStates(stopIds: string[], progress: TripProgress): StepState[] {
  return stopIds.map((id, index) => {
    if (progress.outcomes[id]) return 'done';
    if (progress.status === 'PLANNED') return index === 0 ? 'next' : 'later';
    if (index === progress.currentStopIndex) return 'current';
    if (index === progress.currentStopIndex + 1) return 'next';
    return 'later';
  });
}

/** 출발 — 위치 추적이 시작된다. */
export function start(progress: TripProgress): TripProgress {
  if (progress.status === 'RUNNING') return progress;
  return { ...progress, status: 'RUNNING' };
}

/**
 * 중지 — 추적과 자동 도착 기록을 멈춘다.
 *
 * 🔴 지금까지 다녀온 것을 지우지 않는다. 중지는 되돌리기가 아니다.
 */
export function pause(progress: TripProgress): TripProgress {
  if (progress.status !== 'RUNNING') return progress;
  return { ...progress, status: 'PAUSED' };
}

/**
 * 도착했다 — 손으로 찍었거나 GPS 가 알아챘거나.
 *
 * 🔴 멈춰 있을 때는 아무 일도 안 일어난다. 중지해 놓고 기록이 쌓이면 「멈췄다」가 거짓이 된다.
 */
export function arrive(
  progress: TripProgress,
  stopIds: string[],
  stopId: string,
  now: string,
  how: 'auto' | 'manual',
): TripProgress {
  if (progress.status !== 'RUNNING') return progress;
  if (progress.outcomes[stopId]) return progress;
  const index = stopIds.indexOf(stopId);
  if (index < 0) return progress;
  const outcomes = { ...progress.outcomes, [stopId]: { kind: 'ARRIVED', at: now, how } as StopOutcome };
  return advance({ ...progress, outcomes }, stopIds, index);
}

/** 건너뛴다 — 이 정차지를 빼고 다음으로. */
export function skip(progress: TripProgress, stopIds: string[], stopId: string, now: string): TripProgress {
  if (progress.status !== 'RUNNING') return progress;
  if (progress.outcomes[stopId]) return progress;
  const index = stopIds.indexOf(stopId);
  if (index < 0) return progress;
  const outcomes = { ...progress.outcomes, [stopId]: { kind: 'SKIPPED', at: now } as StopOutcome };
  return advance({ ...progress, outcomes }, stopIds, index);
}

/** 다음으로 옮긴다. 남은 것이 없으면 끝이다. */
function advance(progress: TripProgress, stopIds: string[], from: number): TripProgress {
  let next = from + 1;
  while (next < stopIds.length && progress.outcomes[stopIds[next]]) next += 1;
  if (next >= stopIds.length) return { ...progress, status: 'DONE', currentStopIndex: stopIds.length };
  return { ...progress, currentStopIndex: next };
}

/**
 * 「예정보다 N분 빠름」 — 지금 시각과 예정 도착의 차이.
 *
 * 🔴 예정 시각을 모르면 **아무 말도 안 한다.** 모르는 것을 「정시」로 적으면 늦고 있는
 *    사람에게 괜찮다고 말하게 된다.
 */
export function drift(nowIso: string, plannedIso: string | null): { minutes: number; early: boolean } | null {
  if (!plannedIso) return null;
  const now = Date.parse(nowIso);
  const planned = Date.parse(plannedIso);
  if (Number.isNaN(now) || Number.isNaN(planned)) return null;
  const minutes = Math.round((planned - now) / 60000);
  if (minutes === 0) return null;
  // 🔴 하루 넘게 벌어지면 아무 말도 안 한다. 그건 「늦었다」가 아니라 **오늘 일정이 아니다**
  //    라는 뜻이다. 그대로 적으면 「예정보다 20547분 빠름」 같은 말이 나오는데, 사람은 그걸
  //    읽고 화면이 고장 났다고 여긴다. 실제로 다음 달 여행을 열어 보면 그렇게 나왔다.
  if (Math.abs(minutes) > SANE_DRIFT_MINUTES) return null;
  return { minutes: Math.abs(minutes), early: minutes > 0 };
}

/**
 * 「예정보다 빠름」이 아니라 「N 뒤 시작」이라고 말해야 하나 — S15P21E201-1489(B-13).
 *
 * <p>실기기에서 「지금 00:52 · 예정보다 8시간 47분 빠름」이 떴다(iOS build 39). 첫 일정이
 * 09:39 이니 {@link drift} 의 계산은 정확하다. 틀린 것은 **말**이다 — 「예정보다 빠름」은
 * 이미 움직이고 있는 사람에게 하는 말이라, 아직 시작도 안 한 여행이 진행 중인 것처럼
 * 읽힌다. 안 떠난 사람에게 필요한 말은 「언제 시작하나」다.
 *
 * <p>🔴 **늦음은 시작 전에도 그대로 둔다.** 첫 일정 시각이 지났는데 아직 {@code PLANNED}
 * 면 실제로 늦은 것이 맞고, 그때는 그렇게 말해 주는 편이 낫다.
 *
 * <p>이 판단을 화면이 아니라 여기에 두는 이유는 시험할 수 있게 하기 위해서다 — 화면에
 * 두면 여행 하나를 통째로 세워야 하고, 그러면 아무도 안 쓴다.
 */
export function saysStartsIn(status: ProgressStatus, drift: { early: boolean } | null): boolean {
  if (!drift) return false;
  return status === 'PLANNED' && drift.early;
}

/**
 * 「도착 찍기」를 보여 줘야 하나.
 *
 * 🔴 **언제나 보이면 안 된다**(인계 §10-4). 기본은 GPS 자동이고, 손으로 찍는 단추는
 *    GPS 가 약하거나 권한이 없을 때만 나온다. 늘 보이면 사람은 그걸 정상 절차로 알고
 *    매번 누르게 되며, 그러면 자동 기록이 있으나 마나가 된다.
 */
export function needsManualArrival(status: ProgressStatus, gpsUsable: boolean): boolean {
  return status === 'RUNNING' && !gpsUsable;
}

/**
 * 그 사람이 있는 곳의 **오늘 날짜**를 `2026-09-19` 꼴로.
 *
 * 🔴 `toISOString()` 을 쓰면 안 된다. 그건 **UTC** 날짜다. 한국은 UTC+9 라 **오전 9시
 *    전에는 UTC 날짜가 하루 전**이고, 그러면 아침에 「지금」 카드가 사라진다 — 하필
 *    사람들이 일정을 시작하는 시간이다. 실제로 이 화면에서 그렇게 났다.
 */
export function localDateKey(now: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * 오늘 일정인가 — 「지금」 카드를 그릴지 정한다.
 *
 * 🔴 지난 날짜나 다음 날짜에 「출발」 단추가 있으면 거짓이다. 다음 달 여행을 열어 「출발」을
 *    누르면 오늘 그 여행을 하고 있다고 기록된다. 일정표는 아무 날이나 볼 수 있어야 하지만
 *    **「지금」은 오늘만의 것**이다.
 *
 * @param todayKey `localDateKey` 가 준 **그 지역의** 오늘 날짜. UTC 를 넣으면 아침에 어긋난다.
 */
export function isToday(dayDate: string | null | undefined, todayKey: string): boolean {
  if (!dayDate) return false;
  return dayDate.slice(0, 10) === todayKey.slice(0, 10);
}

const KEY_PREFIX = '@gabolle/tripProgress:';

export async function loadProgress(itineraryId: string): Promise<TripProgress> {
  try {
    const raw = await AsyncStorage.getItem(`${KEY_PREFIX}${itineraryId}`);
    if (!raw) return EMPTY_PROGRESS;
    const parsed = JSON.parse(raw) as Partial<TripProgress>;
    // 🔴 기기에 남아 있던 값이라 앱 판이 바뀌면 모양이 다를 수 있다. 그대로 믿으면
    //    진행 상태가 깨진 채로 그려진다.
    const status: ProgressStatus = parsed.status === 'RUNNING' || parsed.status === 'PAUSED' || parsed.status === 'DONE'
      ? parsed.status : 'PLANNED';
    return {
      status,
      currentStopIndex: typeof parsed.currentStopIndex === 'number' && parsed.currentStopIndex >= 0 ? parsed.currentStopIndex : 0,
      outcomes: parsed.outcomes && typeof parsed.outcomes === 'object' ? parsed.outcomes : {},
    };
  } catch {
    return EMPTY_PROGRESS;
  }
}

export async function saveProgress(itineraryId: string, progress: TripProgress): Promise<void> {
  try {
    await AsyncStorage.setItem(`${KEY_PREFIX}${itineraryId}`, JSON.stringify(progress));
  } catch {
    // 못 남겨도 화면은 계속 돈다. 이 값은 편의지 근거가 아니다.
  }
}
