// 앱에서 일어난 행동을 서버 이벤트 표로 보내는 유일한 자리.
import * as Crypto from 'expo-crypto';

import { apiRequest } from '@/api/client';
import { loadBehaviorConsent } from '@/personalization/behaviorConsent';

/** 서버 EventType 의 소문자 이름(wireName) 그대로. 없는 이름을 지어내지 않는다. */
export type AppEventType = 'place_like' | 'place_dislike' | 'place_view' | 'place_visit';

export type AppEventInput = {
  type: AppEventType;
  /** 서버가 인증을 요구한다 — 이벤트의 주체를 인증에서만 읽는다. 없으면 안 보낸다. */
  accessToken: string | null;
  tripId?: string | null;
  requestId?: string | null;
  /**
   * 자유 입력·좌표·연락처를 넣지 않는다. 서버의 SensitivePayloadGuard 가 막기도 하지만
   * 막히기 전에 안 담는 것이 맞다 — 이벤트 표는 지우기 어려운 자리다.
   */
  payload?: Record<string, unknown>;
};

/** 부르고 즉시 돌아온다. 절대 던지지 않는다. */
export function sendAppEvent(input: AppEventInput): void {
  void deliver(input).catch(() => undefined);
}

async function deliver(input: AppEventInput): Promise<void> {
  if (!input.accessToken) return;
  if (!(await loadBehaviorConsent())) return;
  await apiRequest<unknown>('/api/v1/events', {
    method: 'POST',
    accessToken: input.accessToken,
    // 이벤트가 401 을 받았다고 사용자를 로그아웃시키지 않는다.
    skipUnauthorizedHandling: true,
    body: {
      eventId: Crypto.randomUUID(),
      eventType: input.type,
      eventVersion: 1,
      tripId: input.tripId ?? undefined,
      requestId: input.requestId ?? undefined,
      occurredAt: isoWithLocalOffset(new Date()),
      payload: input.payload,
    },
  });
}

/**
 * ISO-8601 + 기기의 시간대. `toISOString` 은 항상 UTC(`Z`) 라서 부산 09시와 런던 09시가
 * 같은 문자열이 된다 — 여행 이벤트에서 몇 시에 일어난 일인가는 나중에 복원할 수 없다.
 */
function isoWithLocalOffset(now: Date): string {
  const pad = (value: number, size = 2) => String(value).padStart(size, '0');
  const offsetMinutes = -now.getTimezoneOffset();
  const sign = offsetMinutes < 0 ? '-' : '+';
  const absolute = Math.abs(offsetMinutes);
  const date = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  const time = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}.${pad(now.getMilliseconds(), 3)}`;
  return `${date}T${time}${sign}${pad(Math.floor(absolute / 60))}:${pad(absolute % 60)}`;
}
