// 앱에서 일어난 행동을 서버 이벤트 표로 보내는 **유일한** 자리.
//
// 화면마다 fetch 를 흩뿌리면 동의 검사·실패 처리·시각 형식이 화면 수만큼 갈라지고,
// 그중 하나만 동의를 안 보고도 아무도 모른다. 그래서 입구를 하나로 둔다.
//
// 규칙 셋 — 셋 다 협상 대상이 아니다.
//   ① 동의가 꺼져 있으면 **아무것도 보내지 않는다**(src/personalization/behaviorConsent.ts).
//   ② 전송은 화면을 기다리게 하지 않는다. 저장 버튼은 즉시 반응하고 이벤트는 뒤에서 간다.
//   ③ 전송 실패가 사용자에게 보이지 않는다. 후기를 못 남긴 것이 아니라 통계가 한 건 빈 것이다.
//
// 🔴 지금 이 요청들은 서버가 받아 주지 않는다. 앱 잘못이 아니라 백엔드 계약이 아직
//    이 행동들을 못 받는 상태다. 확인한 것 셋(origin/back/dev, 2026-09-07):
//
//    1. `requestId` 가 필수다(IngestEventRequest 의 @NotNull, EventIngestService 가 다시 검사).
//       그런데 추천 요청의 정본 키(request_id)를 앱에 알려 주는 응답이 하나도 없다 —
//       RecommendationJobResponse·RecommendationResultResponse 둘 다 jobId 만 준다.
//       🔴 그렇다고 앱이 UUID 를 하나 지어내면 안 된다. 그 값은 "노출과 행동을 잇는"
//       분석 키(API-07)라서, 아무 값이나 넣으면 조인이 되는 척하면서 틀린다.
//    2. place_like · place_dislike · place_visit 는 EventType 에서 producer 가 SERVER 다.
//       클라이언트가 보내면 EventIngestService 가 거부한다(DR-13). 서버가 이 이벤트를
//       내려면 "장소 저장" · "체크인 후기" 업무 API 가 있어야 하는데 그 API 가 없다.
//    3. 여행에 붙는 이벤트(aggregate 축 TRIP)는 tripId 없이는 적히지 않는다.
//       홈 하트·장소 상세는 여행 밖 화면이라 지금 줄 tripId 가 없다.
//
//    그래서 이 모듈은 **아는 것만 담아 보내고 결과를 삼킨다.** 위 셋 중 하나라도 풀리면
//    화면 코드는 한 줄도 안 고치고 그대로 실려 나간다.
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
   * 🔴 자유 입력·좌표·연락처를 넣지 않는다. 서버의 SensitivePayloadGuard 가 막기도 하지만,
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
 * ISO-8601 + **기기의 시간대**. `toISOString()` 은 항상 UTC(`Z`) 라서 부산 09시와 런던 09시가
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
