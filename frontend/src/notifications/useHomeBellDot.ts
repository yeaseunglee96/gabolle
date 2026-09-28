// 폰 홈 종의 점 — 안 본 여행 활동이 있나(S15P21E201-1380). 화면에 돌아올 때마다 다시 본다.
//
// 🔴 S15P21E201-1686 (iOS 심사 공지의 알려진 문제 4): 홈이 열리는 1초 안에 내 여행마다(최대 12개) 활동을 동시에 불렀다.
//    S15P21E201-1702: 이제는 서버의 알림 요약 한 번으로 본다(서버 S15P21E201-1699). 「마지막으로 본 시각」은 그대로
//    알림 화면이 기기에 남기고, 서버는 그 뒤에 활동이 있었는지만 답한다.
// 🔴 서버에 이 조회가 없으면(404) 점을 안 켤 뿐이다. 여행마다 부르는 옛 방식으로 돌아가지 않는다 — 해 보고 안 되면
//    다른 길로 넘어가는 구조는 늘 느린 길을 먼저 타기 때문이다(조율 세션 결정). 서버 배포 뒤에 머지해서 막는다.
import { useCallback, useState } from 'react';
import { useFocusEffect } from 'expo-router';

import { apiRequest } from '@/api/client';
import { loadSeenAt } from '@/notifications/activityFeed';

/** 홈이 그려지고 이만큼 뒤에 본다 — 홈이 여는 요청들과 한꺼번에 몰리지 않게. */
export const BELL_DOT_DELAY_MS = 3000;
export const NOTIFICATION_SUMMARY_PATH = '/api/v1/me/notification-summary';

type NotificationSummaryDto = { hasUnseen: boolean; latestAt: string | null };

export function useHomeBellDot({ userId, accessToken, visible }: {
  userId: string | null;
  accessToken: string | null;
  /** 종이 이 화면에 그려지나. 위쪽 메뉴가 떠 있으면(폴드 펼침 등) 종이 거기 있어 홈에서는 세지 않는다. */
  visible: boolean;
}): boolean {
  const [dot, setDot] = useState(false);
  useFocusEffect(useCallback(() => {
    if (!userId) { setDot(false); return undefined; }
    if (!visible) return undefined;
    let active = true;
    const timer = setTimeout(() => {
      void (async () => {
        const seenAt = await loadSeenAt();
        // 한 번도 안 봤으면 since 를 뺀다 — 서버는 활동이 하나라도 있으면 안 본 것으로 친다.
        const path = seenAt ? `${NOTIFICATION_SUMMARY_PATH}?since=${encodeURIComponent(seenAt)}` : NOTIFICATION_SUMMARY_PATH;
        const summary = await apiRequest<NotificationSummaryDto>(path, { accessToken }).catch(() => null);
        if (active) setDot(Boolean(summary?.hasUnseen));
      })();
    }, BELL_DOT_DELAY_MS);
    return () => { active = false; clearTimeout(timer); };
  }, [userId, accessToken, visible]));
  return dot;
}
