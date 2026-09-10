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
// 🔴 **정정 (2026-09-10) — 아래 셋은 전부 풀렸다. 이제 서버가 받는다.**
//
//    2026-09-07 에 이 자리에 "지금 이 요청들은 서버가 받아 주지 않는다" 고 적었다.
//    그때는 사실이었고 지금은 아니다. 백엔드 S15P21E201-735 가 origin/back/dev 로
//    들어가면서 셋을 다 풀었다 (2026-09-10 에 백엔드 코드에서 직접 확인):
//
//    ① requestId 가 더 이상 전부 필수가 아니다 — IngestEventRequest 의 requestId 에서
//       @NotNull 이 빠졌고, 구조적으로 필요한 이벤트(aggregate 축이 추천 요청인 것)
//       에서만 요구한다. 추천 작업·결과 응답도 이제 requestId 를 싣는다.
//    ② place_like · place_dislike · place_visit 에 허용 producer 목록이 붙었고
//       거기에 CLIENT 가 있다 (EventType.java). 앱이 보내도 거부되지 않는다.
//    ③ place_view · place_like 의 aggregate 축이 TRIP 에서 USER 로 옮겨졌다.
//       홈 하트·장소 상세처럼 여행 밖 화면도 tripId 없이 적힌다.
//
//    백엔드에 **앱이 보내는 본문을 그대로 복사해 넣은 검사**(AppEventContractTest)가
//    있고, 그것이 "받는다(202)" 를 못 박고 있다.
//
//    🔴 낡은 경고를 지우지 않고 정정으로 남기는 이유: 이 주석 때문에 며칠 동안
//    아무도 이 코드를 내보내지 않았다. 문서가 **비관 쪽으로 틀린 것**이 버그보다
//    나쁘다 — 읽은 사람이 시도조차 안 한다. 다음 사람이 같은 것을 다시 재지 않게 남긴다.
//
//    아직 남은 것 (경고가 아니라 할 일):
//      · 노출(recommendation_impression)을 보내는 코드가 없다. 지금은 보낼 수 있다.
//      · "지도 앱 열기" 는 서버 EventType 에 이름 자체가 없다. 만드는 것은 서버 쪽 일이다.
//      · requestId 를 모를 때는 여전히 **비운다.** 지어내면 조인이 되는 척하면서 틀린다.
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
