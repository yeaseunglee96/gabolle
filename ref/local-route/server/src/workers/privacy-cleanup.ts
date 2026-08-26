import "dotenv/config";
import { prisma } from "../db.js";

async function main() {
  const now = new Date();
  const exactLocationCutoff = new Date(now.getTime() - 24 * 60 * 60_000);
  const eventCutoffs: Record<string, Date> = {
    "place-interaction-events": new Date(now.getTime() - 14 * 24 * 60 * 60_000),
    "trip-events": new Date(now.getTime() - 30 * 24 * 60 * 60_000),
    "recommendation-events": new Date(now.getTime() - 90 * 24 * 60 * 60_000),
    "translation-events": new Date(now.getTime() - 180 * 24 * 60 * 60_000),
  };
  const visits = await prisma.visitVerification.findMany({ where: { createdAt: { lt: exactLocationCutoff }, coordinatesSanitizedAt: null }, select: { id: true, latitude: true, longitude: true } });
  for (const visit of visits) {
    const latitude = Math.round(visit.latitude * 200) / 200;
    const longitude = Math.round(visit.longitude * 200) / 200;
    await prisma.visitVerification.update({ where: { id: visit.id }, data: { latitude, longitude, gridCell: `${latitude.toFixed(3)}:${longitude.toFixed(3)}`, coordinatesSanitizedAt: now } });
  }
  let deletedEvents = 0;
  for (const [topic, cutoff] of Object.entries(eventCutoffs)) deletedEvents += (await prisma.eventOutbox.deleteMany({ where: { topic, occurredAt: { lt: cutoff } } })).count;
  const expiredSessions = await prisma.anonymousSession.findMany({ where: { expiresAt: { lt: now }, tokenHash: { not: { startsWith: "expired:" } } }, select: { id: true } });
  for (const session of expiredSessions) await prisma.anonymousSession.update({ where: { id: session.id }, data: { tokenHash: `expired:${session.id}` } });
  console.log(JSON.stringify({ sanitizedVisits: visits.length, deletedEvents, expiredSessions: expiredSessions.length }));
}

main().finally(() => prisma.$disconnect());
