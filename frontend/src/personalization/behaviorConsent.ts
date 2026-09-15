// 행동 기반 개인화 동의 — 기본 OFF. 이 값 하나가 이벤트 전송의 스위치다.
//
// 개인정보 처리방침은 이미 "행동 기반 개인화 … 별도 동의를 받은 경우에만 처리합니다" 라고
// 선언해 뒀는데(src/legal/legalContent.ts 1절), 그 동의를 받을 화면이 어디에도 없었다.
// 동의 없이 행동을 보내면 우리가 우리 방침을 어긴다. 그래서 이 모듈이 먼저다.
//
// 🔴 2026-09-16 정정 — 여기 있던 "서버에 올릴 API 가 없다" 는 낡았다. `GET`·`PATCH
//    /api/v1/auth/me/consents` 가 있다(S15P21E201-735). 그동안 이 값은 기기에만 남았고
//    서버는 가입 때 정한 값을 그대로 들고 있었다 — 사용자가 껐는데도 서버 쪽 표시는
//    켜진 채였다. 이제 잇는다.
//
// 🔴 서버와 기기가 다를 때 켜는 쪽으로 맞추지 않는다. 서버가 꺼져 있으면 기기도 끈다.
//    서버가 켜져 있어도 기기가 꺼져 있으면 그대로 둔다 — 이 값의 유일한 목적이 "동의 없이
//    보내지 않는 것" 이라, 어긋남을 없애려고 켜면 그 목적을 정면으로 어긴다. 켜는 것은
//    사람이 스위치를 누를 때만 한다.
//
// 🔴 읽기 전에는 무조건 꺼진 것으로 본다. 저장소를 못 읽는 상황(초기화 전·기기 오류)에서
//    "아마 켜져 있었겠지" 로 넘어가면 동의 없이 보내게 된다. 모르면 안 보낸다.
import { useCallback, useEffect, useState } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { apiRequest } from '@/api/client';

const BEHAVIOR_CONSENT_KEY = 'gabolle.behavior-personalization';

let cached: boolean | null = null;
const listeners = new Set<(enabled: boolean) => void>();

/** 저장된 동의 값. 못 읽으면 false — "모른다" 를 "켜져 있다" 로 읽지 않는다. */
export async function loadBehaviorConsent(): Promise<boolean> {
  if (cached !== null) return cached;
  try {
    cached = (await AsyncStorage.getItem(BEHAVIOR_CONSENT_KEY)) === 'true';
  } catch {
    cached = false;
  }
  return cached;
}

export async function setBehaviorConsent(enabled: boolean, accessToken?: string | null): Promise<void> {
  cached = enabled;
  listeners.forEach((listener) => listener(enabled));
  try {
    await AsyncStorage.setItem(BEHAVIOR_CONSENT_KEY, enabled ? 'true' : 'false');
  } catch {
    // 저장에 실패해도 이번 실행 동안의 선택은 지킨다. 다음 실행에서 다시 OFF 로 시작한다.
  }
  if (!accessToken) return;
  try {
    // PATCH 다 — 보낸 항목만 바뀐다. 다른 동의(정밀 위치·건강 제약)를 같이 실어 보내면
    // 화면에 없는 그 동의들이 요청마다 조용히 덮인다.
    await apiRequest<unknown>('/api/v1/auth/me/consents', {
      method: 'PATCH', accessToken, body: { consents: { BEHAVIOR_PERSONALIZATION: enabled } },
    });
  } catch {
    // 서버에 못 남겨도 기기의 선택은 지킨다. 🔴 끄는 쪽이 기기에서 이미 적용됐으므로
    // 이벤트는 더 안 나간다 — 실패가 "동의 없이 보내는" 방향으로는 기울지 않는다.
  }
}

/** 서버가 꺼져 있다고 하면 기기도 끈다. 그 반대는 하지 않는다(위 머리말의 규칙). */
export function reconcileConsent(deviceEnabled: boolean, serverEnabled: boolean): boolean {
  return deviceEnabled && serverEnabled;
}

export async function syncBehaviorConsentFromServer(accessToken: string | null): Promise<boolean> {
  const device = await loadBehaviorConsent();
  if (!accessToken) return device;
  try {
    const dto = await apiRequest<{ behaviorPersonalizationEnabled: boolean }>('/api/v1/auth/me/consents', { accessToken });
    const next = reconcileConsent(device, dto.behaviorPersonalizationEnabled);
    if (next !== device) await setBehaviorConsent(next);
    return next;
  } catch {
    // 못 물어봤으면 기기 값을 그대로 쓴다 — 모른다고 켜지 않는다.
    return device;
  }
}

export function subscribeBehaviorConsent(listener: (enabled: boolean) => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/**
 * 토글 화면용. `ready` 가 false 인 동안은 아직 저장된 값을 못 읽은 상태다 —
 * 그때 스위치를 보여 주면 켜 둔 사람에게 꺼진 것처럼 보였다가 튄다.
 */
export function useBehaviorConsent(accessToken?: string | null) {
  const [enabled, setEnabled] = useState(false);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    let active = true;
    // 로그인해 있으면 서버 값도 본다. 서버가 꺼져 있으면 기기도 꺼진다(켜지는 일은 없다).
    void syncBehaviorConsentFromServer(accessToken ?? null).then((value) => {
      if (!active) return;
      setEnabled(value);
      setReady(true);
    });
    const unsubscribe = subscribeBehaviorConsent(setEnabled);
    return () => {
      active = false;
      unsubscribe();
    };
  }, [accessToken]);
  const change = useCallback((next: boolean) => {
    setEnabled(next);
    void setBehaviorConsent(next, accessToken ?? null);
  }, [accessToken]);
  return { enabled, ready, setEnabled: change };
}
