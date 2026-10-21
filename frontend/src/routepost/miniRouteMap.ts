// 루트 카드의 작은 지도 — 그리는 규칙 한 벌 (S15P21E201-2027).
//
// 회색 상자에 선만 그리던 썸네일을 진짜 카카오 지도 위로 옮긴다(사용자 — 「회색 사각형에 표시하니 너무 이상하다」).
// 여행 지도(src/map/RouteMap.tsx · kakaoMapHtml.ts)와 같은 카카오 지도를 쓰되, 카드용이라 움직이지 않는다:
// 끌기·확대 없음, 경로 범위에 맞춰 한 번 그리고 끝. 카카오 로고(출처 표시)는 지도가 스스로 그린다 — 가리지 않는다.
//
// 이 파일에는 react-native 도 WebView 도 없다 — 순수 함수라 jest 에서 그대로 시험한다.
// 웹은 drawMiniRoute 를 직접 부르고, 앱은 같은 규칙을 문자열로 옮긴 buildMiniRouteMapHtml 을 WebView 에 띄운다.
import { color } from '@/design/tokens';

type Point = [number, number];

/** 위경도를 상자 안 좌표로. 내보내 시험한다. */
export function project(points: Point[], width: number, height: number, pad: number): (p: Point) => [number, number] {
  if (!points.length) return () => [width / 2, height / 2];
  const lats = points.map((p) => p[0]);
  const lngs = points.map((p) => p[1]);
  const minLat = Math.min(...lats), maxLat = Math.max(...lats);
  const minLng = Math.min(...lngs), maxLng = Math.max(...lngs);
  const k = Math.cos(((minLat + maxLat) / 2) * Math.PI / 180);
  const spanX = Math.max((maxLng - minLng) * k, 1e-6);
  const spanY = Math.max(maxLat - minLat, 1e-6);
  const scale = Math.min((width - pad * 2) / spanX, (height - pad * 2) / spanY);
  const offX = (width - spanX * scale) / 2;
  const offY = (height - spanY * scale) / 2;
  return (p) => [offX + (p[1] - minLng) * k * scale, height - (offY + (p[0] - minLat) * scale)];
}

export type MiniRouteData = {
  /** [lat, lng] — 실제 길. 2점 미만이면 정류장을 직선으로 잇는다. */
  line: Array<[number, number]>;
  stops: Array<{ lat: number; lng: number; n: number }>;
};

export type MiniRouteColors = { line: string; casing: string; marker: string; markerText: string };

export const MINI_ROUTE_COLORS: MiniRouteColors = {
  line: color.brand.navy,
  casing: color.surface.card,
  marker: color.action.primary,
  markerText: color.text.onAction,
};

/** 카드 지도에 넘길 값 — 좌표가 아닌 값(NaN 등)은 버린다. 정류장이 없으면 null(지도를 안 띄운다). */
export function miniRouteData(path: Array<[number, number]>, stops: Array<{ lat: number; lng: number }>): MiniRouteData | null {
  const ok = (lat: number, lng: number) => Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
  const cleanStops = stops.map((s, i) => ({ lat: s.lat, lng: s.lng, n: i + 1 })).filter((s) => ok(s.lat, s.lng));
  if (!cleanStops.length) return null;
  const cleanPath = path.filter((p) => ok(p[0], p[1]));
  return { line: cleanPath.length >= 2 ? cleanPath : cleanStops.map((s) => [s.lat, s.lng] as [number, number]), stops: cleanStops };
}

// 웹·앱이 같이 쓰는 그리기 — 아래 DRAW_SOURCE 와 한 글자씩 같은 일을 한다(앱은 Hermes 라 함수.toString() 을 못 쓴다).
const PAD = 26;
// 위쪽은 「루트」 라벨·인기 순위 배지가, 아래쪽은 카카오 축척·로고가 차지한다 — 경로를 그 모서리 밖에 맞춘다.
const PAD_TOP = 52;
const PAD_BOTTOM = 34;

/** 웹: 이미 불러온 kakao.maps 로 el 안에 움직이지 않는 지도를 그린다. */
export function drawMiniRoute(maps: any, el: HTMLElement, data: MiniRouteData, colors: MiniRouteColors = MINI_ROUTE_COLORS): any {
  const first = data.stops[0];
  const map = new maps.Map(el, {
    center: new maps.LatLng(first.lat, first.lng),
    level: 5,
    draggable: false,
    scrollwheel: false,
    disableDoubleClick: true,
    disableDoubleClickZoom: true,
    keyboardShortcuts: false,
  });
  map.setDraggable?.(false);
  map.setZoomable?.(false);
  const path = data.line.map((p) => new maps.LatLng(p[0], p[1]));
  new maps.Polyline({ map, path, strokeWeight: 8, strokeColor: colors.casing, strokeOpacity: 0.95, strokeStyle: 'solid' });
  new maps.Polyline({ map, path, strokeWeight: 4, strokeColor: colors.line, strokeOpacity: 0.9, strokeStyle: 'solid' });
  const bounds = new maps.LatLngBounds();
  data.stops.forEach((s) => {
    const pos = new maps.LatLng(s.lat, s.lng);
    bounds.extend(pos);
    const dot = document.createElement('div');
    dot.textContent = String(s.n);
    dot.setAttribute('style', markerStyle(colors));
    new maps.CustomOverlay({ map, position: pos, content: dot, yAnchor: 0.5, xAnchor: 0.5, zIndex: 2 });
  });
  data.line.forEach((p) => bounds.extend(new maps.LatLng(p[0], p[1])));
  if (data.stops.length > 1 || data.line.length > 1) map.setBounds(bounds, PAD_TOP, PAD, PAD_BOTTOM, PAD);
  return map;
}

function markerStyle(colors: MiniRouteColors): string {
  return `width:22px;height:22px;border-radius:11px;background:${colors.marker};border:2px solid ${colors.casing};color:${colors.markerText};font:700 11px/22px sans-serif;text-align:center;box-sizing:content-box;box-shadow:0 1px 3px rgba(0,0,0,.3)`;
}

/** 앱: WebView 에 띄우는 한 장짜리 HTML. 그리면 'ready', 못 그리면 'fail' 을 보낸다. */
export function buildMiniRouteMapHtml(appKey: string, data: MiniRouteData, colors: MiniRouteColors = MINI_ROUTE_COLORS): string {
  const src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&autoload=false`;
  // </script> 로 끝나는 값이 섞여도 스크립트가 끊기지 않게 '<' 를 풀어 쓴다.
  const json = (v: unknown) => JSON.stringify(v).replace(/</g, '\\u003c');
  return `<!DOCTYPE html><html><head><meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<style>html,body,#map{width:100%;height:100%;margin:0;padding:0;overflow:hidden;background:${colors.casing};}</style>
</head><body><div id="map"></div>
<script>
var D=${json(data)},C=${json(colors)},S=${json(markerStyle(colors))};
function post(t){try{window.ReactNativeWebView.postMessage(t);}catch(e){}}
function draw(){try{var m=kakao.maps,f=D.stops[0];
var map=new m.Map(document.getElementById('map'),{center:new m.LatLng(f.lat,f.lng),level:5,draggable:false,scrollwheel:false,disableDoubleClick:true,disableDoubleClickZoom:true,keyboardShortcuts:false});
map.setDraggable(false);map.setZoomable(false);
var path=D.line.map(function(p){return new m.LatLng(p[0],p[1]);});
new m.Polyline({map:map,path:path,strokeWeight:8,strokeColor:C.casing,strokeOpacity:0.95,strokeStyle:'solid'});
new m.Polyline({map:map,path:path,strokeWeight:4,strokeColor:C.line,strokeOpacity:0.9,strokeStyle:'solid'});
var b=new m.LatLngBounds();
D.stops.forEach(function(s){var pos=new m.LatLng(s.lat,s.lng);b.extend(pos);var d=document.createElement('div');d.textContent=String(s.n);d.setAttribute('style',S);new m.CustomOverlay({map:map,position:pos,content:d,yAnchor:0.5,xAnchor:0.5,zIndex:2});});
D.line.forEach(function(p){b.extend(new m.LatLng(p[0],p[1]));});
if(D.stops.length>1||D.line.length>1)map.setBounds(b,${PAD_TOP},${PAD},${PAD_BOTTOM},${PAD});
post('ready');}catch(e){post('fail');}}
</script>
<script src="${src}" onload="if(window.kakao&&kakao.maps){kakao.maps.load(draw);}else{post('fail');}" onerror="post('fail')"></script>
</body></html>`;
}
