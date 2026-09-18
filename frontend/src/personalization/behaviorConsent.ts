// 행동 기반 개인화 동의 — 기본 OFF. 이 값 하나가 이벤트 전송의 스위치다.
import { useCallback, useEffect, useState } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { getMyConsents, updateMyConsents } from '@/auth/authApi';

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
    await updateMyConsents(accessToken, { BEHAVIOR_PERSONALIZATION: enabled });
  } catch {
    // 서버에 못 남겨도 기기의 선택은 지킨다. 끄는 쪽이 기기에서 이미 적용됐으므로
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
    const dto = await getMyConsents(accessToken);
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
