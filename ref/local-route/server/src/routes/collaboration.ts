import { createHash, randomBytes } from "node:crypto";
import { Prisma } from "@prisma/client";
import { Router } from "express";
import { prisma } from "../db.js";
import { optionalSession, requireSession } from "../services/auth.js";
import { createJob } from "../services/jobs.js";
import { pseudonymize, recordEvent, recordEventBestEffort } from "../services/events.js";

export const collaborationRouter = Router();
const hash = (value: string) => createHash("sha256").update(value).digest("hex");
const token = () => randomBytes(18).toString("base64url");

async function roleFor(itineraryId: string, sessionId: string) {
  const itinerary = await prisma.itinerary.findUnique({ where: { id: itineraryId }, select: { trip: { select: { id: true, ownerSessionId: true } } } });
  if (!itinerary) return null;
  if (!itinerary.trip.ownerSessionId) {
    await prisma.trip.update({ where: { id: itinerary.trip.id }, data: { ownerSessionId: sessionId } });
    return "OWNER";
  }
  if (itinerary.trip.ownerSessionId === sessionId) return "OWNER";
  return (await prisma.tripMember.findFirst({ where: { tripId: itinerary.trip.id, sessionId, revokedAt: null, joinedAt: { not: null } }, select: { role: true } }))?.role ?? null;
}

collaborationRouter.post("/itineraries/:id/share", async (req, res, next) => {
  try {
    const session = await requireSession(req, res); if (!session) return;
    const role = await roleFor(req.params.id, session.id);
    if (role !== "OWNER") return res.status(403).json({ error_code: "NOT_OWNER" });
    const days = Math.min(90, Math.max(1, Number(req.body?.expiresInDays ?? 30)));
    const now = new Date();
    const existing = await prisma.itineraryShare.findFirst({ where: { itineraryId: req.params.id, ownerSessionId: session.id, revokedAt: null, expiresAt: { gt: now } }, orderBy: { createdAt: "desc" } });
    const share = existing ?? await prisma.itineraryShare.create({ data: { itineraryId: req.params.id, ownerSessionId: session.id, shareSlug: token().slice(0, 10), visibility: "LINK", expiresAt: new Date(Date.now() + days * 86_400_000) } });
    if (!existing) await recordEvent({ eventType: "share_created", actorId: pseudonymize(session.id), entityType: "itinerary", entityId: req.params.id });
    res.status(existing ? 200 : 201).json({ shareSlug: share.shareSlug, url: `${req.protocol}://${req.get("host")}/?share=${share.shareSlug}`, expiresAt: share.expiresAt, reused: !!existing });
  } catch (error) { next(error); }
});

collaborationRouter.get("/s/:slug", async (req, res, next) => {
  try {
    const share = await prisma.itineraryShare.findUnique({ where: { shareSlug: req.params.slug }, include: { itinerary: { include: { trip: { include: { preference: true } }, days: { include: { items: { include: { place: true } } }, orderBy: { dayIndex: "asc" } } } }, owner: { select: { id: true } } } });
    if (!share || share.revokedAt) return res.status(404).json({ error_code: "SHARE_NOT_FOUND" });
    if (share.expiresAt <= new Date()) return res.status(410).json({ error_code: "SHARE_EXPIRED", message: "공유 링크가 만료되었습니다." });
    await prisma.itineraryShare.update({ where: { id: share.id }, data: { viewCount: { increment: 1 } } });
    await recordEventBestEffort({ eventType: "share_viewed", entityType: "itinerary", entityId: share.itineraryId });
    const trip = share.itinerary.trip;
    res.json({ shareSlug: share.shareSlug, authorId: `traveler-${share.owner.id.slice(-6)}`, expiresAt: share.expiresAt, viewCount: share.viewCount + 1, cloneCount: share.cloneCount, trip: { startDate: trip.startDate, endDate: trip.endDate, partySize: trip.partySize, pace: trip.pace, language: trip.preference?.language ?? "KO" }, itinerary: { id: share.itinerary.id, mode: share.itinerary.mode, days: share.itinerary.days.map((day) => ({ dayIndex: day.dayIndex, visitDate: day.visitDate, items: day.items.sort((a,b)=>a.seqOrder-b.seqOrder).map((item) => ({ itemId: item.id, seqOrder: item.seqOrder, plannedArrival: item.plannedArrival, stayMinutes: item.stayMinutes, nameKo: item.place.nameKo, nameEn: item.place.nameEn, category: item.place.category, address: item.place.address, lat: item.place.lat, lng: item.place.lng })) })) } });
  } catch (error) { next(error); }
});

collaborationRouter.post("/s/:slug/clone", async (req, res, next) => {
  try {
    const session = await requireSession(req, res); if (!session) return;
    const share = await prisma.itineraryShare.findUnique({ where: { shareSlug: req.params.slug } });
    if (!share || share.revokedAt) return res.status(404).json({ error_code: "SHARE_NOT_FOUND" });
    if (share.expiresAt <= new Date()) return res.status(410).json({ error_code: "SHARE_EXPIRED" });
    const targetTripId = String(req.body?.tripId ?? "");
    const trip = await prisma.trip.findFirst({ where: { id: targetTripId, OR: [{ ownerSessionId: session.id }, { ownerSessionId: null }] } });
    if (!trip) return res.status(404).json({ error_code: "TARGET_TRIP_NOT_FOUND", message: "먼저 내 여행 조건을 만든 뒤 복제해 주세요." });
    if (!trip.ownerSessionId) await prisma.trip.update({ where: { id: trip.id }, data: { ownerSessionId: session.id } });
    const created = await createJob(trip.id, `clone:${share.id}:${trip.id}`);
    await prisma.itineraryShare.update({ where: { id: share.id }, data: { cloneCount: { increment: 1 } } });
    await recordEvent({ eventType: "itinerary_cloned", actorId: pseudonymize(session.id), entityType: "itinerary", entityId: share.itineraryId, payload: { targetTripId: trip.id } });
    res.status(202).json({ jobId: created.job.jobId, statusUrl: `/api/itinerary-jobs/${created.job.jobId}` });
  } catch (error) { next(error); }
});

collaborationRouter.post("/trips/:tripId/members/invite", async (req, res, next) => {
  try {
    const session = await requireSession(req, res); if (!session) return;
    const trip = await prisma.trip.findUnique({ where: { id: req.params.tripId } });
    if (!trip) return res.status(404).json({ error_code: "TRIP_NOT_FOUND" });
    if (!trip.ownerSessionId) await prisma.trip.update({ where: { id: trip.id }, data: { ownerSessionId: session.id } });
    else if (trip.ownerSessionId !== session.id) return res.status(403).json({ error_code: "NOT_OWNER" });
    const role = req.body?.role === "EDITOR" ? "EDITOR" : "VIEWER";
    const raw = token(); const days = Math.min(30, Math.max(1, Number(req.body?.expiresInDays ?? 7)));
    const member = await prisma.tripMember.create({ data: { tripId: trip.id, role, inviteTokenHash: hash(raw), expiresAt: new Date(Date.now() + days * 86_400_000) } });
    await recordEvent({ eventType: "companion_invited", actorId: pseudonymize(session.id), entityType: "trip", entityId: trip.id, payload: { role } });
    res.status(201).json({ inviteId: member.id, role, inviteToken: raw, inviteUrl: `${req.protocol}://${req.get("host")}/?invite=${raw}`, expiresAt: member.expiresAt });
  } catch (error) { next(error); }
});

collaborationRouter.post("/collaboration/invites/:inviteToken/accept", async (req, res, next) => {
  try {
    const session = await requireSession(req, res); if (!session) return;
    const member = await prisma.tripMember.findUnique({ where: { inviteTokenHash: hash(req.params.inviteToken) } });
    if (!member || member.revokedAt) return res.status(404).json({ error_code: "INVITE_NOT_FOUND" });
    if (!member.expiresAt || member.expiresAt <= new Date()) return res.status(410).json({ error_code: "INVITE_EXPIRED" });
    try {
      const joined = await prisma.tripMember.update({ where: { id: member.id }, data: { sessionId: session.id, joinedAt: new Date(), inviteTokenHash: null } });
      return res.json({ tripId: joined.tripId, role: joined.role, joinedAt: joined.joinedAt });
    } catch (error) {
      if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") return res.status(409).json({ error_code: "ALREADY_MEMBER" });
      throw error;
    }
  } catch (error) { next(error); }
});

collaborationRouter.get("/itineraries/:id/collaboration", async (req, res, next) => {
  try {
    const session = await requireSession(req, res); if (!session) return;
    const role = await roleFor(req.params.id, session.id);
    if (!role) return res.status(403).json({ error_code: "NOT_A_MEMBER" });
    const itinerary = await prisma.itinerary.findUnique({ where: { id: req.params.id }, include: { trip: { include: { members: { where: { revokedAt: null, joinedAt: { not: null } }, select: { id: true, sessionId: true, role: true, joinedAt: true } } } }, days: { select: { dayIndex: true, lockedBySessionId: true, lockedUntil: true, items: { select: { id: true, version: true, seqOrder: true, isPinned: true }, orderBy: { seqOrder: "asc" } } } } } });
    if (!itinerary) return res.status(404).json({ error_code: "ITINERARY_NOT_FOUND" });
    res.json({ version: itinerary.version, myRole: role, members: [{ id: "owner", role: "OWNER", sessionId: itinerary.trip.ownerSessionId }, ...itinerary.trip.members], days: itinerary.days });
  } catch (error) { next(error); }
});

collaborationRouter.patch("/itineraries/:id/items/:itemId/collaborate", async (req, res, next) => {
  try {
    const session = await requireSession(req, res); if (!session) return;
    const role = await roleFor(req.params.id, session.id);
    if (!role || role === "VIEWER") return res.status(403).json({ error_code: "EDIT_PERMISSION_REQUIRED" });
    const item = await prisma.itineraryItem.findFirst({ where: { id: req.params.itemId, day: { itineraryId: req.params.id } }, include: { day: true } });
    if (!item) return res.status(404).json({ error_code: "ITEM_NOT_FOUND" });
    if (item.day.lockedUntil && item.day.lockedUntil > new Date() && item.day.lockedBySessionId !== session.id) return res.status(423).json({ error_code: "DAY_LOCKED", lockedUntil: item.day.lockedUntil });
    const expectedVersion = Number(req.body?.version);
    if (!Number.isInteger(expectedVersion) || expectedVersion !== item.version) return res.status(409).json({ error_code: "VERSION_CONFLICT", latest: { id: item.id, version: item.version, seqOrder: item.seqOrder, isPinned: item.isPinned } });
    const action = String(req.body?.action ?? "");
    if (action === "REMOVE") await prisma.itineraryItem.delete({ where: { id: item.id } });
    else if (action === "PIN" || action === "UNPIN") await prisma.itineraryItem.update({ where: { id: item.id }, data: { isPinned: action === "PIN", version: { increment: 1 } } });
    else if (action === "MOVE_UP" || action === "MOVE_DOWN") {
      const direction = action === "MOVE_UP" ? -1 : 1;
      const other = await prisma.itineraryItem.findFirst({ where: { dayId: item.dayId, seqOrder: item.seqOrder + direction } });
      if (other) await prisma.$transaction([prisma.itineraryItem.update({ where: { id: item.id }, data: { seqOrder: other.seqOrder, version: { increment: 1 } } }), prisma.itineraryItem.update({ where: { id: other.id }, data: { seqOrder: item.seqOrder, version: { increment: 1 } } })]);
    } else return res.status(400).json({ error_code: "INVALID_EDIT_ACTION" });
    const updated = await prisma.itinerary.update({ where: { id: req.params.id }, data: { version: { increment: 1 } }, select: { version: true } });
    await recordEvent({ eventType: "itinerary_edited", actorId: pseudonymize(session.id), entityType: "itinerary", entityId: req.params.id, payload: { itemId: item.id, action, version: updated.version } });
    res.json({ version: updated.version });
  } catch (error) { next(error); }
});
