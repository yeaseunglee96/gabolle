// 왜 이 파일이 따로 있나. 이 파일에는 react-native-webview 도 카카오 SDK 도 안 들어있다
// 순수 문자열 조립뿐이라 이 저장소의 보통 jest(웹) 환경에서 그대로 시험할 수 있다.
// `RouteMap.native.tsx` 는 WebView 를 불러오는데, 그건 네이티브 전용 부품이라 시험 환경에서
// 안 돈다(androidMapKey.ts 를 따로 뗀 것과 같은 이유).
export function buildKakaoMapHtml(appKey: string): string {
  // URL 안에 그대로 넣는 값이라 encodeURIComponent 로 감싼다 — 키에 `&` 같은 글자가
  // 섞이면 그 뒤 쿼리 파라미터(autoload=false)를 깨뜨릴 수 있다.
  const src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&autoload=false`;
  return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<style>html,body,#map{width:100%;height:100%;margin:0;padding:0;background:transparent;}</style>
</head>
<body>
<div id="map"></div>
<script>
  // 웹 버전(RouteMap.tsx)과 같은 자리를 채운다 — 지도 객체 하나를 계속 재사용하고
  // 부를 때마다 오버레이(마커·선·현재 위치 점)만 지우고 다시 그린다.
  var map = null;
  var overlays = [];

  function post(type, payload) {
    if (window.ReactNativeWebView) {
      window.ReactNativeWebView.postMessage(JSON.stringify({ type: type, payload: payload }));
    }
  }

  // RN 쪽(RouteMap.native.tsx)이 injectJavaScript 로 이 함수를 부른다.
  // data 모양은 RouteMap.tsx 의 draw 안 로직과 같은 것을 그대로 옮긴 것이다.
  window.__renderKakaoMap = function (data) {
    if (!window.kakao || !window.kakao.maps) return;
    var maps = window.kakao.maps;
    var stops = data.stops || [];
    var points = data.points || [];
    var routes = data.routes || [];
    var selectedId = data.selectedId;
    var currentLocation = data.currentLocation;
    var colors = data.colors;
    var pointStops = [];
    for (var p = 0; p < points.length; p++) {
      for (var q = 0; q < points[p].stops.length; q++) pointStops.push(points[p].stops[q]);
    }
    var visible = stops.concat(pointStops);
    if (!visible.length) return;

    var center = new maps.LatLng(visible[0].latitude, visible[0].longitude);
    if (!map) map = new maps.Map(document.getElementById('map'), { center: center, level: 8 });

    for (var i = 0; i < overlays.length; i++) overlays[i].setMap(null);
    overlays = [];

    var bounds = new maps.LatLngBounds();
    for (var s = 0; s < visible.length; s++) {
      var stop = visible[s];
      var position = new maps.LatLng(stop.latitude, stop.longitude);
      bounds.extend(position);
      var layer = null;
      for (var l = 0; l < points.length; l++) {
        for (var k = 0; k < points[l].stops.length; k++) {
          if (points[l].stops[k].id === stop.id) { layer = points[l]; break; }
        }
        if (layer) break;
      }
      var markerColor = layer ? layer.color : colors.navy;
      var content = document.createElement('button');
      content.type = 'button';
      content.setAttribute('aria-label', layer ? (layer.label + ' ' + stop.name) : (stop.number + '번 ' + stop.name));
      if (stop.imageUrl) {
        var img = document.createElement('img');
        img.src = stop.imageUrl;
        img.alt = '';
        img.style.width = '100%'; img.style.height = '100%'; img.style.objectFit = 'cover'; img.style.borderRadius = '999px';
        content.appendChild(img);
        content.style.width = '40px'; content.style.height = '40px'; content.style.padding = '0'; content.style.overflow = 'hidden';
        content.style.borderRadius = '999px';
        content.style.border = '3px solid ' + (stop.id === selectedId ? colors.orange : markerColor);
        content.style.background = colors.canvas; content.style.cursor = 'pointer'; content.style.boxShadow = '0 4px 12px rgba(11,29,58,.18)';
      } else {
        content.textContent = layer ? layer.label : String(stop.number);
        content.style.minWidth = '34px'; content.style.height = '34px'; content.style.padding = '0 8px';
        content.style.borderRadius = '999px';
        content.style.border = '3px solid ' + (stop.id === selectedId ? colors.orange : markerColor);
        content.style.background = colors.canvas; content.style.color = markerColor; content.style.fontWeight = '700';
        content.style.cursor = 'pointer'; content.style.boxShadow = '0 4px 12px rgba(11,29,58,.18)';
      }
      (function (stopId) { content.onclick = function () { post('select', stopId); }; })(stop.id);
      var overlay = new maps.CustomOverlay({ position: position, content: content, yAnchor: 0.5 });
      overlay.setMap(map); overlays.push(overlay);
    }

    if (currentLocation) {
      var curPos = new maps.LatLng(currentLocation.latitude, currentLocation.longitude);
      var curEl = document.createElement('div');
      curEl.setAttribute('aria-label', '현재 위치');
      curEl.style.width = '18px'; curEl.style.height = '18px'; curEl.style.borderRadius = '999px';
      curEl.style.border = '3px solid ' + colors.canvas; curEl.style.background = colors.navy;
      curEl.style.boxShadow = '0 0 0 2px rgba(11,29,58,.35), 0 4px 10px rgba(11,29,58,.28)';
      var curOverlay = new maps.CustomOverlay({ position: curPos, content: curEl, yAnchor: 0.5 });
      curOverlay.setMap(map); overlays.push(curOverlay);
    }

    for (var r = 0; r < routes.length; r++) {
      var route = routes[r];
      var points = (route.path && route.path.length) ? route.path : route.stops;
      if (points.length < 2) continue;
      var path = [];
      for (var pi = 0; pi < points.length; pi++) path.push(new maps.LatLng(points[pi].latitude, points[pi].longitude));
      // 실제 길 좌표가 있고 "추정 아님" 이라고 적혀 있을 때만 실선이다
      // 나머지는 직선을 이은 것이므로 점선으로 그린다 — 실선은 "이 길로 가면 된다" 는 뜻이다.
      var real = !!(route.path && route.path.length) && route.estimated === false;
      var line = new maps.Polyline({ path: path, strokeWeight: 5, strokeColor: route.color, strokeOpacity: real ? 0.9 : 0.75, strokeStyle: real ? 'solid' : 'shortdash' });
      line.setMap(map); overlays.push(line);
    }

    // 와 같은 이유 — 점이 하나면 bounds 넓이가 0이라 최대 줌으로 튄다.
    if (visible.length <= 1) { map.setCenter(center); map.setLevel(5); }
    else map.setBounds(bounds, 60, 60, 60, 60);

    post('ready', null);
  };

  document.addEventListener('DOMContentLoaded', function () {
    var script = document.createElement('script');
    script.src = ${JSON.stringify(src)};
    script.onload = function () {
      if (!window.kakao || !window.kakao.maps) { post('scriptError', 'kakao 전역이 없다'); return; }
      window.kakao.maps.load(function () { post('sdkLoaded', null); });
    };
    script.onerror = function () { post('scriptError', '스크립트 요청 실패'); };
    document.head.appendChild(script);
  });
</script>
</body>
</html>`;
}

// RouteMap.tsx·RouteMap.native.tsx 양쪽이 마커 색을 이 표기로 넘긴다 — 디자인 토큰의
// hex 값을 그대로 문자열로 실어 보낸다(HTML 안 JS는 우리 색 토큰 파일을 못 읽는다).
export type KakaoMapColors = { navy: string; orange: string; canvas: string };
