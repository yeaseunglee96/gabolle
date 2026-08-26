import { Router } from "express";
import { prisma } from "../db.js";
import { EVENT_TOPIC, EVENT_TYPES, isEventType, pseudonymize, recordEvent } from "../services/events.js";

export const analyticsRouter = Router();

analyticsRouter.get("/events/catalog", (_req, res) => {
  res.json({ documentedCount: EVENT_TYPES.length, enumeratedCount: EVENT_TYPES.length, discrepancy: "반려동물 정책 이벤트를 내비게이션 이벤트로 대체했습니다(v2 13장).", events: EVENT_TYPES.map((eventType) => ({ eventType, topic: EVENT_TOPIC[eventType] })) });
});

analyticsRouter.post("/events", async (req, res, next) => {
  try {
    const eventType = String(req.body?.eventType ?? "");
    if (!isEventType(eventType)) return res.status(400).json({ error_code: "UNKNOWN_EVENT_TYPE", supported: EVENT_TYPES });
    const eventId = String(req.body?.eventId ?? "");
    if (eventId && !/^[A-Za-z0-9_-]{8,100}$/.test(eventId)) return res.status(400).json({ error_code: "INVALID_EVENT_ID" });
    const authorization = req.header("authorization")?.replace(/^Bearer\s+/i, "").trim();
    const clientSessionId = String(req.body?.clientSessionId ?? "").slice(0, 200);
    const actorId = authorization ? pseudonymize(authorization) : clientSessionId ? pseudonymize(clientSessionId) : null;
    const occurredAt = req.body?.occurredAt ? new Date(req.body.occurredAt) : new Date();
    if (!Number.isFinite(occurredAt.getTime()) || Math.abs(Date.now() - occurredAt.getTime()) > 7 * 24 * 60 * 60_000) return res.status(400).json({ error_code: "INVALID_OCCURRED_AT" });
    const result = await recordEvent({ eventId: eventId || undefined, eventType, actorId, entityType: req.body?.entityType ? String(req.body.entityType).slice(0, 50) : null, entityId: req.body?.entityId ? String(req.body.entityId).slice(0, 100) : null, language: req.body?.language === "EN" ? "EN" : "KO", payload: req.body?.payload, occurredAt });
    res.status(result.deduplicated ? 200 : 202).json({ eventId: result.event.eventId, topic: result.event.topic, publishStatus: result.event.publishStatus, deduplicated: result.deduplicated });
  } catch (error) { next(error); }
});

analyticsRouter.get("/analytics/kpis", async (req, res, next) => {
  try {
    const hours = Math.min(168, Math.max(1, Number(req.query.hours ?? 24)));
    const since = new Date(Date.now() - hours * 60 * 60_000);
    const events = await prisma.eventOutbox.findMany({ where: { occurredAt: { gte: since } }, select: { eventType: true, entityId: true, occurredAt: true, publishStatus: true }, orderBy: { occurredAt: "desc" }, take: 50_000 });
    const count = (type: string) => events.filter((event) => event.eventType === type).length;
    const placeSignals = new Map<string, number>();
    const weights: Record<string, number> = { place_impression: 1, place_clicked: 3, place_saved: 5, place_visit_verified: 8, place_revisited: 12 };
    for (const event of events) if (event.entityId && weights[event.eventType]) placeSignals.set(event.entityId, (placeSignals.get(event.entityId) ?? 0) + weights[event.eventType]);
    const topIds = [...placeSignals.entries()].sort((a, b) => b[1] - a[1]).slice(0, 10);
    const places = await prisma.place.findMany({ where: { id: { in: topIds.map(([id]) => id) } }, select: { id: true, nameKo: true, nameEn: true } });
    const placeMap = new Map(places.map((place) => [place.id, place]));
    const searches = count("trip_searched");
    const generated = count("itinerary_generated");
    const confirmed = count("itinerary_confirmed");
    const completed = count("trip_completed");
    const adImpressions = count("ad_impression");
    const adClicks = count("ad_clicked");
    const bookingStarts = count("booking_started");
    const bookingCompleted = count("booking_completed");
    res.json({
      windowHours: hours,
      generatedAt: new Date(),
      throughput: { total: events.length, perMinute: Math.round((events.length / (hours * 60)) * 100) / 100 },
      consumer: { pending: events.filter((event) => event.publishStatus === "PENDING").length, failed: events.filter((event) => event.publishStatus === "FAILED").length, localCommitted: events.filter((event) => event.publishStatus === "LOCAL_COMMITTED").length },
      tripFunnel: { searches, generated, confirmed, completed, generationRate: searches ? generated / searches : 0, completionRate: confirmed ? completed / confirmed : 0 },
      trendingPlaces: topIds.map(([id, score]) => ({ placeId: id, score, nameKo: placeMap.get(id)?.nameKo ?? "알 수 없는 장소", nameEn: placeMap.get(id)?.nameEn ?? null })),
      recommendation: { excluded: count("place_excluded"), satisfactionResponses: count("recommendation_satisfaction") },
      navigation: { routeGuideViewed: count("route_guide_viewed"), taxiCardGenerated: count("taxi_card_generated") },
      revenue: { adImpressions, adClicks, adCtr: adImpressions ? adClicks / adImpressions : 0, bookingStarts, bookingCompleted, bookingConversion: bookingStarts ? bookingCompleted / bookingStarts : 0 },
    });
  } catch (error) { next(error); }
});
