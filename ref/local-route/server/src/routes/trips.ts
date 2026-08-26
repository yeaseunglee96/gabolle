import { Router } from "express";
import { prisma } from "../db.js";
import { scorePlaces } from "../services/recommend.js";
import { buildItinerary } from "../services/schedule.js";
import { createJob, getJob, subscribeJob } from "../services/jobs.js";
import { loadRhythmProfile, reoptimizeItineraryDay, reorderItineraryDay, replanRemainingDay, undoLatestRevision, type ReplanStrategy } from "../services/reoptimize.js";
import { computeDayPace, resolveStayMinutes, type PaceItem } from "../services/paceLearning.js";
import { getEmbeddedRoute, haversineDistanceM, searchKakaoLocations } from "../services/kakao.js";
import { localizeRoute, transferDifficultyFromCount } from "../services/navText.js";
import { searchPlaceImage } from "../services/placeImages.js";
import { applyCourseCategoryTasteTags, findCourseCategory, getCourseCategories } from "../services/courseCategories.js";
import type { CreateTripRequest, ItineraryOutput, TripMeta } from "../types.js";
import { recordEvent, recordEventBestEffort } from "../services/events.js";
import { optionalSession, requireItineraryEditor, requireSession, requireTripEditor, requireTripViewer } from "../services/auth.js";

export const tripsRouter = Router();

tripsRouter.get("/trips", async (req, res, next) => {
  try {
    const session = await requireSession(req, res); if (!session) return;
    const trips = await prisma.trip.findMany({
      where: { OR: [{ ownerSessionId: session.id }, { members: { some: { sessionId: session.id, joinedAt: { not: null }, revokedAt: null } } }] },
      orderBy: { createdAt: "desc" },
      include: { itineraries: { orderBy: { generatedAt: "desc" }, take: 1, include: { stories: { where: { authorSessionId: session.id }, orderBy: { createdAt: "desc" }, select: { imageDataJson: true } }, days: { orderBy: { dayIndex: "asc" }, include: { items: { orderBy: { seqOrder: "asc" }, include: { place: { select: { nameKo: true, imageUrl: true } } } } } } } } },
    });
    res.json({ trips: trips.map((trip) => {
      const itinerary = trip.itineraries[0] ?? null;
      const items = itinerary?.days.flatMap((day) => day.items) ?? [];
      const storyImages = itinerary?.stories.flatMap((story) => { try { return JSON.parse(story.imageDataJson) as string[]; } catch { return []; } }) ?? [];
      return { id: trip.id, origin: trip.origin, startDate: trip.startDate, endDate: trip.endDate, partySize: trip.partySize, pace: trip.pace, status: itinerary ? "READY" : trip.status, itineraryId: itinerary?.id ?? null, placeCount: items.length, storyCount: itinerary?.stories.length ?? 0, coverImage: storyImages[0] ?? items.find((item) => item.place.imageUrl)?.place.imageUrl ?? null, highlights: items.slice(0, 3).map((item) => item.place.nameKo), createdAt: trip.createdAt };
    }) });
  } catch (error) { next(error); }
});

tripsRouter.get("/locations/search", async (req, res, next) => {
  try {
    const query = typeof req.query.query === "string" ? req.query.query.trim().slice(0, 100) : "";
    if (query.length < 2) return res.status(400).json({ error_code: "QUERY_TOO_SHORT", message: "주소나 장소명을 2자 이상 입력해 주세요." });
    return res.json({ locations: await searchKakaoLocations(query) });
  } catch (error) { next(error); }
});

tripsRouter.get("/course-categories", (_req, res) => {
  res.setHeader("Cache-Control", "public, max-age=3600");
  res.json({ categories: getCourseCategories() });
});

tripsRouter.get("/places/:id/image", async (req, res, next) => {
  try {
    const place = await prisma.place.findUnique({ where: { id: req.params.id }, select: { id: true, nameKo: true, address: true, category: true, imageUrl: true } });
    if (!place) return res.status(404).json({ error_code: "PLACE_NOT_FOUND" });
    const match = await searchPlaceImage(place);
    return res.json(match ?? { imageUrl: null, sourceUrl: null, provider: null, title: null });
  } catch (error) { next(error); }
});

tripsRouter.get("/routes/directions", async (req, res, next) => {
  try {
    const startLat = Number(req.query.startLat); const startLng = Number(req.query.startLng);
    const endLat = Number(req.query.endLat); const endLng = Number(req.query.endLng);
    const mode = req.query.mode === "CAR" ? "CAR" : "TRANSIT";
    const lang = req.query.lang === "EN" ? "EN" : "KO";
    if (![startLat, startLng, endLat, endLng].every(Number.isFinite)) return res.status(400).json({ error_code: "INVALID_COORDINATES" });
    const route = await getEmbeddedRoute(startLat, startLng, endLat, endLng, mode);
    // F-NAV-01/03(v4 6.3장): 구글맵이 한국에서 지원하지 못하는 영어 턴바이턴 안내와
    // 환승 난이도 요약을 얹는다. 별도 DB 테이블 없이 매 요청 응답을 가공한다(v2 13.4장).
    await recordEventBestEffort({ eventType: "route_guide_viewed", entityType: "route", entityId: `${startLat.toFixed(3)},${startLng.toFixed(3)}-${endLat.toFixed(3)},${endLng.toFixed(3)}`, language: lang, payload: { mode } });
    return res.json({ ...localizeRoute(route, lang), transferDifficulty: transferDifficultyFromCount(route.transfers) });
  } catch (error) { next(error); }
});

tripsRouter.get("/routes/taxi-card", async (req, res, next) => {
  try {
    const place = await prisma.place.findUnique({ where: { id: String(req.query.placeId ?? "") }, select: { id: true, nameKo: true, nameEn: true, address: true } });
    if (!place) return res.status(404).json({ error_code: "PLACE_NOT_FOUND" });
    await recordEventBestEffort({ eventType: "taxi_card_generated", entityType: "place", entityId: place.id });
    // F-NAV-02(v4 6.3장): 한국어를 못 읽는 외국인이 택시 기사에게 그대로 보여줄 수 있는 카드.
    return res.json({ placeId: place.id, nameKo: place.nameKo, nameEn: place.nameEn, addressKo: place.address, phraseKo: `기사님, 여기로 가주세요: ${place.address}` });
  } catch (error) { next(error); }
});

function buildTripMeta(
  trip: {
    origin: string;
    originLat: number;
    originLng: number;
    startDate: string;
    endDate: string;
    partySize: number;
    adultCount: number;
    childCount: number;
    totalBudget: number;
    hasCar: boolean;
    pace: string;
    dayStart: string;
    dayEnd: string;
    maxWalkingKm: number;
    recommendationMode: string;
  },
  preference: {
    tasteTags: string;
    courseCategory: string | null;
    language: string;
    allergies: string;
    dietType: string;
    landmarkRatio: number;
    localRatio: number;
    easyRatio: number;
  },
  lodgingPlace?: { id: string; nameKo: string; address: string } | null
): TripMeta {
  return {
    origin: trip.origin,
    courseCategory: preference.courseCategory,
    originLat: trip.originLat,
    originLng: trip.originLng,
    startDate: trip.startDate,
    endDate: trip.endDate,
    partySize: trip.partySize,
    adultCount: trip.adultCount,
    childCount: trip.childCount,
    totalBudget: trip.totalBudget,
    hasCar: trip.hasCar,
    pace: trip.pace as TripMeta["pace"],
    dayStart: trip.dayStart,
    dayEnd: trip.dayEnd,
    maxWalkingKm: trip.maxWalkingKm,
    recommendationMode: trip.recommendationMode as TripMeta["recommendationMode"],
    tasteTags: JSON.parse(preference.tasteTags),
    language: preference.language as TripMeta["language"],
    allergies: JSON.parse(preference.allergies),
    dietType: preference.dietType as TripMeta["dietType"],
    lodgingPlaceId: lodgingPlace?.id ?? null,
    lodgingName: lodgingPlace?.nameKo ?? null,
    lodgingAddress: lodgingPlace?.address ?? null,
    landmarkRatio: preference.landmarkRatio,
    localRatio: preference.localRatio,
    easyRatio: preference.easyRatio,
  };
}

tripsRouter.get("/places", async (req, res) => {
  const category = typeof req.query.category === "string" ? req.query.category : undefined;
  const search = typeof req.query.search === "string" ? req.query.search.trim().slice(0, 100) : undefined;
  const places = await prisma.place.findMany({
    where: {
      ...(category ? { category } : {}),
      // 위저드의 "가고 싶은 곳" 검색은 카카오 전체 검색이 아니라 이 앱의 시드 장소 카탈로그
      // 안에서만 찾는다 - mustVisitPlaceIds는 이 카탈로그의 Place.id를 기대하기 때문에,
      // 카카오 POI id를 그대로 저장하면 스케줄러가 절대 찾지 못해 조용히 실패한다.
      ...(search ? { OR: [{ nameKo: { contains: search } }, { nameEn: { contains: search } }, { address: { contains: search } }] } : {}),
    },
    take: search ? 20 : undefined,
  });
  res.json(places);
});

tripsRouter.get("/places/:id", async (req, res) => {
  const lang = typeof req.query.lang === "string" ? req.query.lang.toUpperCase() : "KO";
  const place = await prisma.place.findUnique({ where: { id: req.params.id }, include: { translations: { where: { lang } } } });
  if (!place) return res.status(404).json({ error_code: "PLACE_NOT_FOUND" });
  const translation = place.translations[0] ?? null;
  return res.json({ ...place, displayName: translation?.name ?? place.nameKo, displayAddress: translation?.address ?? place.addressEn ?? place.address, allergens: JSON.parse(place.allergens), dietOptions: JSON.parse(place.dietOptions), translationSource: translation?.source ?? null });
});

tripsRouter.post("/trips/:id/itineraries\\:generate", async (req, res) => {
  const trip = await prisma.trip.findUnique({ where: { id: req.params.id }, select: { id: true } });
  if (!trip) return res.status(404).json({ error_code: "TRIP_NOT_FOUND", message: "여행 조건을 찾을 수 없습니다." });
  if (!await requireTripEditor(req, res, trip.id)) return;
  const idempotencyKey = typeof req.header("Idempotency-Key") === "string" ? req.header("Idempotency-Key")! : undefined;
  const { job } = await createJob(trip.id, idempotencyKey);
  return res.status(202).json({
    jobId: job.jobId,
    status: job.status,
    statusUrl: `/api/itinerary-jobs/${job.jobId}`,
    streamUrl: `/api/itinerary-jobs/${job.jobId}/events`,
  });
});

tripsRouter.get("/itinerary-jobs/:jobId", async (req, res) => {
  const job = await getJob(req.params.jobId);
  if (!job) return res.status(404).json({ error_code: "JOB_NOT_FOUND", message: "생성 작업을 찾을 수 없습니다." });
  return res.json(job);
});

tripsRouter.get("/itinerary-jobs/:jobId/events", async (req, res) => {
  const initial = await getJob(req.params.jobId);
  if (!initial) return res.status(404).json({ error_code: "JOB_NOT_FOUND", message: "생성 작업을 찾을 수 없습니다." });
  res.setHeader("Content-Type", "text/event-stream");
  res.setHeader("Cache-Control", "no-cache, no-transform");
  res.setHeader("Connection", "keep-alive");
  res.flushHeaders();
  const send = (job: typeof initial) => {
    res.write(`event: progress\ndata: ${JSON.stringify(job)}\n\n`);
    if (["DONE", "FAILED"].includes(job.status)) res.end();
  };
  send(initial);
  if (["DONE", "FAILED"].includes(initial.status)) return;
  const unsubscribe = subscribeJob(req.params.jobId, send);
  const heartbeat = setInterval(() => res.write(": heartbeat\n\n"), 15_000);
  req.on("close", () => {
    clearInterval(heartbeat);
    unsubscribe();
  });
});

tripsRouter.post("/itineraries/:id/days/:dayIndex/reoptimize", async (req, res) => {
  try {
    if (!await requireItineraryEditor(req, res, req.params.id)) return;
    const result = await reoptimizeItineraryDay(req.params.id, Number(req.params.dayIndex), req.body);
    return res.json(result);
  } catch (error) {
    const code = error instanceof Error ? error.message : "REOPTIMIZE_FAILED";
    const status = code.includes("NOT_FOUND") ? 404 : code === "NO_FEASIBLE_ALTERNATIVE" ? 422 : 400;
    return res.status(status).json({ error_code: code, message: code === "NO_FEASIBLE_ALTERNATIVE" ? "고정 장소와 현재 조건을 유지할 수 있는 대안이 없습니다." : "일정 변경 요청을 처리하지 못했습니다." });
  }
});

tripsRouter.patch("/itineraries/:id/days/:dayIndex/reorder", async (req, res) => {
  try {
    if (!await requireItineraryEditor(req, res, req.params.id)) return;
    const itemIds = req.body?.itemIds;
    if (!Array.isArray(itemIds) || itemIds.some((id) => typeof id !== "string") || itemIds.length === 0) {
      return res.status(400).json({ error_code: "INVALID_REQUEST", message: "itemIds 배열이 필요합니다." });
    }
    const result = await reorderItineraryDay(req.params.id, Number(req.params.dayIndex), itemIds);
    return res.json(result);
  } catch (error) {
    const code = error instanceof Error ? error.message : "REORDER_FAILED";
    const status = code.includes("NOT_FOUND") ? 404 : code === "INVALID_ORDER" ? 400 : 400;
    return res.status(status).json({ error_code: code, message: code === "INVALID_ORDER" ? "기존 항목과 같은 장소들의 순서만 바꿀 수 있습니다." : "순서 변경 요청을 처리하지 못했습니다." });
  }
});

// ── 페이스 러닝 ────────────────────────────────────────────────────────────────
// 계획만 있던 일정에 "실제로 언제 도착했고 언제 떠났는지"를 붙이고, 그 차이로
// (1) 지연 예보 (2) 남은 일정 재계산 (3) 개인 체류 리듬 학습 을 제공한다.

const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

/** 방문 진행 기록 - 도착했을 때 arrivedAt, 떠날 때 departedAt 을 보낸다. */
tripsRouter.post("/itineraries/:id/items/:itemId/progress", async (req, res) => {
  try {
    if (!await requireItineraryEditor(req, res, req.params.id)) return;
    const { arrivedAt, departedAt } = req.body ?? {};
    if (arrivedAt !== undefined && arrivedAt !== null && !TIME_PATTERN.test(String(arrivedAt))) {
      return res.status(400).json({ error_code: "INVALID_TIME", message: "arrivedAt 은 HH:MM 형식이어야 합니다." });
    }
    if (departedAt !== undefined && departedAt !== null && !TIME_PATTERN.test(String(departedAt))) {
      return res.status(400).json({ error_code: "INVALID_TIME", message: "departedAt 은 HH:MM 형식이어야 합니다." });
    }
    const item = await prisma.itineraryItem.findFirst({
      where: { id: req.params.itemId, day: { itineraryId: req.params.id } },
      include: { place: { select: { category: true, nameKo: true } } },
    });
    if (!item) return res.status(404).json({ error_code: "ITEM_NOT_FOUND", message: "일정 항목을 찾을 수 없습니다." });

    const nextArrival = arrivedAt === undefined ? item.actualArrival : arrivedAt;
    const nextDeparture = departedAt === undefined ? item.actualDeparture : departedAt;
    // 출발 기록은 도착 기록이 있어야 의미가 있다. 체류시간은 둘이 모두 있을 때만 확정한다.
    if (nextDeparture && !nextArrival) {
      return res.status(400).json({ error_code: "ARRIVAL_REQUIRED", message: "도착 기록 없이 출발만 기록할 수 없습니다." });
    }
    const stayMinutes = nextArrival && nextDeparture ? resolveStayMinutes(nextArrival, nextDeparture) : null;

    const updated = await prisma.itineraryItem.update({
      where: { id: item.id },
      data: { actualArrival: nextArrival, actualDeparture: nextDeparture, actualStayMinutes: stayMinutes },
      select: { id: true, seqOrder: true, plannedArrival: true, stayMinutes: true, actualArrival: true, actualDeparture: true, actualStayMinutes: true },
    });
    await recordEvent({
      eventType: "itinerary_progress_recorded",
      entityType: "itinerary_item", entityId: item.id,
      payload: { itineraryId: req.params.id, category: item.place.category, plannedStayMinutes: item.stayMinutes, actualStayMinutes: stayMinutes },
    });
    return res.json(updated);
  } catch {
    return res.status(400).json({ error_code: "PROGRESS_FAILED", message: "방문 기록을 저장하지 못했습니다." });
  }
});

/** 지연 예보 - 지금 페이스로 하루가 어떻게 끝날지 계산한다. now 를 주지 않으면 서버 시각을 쓴다. */
tripsRouter.get("/itineraries/:id/pace", async (req, res) => {
  const dayIndex = Number(req.query.dayIndex ?? 1);
  if (!Number.isInteger(dayIndex) || dayIndex < 1) {
    return res.status(400).json({ error_code: "INVALID_REQUEST", message: "dayIndex 는 1 이상의 정수여야 합니다." });
  }
  const nowRaw = typeof req.query.now === "string" ? req.query.now : null;
  if (nowRaw && !TIME_PATTERN.test(nowRaw)) {
    return res.status(400).json({ error_code: "INVALID_TIME", message: "now 는 HH:MM 형식이어야 합니다." });
  }
  const day = await prisma.itineraryDay.findFirst({
    where: { itineraryId: req.params.id, dayIndex },
    include: { items: { include: { place: { select: { nameKo: true, nameEn: true, category: true, closeTime: true } } } } },
  });
  if (!day) return res.status(404).json({ error_code: "DAY_NOT_FOUND", message: "해당 날짜의 일정을 찾을 수 없습니다." });

  const now = nowRaw ?? new Date().toTimeString().slice(0, 5);
  const items: PaceItem[] = day.items.map((item) => ({
    seqOrder: item.seqOrder, placeId: item.placeId,
    nameKo: item.place.nameKo, nameEn: item.place.nameEn, category: item.place.category,
    closeTime: item.place.closeTime, plannedArrival: item.plannedArrival, stayMinutes: item.stayMinutes,
    travelMinToNext: item.travelMinToNext, actualArrival: item.actualArrival, actualDeparture: item.actualDeparture,
  }));
  const forecast = computeDayPace(items, Number(now.slice(0, 2)) * 60 + Number(now.slice(3, 5)));
  return res.json({ dayIndex, visitDate: day.visitDate, now, ...forecast });
});

/** 여행 중 재계산 - 지금 이 시각·이 자리를 출발점으로 남은 일정만 다시 짠다. */
tripsRouter.post("/itineraries/:id/days/:dayIndex/replan", async (req, res) => {
  try {
    if (!await requireItineraryEditor(req, res, req.params.id)) return;
    const { currentTime, lat, lng, strategy, useRhythm } = req.body ?? {};
    if (!TIME_PATTERN.test(String(currentTime))) {
      return res.status(400).json({ error_code: "INVALID_TIME", message: "currentTime 은 HH:MM 형식이어야 합니다." });
    }
    if (typeof lat !== "number" || typeof lng !== "number" || !Number.isFinite(lat) || !Number.isFinite(lng)) {
      return res.status(400).json({ error_code: "INVALID_REQUEST", message: "현재 위치(lat, lng)가 필요합니다." });
    }
    const allowed: ReplanStrategy[] = ["KEEP_ALL", "DROP_ONE", "DEFER_LAST"];
    if (strategy !== undefined && !allowed.includes(strategy)) {
      return res.status(400).json({ error_code: "INVALID_REQUEST", message: "지원하지 않는 재계산 방식입니다." });
    }
    const result = await replanRemainingDay(req.params.id, Number(req.params.dayIndex), { currentTime, lat, lng, strategy, useRhythm });
    await recordEvent({
      eventType: "itinerary_replanned_midtrip",
      entityType: "itinerary", entityId: req.params.id,
      payload: { dayIndex: Number(req.params.dayIndex), strategy: result.strategy, preservedCount: result.preservedCount, replacedCount: result.replacedCount, rhythmApplied: result.rhythmApplied },
    });
    return res.json(result);
  } catch (error) {
    const code = error instanceof Error ? error.message : "REPLAN_FAILED";
    const status = code.includes("NOT_FOUND") ? 404 : code === "NO_FEASIBLE_ALTERNATIVE" ? 422 : 400;
    const message = code === "NOTHING_TO_REPLAN" ? "남은 일정이 없어 다시 계산할 것이 없습니다."
      : code === "DAY_ALREADY_OVER" ? "오늘 일정 시간이 이미 끝났습니다."
      : code === "NO_FEASIBLE_ALTERNATIVE" ? "지금 시각과 위치에서 갈 수 있는 대안을 찾지 못했습니다."
      : "남은 일정을 다시 계산하지 못했습니다.";
    return res.status(status).json({ error_code: code, message });
  }
});

/** 개인 체류 리듬 - 아침 브리핑에서 "어제 보니 카페에 84분 계셨어요"를 만들 재료. */
tripsRouter.get("/trips/:tripId/rhythm", async (req, res) => {
  if (!await requireTripViewer(req, res, req.params.tripId)) return;
  const profile = await loadRhythmProfile(req.params.tripId);
  if (!profile) return res.json({ hasProfile: false, scale: {}, observations: [], totalSamples: 0 });
  return res.json(profile);
});

tripsRouter.post("/itineraries/:id/undo", async (req, res) => {
  try { if (!await requireItineraryEditor(req, res, req.params.id)) return; return res.json(await undoLatestRevision(req.params.id)); }
  catch { return res.status(409).json({ error_code: "NO_REVISION", message: "실행 취소할 변경이 없습니다." }); }
});

tripsRouter.get("/itineraries/:id/items/:itemId/alternatives", async (req, res) => {
  const itinerary = await prisma.itinerary.findUnique({ where: { id: req.params.id }, include: { trip: { include: { preference: true } }, days: { include: { items: { include: { place: true } } } } } });
  if (!itinerary?.trip.preference) return res.status(404).json({ error_code: "NOT_FOUND" });
  const current = itinerary.days.flatMap((day) => day.items).find((item) => item.id === req.params.itemId);
  if (!current) return res.status(404).json({ error_code: "ITEM_NOT_FOUND" });
  const currentDay = itinerary.days.find((day) => day.items.some((item) => item.id === req.params.itemId))!;
  const used = itinerary.days.flatMap((day) => day.items.map((item) => item.placeId));
  const pref = itinerary.trip.preference;
  const places = await prisma.place.findMany({ where: { category: current.place.category } });
  const dayCode = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"][new Date(`${currentDay.visitDate}T00:00:00`).getDay()];
  const available = places.filter((place) => !(JSON.parse(place.closedDays) as string[]).includes(dayCode));
  const reachable = itinerary.trip.hasCar ? available : available.filter((place) => haversineDistanceM(itinerary.trip.originLat, itinerary.trip.originLng, place.lat, place.lng) <= itinerary.trip.maxWalkingKm * 500);
  const scored = scorePlaces(reachable, JSON.parse(pref.tasteTags), itinerary.trip.totalBudget / itinerary.days.length, itinerary.trip.partySize, used, { mode: itinerary.mode as any, ratios: { landmark: pref.landmarkRatio, local: pref.localRatio, easy: pref.easyRatio }, allergies: JSON.parse(pref.allergies), dietType: pref.dietType });
  return res.json(scored.slice(0, 5).map((place) => ({ placeId: place.id, nameKo: place.nameKo, nameEn: place.nameEn, category: place.category, address: place.address, score: place.score, localScore: place.localScore, estCost: place.priceTier === 1 ? 0 : place.priceTier === 2 ? 8000 : place.priceTier === 3 ? 20000 : 45000, hasEnglishMenu: place.hasEnglishMenu, foreignCardPayment: place.foreignCardPayment })));
});

tripsRouter.post("/trips", async (req, res) => {
  const body = req.body as CreateTripRequest;
  const ownerSession = await optionalSession(req);

  const requiredFields: (keyof CreateTripRequest)[] = [
    "origin",
    "originLat",
    "originLng",
    "startDate",
    "endDate",
    "partySize",
    "totalBudget",
    "hasCar",
    "pace",
    "tasteTags",
  ];
  const missing = requiredFields.filter((f) => body[f] === undefined || body[f] === null);
  if (missing.length > 0) {
    return res.status(400).json({ error_code: "INVALID_REQUEST", message: `missing fields: ${missing.join(", ")}` });
  }
  if (new Date(body.endDate) < new Date(body.startDate)) {
    return res.status(400).json({ error_code: "INVALID_DATE_RANGE", message: "endDate must be >= startDate" });
  }
  const dayStart = body.dayStart ?? "09:30";
  const dayEnd = body.dayEnd ?? "20:00";
  if (!/^\d{2}:\d{2}$/.test(dayStart) || !/^\d{2}:\d{2}$/.test(dayEnd) || dayEnd <= dayStart) {
    return res.status(400).json({ error_code: "INVALID_DAY_TIME", message: "하루 종료 시간은 시작 시간보다 늦어야 합니다." });
  }
  if ((body.maxWalkingKm ?? 8) <= 0) {
    return res.status(400).json({ error_code: "INVALID_WALKING_LIMIT", message: "최대 도보 거리는 0보다 커야 합니다." });
  }
  const adultCount = body.adultCount ?? body.partySize;
  const childCount = body.childCount ?? 0;
  if (!Number.isInteger(adultCount) || adultCount < 1 || !Number.isInteger(childCount) || childCount < 0) {
    return res.status(400).json({ error_code: "INVALID_PARTY", message: "성인은 1명 이상, 아동은 0명 이상이어야 합니다." });
  }
  // 날짜 지정된 필수 방문 장소(위저드의 "가고 싶은 곳" 추가). dayIndex는 1부터 시작.
  const mustVisitAssignments = (body.mustVisitAssignments ?? []).filter(
    (assignment) => assignment && typeof assignment.placeId === "string" && Number.isInteger(assignment.dayIndex) && assignment.dayIndex >= 1
  );
  const ratios = [body.landmarkRatio ?? 30, body.localRatio ?? 50, body.easyRatio ?? 20];
  if (ratios.some((value) => !Number.isFinite(value) || value < 0) || ratios.reduce((sum, value) => sum + value, 0) <= 0) {
    return res.status(400).json({ error_code: "INVALID_RECOMMENDATION_RATIO", message: "추천 비율 합계는 0보다 커야 합니다." });
  }
  const courseCategory = findCourseCategory(body.courseCategory);
  if (body.courseCategory && !courseCategory) return res.status(400).json({ error_code: "INVALID_COURSE_CATEGORY", message: "지원하지 않는 코스 카테고리입니다." });
  if (courseCategory && !courseCategory.enabled) return res.status(422).json({ error_code: "CATEGORY_NOT_AVAILABLE", disabledReason: courseCategory.disabledReason, message: "아직 제공할 수 없는 코스 카테고리입니다." });
  if (body.lodgingPlaceId) {
    const lodging = await prisma.place.findUnique({ where: { id: body.lodgingPlaceId }, select: { category: true } });
    if (!lodging || lodging.category !== "LODGING") {
      return res.status(400).json({ error_code: "INVALID_LODGING", message: "숙소로 사용할 수 있는 장소가 아닙니다." });
    }
  }

  const trip = await prisma.trip.create({
    data: {
      origin: body.origin,
      originLat: body.originLat,
      originLng: body.originLng,
      startDate: body.startDate,
      endDate: body.endDate,
      partySize: body.partySize,
      adultCount,
      childCount,
      totalBudget: body.totalBudget,
      hasCar: body.hasCar,
      pace: body.pace,
      dayStart,
      dayEnd,
      maxWalkingKm: body.maxWalkingKm ?? 8,
      recommendationMode: body.recommendationMode ?? "LOCAL",
      lodgingPlaceId: body.lodgingPlaceId || null,
      status: "DRAFT",
      ownerSessionId: ownerSession?.id ?? null,
      preference: {
        create: {
          tasteTags: JSON.stringify(body.tasteTags ?? []),
          courseCategory: courseCategory?.code ?? null,
          mustVisitPlaceIds: JSON.stringify(body.mustVisitPlaceIds ?? []),
          mustVisitAssignments: JSON.stringify(mustVisitAssignments),
          excludedPlaceIds: JSON.stringify(body.excludedPlaceIds ?? []),
          language: body.language ?? "KO",
          allergies: JSON.stringify(body.allergies ?? []),
          dietType: body.dietType ?? "NONE",
          landmarkRatio: Math.round(ratios[0]),
          localRatio: Math.round(ratios[1]),
          easyRatio: Math.round(ratios[2]),
        },
      },
    },
  });

  await recordEvent({ eventType: "trip_searched", entityType: "trip", entityId: trip.id, language: body.language ?? "KO", payload: { startDate: body.startDate, endDate: body.endDate, recommendationMode: body.recommendationMode ?? "LOCAL" } });

  res.status(201).json({ tripId: trip.id, status: trip.status, createdAt: trip.createdAt });
});

tripsRouter.post("/trips/:id/itinerary", async (req, res) => {
  if (!await requireTripEditor(req, res, req.params.id)) return;
  const trip = await prisma.trip.findUnique({
    where: { id: req.params.id },
    include: { preference: true, lodgingPlace: true },
  });
  if (!trip || !trip.preference) {
    return res.status(404).json({ error_code: "TRIP_NOT_FOUND" });
  }

  // LODGING은 16.3장 10단계("매일 종료 노드로 고정")를 이번 MVP에서 구현하지 않으므로
  // 낮 시간 관광 후보군에서 제외한다(그렇지 않으면 숙소가 일반 방문지처럼 스케줄링됨).
  const places = (await prisma.place.findMany()).filter(
    (p) => ["TOURIST", "RESTAURANT", "CAFE"].includes(p.category)
  );
  const pref = trip.preference;
  const selectedCourseCategory = findCourseCategory(pref.courseCategory);
  const tasteTags = applyCourseCategoryTasteTags(JSON.parse(pref.tasteTags), pref.courseCategory);
  const mustVisitPlaceIds: string[] = JSON.parse(pref.mustVisitPlaceIds);
  const excludedPlaceIds: string[] = JSON.parse(pref.excludedPlaceIds);

  const numDays =
    Math.round(
      (new Date(`${trip.endDate}T00:00:00`).getTime() - new Date(`${trip.startDate}T00:00:00`).getTime()) / 86400000
    ) + 1;
  const dayBudgetEstimate = trip.totalBudget / numDays;

  const scored = scorePlaces(
    places,
    tasteTags,
    dayBudgetEstimate,
    trip.partySize,
    excludedPlaceIds,
    {
      mode: trip.recommendationMode as any,
      allergies: JSON.parse(pref.allergies), dietType: pref.dietType,
      ratios: { landmark: pref.landmarkRatio, local: pref.localRatio, easy: pref.easyRatio },
      courseCategory: pref.courseCategory,
    }
  );

  if (scored.length === 0) {
    return res.status(422).json({
      error_code: "NO_FEASIBLE_SCHEDULE",
      message: "조건을 만족하는 장소 후보가 없습니다. 예산을 늘려보세요.",
    });
  }

  const stayMinutesScale = selectedCourseCategory?.scheduleParams?.stayMinutesScale ?? 1;
  const categoryAdjusted = scored.map((place) => ({ ...place, recommendedStayMin: Math.max(20, Math.round(place.recommendedStayMin * stayMinutesScale)) }));
  const categoryDayEnd = selectedCourseCategory?.scheduleParams?.dayEndTimeCap ?? selectedCourseCategory?.scheduleParams?.forbidTimeRange?.after;
  const effectiveDayEnd = categoryDayEnd && categoryDayEnd < trip.dayEnd ? categoryDayEnd : trip.dayEnd;
  const { days, warnings, solverSource } = await buildItinerary(categoryAdjusted, {
    startDate: trip.startDate,
    endDate: trip.endDate,
    originLat: trip.lodgingPlace?.lat ?? trip.originLat,
    originLng: trip.lodgingPlace?.lng ?? trip.originLng,
    totalBudget: trip.totalBudget,
    partySize: trip.partySize,
    hasCar: trip.hasCar,
    pace: selectedCourseCategory?.scheduleParams?.pace ?? trip.pace as any,
    dayStart: trip.dayStart,
    dayEnd: effectiveDayEnd,
    maxWalkingKm: trip.maxWalkingKm * (selectedCourseCategory?.scheduleParams?.maxWalkDistanceScale ?? 1),
    mustVisitPlaceIds,
    recommendationMode: trip.recommendationMode,
  });
  if (days.every((day) => day.items.length === 0)) {
    return res.status(422).json({
      error_code: "NO_FEASIBLE_SCHEDULE",
      message: "설정한 활동시간과 도보 거리 안에서 방문 가능한 장소가 없습니다.",
      conflicts: ["DAY_TIME_WINDOW", "MAX_WALKING_DISTANCE"],
      suggestions: [
        { action: "EXPAND_WALKING_LIMIT", label: "최대 도보 거리 늘리기" },
        { action: "USE_CAR", label: "자차 이동으로 변경" },
        { action: "EXPAND_DAY_TIME", label: "하루 활동시간 늘리기" },
      ],
    });
  }

  const itinerary = await prisma.itinerary.create({
    data: {
      tripId: trip.id,
      status: "DONE",
      solverSource,
      mode: trip.recommendationMode,
      days: {
        create: days.map((d) => ({
          dayIndex: d.dayIndex,
          visitDate: d.visitDate,
          dayBudget: d.dayBudget,
          startTravelMin: d.startTravelMin,
          startDistanceM: d.startDistanceM,
          startTravelIsEstimate: d.startTravelIsEstimate,
          returnTravelMin: d.returnTravelMin,
          returnDistanceM: d.returnDistanceM,
          returnTravelIsEstimate: d.returnTravelIsEstimate,
          items: {
            create: d.items.map((it) => ({
              placeId: it.placeId,
              seqOrder: it.seqOrder,
              plannedArrival: it.plannedArrival,
              stayMinutes: it.stayMinutes,
              estCost: it.estCost,
              travelMinToNext: it.travelMinToNext,
              distanceToNextM: it.distanceToNextM,
              travelIsEstimate: it.travelIsEstimate,
              travelSource: it.travelSource,
              recommendReason: it.recommendReason,
            })),
          },
        })),
      },
    },
  });

  await prisma.trip.update({ where: { id: trip.id }, data: { status: "CONFIRMED" } });

  const output: ItineraryOutput = {
    itineraryId: itinerary.id,
    tripId: trip.id,
    trip: buildTripMeta(trip, pref, trip.lodgingPlace),
    days,
    warnings,
    solverSource,
    mode: trip.recommendationMode as ItineraryOutput["mode"],
  };
  res.status(201).json(output);
});

tripsRouter.patch("/trips/:id/preferences", async (req, res) => {
  if (!await requireTripEditor(req, res, req.params.id)) return;
  const { mustVisitPlaceIds = [], excludedPlaceIds = [] } = req.body as {
    mustVisitPlaceIds?: string[];
    excludedPlaceIds?: string[];
  };
  const preference = await prisma.tripPreference.findUnique({ where: { tripId: req.params.id } });
  if (!preference) return res.status(404).json({ error_code: "TRIP_NOT_FOUND" });
  const updated = await prisma.tripPreference.update({
    where: { tripId: req.params.id },
    data: {
      mustVisitPlaceIds: JSON.stringify([...new Set(mustVisitPlaceIds)]),
      excludedPlaceIds: JSON.stringify([...new Set(excludedPlaceIds)]),
    },
  });
  const previousPinned = new Set<string>(JSON.parse(preference.mustVisitPlaceIds));
  const previousExcluded = new Set<string>(JSON.parse(preference.excludedPlaceIds));
  for (const placeId of mustVisitPlaceIds.filter((id) => !previousPinned.has(id))) await recordEvent({ eventType: "place_pinned", entityType: "place", entityId: placeId, payload: { tripId: req.params.id } });
  for (const placeId of excludedPlaceIds.filter((id) => !previousExcluded.has(id))) await recordEvent({ eventType: "place_excluded", entityType: "place", entityId: placeId, payload: { tripId: req.params.id } });
  res.json({
    mustVisitPlaceIds: JSON.parse(updated.mustVisitPlaceIds),
    excludedPlaceIds: JSON.parse(updated.excludedPlaceIds),
  });
});

tripsRouter.get("/trips/:id/itinerary", async (req, res) => {
  if (!await requireTripViewer(req, res, req.params.id)) return;
  const requestedMode = typeof req.query.mode === "string" ? req.query.mode : undefined;
  const itinerary = await prisma.itinerary.findFirst({
    where: { tripId: req.params.id, ...(requestedMode ? { mode: requestedMode } : {}) },
    orderBy: { generatedAt: "desc" },
    include: {
      trip: { include: { preference: true, lodgingPlace: true } },
      days: { include: { items: { include: { place: true } } } },
    },
  });
  if (!itinerary || !itinerary.trip.preference) {
    return res.status(404).json({ error_code: "NOT_FOUND" });
  }

  const output: ItineraryOutput = {
    itineraryId: itinerary.id,
    tripId: itinerary.tripId,
    trip: buildTripMeta(itinerary.trip, itinerary.trip.preference, itinerary.trip.lodgingPlace),
    warnings: [],
    solverSource: itinerary.solverSource as "OR_TOOLS" | "HEURISTIC",
    mode: itinerary.mode as ItineraryOutput["mode"],
    days: itinerary.days
      .sort((a, b) => a.dayIndex - b.dayIndex)
      .map((d) => ({
        dayIndex: d.dayIndex,
        visitDate: d.visitDate,
        dayBudget: d.dayBudget,
        totalEstCost: d.items.reduce((s, it) => s + it.estCost, 0),
        startTravelMin: d.startTravelMin,
        startDistanceM: d.startDistanceM,
        startTravelIsEstimate: d.startTravelIsEstimate,
        returnTravelMin: d.returnTravelMin,
        returnDistanceM: d.returnDistanceM,
        returnTravelIsEstimate: d.returnTravelIsEstimate,
        items: d.items
          .sort((a, b) => a.seqOrder - b.seqOrder)
          .map((it) => ({
            itemId: it.id,
            seqOrder: it.seqOrder,
            placeId: it.placeId,
            // 페이스 러닝: 화면이 계획과 실측을 함께 그릴 수 있도록 실측값을 같이 내려준다.
            actualArrival: it.actualArrival,
            actualDeparture: it.actualDeparture,
            actualStayMinutes: it.actualStayMinutes,
            nameKo: it.place.nameKo,
            nameEn: it.place.nameEn,
            category: it.place.category,
            address: it.place.address,
            addressEn: it.place.addressEn,
            allergens: JSON.parse(it.place.allergens),
            dietOptions: JSON.parse(it.place.dietOptions),
            onlineReservation: it.place.onlineReservation,
            lat: it.place.lat,
            lng: it.place.lng,
            openTime: it.place.openTime,
            closeTime: it.place.closeTime,
            plannedArrival: it.plannedArrival,
            stayMinutes: it.stayMinutes,
            estCost: it.estCost,
            travelMinToNext: it.travelMinToNext,
            distanceToNextM: it.distanceToNextM,
            travelIsEstimate: it.travelIsEstimate,
            travelSource: it.travelSource as "KAKAO_MOBILITY" | "HAVERSINE",
            recommendReason: it.recommendReason,
            hasEnglishMenu: it.place.hasEnglishMenu,
            foreignCardPayment: it.place.foreignCardPayment,
            localScore: it.place.localScore,
            dataSource: it.place.dataSource,
            imageUrl: it.place.imageUrl,
            kakaoPlaceId: it.place.kakaoPlaceId,
            kakaoPlaceUrl: it.place.kakaoPlaceUrl,
            kakaoRating: it.place.kakaoRating,
            kakaoReviewCount: it.place.kakaoReviewCount,
            kakaoPositiveReviewRate: it.place.kakaoPositiveReviewRate,
            kakaoReviewKeywords: JSON.parse(it.place.kakaoReviewKeywords),
            kakaoReviewSource: it.place.kakaoReviewSource,
            kakaoReviewCollectedAt: it.place.kakaoReviewCollectedAt,
          })),
      })),
  };
  res.json(output);
});
