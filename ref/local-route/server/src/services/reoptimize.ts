import { prisma } from "../db.js";
import { scorePlaces } from "./recommend.js";
import { buildItinerary, formatMinToTime, parseTimeToMin } from "./schedule.js";
import { getTravelEstimate } from "./kakao.js";
import { computeRhythmProfile, type RhythmSample } from "./paceLearning.js";

type Action = "REMOVE" | "PIN" | "UNPIN" | "REPLACE";

/** 되돌리기로 복원해도 실측 기록이 사라지면 안 되므로 actual* 는 항상 함께 실어 나른다. */
function actualsOf(item: any) {
  return { actualArrival: item.actualArrival ?? null, actualDeparture: item.actualDeparture ?? null, actualStayMinutes: item.actualStayMinutes ?? null };
}

function itemCreate(item: any, pinnedIds: string[]) {
  return {
    placeId: item.placeId, seqOrder: item.seqOrder, plannedArrival: item.plannedArrival,
    stayMinutes: item.stayMinutes, estCost: item.estCost, travelMinToNext: item.travelMinToNext,
    distanceToNextM: item.distanceToNextM, travelIsEstimate: item.travelIsEstimate,
    travelSource: item.travelSource, recommendReason: item.recommendReason,
    isPinned: pinnedIds.includes(item.placeId),
    ...actualsOf(item),
  };
}

function snapshot(day: any, preference: { excludedPlaceIds: string; mustVisitPlaceIds: string }) {
  return JSON.stringify({
    day: { startTravelMin: day.startTravelMin, startDistanceM: day.startDistanceM, startTravelIsEstimate: day.startTravelIsEstimate, returnTravelMin: day.returnTravelMin, returnDistanceM: day.returnDistanceM, returnTravelIsEstimate: day.returnTravelIsEstimate },
    items: day.items.map((item: any) => ({ placeId: item.placeId, seqOrder: item.seqOrder, plannedArrival: item.plannedArrival, stayMinutes: item.stayMinutes, estCost: item.estCost, travelMinToNext: item.travelMinToNext, distanceToNextM: item.distanceToNextM, travelIsEstimate: item.travelIsEstimate, travelSource: item.travelSource, recommendReason: item.recommendReason, isPinned: item.isPinned, ...actualsOf(item) })),
    preference: { excludedPlaceIds: preference.excludedPlaceIds, mustVisitPlaceIds: preference.mustVisitPlaceIds },
  });
}

export async function reoptimizeItineraryDay(itineraryId: string, dayIndex: number, body: { action: Action; itemId?: string; replacementPlaceId?: string }) {
  const itinerary = await prisma.itinerary.findUnique({ where: { id: itineraryId }, include: { trip: { include: { preference: true, lodgingPlace: true } }, days: { include: { items: true } } } });
  if (!itinerary?.trip.preference) throw new Error("ITINERARY_NOT_FOUND");
  const target = itinerary.days.find((day) => day.dayIndex === dayIndex);
  if (!target) throw new Error("DAY_NOT_FOUND");
  const changed = body.itemId ? target.items.find((item) => item.id === body.itemId) : undefined;
  if (body.itemId && !changed) throw new Error("ITEM_NOT_FOUND");

  let pinned = target.items.filter((item) => item.isPinned).map((item) => item.placeId);
  const excluded = JSON.parse(itinerary.trip.preference.excludedPlaceIds) as string[];
  let mustVisit = JSON.parse(itinerary.trip.preference.mustVisitPlaceIds) as string[];
  if (body.action === "PIN" && changed) pinned = [...new Set([...pinned, changed.placeId])];
  if (body.action === "PIN" && changed) mustVisit = [...new Set([...mustVisit, changed.placeId])];
  if (body.action === "UNPIN" && changed) { pinned = pinned.filter((id) => id !== changed.placeId); mustVisit = mustVisit.filter((id) => id !== changed.placeId); }
  if ((body.action === "REMOVE" || body.action === "REPLACE") && changed) {
    pinned = pinned.filter((id) => id !== changed.placeId);
    mustVisit = mustVisit.filter((id) => id !== changed.placeId);
    excluded.push(changed.placeId);
  }
  if (body.action === "REPLACE") {
    if (!body.replacementPlaceId) throw new Error("REPLACEMENT_REQUIRED");
    pinned.push(body.replacementPlaceId);
    mustVisit = [...new Set([...mustVisit, body.replacementPlaceId])];
  }

  const usedOtherDays = itinerary.days.filter((day) => day.id !== target.id).flatMap((day) => day.items.map((item) => item.placeId));
  const places = (await prisma.place.findMany({ where: { category: { in: ["TOURIST", "RESTAURANT", "CAFE"] } } }));
  const pref = itinerary.trip.preference;
  const scored = scorePlaces(places, JSON.parse(pref.tasteTags), target.dayBudget, itinerary.trip.partySize, [...new Set([...excluded, ...usedOtherDays])], {
    mode: itinerary.mode as any,
    ratios: { landmark: pref.landmarkRatio, local: pref.localRatio, easy: pref.easyRatio },
    allergies: JSON.parse(pref.allergies), dietType: pref.dietType,
  });
  const base = itinerary.trip.lodgingPlace ?? { lat: itinerary.trip.originLat, lng: itinerary.trip.originLng };
  const result = await buildItinerary(scored, { startDate: target.visitDate, endDate: target.visitDate, originLat: base.lat, originLng: base.lng, totalBudget: target.dayBudget, partySize: itinerary.trip.partySize, hasCar: itinerary.trip.hasCar, pace: itinerary.trip.pace as any, dayStart: itinerary.trip.dayStart, dayEnd: itinerary.trip.dayEnd, maxWalkingKm: itinerary.trip.maxWalkingKm, mustVisitPlaceIds: pinned, recommendationMode: itinerary.mode });
  const next = result.days[0];
  const nextIds = new Set(next.items.map((item) => item.placeId));
  if (!next.items.length || pinned.some((id) => !nextIds.has(id))) throw new Error("NO_FEASIBLE_ALTERNATIVE");
  const beforeJson = snapshot(target, pref);
  const afterJson = JSON.stringify({ day: { startTravelMin: next.startTravelMin, startDistanceM: next.startDistanceM, startTravelIsEstimate: next.startTravelIsEstimate, returnTravelMin: next.returnTravelMin, returnDistanceM: next.returnDistanceM, returnTravelIsEstimate: next.returnTravelIsEstimate }, items: next.items.map((item) => itemCreate(item, pinned)) });
  await prisma.$transaction([
    prisma.itineraryRevision.create({ data: { itineraryId, dayIndex, action: body.action, beforeJson, afterJson } }),
    prisma.itineraryItem.deleteMany({ where: { dayId: target.id } }),
    prisma.itineraryDay.update({ where: { id: target.id }, data: { startTravelMin: next.startTravelMin, startDistanceM: next.startDistanceM, startTravelIsEstimate: next.startTravelIsEstimate, returnTravelMin: next.returnTravelMin, returnDistanceM: next.returnDistanceM, returnTravelIsEstimate: next.returnTravelIsEstimate, items: { create: next.items.map((item) => itemCreate(item, pinned)) } } }),
    prisma.tripPreference.update({ where: { id: pref.id }, data: { excludedPlaceIds: JSON.stringify([...new Set(excluded)]), mustVisitPlaceIds: JSON.stringify(mustVisit) } }),
  ]);
  return { changedDayIndex: dayIndex, preservedDayIndexes: itinerary.days.filter((day) => day.dayIndex !== dayIndex).map((day) => day.dayIndex), beforeCount: target.items.length, afterCount: next.items.length };
}

/**
 * 같은 날짜 안에서 방문 순서만 바꾼다(장소 선택은 그대로) - 드래그앤드롭 재정렬용.
 * 도착시각·구간 이동시간을 처음부터 다시 계산하지만, 운영시간·예산을 벗어나도 막지 않고
 * 경고만 반환한다(v2 §16.5 정직성 원칙: 사실을 보여주되 사용자의 조작을 임의로 거부하지 않음).
 */
export async function reorderItineraryDay(itineraryId: string, dayIndex: number, itemIds: string[]) {
  const itinerary = await prisma.itinerary.findUnique({
    where: { id: itineraryId },
    include: { trip: { include: { lodgingPlace: true } }, days: { include: { items: { include: { place: true } } } } },
  });
  if (!itinerary) throw new Error("ITINERARY_NOT_FOUND");
  const target = itinerary.days.find((day) => day.dayIndex === dayIndex);
  if (!target) throw new Error("DAY_NOT_FOUND");

  const currentIds = new Set(target.items.map((item) => item.id));
  if (itemIds.length !== target.items.length || !itemIds.every((id) => currentIds.has(id)) || new Set(itemIds).size !== itemIds.length) {
    throw new Error("INVALID_ORDER");
  }

  const byId = new Map(target.items.map((item) => [item.id, item]));
  const ordered = itemIds.map((id) => byId.get(id)!);
  const base = itinerary.trip.lodgingPlace ?? { lat: itinerary.trip.originLat, lng: itinerary.trip.originLng };
  const hasCar = itinerary.trip.hasCar;

  const warnings: string[] = [];
  const nextItems: any[] = [];
  let cursorMin = parseTimeToMin(itinerary.trip.dayStart);
  let prevLat = base.lat;
  let prevLng = base.lng;

  for (let i = 0; i < ordered.length; i++) {
    const item = ordered[i];
    const est = await getTravelEstimate(prevLat, prevLng, item.place.lat, item.place.lng, hasCar);
    if (i === 0) {
      cursorMin += est.durationMin;
    } else {
      nextItems[i - 1].travelMinToNext = est.durationMin;
      nextItems[i - 1].distanceToNextM = est.distanceM;
      nextItems[i - 1].travelIsEstimate = est.isEstimate;
      nextItems[i - 1].travelSource = est.isEstimate ? "HAVERSINE" : "KAKAO_MOBILITY";
    }
    const arrivalMin = cursorMin;
    if (arrivalMin < parseTimeToMin(item.place.openTime) || arrivalMin > parseTimeToMin(item.place.closeTime)) {
      warnings.push(`${item.place.nameKo}의 새 도착 시각(${formatMinToTime(arrivalMin)})이 영업시간(${item.place.openTime}~${item.place.closeTime})을 벗어났습니다.`);
    }
    nextItems.push({
      placeId: item.placeId, seqOrder: i + 1, plannedArrival: formatMinToTime(arrivalMin),
      stayMinutes: item.stayMinutes, estCost: item.estCost,
      travelMinToNext: null, distanceToNextM: null, travelIsEstimate: true, travelSource: "HAVERSINE",
      recommendReason: item.recommendReason, isPinned: item.isPinned,
      ...actualsOf(item),
    });
    cursorMin = arrivalMin + item.stayMinutes;
    prevLat = item.place.lat;
    prevLng = item.place.lng;
  }
  const dayEndMin = parseTimeToMin(itinerary.trip.dayEnd);
  if (cursorMin > dayEndMin) {
    warnings.push(`새 순서대로면 하루 종료 시각(${itinerary.trip.dayEnd})을 넘겨 ${formatMinToTime(cursorMin)}에 마지막 장소가 끝납니다.`);
  }
  const returnEst = ordered.length ? await getTravelEstimate(prevLat, prevLng, base.lat, base.lng, hasCar) : null;

  // 순서변경은 TripPreference를 건드리지 않으므로 snapshot()과 달리 preference 키를 아예 넣지 않는다
  // - undoLatestRevision이 before.preference가 없으면 preference 복원을 건너뛰기 때문에, 여기서
  // 가짜 값을 넣으면 되돌리기 시 실제 필수/제외 장소 목록이 빈 배열로 잘못 초기화된다.
  const beforeJson = JSON.stringify({
    day: { startTravelMin: target.startTravelMin, startDistanceM: target.startDistanceM, startTravelIsEstimate: target.startTravelIsEstimate, returnTravelMin: target.returnTravelMin, returnDistanceM: target.returnDistanceM, returnTravelIsEstimate: target.returnTravelIsEstimate },
    items: target.items.map((item) => ({ placeId: item.placeId, seqOrder: item.seqOrder, plannedArrival: item.plannedArrival, stayMinutes: item.stayMinutes, estCost: item.estCost, travelMinToNext: item.travelMinToNext, distanceToNextM: item.distanceToNextM, travelIsEstimate: item.travelIsEstimate, travelSource: item.travelSource, recommendReason: item.recommendReason, isPinned: item.isPinned, ...actualsOf(item) })),
  });
  const afterJson = JSON.stringify({
    day: {
      startTravelMin: target.startTravelMin, startDistanceM: target.startDistanceM, startTravelIsEstimate: target.startTravelIsEstimate,
      returnTravelMin: returnEst?.durationMin ?? null, returnDistanceM: returnEst?.distanceM ?? null, returnTravelIsEstimate: returnEst?.isEstimate ?? true,
    },
    items: nextItems,
  });

  await prisma.$transaction([
    prisma.itineraryRevision.create({ data: { itineraryId, dayIndex, action: "REORDER", beforeJson, afterJson } }),
    prisma.itineraryItem.deleteMany({ where: { dayId: target.id } }),
    prisma.itineraryDay.update({
      where: { id: target.id },
      data: {
        returnTravelMin: returnEst?.durationMin ?? null, returnDistanceM: returnEst?.distanceM ?? null, returnTravelIsEstimate: returnEst?.isEstimate ?? true,
        items: { create: nextItems },
      },
    }),
  ]);

  return { changedDayIndex: dayIndex, items: nextItems, warnings };
}

export type ReplanStrategy = "KEEP_ALL" | "DROP_ONE" | "DEFER_LAST";

/**
 * 페이스 러닝 - 여행 중 "지금 이 시각, 이 자리"를 출발점으로 남은 일정만 다시 짠다.
 *
 * reoptimizeItineraryDay 와의 결정적 차이는 두 가지다.
 *  1. 이미 다녀온 항목(actualDeparture 기록됨)은 손대지 않고 그대로 보존한다.
 *  2. 출발점은 현재 위치·현재 시각이지만, 복귀점은 여전히 숙소다.
 * 하루 전체를 dayStart 부터 다시 짜면 이미 다녀온 장소까지 갈아엎게 되므로 이 경로가 따로 필요하다.
 */
export async function replanRemainingDay(
  itineraryId: string,
  dayIndex: number,
  body: { currentTime: string; lat: number; lng: number; strategy?: ReplanStrategy; useRhythm?: boolean }
) {
  const itinerary = await prisma.itinerary.findUnique({
    where: { id: itineraryId },
    include: { trip: { include: { preference: true, lodgingPlace: true } }, days: { include: { items: { include: { place: true } } } } },
  });
  if (!itinerary?.trip.preference) throw new Error("ITINERARY_NOT_FOUND");
  const target = itinerary.days.find((day) => day.dayIndex === dayIndex);
  if (!target) throw new Error("DAY_NOT_FOUND");

  const ordered = [...target.items].sort((a, b) => a.seqOrder - b.seqOrder);
  const visited = ordered.filter((item) => item.actualDeparture);
  const staying = ordered.find((item) => item.actualArrival && !item.actualDeparture) ?? null;
  // 머무는 중인 곳도 "이미 확정된 현실"이므로 보존 대상이다.
  const preserved = staying ? [...visited, staying] : visited;
  const preservedIds = new Set(preserved.map((item) => item.id));
  const droppedCandidates = ordered.filter((item) => !preservedIds.has(item.id));
  if (!droppedCandidates.length) throw new Error("NOTHING_TO_REPLAN");

  const spentCost = preserved.reduce((sum, item) => sum + item.estCost, 0);
  const remainingBudget = Math.max(0, target.dayBudget - spentCost);
  const strategy: ReplanStrategy = body.strategy ?? "KEEP_ALL";

  // 남은 자리 수. DROP_ONE 은 한 곳을 덜어내 여유를 만들고, DEFER_LAST 는 마지막 한 곳을 다음 날로 미룬다.
  const targetCount = strategy === "KEEP_ALL" ? droppedCandidates.length : Math.max(1, droppedCandidates.length - 1);

  const pref = itinerary.trip.preference;
  const excluded = JSON.parse(pref.excludedPlaceIds) as string[];
  const usedOtherDays = itinerary.days.filter((day) => day.id !== target.id).flatMap((day) => day.items.map((item) => item.placeId));
  const places = await prisma.place.findMany({ where: { category: { in: ["TOURIST", "RESTAURANT", "CAFE"] } } });
  const scored = scorePlaces(places, JSON.parse(pref.tasteTags), remainingBudget, itinerary.trip.partySize,
    [...new Set([...excluded, ...usedOtherDays, ...preserved.map((item) => item.placeId)])], {
      mode: itinerary.mode as any,
      ratios: { landmark: pref.landmarkRatio, local: pref.localRatio, easy: pref.easyRatio },
      allergies: JSON.parse(pref.allergies), dietType: pref.dietType,
      courseCategory: pref.courseCategory,
    });

  const lodging = itinerary.trip.lodgingPlace ?? { lat: itinerary.trip.originLat, lng: itinerary.trip.originLng };
  const rhythm = body.useRhythm === false ? null : await loadRhythmProfile(itinerary.tripId);
  // 남은 시간이 하루 종료보다 늦으면 재계산할 여지가 없다.
  const dayEndMin = parseTimeToMin(itinerary.trip.dayEnd);
  const startMin = parseTimeToMin(body.currentTime);
  if (startMin >= dayEndMin) throw new Error("DAY_ALREADY_OVER");

  const result = await buildItinerary(scored, {
    startDate: target.visitDate, endDate: target.visitDate,
    originLat: body.lat, originLng: body.lng,          // 출발점 = 지금 서 있는 곳
    returnLat: lodging.lat, returnLng: lodging.lng,    // 복귀점 = 숙소
    totalBudget: remainingBudget,
    partySize: itinerary.trip.partySize,
    hasCar: itinerary.trip.hasCar,
    pace: itinerary.trip.pace as any,
    dayStart: body.currentTime,                        // 시작 시각 = 지금
    dayEnd: itinerary.trip.dayEnd,
    maxWalkingKm: itinerary.trip.maxWalkingKm,
    mustVisitPlaceIds: [],
    recommendationMode: itinerary.mode,
    stayMinutesScale: rhythm?.hasProfile ? rhythm.scale : undefined,
  });

  const rebuilt = (result.days[0]?.items ?? []).slice(0, targetCount);
  if (!rebuilt.length) throw new Error("NO_FEASIBLE_ALTERNATIVE");

  const beforeJson = snapshot(target, pref);
  const nextItems = [
    ...preserved.map((item) => ({
      placeId: item.placeId, seqOrder: item.seqOrder, plannedArrival: item.plannedArrival,
      stayMinutes: item.stayMinutes, estCost: item.estCost, travelMinToNext: item.travelMinToNext,
      distanceToNextM: item.distanceToNextM, travelIsEstimate: item.travelIsEstimate,
      travelSource: item.travelSource, recommendReason: item.recommendReason, isPinned: item.isPinned,
      actualArrival: item.actualArrival, actualDeparture: item.actualDeparture, actualStayMinutes: item.actualStayMinutes,
    })),
    ...rebuilt.map((item, index) => ({
      placeId: item.placeId, seqOrder: preserved.length + index + 1, plannedArrival: item.plannedArrival,
      stayMinutes: item.stayMinutes, estCost: item.estCost, travelMinToNext: item.travelMinToNext,
      distanceToNextM: item.distanceToNextM, travelIsEstimate: item.travelIsEstimate,
      travelSource: item.travelSource, recommendReason: item.recommendReason, isPinned: false,
      actualArrival: null, actualDeparture: null, actualStayMinutes: null,
    })),
  ];
  const afterJson = JSON.stringify({
    day: { startTravelMin: target.startTravelMin, startDistanceM: target.startDistanceM, startTravelIsEstimate: target.startTravelIsEstimate, returnTravelMin: result.days[0]?.returnTravelMin ?? null, returnDistanceM: result.days[0]?.returnDistanceM ?? null, returnTravelIsEstimate: result.days[0]?.returnTravelIsEstimate ?? true },
    items: nextItems,
  });

  await prisma.$transaction([
    prisma.itineraryRevision.create({ data: { itineraryId, dayIndex, action: `REPLAN_${strategy}`, beforeJson, afterJson } }),
    prisma.itineraryItem.deleteMany({ where: { dayId: target.id } }),
    prisma.itineraryDay.update({
      where: { id: target.id },
      data: {
        returnTravelMin: result.days[0]?.returnTravelMin ?? null,
        returnDistanceM: result.days[0]?.returnDistanceM ?? null,
        returnTravelIsEstimate: result.days[0]?.returnTravelIsEstimate ?? true,
        items: { create: nextItems },
      },
    }),
  ]);

  return {
    changedDayIndex: dayIndex,
    strategy,
    preservedCount: preserved.length,
    replacedCount: rebuilt.length,
    droppedCount: droppedCandidates.length - rebuilt.length,
    rhythmApplied: rhythm?.hasProfile ?? false,
    warnings: result.warnings,
  };
}

/**
 * 이 여행에서 지금까지 쌓인 실측 체류시간으로 개인 리듬 계수를 만든다.
 *
 * 계수를 만들 만큼 표본이 모이지 않아도 프로필 자체는 돌려준다(hasProfile: false).
 * 그래야 "아직 N건 모았다"를 정직하게 보여줄 수 있다. 실측이 아예 없을 때만 null 이다.
 */
export async function loadRhythmProfile(tripId: string) {
  const items = await prisma.itineraryItem.findMany({
    where: { day: { itinerary: { tripId } }, actualStayMinutes: { not: null } },
    select: { stayMinutes: true, actualStayMinutes: true, place: { select: { category: true } } },
  });
  if (!items.length) return null;
  const samples: RhythmSample[] = items.map((item) => ({
    category: item.place.category,
    plannedStayMinutes: item.stayMinutes,
    actualStayMinutes: item.actualStayMinutes!,
  }));
  return computeRhythmProfile(samples);
}

export async function undoLatestRevision(itineraryId: string) {
  const revision = await prisma.itineraryRevision.findFirst({ where: { itineraryId, undoneAt: null }, orderBy: { createdAt: "desc" } });
  if (!revision) throw new Error("NO_REVISION");
  const day = await prisma.itineraryDay.findFirstOrThrow({ where: { itineraryId, dayIndex: revision.dayIndex }, include: { itinerary: { include: { trip: { include: { preference: true } } } } } });
  const before = JSON.parse(revision.beforeJson);
  await prisma.$transaction([
    prisma.itineraryItem.deleteMany({ where: { dayId: day.id } }),
    prisma.itineraryDay.update({ where: { id: day.id }, data: { ...before.day, items: { create: before.items } } }),
    ...(before.preference && day.itinerary.trip.preference ? [prisma.tripPreference.update({ where: { id: day.itinerary.trip.preference.id }, data: before.preference })] : []),
    prisma.itineraryRevision.update({ where: { id: revision.id }, data: { undoneAt: new Date() } }),
  ]);
  return { restoredDayIndex: revision.dayIndex };
}
