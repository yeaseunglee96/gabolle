import { EventEmitter } from "node:events";
import { prisma } from "../db.js";
import { GenerationError, generateItineraryForTrip } from "./generate.js";
import { recordEvent } from "./events.js";

const events = new EventEmitter();
events.setMaxListeners(100);

export type JobSnapshot = {
  jobId: string;
  tripId: string;
  status: string;
  stage: string;
  progress: number;
  resultItineraryId: string | null;
  errorCode: string | null;
  errorMessage: string | null;
};

function snapshot(job: Awaited<ReturnType<typeof prisma.itineraryJob.findUnique>>): JobSnapshot | null {
  if (!job) return null;
  return { jobId: job.id, tripId: job.tripId, status: job.status, stage: job.stage, progress: job.progress, resultItineraryId: job.resultItineraryId, errorCode: job.errorCode, errorMessage: job.errorMessage };
}

async function update(jobId: string, data: Parameters<typeof prisma.itineraryJob.update>[0]["data"]) {
  const job = await prisma.itineraryJob.update({ where: { id: jobId }, data });
  const value = snapshot(job)!;
  events.emit(jobId, value);
  return value;
}

export async function createJob(tripId: string, idempotencyKey?: string) {
  if (idempotencyKey) {
    const existing = await prisma.itineraryJob.findUnique({ where: { idempotencyKey } });
    if (existing) return { job: snapshot(existing)!, created: false };
  }
  const job = await prisma.itineraryJob.create({ data: { tripId, idempotencyKey: idempotencyKey || null } });
  const value = snapshot(job)!;
  queueMicrotask(() => void runJob(value.jobId));
  return { job: value, created: true };
}

async function runJob(jobId: string) {
  try {
    await update(jobId, { status: "RUNNING", stage: "COLLECTING", progress: 5 });
    const itineraryId = await generateItineraryForTrip(jobId ? (await prisma.itineraryJob.findUniqueOrThrow({ where: { id: jobId } })).tripId : "", async (stage, progress) => {
      await update(jobId, { status: stage === "DONE" ? "RUNNING" : "RUNNING", stage, progress });
    });
    await update(jobId, { status: "DONE", stage: "DONE", progress: 100, resultItineraryId: itineraryId });
    const completedJob = await prisma.itineraryJob.findUniqueOrThrow({ where: { id: jobId } });
    await recordEvent({ eventType: "itinerary_generated", entityType: "trip", entityId: completedJob.tripId, payload: { itineraryId, jobId } });
  } catch (error) {
    const known = error instanceof GenerationError;
    await update(jobId, { status: "FAILED", stage: "FAILED", errorCode: known ? error.code : "GENERATION_FAILED", errorMessage: error instanceof Error ? error.message : "일정 생성에 실패했습니다." });
  }
}

export async function getJob(jobId: string) {
  return snapshot(await prisma.itineraryJob.findUnique({ where: { id: jobId } }));
}

export function subscribeJob(jobId: string, listener: (job: JobSnapshot) => void) {
  events.on(jobId, listener);
  return () => events.off(jobId, listener);
}
