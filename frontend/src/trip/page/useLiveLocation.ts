// 여행 중 내 위치를 따라간다 — 켜져 있을 때만 (S15P21E201-1568).
//
// 🔴 출발(RUNNING) 동안만 켠다. 출발 전에 켜면 여행 페이지를 열기만 해도 위치 권한을 묻고, 사람은 무엇을 위해
//    묻는지 모른 채 거절한다. 「출발」을 누른 순간이 「왜 위치가 필요한가」가 가장 분명한 때다.
// expo-location 의 watchPositionAsync 는 웹(navigator.geolocation)에서도 돈다.
import { useEffect, useState } from 'react';
import * as Location from 'expo-location';

import type { Fix } from './autoArrival';

export type LiveLocation = { state: 'off' | 'asking' | 'denied' | 'unavailable' | 'on'; fix: Fix | null };

const OFF: LiveLocation = { state: 'off', fix: null };

export function useLiveLocation(enabled: boolean): LiveLocation {
  const [live, setLive] = useState<LiveLocation>(OFF);

  useEffect(() => {
    if (!enabled) { setLive(OFF); return; }
    let alive = true;
    let subscription: Location.LocationSubscription | null = null;
    setLive({ state: 'asking', fix: null });
    void (async () => {
      try {
        const permission = await Location.requestForegroundPermissionsAsync();
        if (!alive) return;
        if (permission.status !== 'granted') { setLive({ state: 'denied', fix: null }); return; }
        subscription = await Location.watchPositionAsync(
          // 10m 움직이거나 15초마다 — 50m·2분을 가르기에 충분하고 배터리를 덜 쓴다.
          { accuracy: Location.Accuracy.Balanced, distanceInterval: 10, timeInterval: 15_000 },
          (position) => {
            if (!alive) return;
            setLive({
              state: 'on',
              fix: { latitude: position.coords.latitude, longitude: position.coords.longitude, accuracy: position.coords.accuracy ?? null, at: position.timestamp },
            });
          },
        );
        if (!alive) subscription.remove();
        else setLive((prev) => (prev.state === 'asking' ? { state: 'on', fix: null } : prev));
      } catch {
        if (alive) setLive({ state: 'unavailable', fix: null });
      }
    })();
    return () => { alive = false; subscription?.remove(); };
  }, [enabled]);

  return live;
}
