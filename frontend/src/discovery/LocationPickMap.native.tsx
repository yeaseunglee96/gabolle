// 장소 제안의 위치 고르기 — 앱 판(WebView). 웹 판은 LocationPickMap.tsx. S15P21E201-2031.
import { useEffect, useMemo, useRef } from 'react';
import { StyleSheet, View } from 'react-native';
import { WebView, type WebViewMessageEvent } from 'react-native-webview';

import { Text } from '@/components/Text';
import { color, radius } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { buildLocationPickHtml } from './locationPickHtml';
import { CenterPin, type LocationPickMapProps } from './LocationPickParts';

const RAW_BASE = process.env.EXPO_PUBLIC_API_BASE_URL ?? 'https://api.gabolle.kr';
const BASE_URL = RAW_BASE.endsWith('/') ? RAW_BASE.slice(0, -1) : RAW_BASE;

export function LocationPickMap({ initial, moveTo, onCenter, height = 280 }: LocationPickMapProps) {
  const { tx } = useI18n();
  const appKey = process.env.EXPO_PUBLIC_KAKAO_MAP_JS_KEY;
  const webRef = useRef<WebView | null>(null);
  // 처음 위치로만 HTML 을 만든다 — 좌표가 바뀔 때마다 다시 만들면 지도가 처음부터 다시 뜬다.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const html = useMemo(() => (appKey ? buildLocationPickHtml(appKey, initial.lat, initial.lng) : null), [appKey]);

  useEffect(() => {
    if (!moveTo) return;
    webRef.current?.injectJavaScript(`window.__moveTo && window.__moveTo(${moveTo.lat}, ${moveTo.lng}); true;`);
  }, [moveTo]);

  const onMessage = (event: WebViewMessageEvent) => {
    try {
      const message = JSON.parse(event.nativeEvent.data) as { type: string; payload?: { lat: number; lng: number } };
      if (message.type === 'center' && message.payload) onCenter(message.payload);
    } catch {
      // 모양이 다른 말은 버린다.
    }
  };

  if (!html) {
    return <View style={[styles.shell, { height }]}><Text color={color.text.body}>{tx('지도를 띄울 수 없어요.', 'The map is unavailable.')}</Text></View>;
  }
  return (
    <View style={[styles.shell, { height }]}>
      {/* 🔴 domStorageEnabled — 경로 지도(RouteMap)와 같게. 빠져 있어 앱에서 카카오 지도가 회색 빈칸으로 남았다(2026-10-17 폰 1.0.5 실측). */}
      <WebView ref={webRef} originWhitelist={['*']} source={{ html, baseUrl: BASE_URL }} onMessage={onMessage} style={styles.map} javaScriptEnabled domStorageEnabled />
      <CenterPin />
    </View>
  );
}

const styles = StyleSheet.create({
  shell: { width: '100%', borderRadius: radius.lg, overflow: 'hidden', backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  // 가운데 정렬 칸 안에서도 지도가 칸을 다 차지하게 — 퍼센트 크기만으로는 WebView 가 0 으로 잡힐 수 있다.
  map: { ...StyleSheet.absoluteFillObject, backgroundColor: 'transparent' },
});
