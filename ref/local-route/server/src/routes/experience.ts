import { Router } from "express";
import { prisma } from "../db.js";
import { haversineDistanceM } from "../services/kakao.js";
import { recordEvent, recordEventBestEffort } from "../services/events.js";
import { requireItineraryEditor } from "../services/auth.js";

export const experienceRouter = Router();

function validDate(value: unknown): value is string {
  return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(`${value}T00:00:00Z`));
}

experienceRouter.get("/events", async (req, res, next) => {
  try {
    const from = req.query.from; const to = req.query.to;
    if (!validDate(from) || !validDate(to) || from > to) return res.status(400).json({ error_code: "INVALID_DATE_RANGE", message: "여행 기간을 YYYY-MM-DD 형식으로 확인해 주세요." });
    const [festivals, nightMarkets, traditionalMarkets] = await Promise.all([
      prisma.place.findMany({ where: { category: "FESTIVAL", eventStartDate: { lte: to }, eventEndDate: { gte: from } }, orderBy: [{ eventStartDate: "asc" }, { localScore: "desc" }] }),
      // Visit Busan에서 상설 야시장 운영 정보를 확인할 수 있는 장소만 노출한다.
      prisma.place.findMany({ where: { nameKo: { in: ["부평깡통시장", "부평깡통야시장"] } }, orderBy: { localScore: "desc" } }),
      // 시드된 전통/재래시장 이름을 하드코딩 목록으로만 걸러내면 실제로 존재하는 시장 대부분이
      // 철자 불일치로 빠진다("부전시장" 등은 시드에 아예 없고, "초량전통시장"은 "초량시장"으로
      // 시드돼 있는 식). 이름에 "시장"이 들어간 관광 장소를 폭넓게 노출한다.
      prisma.place.findMany({ where: { category: "TOURIST", nameKo: { contains: "시장" } }, orderBy: { localScore: "desc" } }),
    ]);
    const festivalEvents = festivals.map((place) => ({ placeId: place.id, eventType: "FESTIVAL", title: place.nameKo, titleEn: place.nameEn, address: place.address, lat: place.lat, lng: place.lng, startDate: place.eventStartDate, endDate: place.eventEndDate, playTime: place.playTime, imageUrl: place.imageUrl, localScore: place.localScore }));
    const nightMarketEvents = nightMarkets.map((place) => ({ placeId: place.id, eventType: "NIGHT_MARKET", title: "부평깡통야시장", titleEn: "Bupyeong Kkangtong Night Market", address: place.address, lat: place.lat, lng: place.lng, startDate: from, endDate: to, playTime: "매일 19:30–24:00 · 방문 전 운영 확인", imageUrl: place.imageUrl, localScore: place.localScore, officialUrl: "https://www.visitbusan.net/kr/index.do?lang_cd=ko&menuCd=DOM_000000202003001000&uc_seq=1861" }));
    const traditionalMarketEvents = traditionalMarkets.map((place) => ({ placeId: place.id, eventType: "TRADITIONAL_MARKET", title: place.nameKo, titleEn: place.nameEn, address: place.address, lat: place.lat, lng: place.lng, startDate: from, endDate: to, playTime: `운영 ${place.openTime}–${place.closeTime} · 점포별 휴무 확인`, imageUrl: place.imageUrl, localScore: place.localScore }));
    return res.json({ events: [...festivalEvents, ...nightMarketEvents, ...traditionalMarketEvents] });
  } catch (error) { next(error); }
});

experienceRouter.get("/festivals", async (req, res, next) => {
  try {
    const from = req.query.from;
    const to = req.query.to;
    if (!validDate(from) || !validDate(to) || from > to) return res.status(400).json({ error_code: "INVALID_DATE_RANGE", message: "여행 기간을 YYYY-MM-DD 형식으로 확인해 주세요." });
    const festivals = await prisma.place.findMany({
      where: { category: "FESTIVAL", eventStartDate: { lte: to }, eventEndDate: { gte: from } },
      orderBy: [{ eventStartDate: "asc" }, { localScore: "desc" }],
    });
    await recordEventBestEffort({ eventType: "festival_impression", entityType: "trip_period", entityId: `${from}:${to}`, payload: { count: festivals.length } });
    return res.json({ festivals: festivals.map((place) => ({ placeId: place.id, title: place.nameKo, titleEn: place.nameEn, address: place.address, lat: place.lat, lng: place.lng, startDate: place.eventStartDate, endDate: place.eventEndDate, playTime: place.playTime, imageUrl: place.imageUrl, localScore: place.localScore })) });
  } catch (error) { next(error); }
});

experienceRouter.post("/itineraries/:id/festivals/:placeId", async (req, res, next) => {
  try {
    if (!await requireItineraryEditor(req, res, req.params.id)) return;
    const itinerary = await prisma.itinerary.findUnique({ where: { id: req.params.id }, include: { trip: true, days: { include: { items: true }, orderBy: { dayIndex: "asc" } } } });
    const festival = await prisma.place.findFirst({ where: { id: req.params.placeId, category: "FESTIVAL" } });
    if (!itinerary || !festival) return res.status(404).json({ error_code: "FESTIVAL_NOT_FOUND" });
    if (!festival.eventStartDate || !festival.eventEndDate || festival.eventEndDate < itinerary.trip.startDate || festival.eventStartDate > itinerary.trip.endDate) return res.status(409).json({ error_code: "FESTIVAL_OUTSIDE_TRIP", message: "여행 기간에 열리지 않는 축제입니다." });
    if (itinerary.days.some((day) => day.items.some((item) => item.placeId === festival.id))) return res.json({ added: false, reason: "ALREADY_ADDED" });
    const day = itinerary.days.find((candidate) => candidate.visitDate >= festival.eventStartDate! && candidate.visitDate <= festival.eventEndDate!);
    if (!day) return res.status(409).json({ error_code: "NO_MATCHING_DAY" });
    const seqOrder = day.items.length;
    const item = await prisma.itineraryItem.create({ data: { dayId: day.id, placeId: festival.id, seqOrder, plannedArrival: festival.playTime?.match(/\d{2}:\d{2}/)?.[0] ?? "18:00", stayMinutes: festival.recommendedStayMin, estCost: festival.priceTier <= 1 ? 0 : 15000, recommendReason: "여행 기간에만 열리는 지역축제 — 사용자가 직접 추가", travelSource: "HAVERSINE", travelIsEstimate: true } });
    await prisma.itinerary.update({ where: { id: itinerary.id }, data: { version: { increment: 1 } } });
    await recordEvent({ eventType: "festival_added", entityType: "itinerary", entityId: itinerary.id, payload: { placeId: festival.id, dayIndex: day.dayIndex } });
    return res.status(201).json({ added: true, itemId: item.id, dayIndex: day.dayIndex });
  } catch (error) { next(error); }
});

experienceRouter.get("/shops/souvenir", async (req, res, next) => {
  try {
    const lat = Number(req.query.lat); const lng = Number(req.query.lng); const radius = Math.min(20_000, Math.max(100, Number(req.query.radius ?? 5000)));
    if (![lat, lng, radius].every(Number.isFinite)) return res.status(400).json({ error_code: "INVALID_LOCATION" });
    const shops = (await prisma.place.findMany({ where: { category: "SOUVENIR" } }))
      .map((place) => ({ ...place, distanceM: haversineDistanceM(lat, lng, place.lat, place.lng) }))
      .filter((place) => place.distanceM <= radius)
      .sort((a, b) => (b.localScore - a.localScore) || (a.distanceM - b.distanceM));
    await recordEventBestEffort({ eventType: "souvenir_layer_viewed", entityType: "map_area", entityId: `${lat.toFixed(2)}:${lng.toFixed(2)}`, payload: { count: shops.length, radius } });
    res.json(shops.map((place) => ({ id: place.id, nameKo: place.nameKo, nameEn: place.nameEn, address: place.address, lat: place.lat, lng: place.lng, distanceM: Math.round(place.distanceM), items: JSON.parse(place.souvenirItems), openTime: place.openTime, closeTime: place.closeTime, cardPayment: place.foreignCardPayment, foreignAssistance: place.foreignAssistance, localScore: place.localScore, imageUrl: place.imageUrl, rankingBasis: "LOCAL_SCORE_AND_DISTANCE", sponsored: false })));
  } catch (error) { next(error); }
});

async function findNearbySpots(tag: string, lat: number, lng: number, radius: number) {
  // FESTIVAL/SOUVENIR 는 각자 전용 탭이 있으니 다른 로컬 탭에서는 중복 노출하지 않는다.
  return (await prisma.place.findMany({ where: { tasteTags: { contains: tag }, category: { notIn: ["FESTIVAL", "SOUVENIR"] } } }))
    .map((place) => ({ ...place, distanceM: haversineDistanceM(lat, lng, place.lat, place.lng) }))
    .filter((place) => place.distanceM <= radius)
    .sort((a, b) => (b.localScore - a.localScore) || (a.distanceM - b.distanceM));
}

function serializeSpot(place: { id: string; nameKo: string; nameEn: string | null; address: string; lat: number; lng: number; distanceM: number; openTime: string; closeTime: string; priceTier: number; localScore: number; imageUrl: string | null; nightImageUrl: string | null; recommendedStayMin: number }, tag: string) {
  // 야경 탭은 낮 사진(imageUrl)을 절대 쓰지 않는다 — 같은 장소가 자연/산책 탭에서 낮 사진으로도 쓰이므로,
  // 야경용 사진(nightImageUrl)이 없으면 사진 없음(플레이스홀더)으로 둔다.
  const imageUrl = tag === "nightview" ? place.nightImageUrl : place.imageUrl;
  return {
    id: place.id, nameKo: place.nameKo, nameEn: place.nameEn, address: place.address, lat: place.lat, lng: place.lng,
    distanceM: Math.round(place.distanceM), openTime: place.openTime, closeTime: place.closeTime,
    priceTier: place.priceTier, localScore: place.localScore, imageUrl,
    recommendedStayMin: place.recommendedStayMin,
  };
}

// "activity" 취향 태그는 일정 추천 엔진이 그대로 쓰고 있어 손대지 않았다. 로컬 탭에서는 그 안에서
// 성격이 다른 여러 묶음 — 예약/체험형 액티비티, 산책로/공원, 해수욕장/산, 야경 명소 — 을 구분해서
// 보여주기 위해 prisma/ensure-discover-tags.ts 로 얹어둔 세부 태그를 쓴다. 캠핑장류는 어느 세부
// 태그에도 넣지 않아 로컬 탭 어디에도 노출되지 않는다.
function registerSpotRoute(path: string, tag: string, eventType: "activity_layer_viewed" | "nature_spot_layer_viewed" | "walk_trail_layer_viewed" | "night_view_layer_viewed") {
  experienceRouter.get(path, async (req, res, next) => {
    try {
      const lat = Number(req.query.lat); const lng = Number(req.query.lng); const radius = Math.min(30_000, Math.max(100, Number(req.query.radius ?? 15_000)));
      if (![lat, lng, radius].every(Number.isFinite)) return res.status(400).json({ error_code: "INVALID_LOCATION" });
      const spots = await findNearbySpots(tag, lat, lng, radius);
      await recordEventBestEffort({ eventType, entityType: "map_area", entityId: `${lat.toFixed(2)}:${lng.toFixed(2)}`, payload: { count: spots.length, radius } });
      res.json(spots.map((spot) => serializeSpot(spot, tag)));
    } catch (error) { next(error); }
  });
}

registerSpotRoute("/activities", "adventure_activity", "activity_layer_viewed");
registerSpotRoute("/walk-trails", "walk_trail", "walk_trail_layer_viewed");
registerSpotRoute("/nature-spots", "nature_spot", "nature_spot_layer_viewed");
registerSpotRoute("/night-views", "nightview", "night_view_layer_viewed");

const weatherCache = new Map<string, { expiresAt: number; value: object }>();
experienceRouter.get("/weather", async (req, res) => {
  const date = req.query.date;
  if (!validDate(date)) return res.status(400).json({ error_code: "INVALID_DATE" });
  const region = typeof req.query.region === "string" ? req.query.region.slice(0, 30) : "BUSAN";
  const key = `${region}:${date}`;
  const cached = weatherCache.get(key);
  if (cached && cached.expiresAt > Date.now()) return res.json(cached.value);
  const seed = [...date].reduce((sum, ch) => sum + ch.charCodeAt(0), 0);
  const rainProbability = [10, 20, 30, 60, 70][seed % 5];
  const value = { date, region, tempMin: 18 + (seed % 4), tempMax: 25 + (seed % 5), rainProbability, sky: rainProbability >= 60 ? "RAIN" : rainProbability >= 30 ? "CLOUDY" : "CLEAR", isEstimate: true, source: "CACHED_DEMO_FORECAST", outdoorWarning: rainProbability >= 60, cachedForSeconds: 3600 };
  weatherCache.set(key, { expiresAt: Date.now() + 60 * 60_000, value });
  return res.json(value);
});
