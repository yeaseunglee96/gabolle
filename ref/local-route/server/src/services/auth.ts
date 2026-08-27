import type { Request, Response } from "express";
import { prisma } from "../db.js";
import { hashSessionToken } from "./community.js";

export function sessionToken(req: Request): string | null {
  const authorization = req.header("authorization");
  if (authorization?.startsWith("Bearer ")) return authorization.slice(7).trim();
  return req.header("x-session-token")?.trim() || null;
}

export async function optionalSession(req: Request) {
  const token = sessionToken(req);
  if (!token) return null;
  const session = await prisma.anonymousSession.findUnique({ where: { tokenHash: hashSessionToken(token) } });
  if (session && session.expiresAt > new Date()) return session;
  const account = await prisma.accountSession.findUnique({ where: { tokenHash: hashSessionToken(token) }, include: { user: { include: { ownerSession: true } } } });
  if (!account || account.expiresAt <= new Date()) return null;
  await prisma.accountSession.update({ where: { id: account.id }, data: { lastSeenAt: new Date() } });
  return account.user.ownerSession;
}

export async function requireSession(req: Request, res: Response) {
  const session = await optionalSession(req);
  if (!session) {
    res.status(401).json({ error_code: "SESSION_REQUIRED", message: "유효한 익명 세션 토큰이 필요합니다." });
    return null;
  }
  await prisma.anonymousSession.update({ where: { id: session.id }, data: { lastSeenAt: new Date() } });
  return session;
}

export async function resolveTripRole(tripId: string, sessionId: string, claimUnowned = true) {
  const trip = await prisma.trip.findUnique({ where: { id: tripId }, select: { ownerSessionId: true } });
  if (!trip) return null;
  if (!trip.ownerSessionId && claimUnowned) { await prisma.trip.update({ where: { id: tripId }, data: { ownerSessionId: sessionId } }); return "OWNER"; }
  if (trip.ownerSessionId === sessionId) return "OWNER";
  return (await prisma.tripMember.findFirst({ where: { tripId, sessionId, joinedAt: { not: null }, revokedAt: null }, select: { role: true } }))?.role ?? null;
}

export async function requireTripEditor(req: Request, res: Response, tripId: string) {
  const session = await requireSession(req, res); if (!session) return null;
  const role = await resolveTripRole(tripId, session.id);
  if (!role || role === "VIEWER") { res.status(403).json({ error_code: "EDIT_PERMISSION_REQUIRED", message: "소유자 또는 편집자만 일정을 변경할 수 있습니다." }); return null; }
  return { session, role };
}

export async function requireTripViewer(req: Request, res: Response, tripId: string) {
  const session = await requireSession(req, res); if (!session) return null;
  const role = await resolveTripRole(tripId, session.id);
  if (!role) { res.status(403).json({ error_code: "TRIP_ACCESS_DENIED", message: "이 일정에 접근할 권한이 없습니다. 공유 링크를 사용해 주세요." }); return null; }
  return { session, role };
}

export async function requireItineraryEditor(req: Request, res: Response, itineraryId: string) {
  const itinerary = await prisma.itinerary.findUnique({ where: { id: itineraryId }, select: { tripId: true } });
  if (!itinerary) { res.status(404).json({ error_code: "ITINERARY_NOT_FOUND" }); return null; }
  return requireTripEditor(req, res, itinerary.tripId);
}
