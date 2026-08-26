import type { ItineraryDayOutput, ItineraryItemOutput, Pace, ScoredPlace } from "../types.js";
import { estimateTravelSync, getTravelEstimate, getTravelMatrix, haversineDistanceM } from "./kakao.js";
import { buildRecommendReason, estimatePlaceCost } from "./recommend.js";
import { optimizeDayWithOrTools } from "./optimizer.js";

const DAY_ABBR = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"] as const;

const PACE_CONFIG: Record<Pace, { dayStart: string; dayEnd: string; maxPerDay: number }> = {
  RELAXED: { dayStart: "10:00", dayEnd: "19:00", maxPerDay: 3 },
  NORMAL: { dayStart: "09:30", dayEnd: "20:00", maxPerDay: 4 },
  PACKED: { dayStart: "09:00", dayEnd: "21:00", maxPerDay: 6 },
};

export function parseTimeToMin(t: string): number {
  const [h, m] = t.split(":").map(Number);
  return h * 60 + m;
}

export function formatMinToTime(min: number): string {
  const h = Math.floor(min / 60) % 24;
  const m = min % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

function dateToDayAbbr(dateStr: string): string {
  return DAY_ABBR[new Date(`${dateStr}T00:00:00`).getDay()];
}

function daysBetween(start: string, end: string): number {
  const a = new Date(`${start}T00:00:00`);
  const b = new Date(`${end}T00:00:00`);
  return Math.round((b.getTime() - a.getTime()) / 86400000) + 1;
}

function addDays(dateStr: string, n: number): string {
  // 로컬 타임존으로 파싱 후 toISOString()(UTC)으로 포맷하면 UTC+시간대 지역에서
  // 날짜가 하루 밀리는 버그가 생기므로, 로컬 getter로만 문자열을 조립한다.
  const d = new Date(`${dateStr}T00:00:00`);
  d.setDate(d.getDate() + n);
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

function isOpenOnDay(place: ScoredPlace, dateStr: string): boolean {
  return !place.closedDays.includes(dateToDayAbbr(dateStr));
}

/**
 * 카테고리별 체류시간 계수를 후보 목록에 미리 반영한다.
 * 계수가 없으면 원본 배열을 그대로 돌려주어 기존 동작을 건드리지 않는다.
 */
function applyStayMinutesScale(places: ScoredPlace[], scale: Record<string, number> | undefined): ScoredPlace[] {
  if (!scale || Object.keys(scale).length === 0) return places;
  return places.map((place) => {
    const factor = scale[place.category];
    if (!factor || factor === 1) return place;
    // 15분 미만으로 줄어들면 일정이 비현실적으로 촘촘해지므로 하한을 둔다.
    return { ...place, recommendedStayMin: Math.max(15, Math.round(place.recommendedStayMin * factor)) };
  });
}

function centroid(places: ScoredPlace[]): { lat: number; lng: number } {
  if (places.length === 0) return { lat: 0, lng: 0 };
  const lat = places.reduce((s, p) => s + p.lat, 0) / places.length;
  const lng = places.reduce((s, p) => s + p.lng, 0) / places.length;
  return { lat, lng };
}

export interface ScheduleInput {
  startDate: string;
  endDate: string;
  originLat: number;
  originLng: number;
  totalBudget: number;
  partySize: number;
  hasCar: boolean;
  pace: Pace;
  dayStart: string;
  dayEnd: string;
  maxWalkingKm: number;
  mustVisitPlaceIds: string[];
  /** 사용자가 위저드에서 "며칠째"까지 직접 지정한 필수 방문 장소. dayIndex는 1부터 시작. */
  mustVisitAssignments?: { placeId: string; dayIndex: number }[];
  recommendationMode?: string;
  /**
   * 하루를 마치고 돌아갈 지점. 지정하지 않으면 originLat/originLng 로 복귀한다.
   * 여행 중 재계산에서는 출발점이 "지금 서 있는 곳"이고 복귀점은 여전히 숙소이므로 둘을 분리해야 한다.
   */
  returnLat?: number;
  returnLng?: number;
  /**
   * 카테고리별 체류시간 보정 계수(페이스 러닝).
   * 예: { CAFE: 1.8 } 이면 카페 체류시간을 1.8배로 잡고 일정을 짠다.
   */
  stayMinutesScale?: Record<string, number>;
}

/**
 * 16장 "제약조건 기반 다일차 경로 최적화"의 단순화 버전.
 * OR-Tools 대신 (1) 점수 기반 군집화 → (2) 최근접 이웃 + 시간창 삽입 그리디 알고리즘을 사용한다.
 * 운영시간·예산·이동시간·필수장소 제약은 그대로 검증하되, 전역 최적해가 아닌 휴리스틱 해를 반환한다.
 */
export async function buildItinerary(
  rawScoredPlaces: ScoredPlace[],
  input: ScheduleInput
): Promise<{ days: ItineraryDayOutput[]; warnings: string[]; solverSource: "OR_TOOLS" | "HEURISTIC" }> {
  const warnings: string[] = [];
  // 체류시간 보정은 여기서 한 번만 적용한다. 이후 OR-Tools·휴리스틱·시간표 확정이 모두
  // recommendedStayMin 을 참조하므로, 입구에서 갈아끼우면 경로 전체에 일관되게 반영된다.
  const scoredPlaces = applyStayMinutesScale(rawScoredPlaces, input.stayMinutesScale);
  const returnLat = input.returnLat ?? input.originLat;
  const returnLng = input.returnLng ?? input.originLng;
  const numDays = daysBetween(input.startDate, input.endDate);
  const dayBudget = input.totalBudget / numDays;
  const paceConfig = PACE_CONFIG[input.pace];
  const dayStart = input.dayStart || paceConfig.dayStart;
  const dayEnd = input.dayEnd || paceConfig.dayEnd;
  const maxPerDay = paceConfig.maxPerDay;
  // 식사는 여행의 한 부분으로 제한하고, 나머지 슬롯은 관광·문화·자연·체험에 사용한다.
  const maxFoodStops = Math.max(1, Math.floor(maxPerDay / 2));
  const maxRestaurantStops = maxPerDay >= 5 ? 2 : 1;
  const solverDayEnd = formatMinToTime(Math.max(parseTimeToMin(dayStart) + 120, parseTimeToMin(dayEnd)));

  // 1) 날짜별 군집화
  // 매일 출발 기준점으로 복귀하므로, 개별 후보도 최소 왕복 거리 안에 있어야 한다.
  const walkRadiusM = (input.maxWalkingKm * 1000) / 2;
  const reachablePlaces = input.hasCar
    ? scoredPlaces
    : scoredPlaces.filter((place) => haversineDistanceM(input.originLat, input.originLng, place.lat, place.lng) <= walkRadiusM);
  if (!input.hasCar && reachablePlaces.length < scoredPlaces.length) {
    warnings.push(`출발지 기준 ${input.maxWalkingKm}km 도보 범위를 벗어난 후보 ${scoredPlaces.length - reachablePlaces.length}곳을 제외했습니다.`);
  }
  const pool = [...reachablePlaces];
  const dayBuckets: ScoredPlace[][] = Array.from({ length: numDays }, () => []);

  const takeFromPool = (id: string) => {
    const idx = pool.findIndex((p) => p.id === id);
    if (idx === -1) return null;
    return pool.splice(idx, 1)[0];
  };

  // 사용자가 날짜를 직접 지정한 필수 방문 장소를 먼저 그 날짜에 확정 배정한다(라운드로빈보다 우선).
  const assignedPlaceIds = new Set<string>();
  for (const { placeId, dayIndex } of input.mustVisitAssignments ?? []) {
    const place = takeFromPool(placeId);
    if (!place) {
      warnings.push(`직접 추가한 장소(${placeId})가 조건(제외 목록 등)과 맞지 않아 일정에서 제외되었습니다.`);
      continue;
    }
    assignedPlaceIds.add(placeId);
    let target = dayIndex - 1;
    if (target < 0 || target >= numDays) {
      const clamped = Math.min(Math.max(target, 0), numDays - 1);
      warnings.push(`${dayIndex}일차 일정이 없어 ${place.nameKo}은(는) ${clamped + 1}일차로 배정되었습니다.`);
      target = clamped;
    }
    dayBuckets[target].push(place);
  }

  // 날짜 지정이 없는 필수 방문 장소는 지금까지처럼 가장 여유 있는 날짜에 고르게 배정한다.
  for (const mustId of input.mustVisitPlaceIds) {
    if (assignedPlaceIds.has(mustId)) continue; // 이미 위에서 날짜 지정으로 배정됨
    const place = takeFromPool(mustId);
    if (!place) {
      warnings.push(`필수 방문 장소(${mustId})가 조건(제외 목록 등)과 맞지 않아 일정에서 제외되었습니다.`);
      continue;
    }
    const target = dayBuckets.reduce((minDay, day, i, arr) =>
      day.length < arr[minDay].length ? i : minDay, 0);
    dayBuckets[target].push(place);
  }

  // 사용자가 고른 취향(tasteTags) 및 먹고 싶은 음식(desiredFoods) 가산점이 반영된 최고 점수 순으로 후보를 정렬
  pool.sort((a, b) => b.score - a.score);

  // 각 날짜에 사용자의 취향 점수가 높은 관광·체험 앵커와 식사 후보를 먼저 확보한다.
  const assignCategoryAnchors = (category: string) => {
    for (let d = 0; d < numDays; d++) {
      if (dayBuckets[d].some((place) => place.category === category)) continue;
      const index = pool.findIndex((place) => place.category === category);
      if (index >= 0) dayBuckets[d].push(pool.splice(index, 1)[0]);
    }
  };
  assignCategoryAnchors("TOURIST");
  assignCategoryAnchors("RESTAURANT");

  // 데이터가 부족해 위 앵커를 만들지 못한 날짜는 전체 최고 점수 장소로 시작한다.
  for (let d = 0; d < numDays; d++) {
    if (dayBuckets[d].length === 0 && pool.length > 0) {
      dayBuckets[d].push(pool.shift()!);
    }
  }

  // 지리적으로 가장 가까운 장소를 같은 날짜로 채움 (거리 제곱 페널티로 이동시간 최적화)
  let guard = 0;
  while (pool.length > 0 && guard < 10000) {
    guard++;
    let assignedAny = false;
    for (let d = 0; d < numDays; d++) {
      // 최종 방문 수는 OR-Tools가 maxPerDay 제약으로 고르며, 후보군은 탐색 여유를 둔다.
      if (dayBuckets[d].length >= maxPerDay * 3) continue;
      if (pool.length === 0) break;
      const c = centroid(dayBuckets[d]);
      let nearestIdx = 0;
      let minCostScore = Infinity;
      pool.forEach((p, i) => {
        const distM = haversineDistanceM(c.lat, c.lng, p.lat, p.lng);
        // 이동 시간을 최소화하기 위해 4km 초과 장소에는 거리에 비선형 페널티를 부과
        const spatialPenalty = distM > 4000 ? Math.pow(distM / 1000, 2.2) * 800 : distM;
        // 장소 추천 점수(p.score)와 지리적 근접성을 결합
        const scoreFit = spatialPenalty - p.score * 1500;
        if (scoreFit < minCostScore) {
          minCostScore = scoreFit;
          nearestIdx = i;
        }
      });
      dayBuckets[d].push(pool.splice(nearestIdx, 1)[0]);
      assignedAny = true;
    }
    if (!assignedAny) break;
  }

  // 날짜별 식당 최소 1곳 보장
  for (let d = 0; d < numDays; d++) {
    const hasRestaurant = dayBuckets[d].some((p) => p.category === "RESTAURANT");
    if (hasRestaurant) continue;
    const c = centroid(dayBuckets[d]);
    let bestIdx = -1;
    let bestDist = Infinity;
    pool.forEach((p, i) => {
      if (p.category !== "RESTAURANT") return;
      const dist = haversineDistanceM(c.lat, c.lng, p.lat, p.lng);
      if (dist < bestDist) {
        bestDist = dist;
        bestIdx = i;
      }
    });
    if (bestIdx >= 0) {
      dayBuckets[d].push(pool.splice(bestIdx, 1)[0]);
    } else {
      warnings.push(`${d + 1}일차에 배정 가능한 식당 후보가 부족합니다.`);
    }
  }

  // 2) 날짜별 시간표 확정
  const days: ItineraryDayOutput[] = [];
  let usedOrTools = true;
  for (let d = 0; d < numDays; d++) {
    const visitDate = addDays(input.startDate, d);
    const dayOptions = {
      visitDate,
      startLat: input.originLat,
      startLng: input.originLng,
      dayStart,
      dayEnd: solverDayEnd,
      dayBudget,
      hasCar: input.hasCar,
      partySize: input.partySize,
      maxWalkingKm: input.maxWalkingKm,
      maxItems: maxPerDay,
      maxFoodStops,
      maxRestaurantStops,
    };
    const openCandidates = dayBuckets[d].filter((place) => isOpenOnDay(place, visitDate));
    const closedCandidates = dayBuckets[d].filter((place) => !isOpenOnDay(place, visitDate));
    let optimized = await optimizeDayWithOrTools(openCandidates, {
      ...dayOptions,
      mustVisitPlaceIds: input.mustVisitPlaceIds,
      maxItems: maxPerDay,
      maxFoodStops,
      maxRestaurantStops,
    });
    if (input.hasCar && optimized && optimized.status !== "INFEASIBLE" && optimized.orderedPlaceIds.length > 0) {
      const selectedById = new Map(openCandidates.map((place) => [place.id, place]));
      const selected = optimized.orderedPlaceIds.flatMap((id) => selectedById.get(id) ?? []);
      const matrix = await getTravelMatrix([{ lat: input.originLat, lng: input.originLng }, ...selected], true);
      const refined = await optimizeDayWithOrTools(selected, {
        ...dayOptions,
        mustVisitPlaceIds: input.mustVisitPlaceIds,
        maxItems: maxPerDay,
        maxFoodStops,
        maxRestaurantStops,
        travelMinutes: matrix.travelMinutes,
        distanceMeters: matrix.distanceMeters,
      });
      if (refined && refined.status !== "INFEASIBLE") optimized = refined;
      if (matrix.estimatedPairs > 0) warnings.push(`${visitDate}: 실제 자동차 경로를 확인하지 못한 ${matrix.estimatedPairs}개 구간은 추정값을 사용했습니다.`);
    }
    let dayResult: { items: ItineraryItemOutput[]; warnings: string[] };
    if (optimized && optimized.status !== "INFEASIBLE") {
      dayResult = materializeOptimizedDay(openCandidates, optimized.orderedPlaceIds, optimized.arrivals, dayOptions);
      closedCandidates.forEach((place) => dayResult.warnings.push(`${place.nameKo}은(는) ${visitDate}에 휴무일이라 제외되었습니다.`));
    } else if (optimized?.status === "INFEASIBLE") {
      dayResult = { items: [], warnings: [`${visitDate}: 필수 장소를 포함한 제약조건 해를 찾을 수 없습니다.`] };
    } else {
      usedOrTools = false;
      dayResult = scheduleSingleDay(dayBuckets[d], dayOptions);
      dayResult.warnings.unshift(`${visitDate}: OR-Tools 실행 환경을 사용할 수 없어 휴리스틱으로 계산했습니다.`);
    }
    warnings.push(...dayResult.warnings);
    days.push({
      dayIndex: d + 1,
      visitDate,
      dayBudget,
      totalEstCost: dayResult.items.reduce((s, it) => s + it.estCost, 0),
      startTravelMin: null,
      startDistanceM: null,
      startTravelIsEstimate: true,
      returnTravelMin: null,
      returnDistanceM: null,
      returnTravelIsEstimate: true,
      items: dayResult.items,
    });
  }

  // 3) 확정된 순서에 대해 실제(또는 폴백) 이동시간/거리를 채워넣음
  for (const day of days) {
    const first = day.items[0];
    if (first) {
      const start = await getTravelEstimate(input.originLat, input.originLng, first.lat, first.lng, input.hasCar);
      day.startTravelMin = start.durationMin;
      day.startDistanceM = start.distanceM;
      day.startTravelIsEstimate = start.isEstimate;
    }
    for (let i = 0; i < day.items.length - 1; i++) {
      const cur = day.items[i];
      const next = day.items[i + 1];
      const est = await getTravelEstimate(cur.lat, cur.lng, next.lat, next.lng, input.hasCar);
      cur.travelMinToNext = est.durationMin;
      cur.distanceToNextM = est.distanceM;
      cur.travelIsEstimate = est.isEstimate;
      cur.travelSource = est.isEstimate ? "HAVERSINE" : "KAKAO_MOBILITY";
    }
    const last = day.items.at(-1);
    if (last) {
      const back = await getTravelEstimate(last.lat, last.lng, returnLat, returnLng, input.hasCar);
      day.returnTravelMin = back.durationMin;
      day.returnDistanceM = back.distanceM;
      day.returnTravelIsEstimate = back.isEstimate;
    }
  }

  return { days, warnings, solverSource: usedOrTools ? "OR_TOOLS" : "HEURISTIC" };
}

function materializeOptimizedDay(
  candidates: ScoredPlace[],
  orderedPlaceIds: string[],
  arrivals: number[],
  opts: {
    visitDate: string;
    startLat: number;
    startLng: number;
    dayStart: string;
    dayEnd: string;
    dayBudget: number;
    hasCar: boolean;
    partySize: number;
    maxWalkingKm: number;
    maxItems: number;
    maxFoodStops: number;
    maxRestaurantStops: number;
  }
): { items: ItineraryItemOutput[]; warnings: string[] } {
  const byId = new Map(candidates.map((place) => [place.id, place]));
  const items = orderedPlaceIds.flatMap((id, index) => {
    const place = byId.get(id);
    if (!place) return [];
    return [{
      seqOrder: index + 1,
      placeId: place.id,
      nameKo: place.nameKo,
      nameEn: place.nameEn,
      category: place.category,
      address: place.address,
      addressEn: place.addressEn,
      allergens: place.allergens,
      dietOptions: place.dietOptions,
      onlineReservation: place.onlineReservation,
      lat: place.lat,
      lng: place.lng,
      openTime: place.openTime,
      closeTime: place.closeTime,
      plannedArrival: formatMinToTime(arrivals[index]),
      stayMinutes: place.recommendedStayMin,
      estCost: estimatePlaceCost(place, opts.partySize),
      travelMinToNext: null,
      distanceToNextM: null,
      travelIsEstimate: true,
      travelSource: "HAVERSINE" as const,
      recommendReason: buildRecommendReason(place, null),
      hasEnglishMenu: place.hasEnglishMenu,
      foreignCardPayment: place.foreignCardPayment,
      localScore: place.localScore,
      dataSource: place.dataSource ?? "MANUAL",
      imageUrl: place.imageUrl ?? null,
      kakaoPlaceId: place.kakaoPlaceId,
      kakaoPlaceUrl: place.kakaoPlaceUrl,
      kakaoRating: place.kakaoRating,
      kakaoReviewCount: place.kakaoReviewCount,
      kakaoPositiveReviewRate: place.kakaoPositiveReviewRate,
      kakaoReviewKeywords: place.kakaoReviewKeywords,
      kakaoReviewSource: place.kakaoReviewSource,
      kakaoReviewCollectedAt: place.kakaoReviewCollectedAt,
    }];
  });
  const warnings: string[] = [];
  if (!items.some((item) => item.category === "RESTAURANT")) warnings.push(`${opts.visitDate}: 배정 가능한 식당이 없습니다.`);
  return { items, warnings };
}

function scheduleSingleDay(
  candidates: ScoredPlace[],
  opts: {
    visitDate: string;
    startLat: number;
    startLng: number;
    dayStart: string;
    dayEnd: string;
    dayBudget: number;
    hasCar: boolean;
    partySize: number;
    maxWalkingKm: number;
    maxItems: number;
    maxFoodStops: number;
    maxRestaurantStops: number;
  }
): { items: ItineraryItemOutput[]; warnings: string[] } {
  const warnings: string[] = [];
  const items: ItineraryItemOutput[] = [];

  const openToday = candidates.filter((p) => isOpenOnDay(p, opts.visitDate));
  const closedToday = candidates.filter((p) => !isOpenOnDay(p, opts.visitDate));
  closedToday.forEach((p) => warnings.push(`${p.nameKo}은(는) ${opts.visitDate}에 휴무일이라 제외되었습니다.`));

  let remainingMeal = openToday.filter((p) => p.category === "RESTAURANT");
  let remainingNonMeal = openToday.filter((p) => p.category !== "RESTAURANT");

  let currentLat = opts.startLat;
  let currentLng = opts.startLng;
  let currentTimeMin = parseTimeToMin(opts.dayStart);
  const endTimeMin = parseTimeToMin(opts.dayEnd);
  let cumulativeCost = 0;
  let cumulativeWalkingM = 0;
  let seq = 1;
  let lunchDone = false;
  let dinnerDone = false;
  let foodStopCount = 0;
  let restaurantCount = 0;

  const budgetOk = (cost: number) => cumulativeCost + cost <= opts.dayBudget + 1; // +1 부동소수 오차 허용

  const categoryAllowed = (place: ScoredPlace) => {
    if (place.category === "RESTAURANT" && restaurantCount >= opts.maxRestaurantStops) return false;
    if (["RESTAURANT", "CAFE"].includes(place.category) && foodStopCount >= opts.maxFoodStops) return false;
    return true;
  };

  while (currentTimeMin < endTimeMin && items.length < opts.maxItems) {
    const wantLunch = !lunchDone && currentTimeMin >= 12 * 60 && remainingMeal.length > 0;
    const wantDinner = !dinnerDone && currentTimeMin >= 18 * 60 && remainingMeal.length > 0;
    let pool = wantLunch || wantDinner ? remainingMeal : remainingNonMeal;
    if (pool.length === 0) pool = wantLunch || wantDinner ? remainingNonMeal : remainingMeal;
    if (pool.length === 0) break;

    // 이동시간 + 영업시간 + 예산을 모두 만족하는 후보 중 가장 가까운 곳을 선택
    let chosenIdx = -1;
    let chosenTravel = Infinity;
    let chosenArrival = -1;
    for (let i = 0; i < pool.length; i++) {
      const p = pool[i];
      if (!categoryAllowed(p)) continue;
      const cost = estimatePlaceCost(p.priceTier, opts.partySize);
      if (!budgetOk(cost)) continue;
      const travel = estimateTravelSync(currentLat, currentLng, p.lat, p.lng, opts.hasCar);
      if (!opts.hasCar && cumulativeWalkingM + travel.distanceM > opts.maxWalkingKm * 1000) continue;
      const arrival = currentTimeMin + travel.durationMin;
      const openMin = parseTimeToMin(p.openTime);
      const closeMin = parseTimeToMin(p.closeTime);
      const departure = arrival + p.recommendedStayMin;
      if (arrival < openMin || departure > closeMin || departure > endTimeMin) continue;
      // 이동 시간 최소화 우선
      const travelScore = travel.durationMin * 2.0 - p.score * 2;
      if (travelScore < chosenTravel) {
        chosenTravel = travelScore;
        chosenIdx = i;
        chosenArrival = arrival;
      }
    }

    if (chosenIdx === -1) {
      // 이 풀에서 조건을 만족하는 후보가 없으면, 다른 풀(비식사/식사)로 한 번 더 시도
      const altPool = pool === remainingMeal ? remainingNonMeal : remainingMeal;
      let altIdx = -1;
      let altTravel = Infinity;
      let altArrival = -1;
      for (let i = 0; i < altPool.length; i++) {
        const p = altPool[i];
        if (!categoryAllowed(p)) continue;
        const cost = estimatePlaceCost(p.priceTier, opts.partySize);
        if (!budgetOk(cost)) continue;
        const travel = estimateTravelSync(currentLat, currentLng, p.lat, p.lng, opts.hasCar);
        if (!opts.hasCar && cumulativeWalkingM + travel.distanceM > opts.maxWalkingKm * 1000) continue;
        const arrival = currentTimeMin + travel.durationMin;
        const openMin = parseTimeToMin(p.openTime);
        const closeMin = parseTimeToMin(p.closeTime);
        const departure = arrival + p.recommendedStayMin;
        if (arrival < openMin || departure > closeMin || departure > endTimeMin) continue;
        if (travel.durationMin < altTravel) {
          altTravel = travel.durationMin;
          altIdx = i;
          altArrival = arrival;
        }
      }
      if (altIdx === -1) break; // 더 이상 넣을 수 있는 후보가 없음
      const place = altPool.splice(altIdx, 1)[0];
      pushItem(place, altArrival);
      continue;
    }

    const place = pool.splice(chosenIdx, 1)[0];
    pushItem(place, chosenArrival);
  }

  function pushItem(place: ScoredPlace, arrivalMin: number) {
    const cost = estimatePlaceCost(place.priceTier, opts.partySize);
    const incomingTravel = estimateTravelSync(currentLat, currentLng, place.lat, place.lng, opts.hasCar);
    if (!opts.hasCar) cumulativeWalkingM += incomingTravel.distanceM;
    cumulativeCost += cost;
    if (place.category === "RESTAURANT") {
      restaurantCount++;
      if (currentTimeMin >= 12 * 60 && !lunchDone) lunchDone = true;
      else dinnerDone = true;
    }
    if (["RESTAURANT", "CAFE"].includes(place.category)) foodStopCount++;
    items.push({
      seqOrder: seq++,
      placeId: place.id,
      nameKo: place.nameKo,
      nameEn: place.nameEn,
      category: place.category,
      address: place.address,
      addressEn: place.addressEn,
      allergens: place.allergens,
      dietOptions: place.dietOptions,
      onlineReservation: place.onlineReservation,
      lat: place.lat,
      lng: place.lng,
      openTime: place.openTime,
      closeTime: place.closeTime,
      plannedArrival: formatMinToTime(arrivalMin),
      stayMinutes: place.recommendedStayMin,
      estCost: cost,
      travelMinToNext: null,
      distanceToNextM: null,
      travelIsEstimate: true,
      travelSource: "HAVERSINE",
      recommendReason: buildRecommendReason(place, null),
      hasEnglishMenu: place.hasEnglishMenu,
      foreignCardPayment: place.foreignCardPayment,
      localScore: place.localScore,
      dataSource: place.dataSource ?? "MANUAL",
      imageUrl: place.imageUrl ?? null,
      kakaoPlaceId: place.kakaoPlaceId,
      kakaoPlaceUrl: place.kakaoPlaceUrl,
      kakaoRating: place.kakaoRating,
      kakaoReviewCount: place.kakaoReviewCount,
      kakaoPositiveReviewRate: place.kakaoPositiveReviewRate,
      kakaoReviewKeywords: place.kakaoReviewKeywords,
      kakaoReviewSource: place.kakaoReviewSource,
      kakaoReviewCollectedAt: place.kakaoReviewCollectedAt,
    });
    currentTimeMin = arrivalMin + place.recommendedStayMin;
    currentLat = place.lat;
    currentLng = place.lng;
  }

  if (!lunchDone) warnings.push(`${opts.visitDate}: 점심시간대에 배정된 식당이 없습니다.`);
  if (!dinnerDone) warnings.push(`${opts.visitDate}: 저녁시간대에 배정된 식당이 없습니다.`);
  if (!opts.hasCar && cumulativeWalkingM >= opts.maxWalkingKm * 1000 * 0.9) {
    warnings.push(`${opts.visitDate}: 설정한 최대 도보 거리 ${opts.maxWalkingKm}km에 근접해 후보 일부를 제외했습니다.`);
  }

  return { items, warnings };
}
