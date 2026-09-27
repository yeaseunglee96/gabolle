/**
 * 내 위치 한 번 읽기 — 끝없이 기다리지 않는다 (S15P21E201-1824).
 *
 * `getCurrentPositionAsync` 는 GPS 가 안 잡히는 실내·지하에서 **답을 안 하고 계속 기다린다.**
 * 그동안 화면은 「내 위치 찾는 중…」에 멈춰 있고, 사람은 앱이 멈춘 줄 안다.
 *
 * 그래서 두 가지를 한다.
 * 1. 기기가 최근(기본 2분 안)에 이미 알던 위치(`getLastKnownPositionAsync`)가 있으면 그것을 바로 쓴다.
 * 2. 없으면 새로 묻되 8초만 기다린다. 넘기면 던진다 — 부르는 쪽은 이미 `catch` 에서
 *    「위치를 못 쓴다(권한 거부와 같은 길)」로 처리하므로, 거기로 떨어진다.
 */
import * as Location from 'expo-location';

export const POSITION_TIMEOUT_MS = 8000;
export const LAST_KNOWN_MAX_AGE_MS = 2 * 60 * 1000;

export class PositionTimeoutError extends Error {
  constructor() { super('position timeout'); this.name = 'PositionTimeoutError'; }
}

export async function readCurrentPosition(
  { timeoutMs = POSITION_TIMEOUT_MS, maxAgeMs = LAST_KNOWN_MAX_AGE_MS }: { timeoutMs?: number; maxAgeMs?: number } = {},
): Promise<Location.LocationObject> {
  try {
    const last = await Location.getLastKnownPositionAsync({ maxAge: maxAgeMs });
    if (last && Date.now() - last.timestamp <= maxAgeMs) return last;
  } catch {
    // 최근 위치를 못 읽어도 새로 묻는 길은 남아 있다.
  }
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<never>((_, reject) => { timer = setTimeout(() => reject(new PositionTimeoutError()), timeoutMs); });
  try {
    return await Promise.race([Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }), timeout]);
  } finally {
    if (timer) clearTimeout(timer);
  }
}
