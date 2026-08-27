import { randomUUID } from "node:crypto";
import { Prisma } from "@prisma/client";
import { Router, type Request, type Response } from "express";
import { prisma } from "../db.js";
import { isAllowedAdCategory, rankEligibleAds } from "../services/ads.js";
import { pseudonymize, recordEvent } from "../services/events.js";
import { hashSessionToken } from "../services/community.js";

export const commerceRouter = Router();

async function rawBearer(req: Request, res: Response): Promise<string | null> {
  const token = req.header("authorization")?.replace(/^Bearer\s+/i, "").trim();
  if (!token || token.length < 16) { res.status(401).json({ error_code: "SESSION_REQUIRED" }); return null; }
  const session = await prisma.anonymousSession.findUnique({ where: { tokenHash: hashSessionToken(token) }, select: { expiresAt: true } });
  if (!session || session.expiresAt <= new Date()) { res.status(401).json({ error_code: "SESSION_EXPIRED" }); return null; }
  return token;
}

commerceRouter.get("/ads", async (req, res, next) => {
  try {
    const now = new Date();
    const candidates = await prisma.adCampaign.findMany({ where: { status: "ACTIVE", startsAt: { lte: now }, endsAt: { gte: now } }, include: { place: true }, take: 100 });
    const ranked = rankEligibleAds(candidates, { mode: typeof req.query.mode === "string" ? req.query.mode : undefined, language: typeof req.query.language === "string" ? req.query.language : undefined, category: typeof req.query.category === "string" ? req.query.category : undefined });
    res.json(ranked.slice(0, 4).map((candidate) => ({ campaignId: candidate.id, placeId: candidate.place.id, nameKo: candidate.name, nameEn: null, category: candidate.serviceCategory, imageUrl: candidate.place.imageUrl, label: req.query.language === "EN" ? "Sponsored" : "광고", disclosure: req.query.language === "EN" ? "Essential travel service · paid placement, separate from organic recommendations" : "여행 필수 서비스 · 자연 추천과 분리된 유료 노출", adRankScore: candidate.adRankScore })));
  } catch (error) { next(error); }
});

async function registerAdEvent(req: Request, res: Response, eventType: "IMPRESSION" | "CLICK") {
  const campaign = await prisma.adCampaign.findUnique({ where: { id: req.params.id } });
  if (!campaign || campaign.status !== "ACTIVE") return res.status(404).json({ error_code: "ACTIVE_CAMPAIGN_NOT_FOUND" });
  const eventId = String(req.body?.eventId ?? randomUUID());
  const actorId = req.body?.clientSessionId ? pseudonymize(String(req.body.clientSessionId)) : null;
  const amount = eventType === "CLICK" ? Math.min(campaign.bidCpc, Math.max(0, campaign.budget - campaign.spent)) : 0;
  try {
    const ledger = await prisma.$transaction(async (tx) => {
      const created = await tx.adLedger.create({ data: { eventId, campaignId: campaign.id, eventType, actorId, amount } });
      if (amount > 0) await tx.adCampaign.update({ where: { id: campaign.id }, data: { spent: { increment: amount } } });
      return created;
    });
    await recordEvent({ eventId: `stream_${eventId}`, eventType: eventType === "CLICK" ? "ad_clicked" : "ad_impression", actorId, entityType: "campaign", entityId: campaign.id, payload: { placeId: campaign.placeId, cost: amount } });
    return res.status(202).json({ eventId: ledger.eventId, charged: amount });
  } catch (error) {
    if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") return res.json({ eventId, deduplicated: true, charged: 0 });
    throw error;
  }
}
commerceRouter.post("/ads/:id/impressions", (req, res, next) => registerAdEvent(req, res, "IMPRESSION").catch(next));
commerceRouter.post("/ads/:id/clicks", (req, res, next) => registerAdEvent(req, res, "CLICK").catch(next));

commerceRouter.get("/places/:id/booking-options", async (req, res, next) => {
  try { res.json(await prisma.bookingPartner.findMany({ where: { placeId: req.params.id, active: true }, select: { id: true, provider: true } })); }
  catch (error) { next(error); }
});

commerceRouter.post("/bookings/start", async (req, res, next) => {
  try {
    const partner = await prisma.bookingPartner.findFirst({ where: { id: String(req.body?.partnerId ?? ""), active: true }, include: { place: { select: { id: true, nameKo: true } } } });
    if (!partner) return res.status(404).json({ error_code: "BOOKING_PARTNER_NOT_FOUND" });
    const eventId = String(req.body?.eventId ?? randomUUID());
    const actorId = req.body?.clientSessionId ? pseudonymize(String(req.body.clientSessionId)) : null;
    try {
      const booking = await prisma.bookingRecord.create({ data: { eventId, partnerId: partner.id, actorId, tripId: req.body?.tripId ? String(req.body.tripId) : null } });
      await recordEvent({ eventId: `stream_${eventId}`, eventType: "booking_started", actorId, entityType: "booking", entityId: booking.id, payload: { provider: partner.provider, placeId: partner.placeId } });
      return res.status(201).json({ bookingId: booking.id, status: booking.status, provider: partner.provider, bookingUrl: partner.bookingUrl, disclosure: "외부 예약 사이트로 이동합니다. 가격·재고는 제휴사에서 최종 확인하세요." });
    } catch (error) {
      if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") {
        const booking = await prisma.bookingRecord.findUniqueOrThrow({ where: { eventId } });
        return res.json({ bookingId: booking.id, status: booking.status, provider: partner.provider, bookingUrl: partner.bookingUrl, deduplicated: true });
      }
      throw error;
    }
  } catch (error) { next(error); }
});

commerceRouter.post("/bookings/:id/status", async (req, res, next) => {
  try {
    const secret = req.header("x-booking-webhook-secret");
    if (!process.env.BOOKING_WEBHOOK_SECRET || secret !== process.env.BOOKING_WEBHOOK_SECRET) return res.status(401).json({ error_code: "INVALID_WEBHOOK_SIGNATURE" });
    const status = String(req.body?.status ?? "");
    if (!["COMPLETED", "CANCELLED"].includes(status)) return res.status(400).json({ error_code: "INVALID_BOOKING_STATUS" });
    const current = await prisma.bookingRecord.findUnique({ where: { id: req.params.id }, include: { partner: true } });
    if (!current || current.status !== "STARTED") return res.status(409).json({ error_code: "INVALID_STATUS_TRANSITION" });
    const amount = status === "COMPLETED" ? Math.max(0, Number(req.body?.amount ?? 0)) : null;
    const booking = await prisma.bookingRecord.update({ where: { id: current.id }, data: { status, amount, commission: amount === null ? null : amount * current.partner.commissionRate, externalRef: req.body?.externalRef ? String(req.body.externalRef).slice(0, 100) : null } });
    await recordEvent({ eventType: status === "COMPLETED" ? "booking_completed" : "booking_cancelled", actorId: booking.actorId, entityType: "booking", entityId: booking.id, payload: { provider: current.partner.provider, amount } });
    res.json(booking);
  } catch (error) { next(error); }
});

commerceRouter.post("/businesses", async (req, res, next) => {
  try {
    const token = await rawBearer(req, res); if (!token) return;
    const name = String(req.body?.name ?? "").trim();
    const contactEmail = String(req.body?.contactEmail ?? "").trim().toLowerCase();
    if (name.length < 2 || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(contactEmail)) return res.status(400).json({ error_code: "INVALID_BUSINESS" });
    const business = await prisma.business.create({ data: { name, contactEmail, ownerSessionHash: pseudonymize(token), placeId: req.body?.placeId ? String(req.body.placeId) : null } });
    res.status(201).json({ id: business.id, status: business.status, name: business.name });
  } catch (error) { next(error); }
});

commerceRouter.post("/businesses/:id/campaigns", async (req, res, next) => {
  try {
    const token = await rawBearer(req, res); if (!token) return;
    const business = await prisma.business.findFirst({ where: { id: req.params.id, ownerSessionHash: pseudonymize(token) } });
    if (!business) return res.status(403).json({ error_code: "BUSINESS_OWNER_REQUIRED" });
    const budget = Number(req.body?.budget); const bidCpc = Number(req.body?.bidCpc);
    const serviceCategory = String(req.body?.serviceCategory ?? "");
    if (!isAllowedAdCategory(serviceCategory)) return res.status(422).json({ error_code: "AD_CATEGORY_NOT_ALLOWED", allowedCategories: ["LODGING", "RENTAL_CAR", "TRAVEL_INSURANCE", "TAXI", "AIRPORT_TRANSFER", "ESIM"], message: "숙박·렌터카·여행보험·택시·공항이동·eSIM 서비스만 광고할 수 있습니다." });
    const startsAt = new Date(req.body?.startsAt); const endsAt = new Date(req.body?.endsAt);
    if (budget <= 0 || bidCpc <= 0 || bidCpc > budget || !Number.isFinite(startsAt.getTime()) || endsAt <= startsAt) return res.status(400).json({ error_code: "INVALID_CAMPAIGN" });
    const campaign = await prisma.adCampaign.create({ data: { businessId: business.id, placeId: String(req.body?.placeId), name: String(req.body?.name ?? "캠페인").slice(0, 100), serviceCategory, budget, bidCpc, startsAt, endsAt, targetingModes: JSON.stringify(Array.isArray(req.body?.targetingModes) ? req.body.targetingModes : []), targetingLanguage: ["KO", "EN"].includes(req.body?.targetingLanguage) ? req.body.targetingLanguage : null } });
    res.status(201).json(campaign);
  } catch (error) { next(error); }
});
