import assert from "node:assert/strict";
import test from "node:test";
import { computeDayPace, computeRhythmProfile, resolveStayMinutes, type PaceItem } from "../src/services/paceLearning.js";

function item(overrides: Partial<PaceItem> & { seqOrder: number }): PaceItem {
  return {
    placeId: `place-${overrides.seqOrder}`,
    nameKo: `장소${overrides.seqOrder}`,
    nameEn: null,
    category: "TOURIST",
    closeTime: "21:00",
    plannedArrival: "10:00",
    stayMinutes: 60,
    travelMinToNext: 20,
    actualArrival: null,
    actualDeparture: null,
    ...overrides,
  };
}

test("실측이 하나도 없으면 아직 시작 전으로 보고 계획을 그대로 예보한다", () => {
  const forecast = computeDayPace([
    item({ seqOrder: 1, plannedArrival: "10:00", stayMinutes: 60 }),
    item({ seqOrder: 2, plannedArrival: "11:20", stayMinutes: 90 }),
  ], 9 * 60);

  assert.equal(forecast.status, "NOT_STARTED");
  assert.equal(forecast.delayMinutes, 0);
  assert.equal(forecast.projectedEndTime, "12:50");
  assert.equal(forecast.plannedEndTime, "12:50");
  assert.equal(forecast.completedCount, 0);
  assert.equal(forecast.atRisk.length, 0);
});

test("머무는 중인 장소의 도착 지연을 남은 일정에 그대로 이어붙인다", () => {
  // 10:00 계획 → 10:35 실제 도착(35분 지연). 체류 60분이면 11:35 출발, 이동 20분이면 11:55 도착.
  const forecast = computeDayPace([
    item({ seqOrder: 1, plannedArrival: "10:00", stayMinutes: 60, travelMinToNext: 20, actualArrival: "10:35" }),
    item({ seqOrder: 2, plannedArrival: "11:20", stayMinutes: 90, travelMinToNext: null }),
  ], 10 * 60 + 40);

  assert.equal(forecast.delayMinutes, 35);
  assert.equal(forecast.status, "BEHIND");
  assert.equal(forecast.currentSeqOrder, 1);
  assert.equal(forecast.nextSeqOrder, 2);
  assert.equal(forecast.projectedArrivals.find((entry) => entry.seqOrder === 2)?.projectedArrival, "11:55");
  assert.equal(forecast.projectedEndTime, "13:25");
});

test("계획보다 오래 머물고 있으면 현재 시각을 출발 기준으로 삼는다", () => {
  // 10:00 도착 + 체류 60분 = 11:00 출발 예정인데, 이미 11:40이면 아직 안 떠난 것이므로 11:40 기준.
  const forecast = computeDayPace([
    item({ seqOrder: 1, plannedArrival: "10:00", stayMinutes: 60, travelMinToNext: 20, actualArrival: "10:00" }),
    item({ seqOrder: 2, plannedArrival: "11:20", stayMinutes: 60, travelMinToNext: null }),
  ], 11 * 60 + 40);

  assert.equal(forecast.projectedArrivals.find((entry) => entry.seqOrder === 2)?.projectedArrival, "12:00");
});

test("영업 종료 전에 도착할 수 없는 장소를 위험 항목으로 알린다", () => {
  const forecast = computeDayPace([
    item({ seqOrder: 1, plannedArrival: "17:00", stayMinutes: 60, travelMinToNext: 30, actualArrival: "18:30", actualDeparture: "19:50" }),
    item({ seqOrder: 2, plannedArrival: "18:30", stayMinutes: 60, closeTime: "20:00", travelMinToNext: null }),
  ], 19 * 60 + 50);

  assert.equal(forecast.status, "AT_RISK");
  assert.equal(forecast.atRisk.length, 1);
  assert.equal(forecast.atRisk[0].seqOrder, 2);
  assert.equal(forecast.atRisk[0].projectedArrival, "20:20");
  assert.equal(forecast.atRisk[0].marginMinutes, -20);
});

test("지연이 오차 범위이면 정시로 판정하고, 모두 마치면 완료로 본다", () => {
  const onTime = computeDayPace([
    item({ seqOrder: 1, plannedArrival: "10:00", stayMinutes: 60, travelMinToNext: 10, actualArrival: "10:05", actualDeparture: "11:03" }),
    item({ seqOrder: 2, plannedArrival: "11:10", stayMinutes: 60, travelMinToNext: null }),
  ], 11 * 60 + 5);
  assert.equal(onTime.status, "ON_TIME");
  assert.ok(Math.abs(onTime.delayMinutes) < 10);

  const done = computeDayPace([
    item({ seqOrder: 1, plannedArrival: "10:00", stayMinutes: 60, actualArrival: "10:00", actualDeparture: "11:00" }),
  ], 11 * 60 + 30);
  assert.equal(done.status, "DONE");
  assert.equal(done.completedCount, 1);
  assert.equal(done.nextSeqOrder, null);
});

test("계획보다 빨리 움직이면 음수 지연으로 표시한다", () => {
  const forecast = computeDayPace([
    item({ seqOrder: 1, plannedArrival: "10:00", stayMinutes: 60, travelMinToNext: 20, actualArrival: "09:40", actualDeparture: "10:30" }),
    item({ seqOrder: 2, plannedArrival: "11:20", stayMinutes: 60, travelMinToNext: null }),
  ], 10 * 60 + 30);

  assert.equal(forecast.delayMinutes, -30);
  assert.equal(forecast.status, "ON_TIME");
});

test("카테고리별 실측 체류시간으로 리듬 계수를 만든다", () => {
  const profile = computeRhythmProfile([
    { category: "CAFE", plannedStayMinutes: 45, actualStayMinutes: 90 },
    { category: "CAFE", plannedStayMinutes: 45, actualStayMinutes: 80 },
    { category: "TOURIST", plannedStayMinutes: 60, actualStayMinutes: 62 },
    { category: "TOURIST", plannedStayMinutes: 60, actualStayMinutes: 58 },
  ]);

  assert.equal(profile.hasProfile, true);
  // 카페는 계획의 약 1.9배 → 계수 생성
  assert.ok(profile.scale.CAFE > 1.7 && profile.scale.CAFE <= 2.0);
  // 관광지는 계획과 거의 같으므로 굳이 보정하지 않는다
  assert.equal(profile.scale.TOURIST, undefined);
  assert.equal(profile.observations[0].category, "CAFE");
  assert.equal(profile.observations[0].deltaMinutes, 40);
});

test("표본이 부족한 카테고리는 계수를 만들지 않는다", () => {
  const profile = computeRhythmProfile([{ category: "CAFE", plannedStayMinutes: 45, actualStayMinutes: 200 }]);
  assert.equal(profile.hasProfile, false);
  assert.equal(profile.scale.CAFE, undefined);
  assert.equal(profile.totalSamples, 1);
});

test("이상치가 있어도 리듬 계수를 허용 범위 안으로 가둔다", () => {
  const profile = computeRhythmProfile([
    { category: "CAFE", plannedStayMinutes: 30, actualStayMinutes: 600 },
    { category: "CAFE", plannedStayMinutes: 30, actualStayMinutes: 540 },
    { category: "RESTAURANT", plannedStayMinutes: 90, actualStayMinutes: 5 },
    { category: "RESTAURANT", plannedStayMinutes: 90, actualStayMinutes: 8 },
  ]);

  assert.equal(profile.scale.CAFE, 2);
  assert.equal(profile.scale.RESTAURANT, 0.6);
});

test("자정을 넘긴 체류도 음수가 되지 않는다", () => {
  assert.equal(resolveStayMinutes("10:00", "11:30"), 90);
  assert.equal(resolveStayMinutes("23:30", "00:45"), 75);
});
