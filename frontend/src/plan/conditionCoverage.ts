// 「이 문항을 판정할 장소 자료가 지금 있나」 — S15P21E201-1044.
//
// 여행 조건 화면은 조건을 물어 놓고 그것을 지키겠다고 적는데, 그 조건을 **판정할 장소 값이
// 한 건도 없는** 문항이 있다. 묻고서 못 지키는 약속이고, 🔴 **그 상태가 아무 오류도 안 낸다** —
// 조건에 걸릴 후보가 없으니 조용히 무시되거나, 후보가 통째로 사라져 「만들지 못했어요」만 뜬다.
//
// 🔴 **문항 목록을 앱에 박지 않는다.** 서버가 세어 준다 (S15P21E201-1508 의
//    `GET /api/v1/places/condition-coverage`). 박으면 **자료가 들어온 날 거짓말이 된다** —
//    실제로 그럴 뻔했다. `-1044` 본문의 표는 2026-09-16 실측인데 엿새 뒤 다시 재니
//    경사(0 → 2,682곳)와 그늘(0 → 1,936곳) 두 줄이 뒤집혔다. 박아 뒀으면 지금
//    「경사 자료가 없어요」라고 말하고 있을 것이다.
//
// 🔴 **못 물어봤으면 아무 말도 안 한다.** 끝점이 아직 안 올라온 서버(404)·네트워크 실패에
//    「자료가 없어요」를 적으면 자료는 멀쩡한데 화면만 거짓말을 한다.
//    **모르는 것과 없는 것은 다른 사실이다.**
import { useQuery } from '@tanstack/react-query';

import { apiRequest } from '@/api/client';

/** 표식 하나의 덮임. 비율은 화면이 낸다 — 서버는 「28%면 충분한가」를 대신 정하지 않는다. */
export type CoverageFeature = {
  /** 장소 표식 갈래. 예: `ALLERGEN_TAG` */
  featureType: string;
  /** 그 표식을 가진 장소 수. 근거가 `UNKNOWN` 인 줄은 서버가 안 센다. */
  placeCount: number;
  /** 전체 장소 수. */
  totalPlaceCount: number;
};

export type CoverageCondition = {
  /** `PREFERENCE`(취향 문항) · `CONSTRAINT`(꼭 지켜야 하는 조건) */
  kind: string;
  /** 사용자 문항 코드. 예: `DIET`·`SLOPE_PREFERENCE` */
  code: string;
  features: CoverageFeature[];
};

export type ConditionCoverageResponse = { conditions: CoverageCondition[] };

/**
 * 표식 갈래 → 덮임. 🔴 **문항이 아니라 표식으로 펼쳐 둔다.**
 *
 * 문항 하나가 표식 둘에 걸리는 경우가 있어서다 — `MOBILITY` 는 접근성과 계단 둘을 본다.
 * 뭉치면 접근성 102곳이 계단 0곳을 덮어 **「이동 조건 자료 있음」**이 되고, 화면의
 * 「계단 피하기」는 여전히 못 지키는 약속으로 남는다. 서버가 갈라서 주는 이유도 같다.
 */
export type ConditionCoverage = Record<string, CoverageFeature>;

/** 여행 조건 화면의 항목들이 실제로 보는 장소 표식. */
export const COVERAGE_FEATURE = {
  diet: 'DIETARY_SUPPORT_TAG',
  slope: 'SLOPE_PERCENT',
  stairs: 'STAIRS_PRESENT',
  shade: 'SHADE_SCORE',
  accessibility: 'ACCESSIBILITY_TAG',
} as const;

const NUMBER = (value: unknown): number => (typeof value === 'number' && Number.isFinite(value) ? value : 0);

/** 응답을 표식별로 펼친다. 모양이 예상과 다르면 그 줄만 버린다 — 화면을 못 그리게 하지 않는다. */
export function toCoverage(response: ConditionCoverageResponse | null | undefined): ConditionCoverage {
  const coverage: ConditionCoverage = {};
  for (const condition of response?.conditions ?? []) {
    for (const feature of condition?.features ?? []) {
      if (!feature || typeof feature.featureType !== 'string') continue;
      coverage[feature.featureType] = {
        featureType: feature.featureType,
        placeCount: NUMBER(feature.placeCount),
        totalPlaceCount: NUMBER(feature.totalPlaceCount),
      };
    }
  }
  return coverage;
}

/**
 * 🔴 **`true` 는 「없다」일 때만 낸다.** 못 물어봤으면 `false` 다.
 *
 * 세 가지를 가른다 — 자료가 없다(`true`) · 자료가 있다(`false`) · **모른다**(`false`).
 * 뒤의 둘을 합치는 것이 안전한 쪽이다: 모르면 **아무 말도 안 하는 것**이 맞고,
 * 「없어요」라고 적는 것은 화면이 지어내는 것이다.
 */
export function hasNoPlaceData(coverage: ConditionCoverage | null, featureType: string): boolean {
  const feature = coverage?.[featureType];
  if (!feature) return false;
  // 전체 장소가 0곳이면 DB 가 비었거나 아직 안 실린 것이다. 그때 「이 조건만 자료가
  // 없어요」라고 적으면 원인을 엉뚱한 곳으로 돌린다.
  if (feature.totalPlaceCount <= 0) return false;
  return feature.placeCount <= 0;
}

/**
 * 「있기는 한데 너무 적다」 — S15P21E201-1855.
 *
 * 🔴 `hasNoPlaceData` 로는 이 상태를 못 잡는다. 접근성 표식은 **6,866곳 중 102곳(1.5%)** 이라
 *    0곳이 아니고, 그래서 화면이 아무 말도 안 했다. 그런데 휠체어를 고르면 **거의 모든 곳**이
 *    「확인되지 않았어요」로 나온다 — 사용자는 그것을 「앱이 고장 났다」로 읽는다.
 *    자료가 적다는 것이 사실이고, 그 사실을 말하면 「고장」이 「아직 덜 모았구나」가 된다.
 *
 * 「없다」와 마찬가지로 **모르면 `false`** 다. 못 물어본 서버에 숫자를 지어내지 않는다.
 */
export const SCARCE_COVERAGE_RATIO = 0.1;

export function hasScarcePlaceData(coverage: ConditionCoverage | null, featureType: string): boolean {
  const feature = coverage?.[featureType];
  if (!feature) return false;
  if (feature.totalPlaceCount <= 0) return false;
  // 0곳은 「없다」쪽이 말한다. 여기는 「있는데 적다」만 맡는다 — 한 줄에 두 말이 겹치지 않게.
  if (feature.placeCount <= 0) return false;
  return feature.placeCount / feature.totalPlaceCount < SCARCE_COVERAGE_RATIO;
}

/** 덮임을 「102 / 6,866」처럼 적는다. 비율만 적으면 얼마나 적은지가 안 와닿는다. */
export function coverageCountsOf(coverage: ConditionCoverage | null, featureType: string): { placeCount: number; totalPlaceCount: number } | null {
  const feature = coverage?.[featureType];
  if (!feature || feature.totalPlaceCount <= 0) return null;
  return { placeCount: feature.placeCount, totalPlaceCount: feature.totalPlaceCount };
}

export const CONDITION_COVERAGE_KEY = ['condition-coverage'] as const;

/**
 * 못 받아오면 `null`. 던지지 않는다 — 이 값이 없다고 여행 조건을 못 적을 이유가 없다.
 *
 * 서버 끝점은 `db`·`dev` 프로필에만 있고, 아직 안 올라온 배포에서는 404 가 온다.
 * 그것을 오류로 올리면 **기능이 늘었는데 화면이 죽는다.**
 */
export async function fetchConditionCoverage(): Promise<ConditionCoverage | null> {
  try {
    const response = await apiRequest<ConditionCoverageResponse>('/api/v1/places/condition-coverage');
    return toCoverage(response);
  } catch {
    return null;
  }
}

/**
 * 답은 사용자와 무관하다 — 장소 자료가 얼마나 있는지는 누가 물어도 같은 값이라
 * 로그인 여부를 안 본다. 자주 바뀌는 값도 아니라 오래 묵혀 둔다.
 */
export function useConditionCoverage(): ConditionCoverage | null {
  const query = useQuery({
    queryKey: CONDITION_COVERAGE_KEY,
    queryFn: fetchConditionCoverage,
    staleTime: 10 * 60_000,
    // 이미 안에서 삼켰다. 여기서 또 재시도하면 끝점 없는 서버에 매번 두 번씩 묻는다.
    retry: false,
  });
  return query.data ?? null;
}
