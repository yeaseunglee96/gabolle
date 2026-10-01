// 앱(폰)의 지도 — S15P21E201-1140.
import { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { WebView, type WebViewMessageEvent } from 'react-native-webview';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

import { buildKakaoMapHtml } from './kakaoMapHtml';
import { fitPadding, focusShiftY } from './mapFocus';
import { gradedSegments, type RouteGrading } from './routeGrading';
import type { SlopePiece } from './slopeGrades';

import type { MapPathPoint, MapStop } from './types';

/** 웹 RouteMap.tsx 의 MapRouteLayer 와 같은 모양 — 경로·어림·굵기·경사 조각(S15P21E201-1658)·고른 조건(S15P21E201-1896)까지 WebView 로 그대로 넘긴다. */
export type MapRouteLayer = {
  id: string;
  color: string;
  stops: MapStop[];
  path?: MapPathPoint[];
  estimated?: boolean;
  weight?: number;
  opacity?: number;
  pieces?: SlopePiece[];
  grading?: RouteGrading;
};
export type MapPointLayer = { id: string; label: string; color: string; stops: MapStop[] };
export type CurrentLocation = { latitude: number; longitude: number };

type RouteMapProps = {
  stops: MapStop[];
  selectedId: string;
  onSelect: (id: string) => void;
  routes?: MapRouteLayer[];
  points?: MapPointLayer[];
  currentLocation?: CurrentLocation | null;
  onBackToList?: () => void;
  height?: number;
  /** 지도 아래쪽이 창에 가려진 높이(px) — 웹 RouteMap 과 같은 뜻(S15P21E201-1607). */
  bottomInset?: number;
  /** 지도 위쪽이 상태바·칩에 가려진 높이(px) — 웹 RouteMap 과 같은 뜻(S15P21E201-1754). */
  topInset?: number;
  /** 이 값이 (null 이 아닌 새 값으로) 바뀌면 지금 여백으로 한 번 다시 맞춘다 — 웹 RouteMap 과 같은 뜻(S15P21E201-1754). */
  refitKey?: string | number | null;
  /** 고른 곳을 지도 가운데로 옮긴다 — 웹 RouteMap 과 같은 뜻(S15P21E201-1535). 기본은 꺼짐. */
  focusSelected?: boolean;
};

/**
 * 기본값 배열을 파일 수준에서 한 번만 만든다. 함수 시그니처에 `points = []` 라고
 * 쓰면 이 컴포넌트가 다시 그려질 때마다 새 배열이 생기고, 그게 `useEffect` 의 의존성으로
 * 들어가 매번 다시 돈다, 웹 지도가 겪었다).
 */
const NO_POINT_LAYERS: MapPointLayer[] = [];

type WebViewOutMessage = { type: 'sdkLoaded' | 'ready' | 'select' | 'scriptError'; payload?: unknown };

export function RouteMap({
  stops,
  selectedId,
  onSelect,
  routes,
  points = NO_POINT_LAYERS,
  currentLocation,
  onBackToList,
  height = 340,
  bottomInset = 0,
  topInset = 0,
  refitKey = null,
  focusSelected = false,
}: RouteMapProps) {
  const { tx } = useI18n();
  const webViewRef = useRef<WebView | null>(null);
  const sdkReadyRef = useRef(false);
  const [scriptFailed, setScriptFailed] = useState(false);
  const appKey = process.env.EXPO_PUBLIC_KAKAO_MAP_JS_KEY;
  // 키가 바뀔 일은 앱이 켜져 있는 동안 없다시피 하다 — appKey 만 의존성으로 둬서
  // stops·selectedId 가 바뀔 때마다 HTML을 다시 만들어 WebView를 재시작하지 않는다.
  const html = useMemo(() => (appKey ? buildKakaoMapHtml(appKey) : ''), [appKey]);

  const visibleStops = useMemo(() => [...stops, ...points.flatMap((layer) => layer.stops)], [points, stops]);
  // 걷는 길의 조각을 잘라 고른 조건의 색을 붙여 둔다 — WebView 안의 스크립트는 routeGrading.ts 를 못 읽는다(S15P21E201-1658 · -1896).
  // 🔴 웹 지도(RouteMap.tsx)와 같은 함수(gradedSegments)를 부른다 — 여행 중 GPS 로 따라갈 때도 이 지도가 같은 선을 그린다.
  const drawnRoutes = useMemo(
    () => (routes ?? [{ id: 'selected', color: color.action.primary, stops }]).map((route) => {
      const segments = route.weight == null && route.path?.length ? gradedSegments(route.path, route.pieces, route.grading) : null;
      return segments ? { ...route, segments } : route;
    }),
    [routes, stops],
  );

  const sendRender = () => {
    if (!sdkReadyRef.current || !webViewRef.current) return;
    const data = {
      stops,
      points,
      routes: drawnRoutes,
      selectedId,
      currentLocation: currentLocation ?? null,
      // 아래가 창에 가려진 만큼 맞추기 여백을 더 둔다(S15P21E201-1607, 웹과 같은 셈 — mapFocus.ts).
      // 🔴 이 값만 바뀌어서는 다시 보내지 않는다 — 창을 여닫을 때마다 지도가 다시 맞춰져 튀면 안 된다.
      //    다시 맞추는 것은 refitKey 가 바뀔 때 한 번뿐이다(아래 sendRefit).
      fitPadding: fitPadding(bottomInset, height, topInset),
      colors: { navy: color.brand.navy, selected: color.action.secondary, canvas: color.canvas, casing: color.surface.card },
      focus: focusSelected,
      shiftY: focusShiftY(bottomInset, height, topInset),
    };
    webViewRef.current.injectJavaScript(`window.__renderKakaoMap(${JSON.stringify(data)}); true;`);
  };

  // 🔴 고른 곳만 바뀌면 다시 그리지 않는다(S15P21E201-1654) — 전에는 고를 때마다 전체를 다시 그리고 다시 맞춰서
  //    지도가 여행 전체로 튀었다. 마커 모양만 바꾸고 지금 화면에서 고른 곳으로 민다.
  const sendSelect = () => {
    if (!sdkReadyRef.current || !webViewRef.current) return;
    const data = { selectedId, focus: focusSelected, shiftY: focusShiftY(bottomInset, height, topInset) };
    webViewRef.current.injectJavaScript(`window.__selectKakaoMap(${JSON.stringify(data)}); true;`);
  };

  // 여백만 새로 보내 같은 범위를 다시 맞춘다(S15P21E201-1754) — «지도 보기»처럼 딱 끊어지는 전환 때만.
  const sendRefit = () => {
    if (!sdkReadyRef.current || !webViewRef.current) return;
    const data = { fitPadding: fitPadding(bottomInset, height, topInset), shiftY: focusShiftY(bottomInset, height, topInset) };
    webViewRef.current.injectJavaScript(`window.__fitKakaoMap(${JSON.stringify(data)}); true;`);
  };

  // 현재 위치는 점만 옮긴다. 위치 객체는 부를 때마다 새것이라 좌표 두 숫자로 본다.
  const sendLocation = () => {
    if (!sdkReadyRef.current || !webViewRef.current) return;
    webViewRef.current.injectJavaScript(`window.__moveKakaoLocation(${JSON.stringify(currentLocation ?? null)}); true;`);
  };

  // stops·points·routes 가 바뀔 때마다 이미 떠 있는 지도에 새 데이터를 밀어 넣는다(고른 곳·현재 위치는 아래 따로).
  // sdk 가 아직 안 떴으면(sdkReadyRef.current === false) 아무 일도
  // 안 하고, onMessage 의 'sdkLoaded' 처리부가 뜬 직후 한 번 sendRender 를 부른다.
  useEffect(() => {
    sendRender();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stops, points, routes]);

  useEffect(() => {
    sendSelect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId, focusSelected]);

  // 🔴 null 로 바뀔 때(창을 다시 열 때)는 안 맞춘다 — 창을 열 때 지도가 튀면 안 된다. 처음 뜰 때는 sendRender 가 맞춘다.
  const lastRefitKey = useRef(refitKey);
  useEffect(() => {
    if (refitKey === lastRefitKey.current) return;
    lastRefitKey.current = refitKey;
    if (refitKey != null) sendRefit();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refitKey]);

  useEffect(() => {
    sendLocation();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentLocation?.latitude, currentLocation?.longitude]);

  const onMessage = (event: WebViewMessageEvent) => {
    let message: WebViewOutMessage;
    try {
      message = JSON.parse(event.nativeEvent.data);
    } catch {
      return;
    }
    if (message.type === 'sdkLoaded') {
      sdkReadyRef.current = true;
      sendRender();
      return;
    }
    if (message.type === 'select' && typeof message.payload === 'string') {
      onSelect(message.payload);
      return;
    }
    if (message.type === 'scriptError') setScriptFailed(true);
  };

  // ① 키가 아예 없다 — 웹(RouteMap.tsx)과 완전히 같은 문구를 쓴다. 원인이 같기 때문이다
  // (EXPO_PUBLIC_ 값은 빌드 순간 문자열로 박히므로, 폰 빌드에도 이 키가 EAS 환경
  // 변수로 들어가야 한다 — docs/MAP-RECOVERY.md 참고).
  if (!appKey) {
    return (
      <View style={[styles.fallback, { minHeight: height }]}>
        <Text variant="title" weight="bold">{tx('지금은 지도를 불러올 수 없어요', 'The map can’t be shown right now')}</Text>
        <Text variant="body" style={styles.description}>
          {tx(
            '지도 설정에 문제가 있어 지도를 그리지 못했어요. 방문 순서와 장소 목록은 아래에서 그대로 볼 수 있어요.',
            'Something is wrong with the map setup, so we couldn’t draw it. You can still use the visit order and place list below.',
          )}
        </Text>
        <Text variant="caption" style={styles.tech}>EXPO_PUBLIC_KAKAO_MAP_JS_KEY = (빈 값) · EAS 빌드 환경 변수로 넣어야 한다</Text>
        {onBackToList ? <Button label={tx('목록으로 돌아가기', 'Back to list')} variant="tertiary" onPress={onBackToList} /> : null}
      </View>
    );
  }

  // ② 스크립트를 못 받았다 — 웹의 「지도 파일을 못 받았어요」와 같은 자리다. 다만 폰에서는
  // 서버 CSP(웹의 두 번째 실패 원인)가 적용되지 않는다 — WebView가 로드하는 것은
  // 우리 서버가 아니라 이 자리에서 만든 HTML 문자열이라 우리 nginx 응답 헤더를 안 거친다.
  // 대신 카카오 콘솔의 "사이트 도메인" 등록이 이 경로(출처가 없는 로컬 HTML)에서도
  // 똑같이 통하는지는 확인하지 못했다 — 실기기 빌드가 나와야 알 수 있다.
  if (scriptFailed) {
    return (
      <View style={[styles.fallback, { minHeight: height }]}>
        <Text variant="title" weight="bold">{tx('지도 파일을 못 받았어요', 'Could not fetch the map file')}</Text>
        <Text variant="body" style={styles.description}>
          {tx(
            '카카오 지도 파일을 받지 못했습니다. 네트워크가 막혔거나, 이 앱이 카카오 개발자 콘솔에 등록되지 않았을 수 있습니다.',
            'The Kakao map file could not be fetched. The network may be blocked, or this app may not be registered in the Kakao developer console.',
          )}
        </Text>
        {onBackToList ? <Button label={tx('목록으로 돌아가기', 'Back to list')} variant="tertiary" onPress={onBackToList} /> : null}
      </View>
    );
  }

  if (!stops.length) return <View style={[styles.empty, { height }]} />;

  return (
    <View style={[styles.shell, { height }]}>
      <WebView
        ref={webViewRef}
        style={styles.map}
        originWhitelist={['*']}
        // — baseUrl 을 꼭 준다. 없으면 지도가 조용히 안 뜬다.
        source={{ html, baseUrl: MAP_BASE_URL }}
        onMessage={onMessage}
        onError={() => setScriptFailed(true)}
        javaScriptEnabled
        domStorageEnabled
      />
      {/* 🔴 확대·축소 단추(S15P21E201-1903) — 길 안내처럼 스크롤 안에 든 지도는 두 손가락 확대가 화면 스크롤과 다툰다.
          오른쪽에 둔다 — 왼쪽 아래는 카카오 로고·축척 자리다(아래 backRow 주석). 창에 가려진 높이만큼 올린다. */}
      <View style={[styles.zoomColumn, { bottom: Math.max(0, Math.min(bottomInset, height - 140)) + spacing[3] }]}>
        {([[-1, '+', tx('지도 확대', 'Zoom in')], [1, '−', tx('지도 축소', 'Zoom out')]] as const).map(([delta, glyph, label]) => (
          <Pressable key={glyph} accessibilityRole="button" accessibilityLabel={label} hitSlop={6} onPress={() => webViewRef.current?.injectJavaScript(`window.__zoomKakaoMap && window.__zoomKakaoMap(${delta}); true;`)} style={({ pressed }) => [styles.zoomButton, pressed && styles.zoomPressed]}>
            <Text variant="title" weight="bold" color={color.text.heading}>{glyph}</Text>
          </Pressable>
        ))}
      </View>
      {/* 걷는 길 경사 색의 안내 문구는 뺐다 — 어색하다는 사용자 결정(S15P21E201-1820). 색 선은 그대로 긋는다. */}
      {onBackToList ? (
        <View style={styles.backRow}>
          <Button label={tx('목록으로 돌아가기', 'Back to list')} variant="tertiary" onPress={onBackToList} />
        </View>
      ) : null}
    </View>
  );
}

/** WebView 가 자기 출처로 말할 주소 — S15P21E201-1176. */
const RAW_MAP_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? 'https://j15e201.p.ssafy.io';
const MAP_BASE_URL = RAW_MAP_BASE_URL.endsWith('/') ? RAW_MAP_BASE_URL.slice(0, -1) : RAW_MAP_BASE_URL;

const styles = StyleSheet.create({
  shell: { width: '100%', borderRadius: radius.lg, overflow: 'hidden', backgroundColor: color.surface.soft },
  map: { width: '100%', height: '100%', backgroundColor: 'transparent' },
  /**
   * 🔴 **좌하단에 두지 않는다** — S15P21E201-1490(B-12). 카카오 지도는 그 자리에
   * **로고와 축척 표시**를 그린다. 전에는 `bottom: spacing[3]` 이라 이 단추가 그 둘을
   * 덮었고, 지도 제공처 표기는 이용약관상 가려지면 안 되는 자리다(iOS build 39 QA).
   *
   * 위쪽으로 옮긴다. 이 앱은 카카오 컨트롤(확대·지도 종류)을 **하나도 안 넣으므로**
   * (`kakaoMapHtml.ts` — addControl 0건) 지도 위쪽은 비어 있다. 단추를 지도 «밖»으로
   * 내보내지 않는 이유는 이 부품이 `height` 만큼만 자리를 받기 때문이다 — 밖으로
   * 빼면 부르는 화면 넷의 높이 계산이 같이 어긋난다.
   */
  backRow: { position: 'absolute', left: spacing[3], top: spacing[3] },
  zoomColumn: { position: 'absolute', right: spacing[3], gap: spacing[2] },
  zoomButton: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center', shadowColor: '#000', shadowOpacity: 0.15, shadowRadius: 6, shadowOffset: { width: 0, height: 2 }, elevation: 3 },
  zoomPressed: { opacity: 0.7 },
  empty: { width: '100%', borderRadius: radius.lg, backgroundColor: color.surface.soft },
  fallback: {
    width: '100%', borderRadius: radius.lg, backgroundColor: color.surface.soft,
    borderWidth: 1, borderColor: color.surface.field,
    alignItems: 'center', justifyContent: 'center', padding: spacing[6], gap: spacing[2],
  },
  description: { color: color.text.body, textAlign: 'center', maxWidth: 420 },
  tech: { color: color.text.muted, textAlign: 'center', maxWidth: 460 },
});
