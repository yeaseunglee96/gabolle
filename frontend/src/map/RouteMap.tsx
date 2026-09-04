import { createElement, useEffect, useRef, useState } from 'react';
import { Platform, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import type { MapStop } from './types';

declare global { interface Window { kakao?: any } }

const SDK_ID = 'kakao-map-sdk';

export function RouteMap({ stops, selectedId, onSelect }: { stops: MapStop[]; selectedId: string; onSelect: (id: string) => void }) {
  const hostRef = useRef<HTMLElement | null>(null);
  const mapRef = useRef<any>(null);
  const overlaysRef = useRef<any[]>([]);
  const [error, setError] = useState<string | null>(null);
  const appKey = process.env.EXPO_PUBLIC_KAKAO_MAP_JS_KEY;

  useEffect(() => {
    if (Platform.OS !== 'web') return;
    if (!appKey) { setError('지도 API 키가 없어 목록 모드로 표시합니다.'); return; }
    const draw = () => window.kakao?.maps.load(() => {
      if (!hostRef.current || !window.kakao) return;
      const maps = window.kakao.maps;
      const center = new maps.LatLng(stops[0].latitude, stops[0].longitude);
      const map = mapRef.current ?? new maps.Map(hostRef.current, { center, level: 8 });
      mapRef.current = map;
      overlaysRef.current.forEach((overlay) => overlay.setMap(null));
      overlaysRef.current = [];
      const bounds = new maps.LatLngBounds();
      const path = stops.map((stop) => {
        const position = new maps.LatLng(stop.latitude, stop.longitude);
        bounds.extend(position);
        const content = document.createElement('button');
        content.type = 'button'; content.textContent = String(stop.number);
        content.setAttribute('aria-label', `${stop.number}번 ${stop.name}`);
        Object.assign(content.style, { width: '34px', height: '34px', borderRadius: '999px', border: `3px solid ${stop.id === selectedId ? color.brand.orange : color.brand.navy}`, background: color.canvas, color: color.brand.navy, fontWeight: '700', cursor: 'pointer' });
        content.onclick = () => onSelect(stop.id);
        const overlay = new maps.CustomOverlay({ position, content, yAnchor: 0.5 });
        overlay.setMap(map); overlaysRef.current.push(overlay);
        return position;
      });
      const line = new maps.Polyline({ path, strokeWeight: 5, strokeColor: color.brand.orange, strokeOpacity: 0.9, strokeStyle: 'solid' });
      line.setMap(map); overlaysRef.current.push(line);
      map.setBounds(bounds, 60, 60, 60, 60);
      setError(null);
    });
    if (window.kakao?.maps) { draw(); return; }
    const existing = document.getElementById(SDK_ID) as HTMLScriptElement | null;
    const script = existing ?? document.createElement('script');
    if (!existing) { script.id = SDK_ID; script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false`; document.head.appendChild(script); }
    script.addEventListener('load', draw);
    script.addEventListener('error', () => setError('지도를 불러오지 못했어요. 목록은 계속 사용할 수 있습니다.'));
    return () => script.removeEventListener('load', draw);
  }, [appKey, onSelect, selectedId, stops]);

  if (Platform.OS === 'web') {
    return (
      <View style={styles.webShell}>
        {createElement('div', { ref: hostRef, style: { width: '100%', height: 340 }, 'aria-label': '여행 동선 지도' })}
        {error ? <View style={styles.webFallback}><Text variant="title" weight="bold">지도 없이 동선을 확인하고 있어요</Text><Text variant="body" style={styles.description}>{error}</Text></View> : null}
      </View>
    );
  }

  return (
    <View style={styles.fallback}>
      <Text variant="title" weight="bold">앱 지도 연동을 준비하고 있어요</Text>
      <Text variant="body" style={styles.description}>방문 순서와 장소 목록은 그대로 확인할 수 있습니다. 앱용 지도 SDK가 확정되면 이 영역에 동선을 표시해요.</Text>
      <View style={styles.routePreview}>
        {stops.map((stop, index) => (
          <View key={stop.id} style={styles.routeItem}>
            <View style={styles.marker}><Text variant="caption" weight="bold" color={color.text.onAction}>{stop.number}</Text></View>
            {index < stops.length - 1 ? <View style={styles.line} /> : null}
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  webShell: { minHeight: 340, overflow: 'hidden', borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.soft },
  webFallback: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center', padding: spacing[6], gap: spacing[2], backgroundColor: color.surface.soft },
  fallback: { minHeight: 260, borderRadius: radius.lg, backgroundColor: color.surface.soft, borderWidth: 1, borderColor: color.surface.field, alignItems: 'center', justifyContent: 'center', padding: spacing[6], gap: spacing[2] },
  description: { color: color.text.body, textAlign: 'center', maxWidth: 420 },
  routePreview: { flexDirection: 'row', alignItems: 'center', marginTop: spacing[3] },
  routeItem: { flexDirection: 'row', alignItems: 'center' },
  marker: { width: 30, height: 30, borderRadius: radius.full, backgroundColor: color.brand.orange, alignItems: 'center', justifyContent: 'center' },
  line: { width: 28, height: 2, backgroundColor: color.brand.orange },
});
