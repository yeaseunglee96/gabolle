// 루트 카드의 지도 바탕 — 앱 (S15P21E201-2027). 여행 지도(src/map/RouteMap.native.tsx)처럼 WebView 에 카카오 지도를 띄운다.
// 카드마다 WebView 하나라 무겁다 — RouteThumb 가 화면 가까이 온 카드만 띄우고 멀어지면 내린다.
import { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';

import { buildMiniRouteMapHtml, type MiniRouteData } from './miniRouteMap';

// 카카오 콘솔에 등록된 도메인 — baseUrl 이 없으면 지도가 조용히 안 뜬다(RouteMap.native.tsx 와 같은 값).
const RAW_BASE = process.env.EXPO_PUBLIC_API_BASE_URL ?? 'https://j15e201.p.ssafy.io';
const BASE_URL = RAW_BASE.endsWith('/') ? RAW_BASE.slice(0, -1) : RAW_BASE;

export function RouteMapLayer({ data, appKey, onReady, onFail }: { data: MiniRouteData; appKey: string; onReady: () => void; onFail: () => void }) {
  // WebView 는 그릴 때 불러온다 — 맨 위에서 불러오면 이 카드를 거쳐 가는 jest 시험이 네이티브 모듈이 없다며 전부 죽는다.
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { WebView } = require('react-native-webview') as typeof import('react-native-webview');
  const html = useMemo(() => buildMiniRouteMapHtml(appKey, data), [appKey, data]);
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      <WebView
        style={styles.web}
        originWhitelist={['*']}
        source={{ html, baseUrl: BASE_URL }}
        onMessage={(event: { nativeEvent: { data: string } }) => (event.nativeEvent.data === 'ready' ? onReady() : onFail())}
        onError={onFail}
        javaScriptEnabled
        scrollEnabled={false}
        overScrollMode="never"
        showsHorizontalScrollIndicator={false}
        showsVerticalScrollIndicator={false}
      />
    </View>
  );
}

const styles = StyleSheet.create({ web: { flex: 1, backgroundColor: 'transparent' } });
