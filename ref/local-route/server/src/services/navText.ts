import type { EmbeddedRoute, TransitAlternative } from "./kakao.js";

/**
 * v2 13장: 환승 횟수를 초행 외국인이 바로 이해할 수 있는 난이도 등급으로 요약한다.
 * 자동차 경로처럼 transfers가 없는 경우(null)는 등급을 매기지 않는다.
 */
export function transferDifficultyFromCount(transfers: number | null): "EASY" | "MODERATE" | "HARD" | null {
  if (transfers === null) return null;
  if (transfers <= 1) return "EASY";
  if (transfers === 2) return "MODERATE";
  return "HARD";
}

/**
 * 구글맵이 한국에서 도보·대중교통 안내를 제대로 제공하지 못하는 문제(v2 13.1장)를
 * 보완하기 위해, vehicle 종류별로 영어 안내 문구를 생성한다. Kakao API가 주는 한글
 * guidance 원문을 그대로 번역하는 대신, vehicle·시간·거리로 구조화된 문장을 만든다.
 */
function englishStep(step: { guidance: string; durationMin: number; distanceM: number; vehicle: string | null; destinationStop?: string | null; path: [number, number][] }) {
  const distanceLabel = step.distanceM >= 1000 ? `${(step.distanceM / 1000).toFixed(1)}km` : `${Math.round(step.distanceM)}m`;
  const vehicle = step.vehicle ?? "";
  // 노선번호·정류장명은 한글 표지판과 그대로 대조해야 하므로 번역하지 않고 원문을 남긴다
  // (구글 지도도 같은 방식 - 고유명사는 로마자/원문 그대로, 문장 구조만 영어로 감싼다).
  const towardText = step.destinationStop ? ` toward "${step.destinationStop}"` : "";
  if (!vehicle || vehicle.includes("도보")) {
    return { ...step, guidance: `Walk ${distanceLabel} (about ${step.durationMin} min)` };
  }
  if (vehicle.includes("지하철") || /subway|metro/i.test(vehicle)) {
    return { ...step, guidance: `Take the subway line ${vehicle.replace(/^지하철\s*/, "")}${towardText} — about ${step.durationMin} min` };
  }
  if (vehicle.includes("버스") || /bus/i.test(vehicle)) {
    return { ...step, guidance: `Take bus ${vehicle.replace(/^버스\s*/, "")}${towardText} — about ${step.durationMin} min` };
  }
  return { ...step, guidance: `Continue toward your destination (about ${step.durationMin} min)` };
}

function translateAlternative(alt: TransitAlternative): TransitAlternative {
  return { ...alt, steps: alt.steps.map(englishStep) };
}

/** lang === "EN"일 때만 안내 문구를 영어 템플릿으로 바꾼다. 그 외에는 원본을 그대로 반환한다. */
export function localizeRoute(route: EmbeddedRoute, lang: string): EmbeddedRoute {
  if (lang !== "EN") return route;
  return {
    ...route,
    steps: route.steps.map(englishStep),
    alternatives: route.alternatives?.map(translateAlternative),
  };
}
