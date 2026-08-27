import { Router, type Request, type Response } from "express";
import { Prisma } from "@prisma/client";
import { prisma } from "../db.js";
import { calculateCommunityLocalScore, calculateLocalGrade, evaluateVisit, hashSessionToken, issueSessionToken } from "../services/community.js";
import { pseudonymize, recordEvent } from "../services/events.js";

export const communityRouter = Router();

function bearerToken(req: Request): string | null {
  const authorization = req.header("authorization");
  if (authorization?.startsWith("Bearer ")) return authorization.slice(7).trim();
  return req.header("x-session-token")?.trim() || null;
}

async function requireSession(req: Request, res: Response) {
  const token = bearerToken(req);
  if (!token) {
    res.status(401).json({ error_code: "SESSION_REQUIRED", message: "익명 세션 토큰이 필요합니다." });
    return null;
  }
  const session = await prisma.anonymousSession.findUnique({ where: { tokenHash: hashSessionToken(token) } });
  if (!session || session.expiresAt <= new Date()) {
    res.status(401).json({ error_code: "SESSION_EXPIRED", message: "세션이 없거나 만료되었습니다." });
    return null;
  }
  await prisma.anonymousSession.update({ where: { id: session.id }, data: { lastSeenAt: new Date() } });
  return session;
}

async function refreshLocalProfile(sessionId: string) {
  const visits = await prisma.visitVerification.findMany({ where: { sessionId, status: "VERIFIED" }, select: { placeId: true } });
  const reviewCount = await prisma.review.count({ where: { sessionId, status: "PUBLISHED" } });
  const counts = new Map<string, number>();
  for (const visit of visits) counts.set(visit.placeId, (counts.get(visit.placeId) ?? 0) + 1);
  const stats = {
    verifiedVisitCount: visits.length,
    uniquePlaceCount: counts.size,
    repeatVisitCount: [...counts.values()].reduce((sum, count) => sum + Math.max(0, count - 1), 0),
    reviewCount,
  };
  const grade = calculateLocalGrade(stats);
  return prisma.localProfile.upsert({
    where: { sessionId },
    create: { sessionId, ...stats, ...grade },
    update: { ...stats, ...grade },
  });
}

export async function refreshPlaceLocalScore(placeId: string) {
  const visits = await prisma.visitVerification.findMany({ where: { placeId, status: "VERIFIED" }, select: { sessionId: true } });
  const visitorCounts = new Map<string, number>();
  for (const visit of visits) visitorCounts.set(visit.sessionId, (visitorCounts.get(visit.sessionId) ?? 0) + 1);
  const reviewStats = await prisma.review.aggregate({
    where: { placeId, status: "PUBLISHED" },
    _avg: { rating: true },
    _count: { rating: true },
  });
  const score = calculateCommunityLocalScore({
    verifiedVisitorCount: visitorCounts.size,
    verifiedVisitCount: visits.length,
    repeatVisitorCount: [...visitorCounts.values()].filter((count) => count > 1).length,
    averageRating: reviewStats._avg.rating,
    reviewCount: reviewStats._count.rating,
  });
  if (score === null) return null;
  return prisma.place.update({
    where: { id: placeId },
    data: { localScore: score, localScoreSource: "VERIFIED_COMMUNITY", localScoreUpdatedAt: new Date() },
    select: { id: true, localScore: true, localScoreSource: true, localScoreUpdatedAt: true },
  });
}

communityRouter.post("/auth/anonymous", async (req, res, next) => {
  try {
    const locale = req.body?.locale === "EN" ? "EN" : "KO";
    const issued = issueSessionToken();
    const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
    const session = await prisma.anonymousSession.create({
      data: { tokenHash: issued.tokenHash, locale, expiresAt, localProfile: { create: {} } },
      select: { id: true, locale: true, expiresAt: true },
    });
    res.status(201).json({ ...session, token: issued.token, tokenType: "Bearer" });
  } catch (error) { next(error); }
});

communityRouter.post("/places/:id/visits/verify", async (req, res, next) => {
  try {
    const session = await requireSession(req, res);
    if (!session) return;
    const place = await prisma.place.findUnique({ where: { id: req.params.id }, select: { id: true, lat: true, lng: true } });
    if (!place) return res.status(404).json({ error_code: "PLACE_NOT_FOUND", message: "장소를 찾을 수 없습니다." });
    const latitude = Number(req.body?.latitude);
    const longitude = Number(req.body?.longitude);
    const arrivedAt = new Date(req.body?.arrivedAt);
    const departedAt = new Date(req.body?.departedAt);
    if (![latitude, longitude, arrivedAt.getTime(), departedAt.getTime()].every(Number.isFinite)) {
      return res.status(400).json({ error_code: "INVALID_VISIT_PROOF", message: "위치와 체류 시각을 확인해 주세요." });
    }
    if (departedAt.getTime() > Date.now() + 5 * 60_000 || arrivedAt.getTime() < Date.now() - 7 * 24 * 60 * 60_000) {
      return res.status(400).json({ error_code: "VISIT_TIME_OUT_OF_RANGE", message: "최근 7일 이내의 방문만 인증할 수 있습니다." });
    }
    const decision = evaluateVisit({ placeLat: place.lat, placeLng: place.lng, latitude, longitude, arrivedAt, departedAt });
    if (!decision.verified) return res.status(422).json({ ...decision, status: "REJECTED" });
    const visitDate = arrivedAt.toISOString().slice(0, 10);
    try {
      const gridLatitude = Math.round(latitude * 200) / 200;
      const gridLongitude = Math.round(longitude * 200) / 200;
      const verification = await prisma.visitVerification.create({
        data: { sessionId: session.id, placeId: place.id, visitDate, arrivedAt, departedAt, latitude: gridLatitude, longitude: gridLongitude, gridCell: `${gridLatitude.toFixed(3)}:${gridLongitude.toFixed(3)}`, coordinatesSanitizedAt: new Date(), distanceM: decision.distanceM, dwellMinutes: decision.dwellMinutes },
      });
      const [profile, placeScore] = await Promise.all([refreshLocalProfile(session.id), refreshPlaceLocalScore(place.id)]);
      await recordEvent({ eventType: profile.repeatVisitCount > 0 ? "place_revisited" : "place_visit_verified", actorId: pseudonymize(session.id), entityType: "place", entityId: place.id, payload: { distanceBandM: Math.ceil(decision.distanceM / 50) * 50, dwellMinutes: decision.dwellMinutes } });
      return res.status(201).json({ ...verification, profile, placeScore });
    } catch (error) {
      if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") {
        return res.status(409).json({ error_code: "VISIT_ALREADY_VERIFIED", message: "같은 장소는 하루에 한 번만 인증할 수 있습니다." });
      }
      throw error;
    }
  } catch (error) { next(error); }
});

communityRouter.post("/places/:id/reviews", async (req, res, next) => {
  try {
    const session = await requireSession(req, res);
    if (!session) return;
    const rating = Number(req.body?.rating);
    const body = String(req.body?.body ?? "").trim();
    const verificationId = String(req.body?.verificationId ?? "");
    if (!Number.isInteger(rating) || rating < 1 || rating > 5 || body.length < 10 || body.length > 1000) {
      return res.status(400).json({ error_code: "INVALID_REVIEW", message: "평점은 1~5, 후기는 10~1000자로 입력해 주세요." });
    }
    const verification = await prisma.visitVerification.findFirst({ where: { id: verificationId, sessionId: session.id, placeId: req.params.id, status: "VERIFIED" } });
    if (!verification) return res.status(403).json({ error_code: "VERIFIED_VISIT_REQUIRED", message: "이 장소의 인증된 방문이 필요합니다." });
    try {
      const review = await prisma.review.create({ data: { sessionId: session.id, placeId: req.params.id, verificationId, rating, body, language: req.body?.language === "EN" ? "EN" : "KO" } });
      const [profile, placeScore] = await Promise.all([refreshLocalProfile(session.id), refreshPlaceLocalScore(req.params.id)]);
      return res.status(201).json({ ...review, profile, placeScore });
    } catch (error) {
      if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") return res.status(409).json({ error_code: "REVIEW_ALREADY_EXISTS", message: "방문 인증 한 건당 후기 한 개만 작성할 수 있습니다." });
      throw error;
    }
  } catch (error) { next(error); }
});

communityRouter.get("/places/:id/reviews", async (req, res, next) => {
  try {
    const reviews = await prisma.review.findMany({ where: { placeId: req.params.id, status: "PUBLISHED" }, orderBy: { createdAt: "desc" }, take: 50, select: { id: true, rating: true, body: true, language: true, createdAt: true } });
    const aggregate = await prisma.review.aggregate({ where: { placeId: req.params.id, status: "PUBLISHED" }, _avg: { rating: true }, _count: { rating: true } });
    res.json({ reviews, averageRating: aggregate._avg.rating, count: aggregate._count.rating });
  } catch (error) { next(error); }
});

communityRouter.get("/local-profile", async (req, res, next) => {
  try {
    const session = await requireSession(req, res);
    if (!session) return;
    res.json(await refreshLocalProfile(session.id));
  } catch (error) { next(error); }
});
