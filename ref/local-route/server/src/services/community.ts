import { createHash, randomBytes } from "node:crypto";
import { haversineDistanceM } from "./kakao.js";

export const VISIT_RADIUS_M = 150;
export const MIN_DWELL_MINUTES = 15;

export function issueSessionToken(): { token: string; tokenHash: string } {
  const token = randomBytes(32).toString("base64url");
  return { token, tokenHash: hashSessionToken(token) };
}

export function hashSessionToken(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}

export type VisitDecision = {
  verified: boolean;
  distanceM: number;
  dwellMinutes: number;
  rejectionCode: "OUTSIDE_RADIUS" | "DWELL_TOO_SHORT" | "INVALID_TIME_RANGE" | null;
};

export function evaluateVisit(input: {
  placeLat: number;
  placeLng: number;
  latitude: number;
  longitude: number;
  arrivedAt: Date;
  departedAt: Date;
}): VisitDecision {
  const distanceM = Math.round(haversineDistanceM(input.placeLat, input.placeLng, input.latitude, input.longitude));
  const durationMs = input.departedAt.getTime() - input.arrivedAt.getTime();
  const dwellMinutes = Math.max(0, Math.floor(durationMs / 60_000));
  if (!Number.isFinite(durationMs) || durationMs <= 0) {
    return { verified: false, distanceM, dwellMinutes, rejectionCode: "INVALID_TIME_RANGE" };
  }
  if (distanceM > VISIT_RADIUS_M) {
    return { verified: false, distanceM, dwellMinutes, rejectionCode: "OUTSIDE_RADIUS" };
  }
  if (dwellMinutes < MIN_DWELL_MINUTES) {
    return { verified: false, distanceM, dwellMinutes, rejectionCode: "DWELL_TOO_SHORT" };
  }
  return { verified: true, distanceM, dwellMinutes, rejectionCode: null };
}

export function calculateLocalGrade(stats: {
  verifiedVisitCount: number;
  uniquePlaceCount: number;
  repeatVisitCount: number;
  reviewCount: number;
}): { grade: "NEWCOMER" | "EXPLORER" | "LOCAL" | "GUIDE"; trustScore: number } {
  const trustScore = Math.min(100, Math.round(
    stats.uniquePlaceCount * 4 + stats.repeatVisitCount * 6 + stats.reviewCount * 3
  ));
  if (stats.uniquePlaceCount >= 20 && stats.repeatVisitCount >= 5 && stats.reviewCount >= 10) return { grade: "GUIDE", trustScore };
  if (stats.uniquePlaceCount >= 8 && stats.repeatVisitCount >= 2) return { grade: "LOCAL", trustScore };
  if (stats.uniquePlaceCount >= 3) return { grade: "EXPLORER", trustScore };
  return { grade: "NEWCOMER", trustScore };
}

export function calculateCommunityLocalScore(input: {
  verifiedVisitorCount: number;
  verifiedVisitCount: number;
  repeatVisitorCount: number;
  averageRating: number | null;
  reviewCount: number;
}): number | null {
  if (input.verifiedVisitCount < 3) return null;
  const visitorSignal = Math.min(1, Math.log1p(input.verifiedVisitorCount) / Math.log(21));
  const repeatRate = input.verifiedVisitorCount > 0 ? Math.min(1, input.repeatVisitorCount / input.verifiedVisitorCount) : 0;
  const rawRating = input.averageRating === null ? 0.6 : Math.max(0, Math.min(1, input.averageRating / 5));
  const confidence = Math.min(1, input.reviewCount / 10);
  const ratingSignal = 0.6 * (1 - confidence) + rawRating * confidence;
  return Math.round((visitorSignal * 0.4 + repeatRate * 0.25 + ratingSignal * 0.35) * 1000) / 1000;
}
