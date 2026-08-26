import { prisma } from "../db.js";
import { diversifyScoredPlaces, scorePlaces } from "./recommend.js";
import { buildItinerary } from "./schedule.js";
import { applyCourseCategoryTasteTags, findCourseCategory } from "./courseCategories.js";

export class GenerationError extends Error {
  constructor(public code: string, message: string, public details?: unknown) {
    super(message);
  }
}

export type GenerationStage = "COLLECTING" | "SCORING" | "OPTIMIZING" | "VALIDATING" | "DONE";

function preferenceSignature(trip: {
  originLat: number; originLng: number; startDate: string; endDate: string; pace: string; hasCar: boolean;
  dayStart: string; dayEnd: string; preference: { tasteTags: string; courseCategory: string | null } | null;
}) {
  const preference = trip.preference;
  let tags: string[] = [];
  try { tags = JSON.parse(preference?.tasteTags ?? "[]"); } catch { tags = []; }
  const duration = Math.round((new Date(`${trip.endDate}T00:00:00`).getTime() - new Date(`${trip.startDate}T00:00:00`).getTime()) / 86_400_000) + 1;
  return JSON.stringify({
    area: [trip.originLat.toFixed(2), trip.originLng.toFixed(2)], duration, pace: trip.pace, hasCar: trip.hasCar,
    dayStart: trip.dayStart, dayEnd: trip.dayEnd, tags: [...new Set(tags)].sort(),
    courseCategory: preference?.courseCategory ?? null,
  });
}

export async function generateItineraryForTrip(
  tripId: string,
  onProgress: (stage: GenerationStage, progress: number) => Promise<void>
): Promise<string> {
  await onProgress("COLLECTING", 15);
  const trip = await prisma.trip.findUnique({ where: { id: tripId }, include: { preference: true, lodgingPlace: true } });
  if (!trip || !trip.preference) throw new GenerationError("TRIP_NOT_FOUND", "여행 조건을 찾을 수 없습니다.");

  const places = (await prisma.place.findMany()).filter((place) => ["TOURIST", "RESTAURANT", "CAFE"].includes(place.category));
  const pref = trip.preference;
  const courseCategory = findCourseCategory(pref.courseCategory);
  const tasteTags = applyCourseCategoryTasteTags(JSON.parse(pref.tasteTags), pref.courseCategory);
  const mustVisitPlaceIds: string[] = JSON.parse(pref.mustVisitPlaceIds);
  const mustVisitAssignments: { placeId: string; dayIndex: number }[] = JSON.parse(pref.mustVisitAssignments ?? "[]");
  const excludedPlaceIds: string[] = JSON.parse(pref.excludedPlaceIds);
  const numDays = Math.round((new Date(`${trip.endDate}T00:00:00`).getTime() - new Date(`${trip.startDate}T00:00:00`).getTime()) / 86_400_000) + 1;
  const modes = ["ESSENTIAL", "LOCAL", "EASY"] as const;
  let selectedItineraryId = "";
  const currentSignature = preferenceSignature(trip);
  const recentItineraries = await prisma.itinerary.findMany({
    where: trip.ownerSessionId ? { trip: { ownerSessionId: trip.ownerSessionId } } : { tripId: trip.id },
    orderBy: { generatedAt: "desc" },
    take: 120,
    include: {
      trip: { include: { preference: true } },
      days: { include: { items: { select: { placeId: true } } } },
    },
  });

  for (let modeIndex = 0; modeIndex < modes.length; modeIndex++) {
  const mode = modes[modeIndex];
  const presets = { ESSENTIAL: { landmark: 70, local: 20, easy: 10 }, LOCAL: { landmark: 10, local: 80, easy: 10 }, EASY: { landmark: 10, local: 30, easy: 60 } } as const;
  const ratios = mode === trip.recommendationMode ? { landmark: pref.landmarkRatio, local: pref.localRatio, easy: pref.easyRatio } : presets[mode];

  await onProgress("SCORING", 20 + modeIndex * 25);
  const scored = scorePlaces(places, tasteTags, trip.totalBudget / numDays, trip.partySize, excludedPlaceIds, {
    mode,
    ratios,
    allergies: JSON.parse(pref.allergies), dietType: pref.dietType,
    courseCategory: pref.courseCategory,
    desiredFoods: JSON.parse((pref as { desiredFoods?: string }).desiredFoods ?? "[]"),
  });
  if (scored.length === 0) {
    throw new GenerationError("NO_FEASIBLE_SCHEDULE", "조건을 만족하는 장소 후보가 없습니다.", {
      suggestions: ["예산 늘리기", "여행 조건 완화"],
    });
  }

  const matchingHistory = recentItineraries
    .filter((itinerary) => itinerary.mode === mode && preferenceSignature(itinerary.trip) === currentSignature)
    .slice(0, 12);
  const recentUsage = new Map<string, number>();
  matchingHistory.forEach((itinerary, index) => {
    const recencyWeight = 2 * Math.pow(0.82, index);
    for (const placeId of new Set(itinerary.days.flatMap((day) => day.items.map((item) => item.placeId)))) {
      recentUsage.set(placeId, (recentUsage.get(placeId) ?? 0) + recencyWeight);
    }
  });
  const stayMinutesScale = courseCategory?.scheduleParams?.stayMinutesScale ?? 1;
  const categoryAdjusted = scored.map((place) => ({ ...place, recommendedStayMin: Math.max(20, Math.round(place.recommendedStayMin * stayMinutesScale)) }));
  const diversified = diversifyScoredPlaces(categoryAdjusted, recentUsage, `${trip.id}:${mode}:${matchingHistory.length}`, mustVisitPlaceIds);
  const categoryDayEnd = courseCategory?.scheduleParams?.dayEndTimeCap ?? courseCategory?.scheduleParams?.forbidTimeRange?.after;
  const effectiveDayEnd = categoryDayEnd && categoryDayEnd < trip.dayEnd ? categoryDayEnd : trip.dayEnd;

  await onProgress("OPTIMIZING", 28 + modeIndex * 25);
  const { days, solverSource } = await buildItinerary(diversified, {
    startDate: trip.startDate,
    endDate: trip.endDate,
    originLat: trip.lodgingPlace?.lat ?? trip.originLat,
    originLng: trip.lodgingPlace?.lng ?? trip.originLng,
    totalBudget: trip.totalBudget,
    partySize: trip.partySize,
    hasCar: trip.hasCar,
    pace: courseCategory?.scheduleParams?.pace ?? trip.pace as any,
    dayStart: trip.dayStart,
    dayEnd: effectiveDayEnd,
    maxWalkingKm: trip.maxWalkingKm * (courseCategory?.scheduleParams?.maxWalkDistanceScale ?? 1),
    mustVisitPlaceIds,
    mustVisitAssignments,
    recommendationMode: mode,
  });

  await onProgress("VALIDATING", 36 + modeIndex * 25);
  if (days.every((day) => day.items.length === 0)) {
    throw new GenerationError("NO_FEASIBLE_SCHEDULE", "설정한 활동시간과 이동 범위에서 방문 가능한 장소가 없습니다.", {
      conflicts: ["DAY_TIME_WINDOW", "MAX_WALKING_DISTANCE"],
    });
  }
  const scheduledIds = new Set(days.flatMap((day) => day.items.map((item) => item.placeId)));
  const missingRequired = mustVisitPlaceIds.filter((id) => !scheduledIds.has(id));
  if (missingRequired.length) {
    throw new GenerationError("REQUIRED_PLACE_INFEASIBLE", "필수 방문 장소를 시간·예산 조건 안에 배치할 수 없습니다.", { missingRequired });
  }

  const itinerary = await prisma.itinerary.create({
    data: {
      tripId: trip.id,
      status: "DONE",
      solverSource,
      mode,
      days: {
        create: days.map((day) => ({
          dayIndex: day.dayIndex,
          visitDate: day.visitDate,
          dayBudget: day.dayBudget,
          startTravelMin: day.startTravelMin,
          startDistanceM: day.startDistanceM,
          startTravelIsEstimate: day.startTravelIsEstimate,
          returnTravelMin: day.returnTravelMin,
          returnDistanceM: day.returnDistanceM,
          returnTravelIsEstimate: day.returnTravelIsEstimate,
          items: {
            create: day.items.map((item) => ({
              placeId: item.placeId,
              seqOrder: item.seqOrder,
              plannedArrival: item.plannedArrival,
              stayMinutes: item.stayMinutes,
              estCost: item.estCost,
              travelMinToNext: item.travelMinToNext,
              distanceToNextM: item.distanceToNextM,
              travelIsEstimate: item.travelIsEstimate,
              travelSource: item.travelSource,
              recommendReason: item.recommendReason,
            })),
          },
        })),
      },
    },
  });
  if (mode === trip.recommendationMode) selectedItineraryId = itinerary.id;
  }
  await prisma.trip.update({ where: { id: trip.id }, data: { status: "CONFIRMED" } });
  await onProgress("DONE", 100);
  return selectedItineraryId;
}
