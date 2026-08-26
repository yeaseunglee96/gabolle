import test from "node:test";
import assert from "node:assert/strict";
import { calculateCommunityLocalScore, calculateLocalGrade, evaluateVisit, hashSessionToken, issueSessionToken } from "../src/services/community.js";

test("anonymous session tokens are random and only their hashes need storage", () => {
  const first = issueSessionToken();
  const second = issueSessionToken();
  assert.notEqual(first.token, second.token);
  assert.equal(first.tokenHash, hashSessionToken(first.token));
  assert.equal(first.tokenHash.length, 64);
});

test("visit verification enforces radius and dwell time", () => {
  const base = { placeLat: 35.115, placeLng: 129.04, latitude: 35.1151, longitude: 129.0401, arrivedAt: new Date("2026-08-24T01:00:00Z") };
  assert.equal(evaluateVisit({ ...base, departedAt: new Date("2026-08-24T01:20:00Z") }).verified, true);
  assert.equal(evaluateVisit({ ...base, latitude: 35.12, departedAt: new Date("2026-08-24T01:20:00Z") }).rejectionCode, "OUTSIDE_RADIUS");
  assert.equal(evaluateVisit({ ...base, departedAt: new Date("2026-08-24T01:05:00Z") }).rejectionCode, "DWELL_TOO_SHORT");
});

test("local grade rewards breadth, repeat visits, and reviews", () => {
  assert.equal(calculateLocalGrade({ verifiedVisitCount: 12, uniquePlaceCount: 8, repeatVisitCount: 3, reviewCount: 4 }).grade, "LOCAL");
  assert.equal(calculateLocalGrade({ verifiedVisitCount: 30, uniquePlaceCount: 20, repeatVisitCount: 6, reviewCount: 12 }).grade, "GUIDE");
});

test("community local score requires enough verified visits and stays normalized", () => {
  assert.equal(calculateCommunityLocalScore({ verifiedVisitorCount: 1, verifiedVisitCount: 2, repeatVisitorCount: 0, averageRating: 5, reviewCount: 1 }), null);
  const score = calculateCommunityLocalScore({ verifiedVisitorCount: 12, verifiedVisitCount: 20, repeatVisitorCount: 6, averageRating: 4.5, reviewCount: 12 });
  assert.ok(score !== null && score > 0.7 && score <= 1);
});
