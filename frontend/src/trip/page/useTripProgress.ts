// 여행 페이지(폰)의 「지금」 카드가 쓰는 진행 상태 — 출발·중지·도착·건너뛰기 (S15P21E201-1535).
//
// 규칙은 지금까지의 일정 화면(app/trips/[id]/itinerary.tsx 의 「일정 진행」)과 같다. 그대로 옮겼다:
//   · 🔴 서버가 정본이다(S15P21E201-1325). 화면은 사건을 보내고 서버가 돌려준 상태를 그린다.
//   · 🔴 서버에 그 자리가 없는 판(404·501)에서는 기기에만 남긴다. 그 판정은 열 때 한 번만 한다 —
//     돌다가 갈아타면 눌러 놓은 것이 어디에 남았는지 알 수 없다.
//   · 🔴 화면을 먼저 바꾸고 보낸다. 서버가 안 받으면 서버 것으로 되돌린다.
import { useEffect, useState } from 'react';

import { useAuth } from '@/auth/AuthProvider';
import {
  EMPTY_PROGRESS, arrive as arriveAt, loadProgress, pause as pauseRun, saveProgress, skip as skipStop, start as startRun,
  type TripProgress,
} from '@/plan/tripProgress';
import { arriveProgress, fetchProgress, pauseProgress, skipProgress, startProgress, type ProgressResult } from '@/plan/tripProgressApi';

/** itineraryId 가 null 이면(확정 전) 아무것도 부르지 않고 「출발 전」에 머문다. */
export function useTripProgress(itineraryId: string | null) {
  const { accessToken } = useAuth();
  const [progress, setProgress] = useState<TripProgress>(EMPTY_PROGRESS);
  const [deviceOnly, setDeviceOnly] = useState<boolean | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setProgress(EMPTY_PROGRESS);
    setDeviceOnly(null);
    setError(null);
    if (!itineraryId) return;
    let alive = true;
    void (async () => {
      const outcome = await fetchProgress(itineraryId, accessToken);
      if (!alive) return;
      if (outcome.state === 'success') { setDeviceOnly(false); setProgress(outcome.progress); return; }
      // 실패도 기기에 남은 것으로 이어 간다 — 통신이 잠깐 안 되는 것과 서버에 자리가 없는 것은
      // 사용자에게 같은 뜻이다. 다만 실패는 말해 준다.
      setDeviceOnly(true);
      if (outcome.state === 'error') setError(outcome.message);
      const saved = await loadProgress(itineraryId);
      if (alive) setProgress(saved);
    })();
    return () => { alive = false; };
  }, [accessToken, itineraryId]);

  const send = (optimistic: TripProgress, call: () => Promise<ProgressResult>) => {
    if (!itineraryId) return;
    setProgress(optimistic);
    setError(null);
    if (deviceOnly !== false) { void saveProgress(itineraryId, optimistic); return; }
    void call().then((outcome) => {
      if (outcome.state === 'success') { setProgress(outcome.progress); return; }
      if (outcome.state === 'error') setError(outcome.message);
      void fetchProgress(itineraryId, accessToken).then((again) => { if (again.state === 'success') setProgress(again.progress); });
    });
  };

  const id = itineraryId ?? '';
  return {
    progress,
    deviceOnly,
    error,
    start: () => send(startRun(progress), () => startProgress(id, accessToken)),
    pause: () => send(pauseRun(progress), () => pauseProgress(id, accessToken)),
    arrive: (stopIds: string[], stopId: string) => send(
      arriveAt(progress, stopIds, stopId, new Date().toISOString(), 'manual'),
      () => arriveProgress(id, stopId, 'manual', accessToken)),
    skip: (stopIds: string[], stopId: string) => send(
      skipStop(progress, stopIds, stopId, new Date().toISOString()),
      () => skipProgress(id, stopId, accessToken)),
  };
}
