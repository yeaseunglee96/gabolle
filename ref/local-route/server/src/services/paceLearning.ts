import { formatMinToTime, parseTimeToMin } from "./schedule.js";

/**
 * 페이스 러닝 - 계획과 실측을 맞대어 "지금 밀리고 있는지"와 "이 사람의 체류 리듬"을 계산한다.
 *
 * 이 파일은 DB·네트워크에 접근하지 않는 순수 계산만 담는다. 스케줄러(schedule.ts)가 계획을 만들고,
 * 여기서는 그 계획이 현실과 얼마나 어긋났는지만 다룬다.
 */

/** 지연이 이 분 수를 넘으면 사용자에게 알린다. 그 아래는 오차 범위로 보고 조용히 넘긴다. */
export const DELAY_NOTICE_MIN = 10;
/** 이 분 수를 넘으면 남은 일정 재계산을 제안한다. */
export const DELAY_REPLAN_MIN = 30;
/** 리듬 계수를 만들기 위해 필요한 최소 실측 건수(카테고리별). 한 건으로는 우연을 못 걸러낸다. */
export const MIN_RHYTHM_SAMPLES = 2;
/** 리듬 계수 허용 범위. 이상치가 다음 일정을 망가뜨리지 않도록 가둔다. */
export const RHYTHM_SCALE_MIN = 0.6;
export const RHYTHM_SCALE_MAX = 2.0;

export interface PaceItem {
  seqOrder: number;
  placeId: string;
  nameKo: string;
  nameEn: string | null;
  category: string;
  closeTime: string;
  plannedArrival: string;
  stayMinutes: number;
  travelMinToNext: number | null;
  actualArrival: string | null;
  actualDeparture: string | null;
}

export type PaceStatus = "NOT_STARTED" | "ON_TIME" | "SLIGHTLY_BEHIND" | "BEHIND" | "AT_RISK" | "DONE";

export interface PaceRiskItem {
  seqOrder: number;
  placeId: string;
  nameKo: string;
  projectedArrival: string;
  closeTime: string;
  /** 영업 종료까지 남는 분. 음수면 도착 시점에 이미 닫혀 있다. */
  marginMinutes: number;
}

export interface PaceForecast {
  status: PaceStatus;
  /** 양수면 지연, 음수면 계획보다 빠름. 실측이 하나도 없으면 0. */
  delayMinutes: number;
  /** 현재 페이스를 유지했을 때 마지막 일정이 끝나는 시각. */
  projectedEndTime: string | null;
  plannedEndTime: string | null;
  currentSeqOrder: number | null;
  nextSeqOrder: number | null;
  completedCount: number;
  totalCount: number;
  /** 이대로 가면 영업 종료 전에 도착하지 못하는 항목들. */
  atRisk: PaceRiskItem[];
  /** 남은 각 항목의 예상 도착 시각. */
  projectedArrivals: { seqOrder: number; placeId: string; projectedArrival: string }[];
  /** 이동시간·체류시간 추정이 섞여 있으므로 항상 추정값임을 명시한다. */
  isEstimate: true;
}

function normalizeDelay(value: number): number {
  return Math.round(value);
}

/**
 * 하루 일정의 진행 상황을 읽어 지연 예보를 만든다.
 *
 * 판정 기준은 "마지막으로 확인된 실측"이다. 어떤 장소에 머무는 중이면 그 도착 시각의 지연을,
 * 이미 떠났다면 그 출발 시각과 계획상 출발 시각의 차이를 지연으로 본다.
 * 실측이 하나도 없으면 아직 시작 전이므로 지연 0으로 둔다.
 */
export function computeDayPace(items: PaceItem[], nowMinutes: number): PaceForecast {
  const sorted = [...items].sort((a, b) => a.seqOrder - b.seqOrder);
  const totalCount = sorted.length;
  const plannedEnd = sorted.length
    ? formatMinToTime(parseTimeToMin(sorted[sorted.length - 1].plannedArrival) + sorted[sorted.length - 1].stayMinutes)
    : null;

  if (!totalCount) {
    return {
      status: "NOT_STARTED", delayMinutes: 0, projectedEndTime: null, plannedEndTime: null,
      currentSeqOrder: null, nextSeqOrder: null, completedCount: 0, totalCount: 0,
      atRisk: [], projectedArrivals: [], isEstimate: true,
    };
  }

  const completed = sorted.filter((item) => item.actualDeparture);
  const staying = sorted.find((item) => item.actualArrival && !item.actualDeparture) ?? null;
  const remaining = sorted.filter((item) => !item.actualDeparture && item.seqOrder !== staying?.seqOrder);

  // 지연 기준점: 머무는 중이면 그 도착 지연, 아니면 마지막으로 떠난 곳의 출발 지연.
  let delayMinutes = 0;
  let cursorMin: number | null = null;
  let cursorSeq: number | null = null;

  if (staying) {
    delayMinutes = normalizeDelay(parseTimeToMin(staying.actualArrival!) - parseTimeToMin(staying.plannedArrival));
    // 아직 안 떠났으므로 예상 출발은 도착 + 계획 체류시간. 다만 이미 그보다 오래 있었다면 현재 시각을 쓴다.
    cursorMin = Math.max(parseTimeToMin(staying.actualArrival!) + staying.stayMinutes, nowMinutes);
    cursorSeq = staying.seqOrder;
  } else if (completed.length) {
    const last = completed[completed.length - 1];
    const plannedDeparture = parseTimeToMin(last.plannedArrival) + last.stayMinutes;
    delayMinutes = normalizeDelay(parseTimeToMin(last.actualDeparture!) - plannedDeparture);
    cursorMin = Math.max(parseTimeToMin(last.actualDeparture!), nowMinutes);
    cursorSeq = last.seqOrder;
  }

  // 남은 항목의 예상 도착을 순서대로 누적한다. 아직 시작 전이면 계획을 그대로 쓴다.
  const projectedArrivals: { seqOrder: number; placeId: string; projectedArrival: string }[] = [];
  const atRisk: PaceRiskItem[] = [];
  let projectedEndMin: number | null = null;

  if (cursorMin === null) {
    // 시작 전 - 계획 그대로가 예보다.
    for (const item of sorted) {
      projectedArrivals.push({ seqOrder: item.seqOrder, placeId: item.placeId, projectedArrival: item.plannedArrival });
    }
    projectedEndMin = parseTimeToMin(sorted[sorted.length - 1].plannedArrival) + sorted[sorted.length - 1].stayMinutes;
  } else {
    let cursor = cursorMin;
    // 머무는 중인 곳에서 다음 장소까지의 이동시간부터 더한다.
    let prev: PaceItem | null = staying ?? (completed.length ? completed[completed.length - 1] : null);
    projectedEndMin = cursor;
    for (const item of remaining) {
      const travel = prev?.travelMinToNext ?? 0;
      const arrival = cursor + travel;
      projectedArrivals.push({ seqOrder: item.seqOrder, placeId: item.placeId, projectedArrival: formatMinToTime(arrival) });
      const closeMin = parseTimeToMin(item.closeTime);
      const marginMinutes = normalizeDelay(closeMin - arrival);
      if (arrival > closeMin) {
        atRisk.push({ seqOrder: item.seqOrder, placeId: item.placeId, nameKo: item.nameKo, projectedArrival: formatMinToTime(arrival), closeTime: item.closeTime, marginMinutes });
      }
      cursor = arrival + item.stayMinutes;
      projectedEndMin = cursor;
      prev = item;
    }
  }

  const status = resolveStatus({ totalCount, completedCount: completed.length, hasProgress: cursorMin !== null, delayMinutes, atRiskCount: atRisk.length });

  return {
    status,
    delayMinutes,
    projectedEndTime: projectedEndMin === null ? null : formatMinToTime(projectedEndMin),
    plannedEndTime: plannedEnd,
    currentSeqOrder: staying?.seqOrder ?? null,
    nextSeqOrder: remaining[0]?.seqOrder ?? null,
    completedCount: completed.length,
    totalCount,
    atRisk,
    projectedArrivals,
    isEstimate: true,
  };
}

function resolveStatus(input: { totalCount: number; completedCount: number; hasProgress: boolean; delayMinutes: number; atRiskCount: number }): PaceStatus {
  if (!input.hasProgress) return "NOT_STARTED";
  if (input.completedCount >= input.totalCount) return "DONE";
  if (input.atRiskCount > 0) return "AT_RISK";
  if (input.delayMinutes >= DELAY_REPLAN_MIN) return "BEHIND";
  if (input.delayMinutes >= DELAY_NOTICE_MIN) return "SLIGHTLY_BEHIND";
  return "ON_TIME";
}

export interface RhythmSample {
  category: string;
  plannedStayMinutes: number;
  actualStayMinutes: number;
}

export interface RhythmObservation {
  category: string;
  sampleCount: number;
  /** 실측 ÷ 계획. 1보다 크면 계획보다 오래 머무는 사람이다. */
  scale: number;
  averagePlannedMinutes: number;
  averageActualMinutes: number;
  /** 하루 일정에 반영했을 때 늘어나는(또는 줄어드는) 분. 사용자에게 보여줄 문구용. */
  deltaMinutes: number;
}

export interface RhythmProfile {
  /** buildItinerary 의 stayMinutesScale 로 그대로 넘길 수 있는 형태. */
  scale: Record<string, number>;
  observations: RhythmObservation[];
  totalSamples: number;
  /** 계수를 하나라도 만들었는지. false 면 아직 학습할 데이터가 부족하다. */
  hasProfile: boolean;
}

/**
 * 실측 체류시간을 카테고리별로 모아 개인 리듬 계수를 만든다.
 *
 * 표본이 적을 때 한 번의 이상치가 다음 날 일정을 통째로 흔들지 않도록,
 * 카테고리당 최소 표본 수를 요구하고 계수를 [0.6, 2.0]으로 가둔다.
 */
export function computeRhythmProfile(samples: RhythmSample[]): RhythmProfile {
  const byCategory = new Map<string, RhythmSample[]>();
  for (const sample of samples) {
    if (sample.plannedStayMinutes <= 0 || sample.actualStayMinutes <= 0) continue;
    const bucket = byCategory.get(sample.category) ?? [];
    bucket.push(sample);
    byCategory.set(sample.category, bucket);
  }

  const scale: Record<string, number> = {};
  const observations: RhythmObservation[] = [];
  let totalSamples = 0;

  for (const [category, bucket] of byCategory) {
    totalSamples += bucket.length;
    if (bucket.length < MIN_RHYTHM_SAMPLES) continue;
    const ratios = bucket.map((s) => s.actualStayMinutes / s.plannedStayMinutes);
    const rawScale = ratios.reduce((sum, r) => sum + r, 0) / ratios.length;
    const clamped = Math.min(RHYTHM_SCALE_MAX, Math.max(RHYTHM_SCALE_MIN, rawScale));
    const averagePlanned = bucket.reduce((sum, s) => sum + s.plannedStayMinutes, 0) / bucket.length;
    const averageActual = bucket.reduce((sum, s) => sum + s.actualStayMinutes, 0) / bucket.length;
    // 계수가 사실상 1이면 굳이 사용자에게 보여줄 것도, 일정을 바꿀 것도 없다.
    if (Math.abs(clamped - 1) < 0.1) continue;
    scale[category] = Math.round(clamped * 100) / 100;
    observations.push({
      category,
      sampleCount: bucket.length,
      scale: scale[category],
      averagePlannedMinutes: Math.round(averagePlanned),
      averageActualMinutes: Math.round(averageActual),
      deltaMinutes: Math.round(averageActual - averagePlanned),
    });
  }

  observations.sort((a, b) => Math.abs(b.deltaMinutes) - Math.abs(a.deltaMinutes));
  return { scale, observations, totalSamples, hasProfile: observations.length > 0 };
}

/** 실측 도착·출발 시각으로 체류 시간을 구한다. 자정을 넘긴 경우 하루를 더해 음수를 막는다. */
export function resolveStayMinutes(actualArrival: string, actualDeparture: string): number {
  const arrival = parseTimeToMin(actualArrival);
  const departure = parseTimeToMin(actualDeparture);
  const raw = departure - arrival;
  return raw >= 0 ? raw : raw + 24 * 60;
}
