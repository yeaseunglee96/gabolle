// 행동 기반 개인화 동의 — 기본 OFF. 이 값 하나가 이벤트 전송의 스위치다.
//
// 개인정보 처리방침은 이미 "행동 기반 개인화 … 별도 동의를 받은 경우에만 처리합니다" 라고
// 선언해 뒀는데(src/legal/legalContent.ts 1절), 그 동의를 받을 화면이 어디에도 없었다.
// 동의 없이 행동을 보내면 우리가 우리 방침을 어긴다. 그래서 이 모듈이 먼저다.
//
// 🔴 값은 이 기기에만 남는다. 서버에 올릴 API 가 없다 —
//    가입 요청(POST /api/v1/auth/signup · /auth/oauth/signup)의
//    behaviorPersonalizationEnabled 말고는 이 동의를 바꿀 수 있는 경로가 백엔드에 없고
//    (PATCH /api/v1/auth/me 는 displayName·language 만 받는다), 조회 응답에도 없다.
//    백엔드에 동의 변경·조회 API 가 생기면 setBehaviorConsent 안에서 같이 부르면 된다.
//
// 🔴 읽기 전에는 무조건 꺼진 것으로 본다. 저장소를 못 읽는 상황(초기화 전·기기 오류)에서
//    "아마 켜져 있었겠지" 로 넘어가면 동의 없이 보내게 된다. 모르면 안 보낸다.
import { useCallback, useEffect, useState } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

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

export async function setBehaviorConsent(enabled: boolean): Promise<void> {
  cached = enabled;
  listeners.forEach((listener) => listener(enabled));
  try {
    await AsyncStorage.setItem(BEHAVIOR_CONSENT_KEY, enabled ? 'true' : 'false');
  } catch {
    // 저장에 실패해도 이번 실행 동안의 선택은 지킨다. 다음 실행에서 다시 OFF 로 시작한다.
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
export function useBehaviorConsent() {
  const [enabled, setEnabled] = useState(false);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    let active = true;
    void loadBehaviorConsent().then((value) => {
      if (!active) return;
      setEnabled(value);
      setReady(true);
    });
    const unsubscribe = subscribeBehaviorConsent(setEnabled);
    return () => {
      active = false;
      unsubscribe();
    };
  }, []);
  const change = useCallback((next: boolean) => {
    setEnabled(next);
    void setBehaviorConsent(next);
  }, []);
  return { enabled, ready, setEnabled: change };
}
