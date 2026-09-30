import { createElement, useEffect, useRef, useState } from 'react';
import { Platform, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { Button } from '@/components/Button';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import type { MapPathPoint, MapStop } from './types';
import { fitPadding, focusShiftY } from './mapFocus';
import { simplifyPath } from './simplifyPath';
import { gradedSegments, type RouteGrading } from './routeGrading';
import type { SlopePiece } from './slopeGrades';
import { txf } from '@/i18n/format';

declare global { interface Window { kakao?: any } }

// 일정 경로 선의 굵기와, 그 아래 까는 흰 테두리가 양옆으로 더 나오는 폭(S15P21E201-1656).
const ROUTE_WEIGHT = 5;
const CASING_EXTRA = 4;
// 실제 길은 진하게, 어림은 옅게 — 점선으로 가르던 것을 불투명도로 가른다.
const REAL_OPACITY = 0.9;
const ESTIMATED_OPACITY = 0.45;
// 이만큼(픽셀)보다 작은 꺾임은 화면에서 안 보이니 덜어 낸다.
const SIMPLIFY_PIXELS = 2;

/** 지금 줌에서 한 픽셀이 몇 미터인가 — 화면의 두 점(100픽셀 떨어진)이 가리키는 땅의 거리로 잰다. 못 재면 0(덜어 내지 않는다). */
function metersPerPixel(map: any, maps: any): number {
  try {
    const projection = map.getProjection();
    const a = projection.coordsFromContainerPoint(new maps.Point(0, 0));
    const b = projection.coordsFromContainerPoint(new maps.Point(100, 0));
    const lat = (a.getLat() * Math.PI) / 180;
    const dx = (b.getLng() - a.getLng()) * 111320 * Math.cos(lat);
    const dy = (b.getLat() - a.getLat()) * 110540;
    return Math.hypot(dx, dy) / 100;
  } catch {
    return 0;
  }
}

/**
 * 고른 마커는 **커진다.** 테두리 색만 바꾸면 지도를 훑는 눈이 어느 것이 켜졌는지 못 찾는다 — 마커가 열 개 넘게
 * 겹쳐 있을 때 특히 그렇다(시안 3절: 선택 마커 scale 1.25, 300ms). 고른 곳이 바뀌면 이것만 다시 부른다.
 */
function styleSelection(el: HTMLElement, markerColor: string, selected: boolean) {
  el.style.border = `3px solid ${selected ? color.action.secondary : markerColor}`;
  el.style.transform = selected ? 'scale(1.25)' : 'scale(1)';
  el.style.zIndex = selected ? '2' : '1';
}

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
  /**
   * 선 굵기·불투명도 — 경로 아래 깔리는 보조 선만 준다. 안 주면 경로 선 그대로.
   * (전에는 경사·그늘 겹 S15P21E201-1569 가 썼다. 그 겹은 S15P21E201-1896 에서 여행 페이지에서 뺐고, 지금 이 값을 주는 곳은 없다.)
   */
  weight?: number;
  opacity?: number;
  /** 걷는 길의 경사 조각(S15P21E201-1658) — 있으면 path 를 조각마다 잘라 고른 조건의 색으로 긋는다(routeGrading.ts). */
  pieces?: SlopePiece[];
  /**
   * 사용자가 고른 조건(S15P21E201-1896) — 이 조건으로 조각을 칠한다. 둘 다 false 면 조각이 있어도 경로 자기 색(color) 한 가지로 그린다.
   * 🔴 안 주면 전과 같다(경사로 칠한다) — 조건을 모르는 화면(이동 경로 상세)의 동작을 바꾸지 않는다.
   */
  grading?: RouteGrading;
};
export type MapPointLayer = { id: string; label: string; color: string; stops: MapStop[] };

// `points` 매개변수 기본값을 여기서 한 번만 만든다. 함수 시그니처에 `points = []` 로
// 직접 쓰면 이 컴포넌트가 스스로 재렌더될 때마다(예: 아래 setFailure) 새 배열이 다시
// 만들어져 effect 의존성이 매번 바뀌고, 그게 다시 setFailure 를 불러 무한 루프가 됐다
// (points 를 안 넘기는 호출부에서 실측 — S15P21E201-435).
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
  /**
   * 고른 곳을 지도 가운데로 옮긴다 — 여행 페이지 시안 「장소를 누르면 지도가 그 위치를 가운데로」
   * (S15P21E201-1535). 기본은 꺼짐: 다른 화면은 지금처럼 모든 점이 들어오게만 맞춘다.
   */
  focusSelected?: boolean;
  /**
   * 지도 아래쪽이 창에 가려진 높이(px). 전체를 맞출 때 그만큼 아래 여백을 더 두고, 고른 곳은 보이는 부분의 가운데로
   * 옮긴다(S15P21E201-1607 — 폰 여행 화면은 지도를 줄이지 않고 창을 겹쳐 올린다).
   * 🔴 이 값만 바뀌어서는 다시 맞추지 않는다. 창을 여닫을 때마다 지도가 가운데를 다시 잡으며 튀면 안 된다 —
   *    다음에 맞출 때(고른 곳이 바뀔 때 등) 쓴다.
   */
  bottomInset?: number;
  /** 지도 위쪽이 상태바·지도 위 칩에 가려진 높이(px). 맞출 때 그만큼 위 여백을 더 둔다(S15P21E201-1754). */
  topInset?: number;
  /**
   * 이 값이 null 이 아닌 새 값으로 바뀌면 지금 여백으로 한 번 다시 맞춘다(S15P21E201-1754). 폰 여행 화면이 «지도 보기»로
   * 창을 접을 때 쓴다 — 창이 열린 채 맞춘 큰 아래 여백이 남아 경로가 위쪽에 몰려 있었다. null 로 바뀔 때는 안 맞춘다.
   */
  refitKey?: string | number | null;
};

export function RouteMap({ stops, selectedId, onSelect, routes, points = NO_POINT_LAYERS, currentLocation, onBackToList, height = 340, focusSelected = false, bottomInset = 0, topInset = 0, refitKey = null }: RouteMapProps) {
  const { tx } = useI18n();
  const hostRef = useRef<HTMLElement | null>(null);
  const mapRef = useRef<any>(null);
  const overlaysRef = useRef<any[]>([]);
  // 마지막으로 맞춘 범위 — 칸 크기가 바뀌면 같은 범위를 새 크기에 다시 맞춘다.
  const fitRef = useRef<(() => void) | null>(null);
  // 가려진 높이는 «맞출 때» 읽는다 — 의존성에 넣으면 창을 여닫을 때마다 지도가 다시 맞춰져 튄다.
  const insetRef = useRef(bottomInset);
  insetRef.current = bottomInset;
  const topInsetRef = useRef(topInset);
  topInsetRef.current = topInset;
  // 지도 칸의 실제 높이 — 여백이 칸보다 커지지 않게 잰다.
  const hostHeight = () => hostRef.current?.clientHeight || height;
  const [failure, setFailure] = useState<MapFailure | null>(null);
  const appKey = process.env.EXPO_PUBLIC_KAKAO_MAP_JS_KEY;
  // 🔴 고른 곳·누를 때 부를 함수·현재 위치는 «다시 그리기» 조건이 아니다(S15P21E201-1654). 전에는 셋 중 하나만 바뀌어도
  //    마커·선을 전부 새로 그리고 전체 맞추기(setBounds — 순간 이동)를 한 뒤 고른 곳으로 panTo 해서, 고를 때마다
  //    여행 전체의 가운데에서 출발해 날아왔다(사용자 폰 — 「늘 왼쪽에서 날아온다」). 그리는 동안에는 이 값들을 ref 로 읽는다.
  const selectedRef = useRef(selectedId);
  selectedRef.current = selectedId;
  const focusRef = useRef(focusSelected);
  focusRef.current = focusSelected;
  const onSelectRef = useRef(onSelect);
  onSelectRef.current = onSelect;
  // 마커마다 그 요소와 테두리 색 — 고른 곳이 바뀌면 이 둘만 고친다.
  const markersRef = useRef(new Map<string, { el: HTMLElement; color: string }>());
  // 지금 지도가 옮겨 가 있는 고른 곳 — 같은 값으로 다시 오면 옮기지 않는다.
  const appliedSelectionRef = useRef<string | null>(null);
  // 고른 곳으로 옮기는 함수 — 그릴 때 만든다(그때의 장소 목록을 안다).
  const focusFnRef = useRef<(() => void) | null>(null);
  const locationOverlayRef = useRef<any>(null);
  const [mapReady, setMapReady] = useState(false);
  // 그린 경로 선마다 원래 점들과 그 선(테두리·선) — 줌이 바뀌면 덜어 낸 모양을 다시 셈한다(S15P21E201-1656).
  const lineRecordsRef = useRef<Array<{ points: MapPathPoint[]; lines: any[] }>>([]);

  useEffect(() => {
    if (Platform.OS !== 'web') return;
    // ① 키가 아예 없다. EXPO_PUBLIC_ 값은 번들을 만드는 순간 문자열로 박히므로
    // 컨테이너를 띄울 때 주는 것은 소용이 없다 — docker build 에 넘겨야 한다.
    if (!appKey) {
      setFailure({
        title: tx('지금은 지도를 불러올 수 없어요', 'The map can’t be shown right now'),
        reason: tx(
          '지도 설정에 문제가 있어 지도를 그리지 못했어요. 방문 순서와 장소 목록은 아래에서 그대로 볼 수 있어요.',
          'Something is wrong with the map setup, so we couldn’t draw it. You can still use the visit order and place list below.',
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
        const firstDraw = !mapRef.current;
        const map = mapRef.current ?? new maps.Map(hostRef.current, { center, level: 8 });
        mapRef.current = map;
        // 줌에 따라 덜어 내는 정도가 다르다 — 줌이 바뀌면 그려 둔 경로 선의 모양만 다시 셈한다(다시 그리지 않는다).
        const resimplify = () => {
          const tolerance = metersPerPixel(map, maps) * SIMPLIFY_PIXELS;
          if (!(tolerance > 0)) return;
          lineRecordsRef.current.forEach(({ points, lines }) => {
            const path = simplifyPath(points, tolerance).map((point) => new maps.LatLng(point.latitude, point.longitude));
            lines.forEach((line) => line.setPath(path));
          });
        };
        if (firstDraw) maps.event?.addListener(map, 'zoom_changed', resimplify);
        overlaysRef.current.forEach((overlay) => overlay.setMap(null));
        overlaysRef.current = [];
        const bounds = new maps.LatLngBounds();
        const visibleStops = [...stops, ...points.flatMap((layer) => layer.stops)];
        const selectedNow = selectedRef.current;
        markersRef.current = new Map();
        visibleStops.forEach((stop) => {
          const position = new maps.LatLng(stop.latitude, stop.longitude);
          bounds.extend(position);
          const content = document.createElement('button');
          const pointLayer = points.find((layer) => layer.stops.some((item) => item.id === stop.id));
          const markerColor = pointLayer?.color ?? color.brand.navy;
          content.type = 'button';
          content.setAttribute('aria-label', pointLayer ? `${pointLayer.label} ${stop.name}` : txf(tx, '%s번 %s', 'Stop %s %s', stop.number, stop.name));
          if (stop.imageUrl) {
            const img = document.createElement('img');
            img.src = stop.imageUrl;
            img.alt = '';
            Object.assign(img.style, { width: '100%', height: '100%', objectFit: 'cover', borderRadius: '999px' });
            content.appendChild(img);
            Object.assign(content.style, { width: '40px', height: '40px', padding: '0', overflow: 'hidden', borderRadius: '999px', background: color.canvas, cursor: 'pointer', boxShadow: '0 4px 12px rgba(25,25,25,.18)' });
          } else {
            content.textContent = pointLayer ? pointLayer.label : String(stop.number);
            Object.assign(content.style, { minWidth: '34px', height: '34px', padding: '0 8px', borderRadius: '999px', background: color.canvas, color: markerColor, fontWeight: '700', cursor: 'pointer', boxShadow: '0 4px 12px rgba(25,25,25,.18)' });
          }
          content.style.transition = 'transform 300ms cubic-bezier(.34,1.3,.64,1)';
          styleSelection(content, markerColor, stop.id === selectedNow);
          markersRef.current.set(stop.id, { el: content, color: markerColor });
          content.onclick = () => onSelectRef.current(stop.id);
          // 🔴 출발지·숙소 같은 표시는 번호 장소 아래에 깐다(S15P21E201-1788) — 가까우면 「출발지」가 1번을 덮었다.
          //    겹쳐도 글자가 더 넓어 옆으로 보인다. kakaoMapHtml.ts 와 같은 값.
          const overlay = new maps.CustomOverlay({ position, content, yAnchor: 0.5, zIndex: pointLayer ? 1 : 2 });
          overlay.setMap(map); overlaysRef.current.push(overlay);
        });
        // 🔴 일정 경로 선 하나 — 넓은 흰 테두리를 먼저 깔고 그 위에 실선(S15P21E201-1656). 전에는 어림 구간을 5px 짧은
        //    점선으로 그렸는데, 선 모양이 자동차 길이라 꺾임점이 촘촘해서 점선이 꺾임마다 끊기며 떨려 보였다(사용자 —
        //    「지글지글하다」). 줌이 멀면 안 보이는 꺾임은 덜어 낸다. 한 경로를 여러 색 조각으로 그릴 때도 이것을 조각마다 부른다.
        lineRecordsRef.current = [];
        const tolerance = metersPerPixel(map, maps) * SIMPLIFY_PIXELS;
        // 흰 테두리는 경로 전체에 한 번만 깔고 그 위에 조각별 색 선을 긋는다 — 조각마다 테두리를 깔면 이음매마다
        // 흰 점이 생긴다. 조각이 없으면 한 조각(경로 색)이다.
        const toPath = (points: MapPathPoint[]) => simplifyPath(points, tolerance).map((point) => new maps.LatLng(point.latitude, point.longitude));
        const drawRouteLine = (points: MapPathPoint[], parts: Array<{ points: MapPathPoint[]; color: string }>, opacity: number) => {
          const casing = new maps.Polyline({ path: toPath(points), strokeWeight: ROUTE_WEIGHT + CASING_EXTRA, strokeColor: color.surface.card, strokeOpacity: 0.95, strokeStyle: 'solid' });
          casing.setMap(map); overlaysRef.current.push(casing);
          lineRecordsRef.current.push({ points, lines: [casing] });
          parts.forEach((part) => {
            const line = new maps.Polyline({ path: toPath(part.points), strokeWeight: ROUTE_WEIGHT, strokeColor: part.color, strokeOpacity: opacity, strokeStyle: 'solid' });
            line.setMap(map); overlaysRef.current.push(line);
            lineRecordsRef.current.push({ points: part.points, lines: [line] });
          });
        };
        (routes ?? [{ id: 'selected', color: color.text.heading, stops }]).forEach((route) => {
          // 실제 길 좌표가 있으면 그것을, 없으면 장소를 직선으로 잇는다.
          const points = route.path?.length ? route.path : route.stops;
          // 실제 길이라고 적혀 있을 때만 진하다. 나머지는 어림이라 옅다.
          const real = route.path?.length ? route.estimated === false : false;
          if (route.weight == null) {
            // 걷는 길의 조각이 있고 조건을 골랐으면 조각마다 그 조건의 색(S15P21E201-1658 · -1896). 조각은 실제 길에만 온다.
            const segments = route.path?.length ? gradedSegments(route.path, route.pieces, route.grading) : null;
            drawRouteLine(points, segments ?? [{ points, color: route.color }], route.opacity ?? (real ? REAL_OPACITY : ESTIMATED_OPACITY));
            return;
          }
          // 굵기를 직접 준 보조 선(전의 경사·그늘 겹)은 경로 아래 깔리는 옅은 띠라 그대로 그린다.
          const line = new maps.Polyline({
            path: points.map((point) => new maps.LatLng(point.latitude, point.longitude)),
            strokeWeight: route.weight,
            strokeColor: route.color,
            strokeOpacity: route.opacity ?? (real ? 0.9 : 0.75),
            strokeStyle: real ? 'solid' : 'shortdash',
          });
          line.setMap(map); overlaysRef.current.push(line);
        });
        // : stop이 하나면 bounds 넓이가 0이라 setBounds가 지도를 최대 줌으로
        // 밀어붙인다 — 고정 34px 마커가 화면 대부분을 덮어 장소 이름을 가린다. 하나일 때는
        // bounds 대신 그 지점을 도시 단위 줌으로 그냥 센터링한다.
        const fit = () => {
          if (visibleStops.length <= 1) { map.setCenter(center); map.setLevel(5); return; }
          const [top, right, bottom, left] = fitPadding(insetRef.current, hostHeight(), topInsetRef.current);
          map.setBounds(bounds, top, right, bottom, left);
        };
        // 🔴 모두 들어오게 맞춘 «다음에» 고른 곳으로 민다(panTo 는 부드럽게 옮긴다). 맞추기를 건너뛰면
        //    처음 열었을 때 줌이 도시 전체(level 8)라 점들이 한 덩어리로 뭉친다.
        // 고른 곳으로 «지금 화면·줌에서» 민다 — 고른 곳만 바뀔 때는 이것만 부른다(아래 effect).
        const focusOnSelected = () => {
          if (!focusRef.current) return;
          // 점 표시(지하철역 등)도 고를 수 있다 — 번호 장소만 찾으면 역을 눌러도 지도가 안 움직였다(S15P21E201-1834).
          const selectedStop = visibleStops.find((stop) => stop.id === selectedRef.current);
          if (!selectedStop) return;
          // 보이는 부분의 가운데로 — 지도 중심을 가린 높이의 절반만큼 아래에 둔다(mapFocus.ts).
          const target = new maps.LatLng(selectedStop.latitude, selectedStop.longitude);
          const shift = focusShiftY(insetRef.current, hostHeight(), topInsetRef.current);
          if (!shift) { map.panTo(target); return; }
          const projection = map.getProjection();
          const point = projection.containerPointFromCoords(target);
          map.panTo(projection.coordsFromContainerPoint(new maps.Point(point.x, point.y + shift)));
        };
        const fitAndFocus = () => { fit(); focusOnSelected(); };
        fitAndFocus(); fitRef.current = fitAndFocus; focusFnRef.current = focusOnSelected;
        // 맞추면 줌이 바뀐다 — 줌 사건이 안 오는 환경도 있어 맞춘 뒤 한 번 더 셈한다.
        resimplify();
        appliedSelectionRef.current = selectedNow;
        setMapReady(true);
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
    // 고른 곳·현재 위치·누를 때 함수는 일부러 뺐다 — 위 selectedRef 설명.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [appKey, points, routes, stops]);

  // 고른 곳만 바뀌면 다시 그리지 않는다 — 마커 모양만 바꾸고 지금 화면·줌에서 panTo(S15P21E201-1654).
  useEffect(() => {
    if (Platform.OS !== 'web' || !mapReady) return;
    markersRef.current.forEach(({ el, color: markerColor }, id) => styleSelection(el, markerColor, id === selectedId));
    if (appliedSelectionRef.current === selectedId) return;
    appliedSelectionRef.current = selectedId;
    focusFnRef.current?.();
  }, [mapReady, selectedId]);

  // 현재 위치는 점만 옮긴다 — 움직일 때마다 전체를 다시 맞추면 걷는 내내 지도가 튄다.
  const locationLat = currentLocation?.latitude;
  const locationLng = currentLocation?.longitude;
  useEffect(() => {
    if (Platform.OS !== 'web' || !mapReady || !window.kakao?.maps) return;
    const maps = window.kakao.maps;
    if (locationLat == null || locationLng == null) {
      locationOverlayRef.current?.setMap(null);
      locationOverlayRef.current = null;
      return;
    }
    const position = new maps.LatLng(locationLat, locationLng);
    if (locationOverlayRef.current) { locationOverlayRef.current.setPosition(position); return; }
    const content = document.createElement('div');
    content.setAttribute('aria-label', tx('현재 위치', 'Your current location'));
    Object.assign(content.style, { width: '18px', height: '18px', borderRadius: '999px', border: `3px solid ${color.canvas}`, background: color.state.dot, boxShadow: '0 0 0 13px rgba(216,58,72,.25), 0 4px 10px rgba(25,25,25,.20)' });
    const overlay = new maps.CustomOverlay({ position, content, yAnchor: 0.5 });
    overlay.setMap(mapRef.current);
    locationOverlayRef.current = overlay;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mapReady, locationLat, locationLng]);

  // 🔴 칸 크기가 바뀌면 지도에 말해 줘야 한다 — S15P21E201-1417. 카카오 지도는 만들어질 때의 크기만 알고,
  //    피드의 지도 시트는 열리면서 커진다. 안 말해 주면 처음 크기만큼(맨 위 한 줄)만 타일을 그리고
  //    나머지는 회색 「kakaomap」 바탕이다. height 가 바뀔 때와, 그 밖의 이유로 칸이 늘어날 때(ResizeObserver) 둘 다.
  useEffect(() => {
    if (Platform.OS !== 'web') return;
    const host = hostRef.current;
    const relayout = () => { const map = mapRef.current; if (!map) return; map.relayout(); fitRef.current?.(); };
    relayout();
    if (!host || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(() => relayout());
    observer.observe(host);
    return () => observer.disconnect();
  }, [height]);

  // «지도 보기»로 창을 접었을 때 한 번 다시 맞춘다(S15P21E201-1754, 앱 RouteMap.native.tsx 와 같은 규칙).
  // 🔴 null 로 바뀔 때(창을 다시 열 때)는 안 맞춘다 — 창을 열 때 지도가 튀면 안 된다.
  const lastRefitKey = useRef(refitKey);
  useEffect(() => {
    if (refitKey === lastRefitKey.current) return;
    lastRefitKey.current = refitKey;
    if (refitKey != null) fitRef.current?.();
  }, [refitKey]);

  // 지도에 어림 선(옅은 선)이 하나라도 있으면 그 뜻을 글로 적는다(S15P21E201-1656 — 점선 대신 옅게 그린다).
  // 옅은 선이 무슨 뜻인지 모르는 사람에게는 진한 선과 다를 바가 없고, 그러면 옅게 그리는
  // 이유가 사라진다. 실제 길만 그려진 지도에는 이 줄이 안 나온다.
  const hasEstimatedLine = (routes ?? [{ id: 'selected', color: '', stops }])
    .some((route) => !(route.path?.length && route.estimated === false));

  if (Platform.OS === 'web') {
    return (
      <View style={styles.webShell}>
        {createElement('div', { ref: hostRef, style: { width: '100%', height }, 'aria-label': tx('여행 동선 지도', 'Trip route map') })}
        {hasEstimatedLine && !failure ? (
          <Text variant="caption" color={color.text.muted} style={styles.estimateNote}>
            {/* 걷는 길 경사 색의 안내 문구는 뺐다 — 어색하다는 사용자 결정(S15P21E201-1820). 색 선은 그대로 긋는다. */}
            {tx('옅은 선은 어림한 길이라 실제로 가는 길과 다를 수 있어요.', 'Faded lines are estimates and may differ from the way you actually go.')}
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
  /**
   * 어림 선 안내문 — **지도 «위에» 얹는다.**
   *
   * 🔴 전에는 지도 아래에 흐름으로 붙어 있었다. 그래서 이 부품의 실제 높이가
   *    `height` 보다 «안내문 한 줄만큼» 컸고, 남는 자리를 재서 높이를 주는 화면에서는
   *    그만큼 넘쳐 아래 것을 덮었다 (2026-09-22 실기 — 추천 시트에서 정차지 목록과
   *    겹쳤다). 얹으면 이 부품의 높이가 곧 `height` 라 그런 어긋남이 없다.
   *
   * 🔴 왼쪽이 아니라 **오른쪽 아래**다. 왼쪽 아래는 카카오 축척과 로고 자리다 —
   *    가리면 지도 이용약관을 어긴다.
   */
  estimateNote: {
    position: 'absolute', right: spacing[2], bottom: spacing[2], maxWidth: '92%',
    paddingVertical: spacing[1], paddingHorizontal: spacing[2],
    borderRadius: radius.md, backgroundColor: color.surface.card,
  },
  tech: { color: color.text.muted, textAlign: 'center', maxWidth: 460 },
  backButton: { minHeight: 44, marginTop: spacing[2], paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' },
  routePreview: { flexDirection: 'row', alignItems: 'center', marginTop: spacing[3] },
  routeItem: { flexDirection: 'row', alignItems: 'center' },
  // 지도를 못 그릴 때의 대체 미리보기 — 번호 마커와 동선이다. 누를 것이 아니라 읽을 것이라 동백을 안 쓴다.
  marker: { width: 30, height: 30, borderRadius: radius.full, backgroundColor: color.action.secondary, alignItems: 'center', justifyContent: 'center' },
  line: { width: 28, height: 2, backgroundColor: color.text.heading },
});
