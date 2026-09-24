// 자동 도착 — 지금 향하는 장소 가까이에 «머물면» 도착으로 적는다 (S15P21E201-1568).
//
// 🔴 「가까이 갔다」가 아니라 「가까이 머물렀다」로 가른다. 지나가기만 해도 찍히면 사용자는 가지도 않은 곳을
//    다녀온 것으로 남기고, 그 기록이 다음 일정 시각·피드 기록까지 끌고 간다(사용자 결정: 자동 기록 — 2026-09-24).
// 🔴 정확도가 나쁜 위치는 안 쓴다. 실내·지하에서 100m 넘게 튀는 점 하나로 「머묾」이 시작되면 안 된다.
// 화면과 떨어진 계산만 둔다 — 시험이 화면을 띄우지 않고 규칙을 본다.

/** 이 안이면 「그 장소에 있다」. 가게 앞·공원 입구 정도. */
export const ARRIVE_RADIUS_M = 50;
/** 이만큼 머물면 도착. 신호 대기·지나가기는 대개 이보다 짧다. */
export const ARRIVE_DWELL_MS = 2 * 60_000;
/** 이보다 부정확한 위치는 버린다(미터, 기기가 알려 주는 반경). */
export const MAX_ACCURACY_M = 100;

export type Fix = { latitude: number; longitude: number; accuracy: number | null; at: number };
export type Target = { id: string; latitude: number; longitude: number };
/** 지금 머물고 있는 장소와 들어온 시각. 안 머물면 null. */
export type Dwell = { stopId: string; since: number } | null;

/** 두 점 사이의 큰원 거리(m). */
export function distanceM(aLat: number, aLng: number, bLat: number, bLng: number): number {
  const rad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = rad(bLat - aLat);
  const dLng = rad(bLng - aLng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(aLat)) * Math.cos(rad(bLat)) * Math.sin(dLng / 2) ** 2;
  return 6_371_000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

/** 이 위치를 믿어도 되나 — 정확도를 모르면(웹 일부) 믿는다. */
export function usableFix(fix: Fix | null): fix is Fix {
  return fix != null && (fix.accuracy == null || fix.accuracy <= MAX_ACCURACY_M);
}

/**
 * 위치 하나를 받아 머묾 상태를 다음으로 넘기고, 도착으로 적을지 답한다.
 * 목표가 바뀌면(도착·건너뛰기로 다음 곳이 되면) 머묾을 새로 센다.
 */
export function stepDwell(dwell: Dwell, fix: Fix | null, target: Target | null): { dwell: Dwell; arrive: boolean } {
  if (!target || !usableFix(fix)) return { dwell: dwell && target && dwell.stopId === target.id ? dwell : null, arrive: false };
  const inside = distanceM(fix.latitude, fix.longitude, target.latitude, target.longitude) <= ARRIVE_RADIUS_M;
  if (!inside) return { dwell: null, arrive: false };
  const since = dwell && dwell.stopId === target.id ? dwell.since : fix.at;
  return { dwell: { stopId: target.id, since }, arrive: fix.at - since >= ARRIVE_DWELL_MS };
}

/** 「남은 거리 350m」·「1.2km」 — 카드에 붙인다. 위치를 못 믿으면 null. */
export function remainingMeters(fix: Fix | null, target: Target | null): number | null {
  if (!target || !usableFix(fix)) return null;
  return Math.round(distanceM(fix.latitude, fix.longitude, target.latitude, target.longitude));
}
