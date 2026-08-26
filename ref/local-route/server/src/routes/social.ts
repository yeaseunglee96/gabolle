import { Prisma } from "@prisma/client";
import { Router } from "express";
import { prisma } from "../db.js";
import { optionalSession, requireSession } from "../services/auth.js";
import { pseudonymize, recordEvent } from "../services/events.js";

export const socialRouter = Router();

function stripJpegExif(buffer: Buffer) {
  if (buffer.length < 4 || buffer[0] !== 0xff || buffer[1] !== 0xd8) return buffer;
  const parts = [buffer.subarray(0, 2)]; let offset = 2;
  while (offset + 4 <= buffer.length && buffer[offset] === 0xff) {
    const marker = buffer[offset + 1];
    if (marker === 0xda || marker === 0xd9) { parts.push(buffer.subarray(offset)); break; }
    const length = buffer.readUInt16BE(offset + 2);
    if (length < 2 || offset + 2 + length > buffer.length) break;
    if (marker !== 0xe1) parts.push(buffer.subarray(offset, offset + 2 + length));
    offset += 2 + length;
  }
  return Buffer.concat(parts);
}

function sanitizeImage(dataUrl: unknown) {
  if (typeof dataUrl !== "string") throw new Error("INVALID_IMAGE");
  const match = dataUrl.match(/^data:(image\/(?:jpeg|png|webp));base64,([A-Za-z0-9+/=]+)$/);
  if (!match) throw new Error("INVALID_IMAGE");
  const rawBuffer = Buffer.from(match[2], "base64");
  if (rawBuffer.length > 750_000) throw new Error("IMAGE_TOO_LARGE");
  const buffer = match[1] === "image/jpeg" ? stripJpegExif(rawBuffer) : rawBuffer;
  return `data:${match[1]};base64,${buffer.toString("base64")}`;
}

socialRouter.post("/stories", async (req, res, next) => {
  try {
    const session = await requireSession(req, res); if (!session) return;
    const content = String(req.body?.content ?? "").trim();
    const visibility = ["PUBLIC", "FOLLOWERS", "PRIVATE"].includes(req.body?.visibility) ? req.body.visibility : "PUBLIC";
    const images = Array.isArray(req.body?.images) ? req.body.images : [];
    if (content.length < 1 || content.length > 500) return res.status(400).json({ error_code: "INVALID_STORY_CONTENT" });
    if (images.length > 3) return res.status(400).json({ error_code: "IMAGE_LIMIT_EXCEEDED" });
    let sanitizedImages: string[];
    try { sanitizedImages = images.map(sanitizeImage); } catch (error) { return res.status(400).json({ error_code: error instanceof Error ? error.message : "INVALID_IMAGE", message: "JPEG·PNG·WebP 이미지만 장당 750KB 이하로 올릴 수 있습니다." }); }
    const place = await prisma.place.findUnique({ where: { id: String(req.body?.placeId ?? "") } });
    if (!place) return res.status(404).json({ error_code: "PLACE_NOT_FOUND" });
    const itemId = req.body?.itineraryItemId ? String(req.body.itineraryItemId) : null;
    const item = itemId ? await prisma.itineraryItem.findUnique({ where: { id: itemId }, include: { day: { include: { itinerary: { include: { trip: true } } } } } }) : null;
    if (item && item.placeId !== place.id) return res.status(400).json({ error_code: "PLACE_ITEM_MISMATCH" });
    const verified = await prisma.visitVerification.findFirst({ where: { sessionId: session.id, placeId: place.id, status: "VERIFIED" } });
    if (req.body?.claimVisitVerified && !verified) return res.status(403).json({ error_code: "NOT_VISITED" });
    const afterTrip = req.body?.publishMode !== "NOW";
    const tripEnd = item?.day.itinerary.trip.endDate;
    const publishAt = afterTrip && tripEnd ? new Date(`${tripEnd}T15:00:00.000Z`) : new Date();
    const story = await prisma.story.create({ data: { authorSessionId: session.id, placeId: place.id, itineraryId: item?.day.itineraryId ?? null, itineraryItemId: item?.id ?? null, content, imageDataJson: JSON.stringify(sanitizedImages), rating: Number.isInteger(req.body?.rating) && req.body.rating >= 1 && req.body.rating <= 5 ? req.body.rating : null, visibility, visitVerified: !!verified, areaLabel: place.address.split(" ").slice(0, 2).join(" "), publishAt, moderationStatus: "PUBLISHED" } });
    await recordEvent({ eventType: "story_created", actorId: pseudonymize(session.id), entityType: "story", entityId: story.id, payload: { placeId: place.id, visibility, delayed: publishAt > new Date() } });
    res.status(201).json({ storyId: story.id, publishAt: story.publishAt, moderationStatus: story.moderationStatus, exifRemoved: true, delayed: story.publishAt > new Date() });
  } catch (error) { next(error); }
});

socialRouter.get("/stories", async (req, res, next) => {
  try {
    const session = await optionalSession(req); const mine = req.query.mine === "true"; const followingOnly = req.query.following === "true";
    if (mine && !session) return res.status(401).json({ error_code: "SESSION_REQUIRED" });
    if (followingOnly && !session) return res.status(401).json({ error_code: "SESSION_REQUIRED" });
    const following = session ? (await prisma.follow.findMany({ where: { followerSessionId: session.id, status: "ACTIVE" }, select: { followeeSessionId: true } })).map((row) => row.followeeSessionId) : [];
    const where: Prisma.StoryWhereInput = mine ? { authorSessionId: session!.id } : followingOnly ? { authorSessionId: { in: following }, publishAt: { lte: new Date() }, moderationStatus: "PUBLISHED", visibility: { in: ["PUBLIC", "FOLLOWERS"] } } : { publishAt: { lte: new Date() }, moderationStatus: "PUBLISHED", OR: [{ visibility: "PUBLIC" }, ...(session ? [{ authorSessionId: session.id }, { visibility: "FOLLOWERS", authorSessionId: { in: following } }] : [])] };
    if (typeof req.query.placeId === "string") where.placeId = req.query.placeId;
    const stories = await prisma.story.findMany({ where, include: { place: { select: { nameKo: true, nameEn: true } }, itinerary: { select: { tripId: true } }, itineraryItem: { select: { plannedArrival: true, day: { select: { dayIndex: true, visitDate: true } } } }, _count: { select: { reports: true } } }, orderBy: { createdAt: "desc" }, take: 50 });
    res.json(stories.map((story) => ({ id: story.id, authorId: story.authorSessionId, authorLabel: `여행자 ${story.authorSessionId.slice(-4)}`, placeId: story.placeId, placeName: story.place.nameKo, placeNameEn: story.place.nameEn, tripId: story.itinerary?.tripId ?? null, dayIndex: story.itineraryItem?.day.dayIndex ?? null, visitDate: story.itineraryItem?.day.visitDate ?? null, plannedArrival: story.itineraryItem?.plannedArrival ?? null, content: story.content, images: JSON.parse(story.imageDataJson), rating: story.rating, visibility: story.visibility, visitVerified: story.visitVerified, areaLabel: story.areaLabel, publishAt: story.publishAt, moderationStatus: story.moderationStatus, createdAt: story.createdAt, reportCount: story._count.reports, isFollowing: following.includes(story.authorSessionId), mine: session?.id === story.authorSessionId })));
  } catch (error) { next(error); }
});

socialRouter.patch("/stories/:id", async (req, res, next) => {
  try {
    const session = await requireSession(req, res); if (!session) return;
    const story = await prisma.story.findUnique({ where: { id: req.params.id } });
    if (!story) return res.status(404).json({ error_code: "STORY_NOT_FOUND", message: "기록을 찾을 수 없습니다." });
    if (story.authorSessionId !== session.id) return res.status(403).json({ error_code: "STORY_EDIT_FORBIDDEN", message: "내 기록만 수정할 수 있습니다." });
    const content = String(req.body?.content ?? "").trim();
    const visibility = ["PUBLIC", "FOLLOWERS", "PRIVATE"].includes(req.body?.visibility) ? req.body.visibility : story.visibility;
    const images = Array.isArray(req.body?.images) ? req.body.images : JSON.parse(story.imageDataJson);
    if (content.length < 1 || content.length > 500) return res.status(400).json({ error_code: "INVALID_STORY_CONTENT", message: "기록은 1~500자로 입력해주세요." });
    if (images.length > 3) return res.status(400).json({ error_code: "IMAGE_LIMIT_EXCEEDED", message: "사진은 최대 3장까지 올릴 수 있습니다." });
    let sanitizedImages: string[];
    try { sanitizedImages = images.map(sanitizeImage); } catch { return res.status(400).json({ error_code: "INVALID_IMAGE", message: "JPEG·PNG·WebP 이미지만 장당 750KB 이하로 올릴 수 있습니다." }); }
    const publishAt = req.body?.publishNow === true ? new Date() : story.publishAt;
    const updated = await prisma.story.update({ where: { id: story.id }, data: { content, visibility, imageDataJson: JSON.stringify(sanitizedImages), publishAt } });
    await recordEvent({ eventType: "story_updated", actorId: pseudonymize(session.id), entityType: "story", entityId: story.id, payload: { visibility, imageCount: sanitizedImages.length } });
    res.json({ id: updated.id, content: updated.content, visibility: updated.visibility, images: sanitizedImages, publishAt: updated.publishAt, updatedAt: updated.updatedAt });
  } catch (error) { next(error); }
});

socialRouter.delete("/stories/:id", async (req, res, next) => {
  try {
    const session = await requireSession(req, res); if (!session) return;
    const story = await prisma.story.findUnique({ where: { id: req.params.id } });
    if (!story) return res.status(404).json({ error_code: "STORY_NOT_FOUND", message: "기록을 찾을 수 없습니다." });
    if (story.authorSessionId !== session.id) return res.status(403).json({ error_code: "STORY_DELETE_FORBIDDEN", message: "내 기록만 삭제할 수 있습니다." });
    await prisma.story.delete({ where: { id: story.id } });
    await recordEvent({ eventType: "story_deleted", actorId: pseudonymize(session.id), entityType: "story", entityId: story.id });
    res.status(204).end();
  } catch (error) { next(error); }
});

socialRouter.post("/stories/:id/report", async (req, res, next) => {
  try {
    const session = await requireSession(req, res); if (!session) return;
    const reason = String(req.body?.reason ?? "");
    if (!["SPAM", "ABUSE", "COPYRIGHT", "PRIVACY"].includes(reason)) return res.status(400).json({ error_code: "INVALID_REPORT_REASON" });
    const story = await prisma.story.findUnique({ where: { id: req.params.id } });
    if (!story) return res.status(404).json({ error_code: "STORY_NOT_FOUND" });
    try {
      const report = await prisma.storyReport.create({ data: { storyId: story.id, reporterSessionId: session.id, reason } });
      await prisma.story.update({ where: { id: story.id }, data: { moderationStatus: "REVIEW" } });
      await recordEvent({ eventType: "story_reported", actorId: pseudonymize(session.id), entityType: "story", entityId: story.id, payload: { reason } });
      return res.status(201).json({ reportId: report.id, status: report.status, moderationStatus: "REVIEW" });
    } catch (error) {
      if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") return res.json({ deduplicated: true, moderationStatus: "REVIEW" });
      throw error;
    }
  } catch (error) { next(error); }
});

socialRouter.post("/users/:id/follow", async (req, res, next) => {
  try {
    const session = await requireSession(req, res); if (!session) return;
    if (session.id === req.params.id) return res.status(400).json({ error_code: "CANNOT_FOLLOW_SELF" });
    if (!await prisma.anonymousSession.findUnique({ where: { id: req.params.id } })) return res.status(404).json({ error_code: "USER_NOT_FOUND" });
    await prisma.follow.upsert({ where: { followerSessionId_followeeSessionId: { followerSessionId: session.id, followeeSessionId: req.params.id } }, create: { followerSessionId: session.id, followeeSessionId: req.params.id }, update: { status: "ACTIVE" } });
    await recordEvent({ eventType: "user_followed", actorId: pseudonymize(session.id), entityType: "user", entityId: pseudonymize(req.params.id) });
    res.json({ status: "ACTIVE" });
  } catch (error) { next(error); }
});

socialRouter.delete("/users/:id/follow", async (req, res, next) => {
  try {
    const session = await requireSession(req, res); if (!session) return;
    await prisma.follow.deleteMany({ where: { followerSessionId: session.id, followeeSessionId: req.params.id } });
    await recordEvent({ eventType: "user_unfollowed", actorId: pseudonymize(session.id), entityType: "user", entityId: pseudonymize(req.params.id) });
    res.json({ status: "INACTIVE" });
  } catch (error) { next(error); }
});

function adminAllowed(req: { header(name: string): string | undefined }) { return req.header("x-admin-token") === (process.env.ADMIN_TOKEN ?? "local-route-demo-admin"); }
socialRouter.get("/moderation/stories", async (req, res, next) => {
  if (!adminAllowed(req)) return res.status(403).json({ error_code: "ADMIN_REQUIRED" });
  try { res.json(await prisma.storyReport.findMany({ where: { status: "PENDING" }, include: { story: { select: { id: true, content: true, areaLabel: true, moderationStatus: true } } }, orderBy: { createdAt: "asc" } })); } catch (error) { next(error); }
});
socialRouter.patch("/moderation/reports/:id", async (req, res, next) => {
  if (!adminAllowed(req)) return res.status(403).json({ error_code: "ADMIN_REQUIRED" });
  try {
    const action = req.body?.action === "REMOVE" ? "REMOVE" : "DISMISS";
    const report = await prisma.storyReport.update({ where: { id: req.params.id }, data: { status: action === "REMOVE" ? "RESOLVED" : "DISMISSED", resolvedAt: new Date() } });
    await prisma.story.update({ where: { id: report.storyId }, data: { moderationStatus: action === "REMOVE" ? "REMOVED" : "PUBLISHED" } });
    res.json({ reportId: report.id, status: report.status, action });
  } catch (error) { next(error); }
});
