#!/usr/bin/env node
/**
 * 페이스 러닝 엔드투엔드 스모크 테스트.
 * 여행 생성 → 일정 생성 → 실측 기록 → 지연 예보 → 남은 일정 재계산 → 리듬 프로필까지
 * 실제 HTTP 로 한 번 훑는다. 단위 테스트가 못 잡는 배선 오류를 확인하는 용도다.
 */
const BASE = process.env.SMOKE_BASE ?? "http://localhost:4000";

let token = "";
async function call(method, path, body) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { "X-Session-Token": token } : {}),
      ...(method === "POST" ? { "Idempotency-Key": crypto.randomUUID() } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const text = await res.text();
  let parsed;
  try { parsed = JSON.parse(text); } catch { parsed = text; }
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${JSON.stringify(parsed)}`);
  return parsed;
}

function today(offset = 0) {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

const step = (label) => process.stdout.write(`\n▸ ${label}\n`);

const session = await call("POST", "/api/auth/anonymous", { locale: "KO" });
token = session.token ?? session.sessionToken;
step(`익명 세션 발급 ${token ? "OK" : "실패"}`);

// 오늘 출발하는 2박 3일 여행이어야 지연 예보가 켜진다.
const trip = await call("POST", "/api/trips", {
  origin: "부산역", originLat: 35.1152, originLng: 129.0403,
  startDate: today(0), endDate: today(2),
  partySize: 2, adultCount: 2, childCount: 0, totalBudget: 300000,
  hasCar: false, pace: "NORMAL",
  tasteTags: ["food", "cafe", "hidden_local"],
  recommendationMode: "LOCAL", dayStart: "09:30", dayEnd: "20:00",
  maxWalkingKm: 100, language: "KO",
  allergies: [], dietType: "NONE", desiredFoods: [],
  mustVisitPlaceIds: [], mustVisitAssignments: [],
  landmarkRatio: 10, localRatio: 80, easyRatio: 10,
});
step(`여행 생성 tripId=${trip.tripId}`);

await call("POST", `/api/trips/${trip.tripId}/itinerary`);
const itinerary = await call("GET", `/api/trips/${trip.tripId}/itinerary`);
const day1 = itinerary.days[0];
step(`일정 생성 itineraryId=${itinerary.itineraryId} · 1일차 ${day1.items.length}곳`);
day1.items.forEach((item) => console.log(`   ${item.seqOrder}. ${item.nameKo} ${item.plannedArrival} (${item.stayMinutes}분, ${item.category})`));

if (day1.items.length < 2) { console.log("\n⚠ 1일차 항목이 2곳 미만이라 재계산 검증을 건너뜁니다."); process.exit(0); }

// 1번 장소에 계획보다 40분 늦게 도착하고, 계획보다 30분 더 오래 머문 상황을 만든다.
const first = day1.items[0];
const plannedMin = Number(first.plannedArrival.slice(0, 2)) * 60 + Number(first.plannedArrival.slice(3, 5));
const fmt = (m) => `${String(Math.floor(m / 60) % 24).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`;
const lateArrival = fmt(plannedMin + 40);
const lateDeparture = fmt(plannedMin + 40 + first.stayMinutes + 30);

await call("POST", `/api/itineraries/${itinerary.itineraryId}/items/${first.itemId}/progress`, { arrivedAt: lateArrival });
step(`실측 도착 기록 ${first.nameKo} 계획 ${first.plannedArrival} → 실제 ${lateArrival}`);

const behind = await call("GET", `/api/itineraries/${itinerary.itineraryId}/pace?dayIndex=1&now=${lateArrival}`);
step(`지연 예보 status=${behind.status} delay=${behind.delayMinutes}분 종료예상=${behind.projectedEndTime} (계획 ${behind.plannedEndTime}) 위험=${behind.atRisk.length}건`);
behind.atRisk.forEach((risk) => console.log(`   ⚠ ${risk.nameKo}: ${risk.projectedArrival} 도착 · ${risk.closeTime} 마감 (${risk.marginMinutes}분)`));

await call("POST", `/api/itineraries/${itinerary.itineraryId}/items/${first.itemId}/progress`, { departedAt: lateDeparture });
step(`실측 출발 기록 → 체류 ${first.stayMinutes}분 계획 대비 실제 ${first.stayMinutes + 30}분`);

const replan = await call("POST", `/api/itineraries/${itinerary.itineraryId}/days/1/replan`, {
  currentTime: lateDeparture, lat: first.lat, lng: first.lng, strategy: "DROP_ONE",
});
step(`남은 일정 재계산 보존=${replan.preservedCount} 교체=${replan.replacedCount} 제외=${replan.droppedCount} 리듬적용=${replan.rhythmApplied}`);

const after = await call("GET", `/api/trips/${trip.tripId}/itinerary`);
const newDay1 = after.days[0];
console.log("   재계산 후 1일차:");
newDay1.items.forEach((item) => console.log(`   ${item.seqOrder}. ${item.nameKo} ${item.plannedArrival}${item.actualArrival ? ` [실제 ${item.actualArrival} 방문완료]` : ""}`));

const preservedOk = newDay1.items[0]?.placeId === first.placeId && newDay1.items[0]?.actualArrival === lateArrival;
console.log(`\n   다녀온 장소 보존: ${preservedOk ? "✔ 유지됨" : "✖ 유실됨"}`);

const rhythm1 = await call("GET", `/api/trips/${trip.tripId}/rhythm`);
step(`리듬 프로필(표본 1건) hasProfile=${rhythm1.hasProfile} 표본=${rhythm1.totalSamples} — 계수 생성 최소 2건`);

// 같은 카테고리 실측을 2건 이상 만들어 리듬 계수가 실제로 생기는지 확인한다.
const cafeStops = after.days.flatMap((day) => day.items).filter((item) => item.category === "CAFE" && !item.actualStayMinutes);
let recorded = 0;
for (const stop of cafeStops.slice(0, 2)) {
  const base = Number(stop.plannedArrival.slice(0, 2)) * 60 + Number(stop.plannedArrival.slice(3, 5));
  await call("POST", `/api/itineraries/${itinerary.itineraryId}/items/${stop.itemId}/progress`, {
    arrivedAt: fmt(base),
    departedAt: fmt(base + Math.round(stop.stayMinutes * 1.9)), // 계획의 1.9배를 머문 사람
  });
  recorded++;
}
step(`카페 실측 ${recorded}건 추가 기록 (계획 대비 1.9배 체류)`);

const rhythm2 = await call("GET", `/api/trips/${trip.tripId}/rhythm`);
console.log(`   hasProfile=${rhythm2.hasProfile} 표본=${rhythm2.totalSamples} 계수=${JSON.stringify(rhythm2.scale)}`);
rhythm2.observations.forEach((o) => console.log(`   ${o.category}: 계획 ${o.averagePlannedMinutes}분 → 실제 ${o.averageActualMinutes}분 (${o.deltaMinutes > 0 ? "+" : ""}${o.deltaMinutes}분, ×${o.scale})`));

const rhythmOk = recorded < 2 || (rhythm2.hasProfile && rhythm2.scale.CAFE > 1);
console.log(`   리듬 학습: ${rhythmOk ? "✔ 계수 생성됨" : "✖ 계수 미생성"}`);

const ok = preservedOk && rhythmOk;
console.log(`\n${ok ? "✅" : "❌"} 페이스 러닝 스모크 완료\n`);
process.exit(ok ? 0 : 1);
