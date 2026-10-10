// 루트 카드의 지도 바탕 — 웹 (S15P21E201-2027). 앱은 RouteMapLayer.native.tsx.
// 카카오 SDK 는 여행 지도(src/map/RouteMap.tsx)와 같은 <script id="kakao-map-sdk"> 하나를 같이 쓴다 — 두 번 받지 않는다.
import { createElement, useEffect, useRef } from 'react';

import { drawMiniRoute, type MiniRouteData } from './miniRouteMap';

declare global { interface Window { kakao?: any } }

const SDK_ID = 'kakao-map-sdk';

function loadKakao(appKey: string, done: () => void, fail: () => void): () => void {
  if (window.kakao?.maps) { window.kakao.maps.load(done); return () => {}; }
  const existing = document.getElementById(SDK_ID) as HTMLScriptElement | null;
  const script = existing ?? document.createElement('script');
  const onLoad = () => (window.kakao?.maps ? window.kakao.maps.load(done) : fail());
  script.addEventListener('load', onLoad);
  script.addEventListener('error', fail);
  if (!existing) {
    script.id = SDK_ID;
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false`;
    document.head.appendChild(script);
  }
  return () => { script.removeEventListener('load', onLoad); script.removeEventListener('error', fail); };
}

export function RouteMapLayer({ data, appKey, onReady, onFail }: { data: MiniRouteData; appKey: string; onReady: () => void; onFail: () => void }) {
  const hostRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    let alive = true;
    const stop = loadKakao(appKey, () => {
      if (!alive || !hostRef.current) return;
      // 다시 그릴 때 앞의 지도를 걷어 낸다 — 같은 칸에 지도가 겹겹이 쌓이지 않게.
      hostRef.current.innerHTML = '';
      try { drawMiniRoute(window.kakao.maps, hostRef.current, data); onReady(); } catch { onFail(); }
    }, () => { if (alive) onFail(); });
    return () => { alive = false; stop(); };
    // 그릴 값이 바뀔 때만 다시 그린다 — 콜백은 카드가 다시 그려질 때마다 새로 온다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [appKey, data]);
  // 누르기는 카드가 받는다 — 지도는 눈으로만 본다(pointerEvents none).
  return createElement('div', { ref: hostRef, style: { position: 'absolute', inset: 0, pointerEvents: 'none' } });
}
