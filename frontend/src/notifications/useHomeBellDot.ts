// 폰 홈 종의 점 — 안 본 여행 활동이 있나(S15P21E201-1380). 화면에 돌아올 때마다 다시 본다.
//
// 🔴 S15P21E201-1686 (iOS 심사 공지의 알려진 문제 4): 홈이 열리는 1초 안에 내 여행마다(최대 12개) 활동을 동시에 부르고,
//    홈 카드가 이미 받은 여행 목록까지 한 번 더 불렀다. 이제는 홈이 받은 목록을 다시 쓰고,
//    홈이 그려지고 몇 초 뒤에, 가장 최근에 바뀐 여행 셋만 본다(조율 세션 결정 — 알림 묶음 조회가 서버에 생기면 그것으로 바꾼다).
import { useCallback, useState } from 'react';
import { useFocusEffect } from 'expo-router';

import { hasUnseen, loadActivityFeed, loadSeenAt } from '@/notifications/activityFeed';
import type { TripSummaryDto } from '@/trip/trips';

/** 홈이 그려지고 이만큼 뒤에 본다 — 홈이 여는 요청들과 한꺼번에 몰리지 않게. */
export const BELL_DOT_DELAY_MS = 3000;
/** 활동을 볼 여행 수. 가장 최근에 바뀐 것부터. 알림 화면은 그대로 12개를 본다. */
export const BELL_DOT_TRIPS = 3;

export function useHomeBellDot({ userId, accessToken, trips, visible, tx }: {
  userId: string | null;
  accessToken: string | null;
  /** 홈 카드가 받은 여행 목록. 아직 안 왔으면 null — 올 때까지 기다린다. */
  trips: TripSummaryDto[] | null;
  /** 종이 이 화면에 그려지나. 위쪽 메뉴가 떠 있으면(폴드 펼침 등) 종이 거기 있어 홈에서는 세지 않는다. */
  visible: boolean;
  tx: (ko: string, en: string) => string;
}): boolean {
  const [dot, setDot] = useState(false);
  useFocusEffect(useCallback(() => {
    if (!userId) { setDot(false); return undefined; }
    if (!visible || !trips) return undefined;
    let active = true;
    const timer = setTimeout(() => {
      void Promise.all([loadActivityFeed(accessToken, tx, { trips, maxTrips: BELL_DOT_TRIPS }), loadSeenAt()]).then(([feed, seenAt]) => {
        if (active && feed.state === 'success') setDot(hasUnseen(feed.items, seenAt));
      });
    }, BELL_DOT_DELAY_MS);
    return () => { active = false; clearTimeout(timer); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId, accessToken, trips, visible]));
  return dot;
}
