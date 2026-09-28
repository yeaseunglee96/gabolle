// 위치 이용 동의 — 위치를 쓰는 곳 다섯(둘러보기·교통·피드의 「내 주변」, 여행 지도의 내 위치 점, 여행 중 자동 도착·출발)을
// 동의 하나(서버의 PRECISE_LOCATION)로 덮는다 — S15P21E201-1691, 사용자 결정.
//
// 🔴 처리방침은 「정밀 위치정보는 별도 동의」인데 동의 기록이 없었다. 위치는 기기(OS) 권한만 묻고 읽었다.
// 🔴 셋을 가른다: 물은 적 없음(null) · 동의(true) · 거절(false). 거절도 서버에 남긴다 —
//    기록이 없는 것(안 물었다)과 REVOKED(거절했다)는 다른 사실이다.
// 🔴 동의가 없으면 위치를 아예 읽지 않는다. 기기 권한 창도 띄우지 않는다.
// 🔴 로그인했으면 계정의 기록(서버)이 정본이고, 기기 값은 먼저 그리기 위한 사본이다.
//    서버에 못 남기면 기기 값만 바뀌고, 다음에 서버 값을 받으면 그것을 따른다.
import { useCallback, useEffect, useState } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { getMyConsents, updateMyConsents, type MyConsents } from '@/auth/authApi';

export type LocationConsent = boolean | null;

const KEY = 'gabolle.location-consent';

let cached: LocationConsent | undefined;
const listeners = new Set<(value: LocationConsent) => void>();

function publish(value: LocationConsent) {
  cached = value;
  listeners.forEach((listener) => listener(value));
}

async function writeDevice(value: boolean) {
  try { await AsyncStorage.setItem(KEY, value ? 'true' : 'false'); } catch { /* 못 적어도 이번 실행 동안은 지킨다 */ }
}

/** 이 기기에 적어 둔 답. 못 읽으면 「물은 적 없음」— 모르는 것을 동의로 읽지 않는다. */
export async function loadLocationConsent(): Promise<LocationConsent> {
  if (cached !== undefined) return cached;
  try {
    const raw = await AsyncStorage.getItem(KEY);
    cached = raw === 'true' ? true : raw === 'false' ? false : null;
  } catch {
    cached = null;
  }
  return cached;
}

/** 서버 동의 목록에서 위치 동의를 읽는다. 기록이 없으면 null(물은 적 없음). */
export function readLocationConsent(dto: Pick<MyConsents, 'consents'> | null | undefined): LocationConsent {
  const item = dto?.consents?.find((consent) => consent.consentType === 'PRECISE_LOCATION');
  return item ? item.status === 'GRANTED' : null;
}

/** 답을 적는다 — 기기와(로그인했으면) 계정 둘 다. */
export async function setLocationConsent(granted: boolean, accessToken: string | null): Promise<void> {
  publish(granted);
  await writeDevice(granted);
  if (!accessToken) return;
  try {
    // PATCH 다 — 보낸 항목만 바뀐다(맞춤 추천·건강 제약 동의는 그대로).
    await updateMyConsents(accessToken, { PRECISE_LOCATION: granted });
  } catch {
    // 계정에 못 남겨도 기기의 답은 지킨다. 다음에 서버 값을 받으면 그것을 따른다.
  }
}

/**
 * 계정의 기록과 맞춘다. 서버에 기록이 있으면 그것을 따르고, 없으면 이 기기의 답을 쓴다 —
 * 이 기기에서 답한 것이 있으면(로그인 전에 답했거나 전에 못 보냈으면) 그 답을 계정에 남긴다.
 */
export async function syncLocationConsent(accessToken: string | null): Promise<LocationConsent> {
  const device = await loadLocationConsent();
  if (!accessToken) return device;
  try {
    const server = readLocationConsent(await getMyConsents(accessToken));
    if (server === null) {
      if (device !== null) void updateMyConsents(accessToken, { PRECISE_LOCATION: device }).catch(() => undefined);
      return device;
    }
    if (server !== device) {
      publish(server);
      await writeDevice(server);
    }
    return server;
  } catch {
    return device;
  }
}

export function subscribeLocationConsent(listener: (value: LocationConsent) => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

/** 화면이 쓰는 값 — 설정 스위치와 여행 화면이 같은 값을 본다. */
export function useLocationConsent(accessToken: string | null) {
  const [consent, setConsent] = useState<LocationConsent>(cached ?? null);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    let alive = true;
    void syncLocationConsent(accessToken).then((value) => {
      if (!alive) return;
      setConsent(value);
      setReady(true);
    });
    const unsubscribe = subscribeLocationConsent(setConsent);
    return () => { alive = false; unsubscribe(); };
  }, [accessToken]);
  const set = useCallback((granted: boolean) => { void setLocationConsent(granted, accessToken); }, [accessToken]);
  return { consent, ready, set };
}

/** 시험에서 모듈 사본을 비운다. 화면 코드는 부를 일이 없다. */
export function __resetLocationConsentForTests() {
  cached = undefined;
  listeners.clear();
}

/**
 * 서버로 보내는 좌표를 소수 셋째 자리(약 100m)로 줄인다 — 「내 주변」 찾기에는 충분하고,
 * 요청 주소가 서버 접속 기록에 남아도 정확한 자리가 드러나지 않는다(S15P21E201-1691, 조율 세션 결정).
 */
export function coarseCoordinate(value: number): number {
  return Math.round(value * 1000) / 1000;
}
