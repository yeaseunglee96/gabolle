import { createElement, useEffect, useRef, useState } from 'react';
import { Platform, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { Button } from '@/components/Button';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import type { MapPathPoint, MapStop } from './types';

declare global { interface Window { kakao?: any } }

const SDK_ID = 'kakao-map-sdk';
const SDK_HOST = 'dapi.kakao.com';
// 지도가 부르는 곳은 `dapi.kakao.com` 하나가 아니다. 그 주소는 시작 파일이고
// 그 안에서 카카오·다음 쪽 주소를 더 부른다. 차단이 두 번째 파일에서 나면
// 첫 파일만 보고 있는 코드는 아무것도 못 잡는다. 그래서 둘 다 본다.
// (어느 주소를 더 부르는지는 유효한 키 없이는 확인할 수 없었다 — 시작 파일이
// 키 없이는 401 만 준다. 그래서 이름을 넓게 잡아 둔다.)
const MAP_HOSTS = /kakao\.com|daumcdn\.net/;

// reason 은 여행자가 읽는 말이고, tech 는 고칠 사람이 읽는 한 줄이다. 둘 다 화면에 낸다
// 고칠 사람이 화면을 볼 때 개발자 도구를 열고 있으리라는 보장이 없다.
type MapFailure = { title: string; reason: string; tech: string };

/** 지도에 그리는 선 하나. */
export type MapRouteLayer = {
  id: string;
  color: string;
  stops: MapStop[];
  /** 실제 길을 따라가는 좌표들. 서버의 경로 응답이 준다. 없으면 stops 를 직선으로 잇는다. */
  path?: MapPathPoint[];
  /** 실제 길이 아니라 직선 추정인가. 안 적으면 추정으로 본다. */
  estimated?: boolean;
};
export type MapPointLayer = { id: string; label: string; color: string; stops: MapStop[] };

// `points` 매개변수 기본값을 여기서 한 번만 만든다. 함수 시그니처에 `points = []` 로
// 직접 쓰면 이 컴포넌트가 스스로 재렌더될 때마다(예: 아래 setFailure) 새 배열이 다시
// 만들어져 effect 의존성이 매번 바뀌고, 그게 다시 setFailure 를 불러 무한 루프가 됐다
// (points 를 안 넘기는 호출부에서 실측 —.
const NO_POINT_LAYERS: MapPointLayer[] = [];

export type CurrentLocation = { latitude: number; longitude: number };

type RouteMapProps = {
  stops: MapStop[];
  selectedId: string;
  onSelect: (id: string) => void;
  routes?: MapRouteLayer[];
  points?: MapPointLayer[];
  /**
   * 위치 권한을 허용했을 때만 준다. 없으면 점을 그리지 않는다 — 거부해도
   * 경로·안내는 그대로 보여야 하므로, 이 지도는 이 값이 없다고 오류로 취급하지 않는다.
   */
  currentLocation?: CurrentLocation | null;
  onBackToList?: () => void;
  height?: number;
};

export function RouteMap({ stops, selectedId, onSelect, routes, points = NO_POINT_LAYERS, currentLocation, onBackToList, height = 340 }: RouteMapProps) {
  const { tx } = useI18n();
  const hostRef = useRef<HTMLElement | null>(null);
  const mapRef = useRef<any>(null);
  const overlaysRef = useRef<any[]>([]);
  const [failure, setFailure] = useState<MapFailure | null>(null);
  const appKey = process.env.EXPO_PUBLIC_KAKAO_MAP_JS_KEY;

  useEffect(() => {
    if (Platform.OS !== 'web') return;
    // ① 키가 아예 없다. EXPO_PUBLIC_ 값은 번들을 만드는 순간 문자열로 박히므로
    // 컨테이너를 띄울 때 주는 것은 소용이 없다 — docker build 에 넘겨야 한다.
    if (!appKey) {
      setFailure({
        title: tx('지도 키가 이 빌드에 안 들어갔어요', 'This build was made without a map key'),
        reason: tx(
          '웹 지도를 그리려면 화면을 빌드할 때 카카오 지도 키가 함께 들어가야 하는데, 이 빌드에는 빈 값이 들어갔습니다. 방문 순서와 장소 목록은 아래에서 그대로 볼 수 있어요.',
          'The web map needs a Kakao map key baked in at build time, and this build got an empty one. You can still use the visit order and place list below.',
        ),
        tech: 'EXPO_PUBLIC_KAKAO_MAP_JS_KEY = (빈 값) · docker build --build-arg 로 넘겨야 한다',
      });
      return;
    }
    const draw = () => {
      // ③ 스크립트는 받았는데 지도 기능이 안 생겼다. 키가 이 도메인에 등록되지 않았을 때 이렇게 된다.
      if (!window.kakao?.maps) {
        setFailure({
          title: tx('지도 서버가 이 주소를 거부했어요', 'The map server rejected this site'),
          reason: tx(
            '지도 파일은 받았는데 지도가 만들어지지 않았습니다. 카카오 개발자 콘솔에 이 도메인이 등록되지 않았을 때 이렇게 됩니다.',
            'The map file loaded but no map was created. This happens when this domain is not registered in the Kakao developer console.',
          ),
          tech: `window.kakao 가 없다 · 등록해야 할 도메인: ${window.location.origin}`,
        });
        return;
      }
      window.kakao.maps.load(() => {
        if (!hostRef.current || !window.kakao) return;
        const maps = window.kakao.maps;
        const center = new maps.LatLng(stops[0].latitude, stops[0].longitude);
        const map = mapRef.current ?? new maps.Map(hostRef.current, { center, level: 8 });
        mapRef.current = map;
        overlaysRef.current.forEach((overlay) => overlay.setMap(null));
        overlaysRef.current = [];
        const bounds = new maps.LatLngBounds();
        const visibleStops = [...stops, ...points.flatMap((layer) => layer.stops)];
        visibleStops.forEach((stop) => {
          const position = new maps.LatLng(stop.latitude, stop.longitude);
          bounds.extend(position);
          const content = document.createElement('button');
          const pointLayer = points.find((layer) => layer.stops.some((item) => item.id === stop.id));
          const markerColor = pointLayer?.color ?? color.brand.navy;
          content.type = 'button';
          content.setAttribute('aria-label', pointLayer ? `${pointLayer.label} ${stop.name}` : tx(`${stop.number}번 ${stop.name}`, `Stop ${stop.number} ${stop.name}`));
          if (stop.imageUrl) {
            const img = document.createElement('img');
            img.src = stop.imageUrl;
            img.alt = '';
            Object.assign(img.style, { width: '100%', height: '100%', objectFit: 'cover', borderRadius: '999px' });
            content.appendChild(img);
            Object.assign(content.style, { width: '40px', height: '40px', padding: '0', overflow: 'hidden', borderRadius: '999px', border: `3px solid ${stop.id === selectedId ? color.action.secondary : markerColor}`, background: color.canvas, cursor: 'pointer', boxShadow: '0 4px 12px rgba(25,25,25,.18)' });
          } else {
            content.textContent = pointLayer ? pointLayer.label : String(stop.number);
            Object.assign(content.style, { minWidth: '34px', height: '34px', padding: '0 8px', borderRadius: '999px', border: `3px solid ${stop.id === selectedId ? color.action.secondary : markerColor}`, background: color.canvas, color: markerColor, fontWeight: '700', cursor: 'pointer', boxShadow: '0 4px 12px rgba(25,25,25,.18)' });
          }
          content.onclick = () => onSelect(stop.id);
          const overlay = new maps.CustomOverlay({ position, content, yAnchor: 0.5 });
          overlay.setMap(map); overlaysRef.current.push(overlay);
        });
        if (currentLocation) {
          const position = new maps.LatLng(currentLocation.latitude, currentLocation.longitude);
          const content = document.createElement('div');
          content.setAttribute('aria-label', tx('현재 위치', 'Your current location'));
          Object.assign(content.style, { width: '18px', height: '18px', borderRadius: '999px', border: `3px solid ${color.canvas}`, background: color.state.dot, boxShadow: '0 0 0 13px rgba(216,58,72,.25), 0 4px 10px rgba(25,25,25,.20)' });
          const overlay = new maps.CustomOverlay({ position, content, yAnchor: 0.5 });
          overlay.setMap(map); overlaysRef.current.push(overlay);
        }
        (routes ?? [{ id: 'selected', color: color.text.heading, stops }]).forEach((route) => {
          // 실제 길 좌표가 있으면 그것을, 없으면 장소를 직선으로 잇는다.
          const points = route.path?.length ? route.path : route.stops;
          const path = points.map((point) => new maps.LatLng(point.latitude, point.longitude));
          // 실제 길이라고 적혀 있을 때만 실선이다. 나머지는 전부 점선이다.
          const real = route.path?.length ? route.estimated === false : false;
          const line = new maps.Polyline({
            path,
            strokeWeight: 5,
            strokeColor: route.color,
            strokeOpacity: real ? 0.9 : 0.75,
            strokeStyle: real ? 'solid' : 'shortdash',
          });
          line.setMap(map); overlaysRef.current.push(line);
        });
        // : stop이 하나면 bounds 넓이가 0이라 setBounds가 지도를 최대 줌으로
        // 밀어붙인다 — 고정 34px 마커가 화면 대부분을 덮어 장소 이름을 가린다. 하나일 때는
        // bounds 대신 그 지점을 도시 단위 줌으로 그냥 센터링한다.
        if (visibleStops.length <= 1) { map.setCenter(center); map.setLevel(5); }
        else map.setBounds(bounds, 60, 60, 60, 60);
        setFailure(null);
      });
    };
    if (window.kakao?.maps) { draw(); return; }

    const onBlocked = (event: SecurityPolicyViolationEvent) => {
      if (!MAP_HOSTS.test(event.blockedURI ?? '')) return;
      setFailure({
        title: tx('이 서버가 지도 스크립트를 막고 있어요', 'This server is blocking the map script'),
        reason: tx(
          '서버의 보안 설정이 카카오 지도 주소를 허용하지 않아 브라우저가 차단했습니다. 서버 설정을 고쳐야 하고, 앱·웹 코드로는 못 고칩니다.',
          "The server's security policy does not allow the Kakao map address, so the browser blocked it. This must be fixed on the server; app code cannot fix it.",
        ),
        tech: `Content-Security-Policy ${event.violatedDirective} 가 ${event.blockedURI} 를 막았다`,
      });
    };
    document.addEventListener('securitypolicyviolation', onBlocked);

    const existing = document.getElementById(SDK_ID) as HTMLScriptElement | null;
    const script = existing ?? document.createElement('script');
    const src = `https://${SDK_HOST}/v2/maps/sdk.js?appkey=${appKey}&autoload=false`;
    if (!existing) { script.id = SDK_ID; script.src = src; document.head.appendChild(script); }
    // 차단 사건과 error 사건은 둘 다 난다. 순서가 규격으로 정해져 있지 않으므로
    // 차단 쪽이 항상 이기게 한다 — 그쪽이 원인을 정확히 말해 주기 때문이다.
    const onError = () => setFailure((prev) => prev ?? {
      title: tx('지도 파일을 못 받았어요', 'Could not fetch the map file'),
      reason: tx(
        '카카오 지도 파일을 받지 못했습니다. 네트워크가 막혔거나, 이 도메인이 카카오 개발자 콘솔에 등록되지 않았을 수 있습니다.',
        'The Kakao map file could not be fetched. The network may be blocked, or this domain may not be registered in the Kakao developer console.',
      ),
      tech: `요청 실패: ${src.replace(appKey, '(키)')}`,
    });
    script.addEventListener('load', draw);
    script.addEventListener('error', onError);
    return () => {
      document.removeEventListener('securitypolicyviolation', onBlocked);
      script.removeEventListener('load', draw);
      script.removeEventListener('error', onError);
    };
  }, [appKey, currentLocation, onSelect, points, routes, selectedId, stops]);

  // 지도에 점선이 하나라도 있으면 그 뜻을 글로 적는다.
  // 점선이 무슨 뜻인지 모르는 사람에게는 실선과 다를 바가 없고, 그러면 점선을 두는
  // 이유가 사라진다. 실제 길만 그려진 지도에는 이 줄이 안 나온다.
  const hasEstimatedLine = (routes ?? [{ id: 'selected', color: '', stops }])
    .some((route) => !(route.path?.length && route.estimated === false));

  if (Platform.OS === 'web') {
    return (
      <View style={styles.webShell}>
        {createElement('div', { ref: hostRef, style: { width: '100%', height }, 'aria-label': tx('여행 동선 지도', 'Trip route map') })}
        {hasEstimatedLine && !failure ? (
          <Text variant="caption" color={color.text.muted} style={styles.estimateNote}>
            {tx('점선은 실제 길이 아니라 장소를 곧게 이은 선이에요.', 'Dashed lines connect places in a straight line, not along real roads.')}
          </Text>
        ) : null}
        {failure ? (
          <View accessibilityRole="alert" style={styles.webFallback}>
            <Text variant="title" weight="bold">{failure.title}</Text>
            <Text variant="body" style={styles.description}>{failure.reason}</Text>
            <Text variant="caption" style={styles.tech}>{failure.tech}</Text>
            {onBackToList ? <Button label={tx('목록으로 돌아가기', 'Back to list')} variant="tertiary" onPress={onBackToList} /> : null}
          </View>
        ) : null}
      </View>
    );
  }

  // 지우지 않고 남기는 이유: `Platform.OS` 가 'web' 도 'ios' 도 'android' 도 아닌
  // 경우(예: 앞으로 생길 다른 플랫폼)에 아무것도 안 돌려주면 화면이 통째로 비어 버린다.
  // 그때 빈 화면 대신 목록이라도 보이게 하는 자리다.
  return (
    <View style={styles.fallback}>
      <Text variant="title" weight="bold">{tx('이 환경에서는 지도를 못 그려요', 'The map cannot be drawn here')}</Text>
      <Text variant="body" style={styles.description}>{tx('방문 순서와 장소 목록은 그대로 확인할 수 있습니다.', 'You can still see the visit order and place list.')}</Text>
      {onBackToList ? <Button label={tx('목록으로 돌아가기', 'Back to list')} variant="tertiary" onPress={onBackToList} /> : null}
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
  webShell: { overflow: 'hidden', borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.soft },
  webFallback: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center', padding: spacing[6], gap: spacing[2], backgroundColor: color.surface.soft },
  fallback: { minHeight: 260, borderRadius: radius.lg, backgroundColor: color.surface.soft, borderWidth: 1, borderColor: color.surface.field, alignItems: 'center', justifyContent: 'center', padding: spacing[6], gap: spacing[2] },
  description: { color: color.text.body, textAlign: 'center', maxWidth: 420 },
  // 고칠 사람이 읽는 한 줄. 여행자에게는 작고 흐리게 보인다.
  estimateNote: { marginTop: spacing[2] },
  tech: { color: color.text.muted, textAlign: 'center', maxWidth: 460 },
  backButton: { minHeight: 44, marginTop: spacing[2], paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' },
  routePreview: { flexDirection: 'row', alignItems: 'center', marginTop: spacing[3] },
  routeItem: { flexDirection: 'row', alignItems: 'center' },
  // 지도를 못 그릴 때의 대체 미리보기 — 번호 마커와 동선이다. 누를 것이 아니라 읽을 것이라 동백을 안 쓴다.
  marker: { width: 30, height: 30, borderRadius: radius.full, backgroundColor: color.action.secondary, alignItems: 'center', justifyContent: 'center' },
  line: { width: 28, height: 2, backgroundColor: color.text.heading },
});
