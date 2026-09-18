// 앱(폰)의 지도 — S15P21E201-1140.
//
// 🔴 2026-09-17 정정 — 이 파일은 한 번 `react-native-maps`(iOS=애플 지도,
//    안드로이드=Google Maps)로 만들어졌다가, 그날 바로 카카오로 다시 바꿨다. 실수를
//    되짚는다: 처음 결정은 "iOS는 키가 필요 없다"는 것만 보고 골랐는데, 그러면 **지도가
//    화면마다 다른 회사 것으로 보인다** — 웹은 카카오, 폰은 애플/구글. 이 앱은 부산
//    구석구석의 한글 주소·상호가 중요한 여행 앱이라, 지도 자체도 웹과 같은 곳(카카오)이어야
//    장소가 같은 이름으로 나온다. 그래서 되돌린다 — 웹(`RouteMap.tsx`)과 같은 카카오
//    지도를, 폰에서는 `react-native-webview` 안에 띄운다.
//
// 🔴 카카오는 폰용 네이티브 SDK가 없다 — 웹에 쓰는 것과 같은 JavaScript SDK를
//    WebView 안에서 돌린다. 그래서 **웹이 쓰는 것과 같은 키**
//    (`EXPO_PUBLIC_KAKAO_MAP_JS_KEY`)가 필요하다. 웹은 이미 EAS/Jenkins 빌드에 이 키가
//    들어가 있다(docs/MAP-RECOVERY.md) — 폰 빌드(EAS)에도 같은 값을 넣어야 하는데,
//    **그건 이 저장소의 코드로 못 하는 일이라 그대로 남겨 둔다.** 아래 `docs/MAP-RECOVERY.md`
//    의 새 항목이 그 일을 적는다.
//
// 🔴 이 파일은 `RouteMap.native.tsx` 다. 이름 가운데의 `.native` 는 **번들러가 폰에서만
//    이 파일을 쓴다는 표시**다 — 웹은 옆의 `RouteMap.tsx` 를 그대로 쓴다.
//
// 🔴 데이터가 바뀔 때마다 WebView를 새로 안 그린다. `source.html` 을 바꾸면 페이지 전체가
//    다시 로드돼 지도가 깜빡이고 카카오 스크립트도 매번 다시 받는다. 그래서 HTML은
//    **키가 바뀔 때만**(사실상 앱 켜져 있는 동안 한 번) 만들고, `stops`·`selectedId` 같은
//    실제 변경은 `injectJavaScript` 로 이미 떠 있는 페이지 안의 함수만 다시 부른다 —
//    웹 버전이 지도 객체(`mapRef.current`)를 재사용하는 것과 같은 원리다.
import { useEffect, useMemo, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { WebView, type WebViewMessageEvent } from 'react-native-webview';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

import { buildKakaoMapHtml } from './kakaoMapHtml';

import type { MapStop } from './types';

export type MapRouteLayer = { id: string; color: string; stops: MapStop[] };
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
};

/**
 * 🔴 기본값 배열을 **파일 수준에서 한 번만** 만든다. 함수 시그니처에 `points = []` 라고
 * 쓰면 이 컴포넌트가 다시 그려질 때마다 새 배열이 생기고, 그게 `useEffect` 의 의존성으로
 * 들어가 매번 다시 돈다(S15P21E201-435, 웹 지도가 겪었다).
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

  const sendRender = () => {
    if (!sdkReadyRef.current || !webViewRef.current) return;
    const data = {
      stops,
      points,
      routes: routes ?? [{ id: 'selected', color: color.brand.orange, stops }],
      selectedId,
      currentLocation: currentLocation ?? null,
      colors: { navy: color.brand.navy, orange: color.brand.orange, canvas: color.canvas },
    };
    webViewRef.current.injectJavaScript(`window.__renderKakaoMap(${JSON.stringify(data)}); true;`);
  };

  // stops·points·routes·selectedId·currentLocation 이 바뀔 때마다 이미 떠 있는 지도에
  // 새 데이터를 밀어 넣는다. sdk 가 아직 안 떴으면(sdkReadyRef.current === false) 아무 일도
  // 안 하고, onMessage 의 'sdkLoaded' 처리부가 뜬 직후 한 번 sendRender() 를 부른다.
  useEffect(() => {
    sendRender();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stops, points, routes, selectedId, currentLocation]);

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
  //    (EXPO_PUBLIC_ 값은 빌드 순간 문자열로 박히므로, 폰 빌드에도 이 키가 EAS 환경
  //    변수로 들어가야 한다 — docs/MAP-RECOVERY.md 참고).
  if (!appKey) {
    return (
      <View style={[styles.fallback, { minHeight: height }]}>
        <Text variant="title" weight="bold">{tx('지도 키가 이 빌드에 안 들어갔어요', 'This build was made without a map key')}</Text>
        <Text variant="body" style={styles.description}>
          {tx(
            '앱 지도를 그리려면 빌드할 때 카카오 지도 키가 함께 들어가야 하는데, 이 빌드에는 빈 값이 들어갔습니다. 방문 순서와 장소 목록은 아래에서 그대로 볼 수 있어요.',
            'The app map needs a Kakao map key baked in at build time, and this build got an empty one. You can still use the visit order and place list below.',
          )}
        </Text>
        <Text variant="caption" style={styles.tech}>EXPO_PUBLIC_KAKAO_MAP_JS_KEY = (빈 값) · EAS 빌드 환경 변수로 넣어야 한다</Text>
        {onBackToList ? <Button label={tx('목록으로 돌아가기', 'Back to list')} variant="ghost" onPress={onBackToList} /> : null}
      </View>
    );
  }

  // ② 스크립트를 못 받았다 — 웹의 「지도 파일을 못 받았어요」와 같은 자리다. 다만 폰에서는
  //    서버 CSP(웹의 두 번째 실패 원인)가 적용되지 않는다 — WebView가 로드하는 것은
  //    우리 서버가 아니라 이 자리에서 만든 HTML 문자열이라 우리 nginx 응답 헤더를 안 거친다.
  //    🔴 대신 카카오 콘솔의 "사이트 도메인" 등록이 이 경로(출처가 없는 로컬 HTML)에서도
  //    똑같이 통하는지는 **확인하지 못했다** — 실기기 빌드가 나와야 알 수 있다.
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
        {onBackToList ? <Button label={tx('목록으로 돌아가기', 'Back to list')} variant="ghost" onPress={onBackToList} /> : null}
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
        // 🔴 S15P21E201-1176 — baseUrl 을 꼭 준다. 없으면 지도가 조용히 안 뜬다.
        //
        //    html 만 주면 이 페이지의 출처가 about:blank 가 된다. 카카오 지도 SDK 는
        //    요청을 보낸 페이지의 도메인을 콘솔에 등록된 목록과 맞춰 보는데, 출처가 없으면
        //    맞춰 볼 것이 없어 초기화가 중간에 멈춘다.
        //
        //    🔴 이 실패는 아무 말도 안 한다 — 스크립트 자체는 받아지므로 onerror 가 안 돌고
        //    (2026-09-17 실기기 vc15 확인: 「지도 파일을 못 받았어요」 대체 화면이 안 떴다),
        //    kakao.maps.load 의 콜백만 안 불려 그냥 빈 칸이 남는다. 키를 넣었는데도 빈 칸이면
        //    여기를 먼저 본다.
        //
        //    실측으로 확인한 것(curl 로 SDK 를 직접 받아 봤다):
        //      Referer: https://j15e201.p.ssafy.io  → 200  (등록돼 있다)
        //      Referer: 등록 안 된 도메인            → 401  "domain mismatched!"
        //    즉 막는 것은 키가 아니라 출처다. 그래서 출처를 등록된 값으로 말해 준다.
        source={{ html, baseUrl: MAP_BASE_URL }}
        onMessage={onMessage}
        onError={() => setScriptFailed(true)}
        javaScriptEnabled
        domStorageEnabled
      />
      {onBackToList ? (
        <View style={styles.backRow}>
          <Button label={tx('목록으로 돌아가기', 'Back to list')} variant="ghost" onPress={onBackToList} />
        </View>
      ) : null}
    </View>
  );
}

/**
 * WebView 가 자기 출처로 말할 주소 — S15P21E201-1176.
 *
 * 🔴 여기에 도메인을 새로 적지 않는다. 카카오 콘솔에 등록하는 값과 서버 주소는 같아야 하고,
 *    두 군데 적으면 한쪽만 바뀌는 날이 온다. 그래서 이미 있는 API 주소에서 끌어온다.
 */
const RAW_MAP_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? 'https://j15e201.p.ssafy.io';
const MAP_BASE_URL = RAW_MAP_BASE_URL.endsWith('/') ? RAW_MAP_BASE_URL.slice(0, -1) : RAW_MAP_BASE_URL;

const styles = StyleSheet.create({
  shell: { width: '100%', borderRadius: radius.lg, overflow: 'hidden', backgroundColor: color.surface.soft },
  map: { width: '100%', height: '100%', backgroundColor: 'transparent' },
  backRow: { position: 'absolute', left: spacing[3], bottom: spacing[3] },
  empty: { width: '100%', borderRadius: radius.lg, backgroundColor: color.surface.soft },
  fallback: {
    width: '100%', borderRadius: radius.lg, backgroundColor: color.surface.soft,
    borderWidth: 1, borderColor: color.surface.field,
    alignItems: 'center', justifyContent: 'center', padding: spacing[6], gap: spacing[2],
  },
  description: { color: color.text.body, textAlign: 'center', maxWidth: 420 },
  tech: { color: color.text.muted, textAlign: 'center', maxWidth: 460 },
});
