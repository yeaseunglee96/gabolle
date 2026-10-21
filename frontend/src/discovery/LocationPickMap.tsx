// 장소 제안의 위치 고르기 — 웹 판. 앱 판은 LocationPickMap.native.tsx. S15P21E201-2031.
//
// 카카오 지도 스크립트는 일정 지도(RouteMap.tsx)와 같은 태그 id 로 한 번만 붙인다 — 두 화면이 각자 붙이면 두 번 받는다.
import { createElement, useEffect, useRef, useState } from 'react';
import { Platform, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { CenterPin, type LocationPickMapProps } from './LocationPickParts';

declare global { interface Window { kakao?: any } }

const SDK_ID = 'kakao-map-sdk';

export function LocationPickMap({ initial, moveTo, onCenter, height = 280 }: LocationPickMapProps) {
  const { tx } = useI18n();
  const appKey = process.env.EXPO_PUBLIC_KAKAO_MAP_JS_KEY;
  const hostRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<any>(null);
  const onCenterRef = useRef(onCenter);
  onCenterRef.current = onCenter;
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (Platform.OS !== 'web' || !appKey) return;
    let cancelled = false;
    const draw = () => {
      const maps = window.kakao?.maps;
      if (!maps || cancelled) return;
      maps.load(() => {
        if (cancelled || !hostRef.current || mapRef.current) return;
        const map = new maps.Map(hostRef.current, { center: new maps.LatLng(initial.lat, initial.lng), level: 3 });
        mapRef.current = map;
        const emit = () => { const c = map.getCenter(); onCenterRef.current({ lat: c.getLat(), lng: c.getLng() }); };
        maps.event.addListener(map, 'idle', emit);
        emit();
      });
    };
    if (window.kakao?.maps) { draw(); return () => { cancelled = true; }; }
    const existing = document.getElementById(SDK_ID) as HTMLScriptElement | null;
    const script = existing ?? document.createElement('script');
    if (!existing) {
      script.id = SDK_ID;
      script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false`;
      document.head.appendChild(script);
    }
    const onError = () => setFailed(true);
    script.addEventListener('load', draw);
    script.addEventListener('error', onError);
    return () => {
      cancelled = true;
      script.removeEventListener('load', draw);
      script.removeEventListener('error', onError);
    };
    // 처음 위치로 한 번만 만든다 — 이후 이동은 아래 moveTo 가 맡는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [appKey]);

  useEffect(() => {
    const maps = window.kakao?.maps;
    if (!moveTo || !mapRef.current || !maps) return;
    mapRef.current.setCenter(new maps.LatLng(moveTo.lat, moveTo.lng));
  }, [moveTo]);

  if (!appKey || failed) {
    return (
      <View style={[styles.shell, styles.center, { height }]}>
        <Text color={color.text.body}>{tx('지도를 띄울 수 없어요.', 'The map is unavailable.')}</Text>
      </View>
    );
  }
  return (
    <View style={[styles.shell, { height }]}>
      {createElement('div', { ref: hostRef, style: { width: '100%', height: '100%' } })}
      <CenterPin />
    </View>
  );
}

const styles = StyleSheet.create({
  shell: { width: '100%', borderRadius: radius.lg, overflow: 'hidden', backgroundColor: color.surface.soft },
  center: { alignItems: 'center', justifyContent: 'center', padding: spacing[4] },
});
