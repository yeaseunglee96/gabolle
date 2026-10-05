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
  var fit = null;
  var FOCUS_LEVEL = 4;
  var padNow = [60, 60, 60, 60];
  // 🔴 고른 곳이 바뀌면 다시 그리지 않는다(S15P21E201-1654). 전에는 고를 때마다 마커·선을 전부 새로 그리고
  //    전체 맞추기(setBounds — 순간 이동)를 해서 지도가 여행 전체로 튀었다. 고를 때는 아래 값만 고친다.
  var markers = {};
  var stopsById = {};
  var colorsNow = null;
  var selectedNow = null;
  var focusNow = false;
  var shiftNow = 0;
  var locationOverlay = null;
  // 그린 경로 선마다 원래 점들과 그 선(테두리·선) — 줌이 바뀌면 덜어 낸 모양만 다시 셈한다(S15P21E201-1656).
  var lineRecords = [];
  // 웹 RouteMap.tsx 의 ROUTE_WEIGHT·CASING_EXTRA·REAL_OPACITY·ESTIMATED_OPACITY·SIMPLIFY_PIXELS 와 같은 값.
  var ROUTE_WEIGHT = 5, CASING_EXTRA = 4, REAL_OPACITY = 0.9, ESTIMATED_OPACITY = 0.45, SIMPLIFY_PIXELS = 2;

  // 🔴 src/map/simplifyPath.ts 와 같은 셈이다(더글러스-푀커). 이 스크립트는 그 파일을 못 읽어서 한 벌 더 들고 있다 — 고치면 둘 다.
  function simplify(points, tolerance) {
    if (points.length < 3 || !(tolerance > 0)) return points;
    var mx = 111320 * Math.cos(points[0].latitude * Math.PI / 180), my = 110540;
    var xy = [];
    for (var i = 0; i < points.length; i++) xy.push([points[i].longitude * mx, points[i].latitude * my]);
    var keep = []; for (var k = 0; k < points.length; k++) keep.push(false);
    keep[0] = true; keep[points.length - 1] = true;
    var spans = [[0, points.length - 1]];
    while (spans.length) {
      var span = spans.pop(), from = span[0], to = span[1], far = -1, farDist = tolerance;
      var ax = xy[from][0], ay = xy[from][1], dx = xy[to][0] - ax, dy = xy[to][1] - ay, len = dx * dx + dy * dy;
      for (var j = from + 1; j < to; j++) {
        var t = len === 0 ? 0 : Math.max(0, Math.min(1, ((xy[j][0] - ax) * dx + (xy[j][1] - ay) * dy) / len));
        var d = Math.sqrt(Math.pow(xy[j][0] - (ax + t * dx), 2) + Math.pow(xy[j][1] - (ay + t * dy), 2));
        if (d > farDist) { farDist = d; far = j; }
      }
      if (far >= 0) { keep[far] = true; spans.push([from, far], [far, to]); }
    }
    var out = [];
    for (var m = 0; m < points.length; m++) if (keep[m]) out.push(points[m]);
    return out;
  }

  // 지금 줌에서 한 픽셀이 몇 미터인가. 못 재면 0(덜어 내지 않는다).
  function metersPerPixel() {
    try {
      var maps = window.kakao.maps, projection = map.getProjection();
      var a = projection.coordsFromContainerPoint(new maps.Point(0, 0));
      var b = projection.coordsFromContainerPoint(new maps.Point(100, 0));
      var dx = (b.getLng() - a.getLng()) * 111320 * Math.cos(a.getLat() * Math.PI / 180);
      var dy = (b.getLat() - a.getLat()) * 110540;
      return Math.sqrt(dx * dx + dy * dy) / 100;
    } catch (e) { return 0; }
  }

  function toPath(points) {
    var maps = window.kakao.maps, path = [];
    for (var i = 0; i < points.length; i++) path.push(new maps.LatLng(points[i].latitude, points[i].longitude));
    return path;
  }

  function resimplify() {
    var tolerance = metersPerPixel() * SIMPLIFY_PIXELS;
    if (!(tolerance > 0)) return;
    for (var i = 0; i < lineRecords.length; i++) {
      var path = toPath(simplify(lineRecords[i].points, tolerance));
      for (var j = 0; j < lineRecords[i].lines.length; j++) lineRecords[i].lines[j].setPath(path);
    }
  }
  // 🔴 시트가 열리며 WebView 가 커지면 지도에 말해 줘야 한다(S15P21E201-1417) — 안 하면 처음 크기만큼만 그린다.
  window.addEventListener('resize', function () { if (map) { map.relayout(); if (fit) fit(); } });

  // 고른 마커는 커진다 — 웹 RouteMap.tsx 의 styleSelection 과 같은 모양(시안: scale 1.25, 300ms).
  function styleSelection(el, markerColor, selected) {
    el.style.border = '3px solid ' + (selected ? colorsNow.selected : markerColor);
    el.style.transform = selected ? 'scale(1.25)' : 'scale(1)';
    el.style.zIndex = selected ? '2' : '1';
  }

  // 고른 곳으로 «지금 화면·줌에서» 민다. 아래가 창에 가려졌으면 보이는 부분의 가운데로(mapFocus.ts 의 focusShiftY).
  function focusOn(id) {
    if (!focusNow || !map || !stopsById[id]) return;
    var maps = window.kakao.maps;
    var stop = stopsById[id];
    var target = new maps.LatLng(stop.latitude, stop.longitude);
    if (!shiftNow) { map.panTo(target); return; }
    var projection = map.getProjection();
    var point = projection.containerPointFromCoords(target);
    map.panTo(projection.coordsFromContainerPoint(new maps.Point(point.x, point.y + shiftNow)));
  }

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
    var colors = data.colors;
    colorsNow = colors;
    selectedNow = selectedId;
    focusNow = !!data.focus;
    shiftNow = data.shiftY || 0;
    markers = {};
    stopsById = {};
    for (var si = 0; si < stops.length; si++) stopsById[stops[si].id] = stops[si];
    var pointStops = [];
    for (var p = 0; p < points.length; p++) {
      for (var q = 0; q < points[p].stops.length; q++) pointStops.push(points[p].stops[q]);
    }
    // 점 표시(지하철역 등)도 고를 수 있다 — 웹 RouteMap.tsx 와 같다(S15P21E201-1834).
    for (var ps = 0; ps < pointStops.length; ps++) if (!stopsById[pointStops[ps].id]) stopsById[pointStops[ps].id] = pointStops[ps];
    var visible = stops.concat(pointStops);
    if (!visible.length) return;

    var center = new maps.LatLng(visible[0].latitude, visible[0].longitude);
    if (!map) {
      map = new maps.Map(document.getElementById('map'), { center: center, level: 8 });
      if (maps.event) maps.event.addListener(map, 'zoom_changed', resimplify);
    }

    for (var i = 0; i < overlays.length; i++) overlays[i].setMap(null);
    overlays = [];

    // 🔴 맞추는 범위는 번호 장소만이다(S15P21E201-1903) — 출발지·숙소 같은 점 표시까지 넣으면 먼 출발지(부산역) 때문에
    //    해운대 네 곳이 한 점에 뭉쳤다(김해~기장이 한 화면). 번호 장소가 없을 때만(주변 도움 지도 등) 점 표시로 맞춘다.
    var fitStops = stops.length ? stops : visible;
    var bounds = new maps.LatLngBounds();
    for (var fb = 0; fb < fitStops.length; fb++) bounds.extend(new maps.LatLng(fitStops[fb].latitude, fitStops[fb].longitude));
    for (var s = 0; s < visible.length; s++) {
      var stop = visible[s];
      var position = new maps.LatLng(stop.latitude, stop.longitude);
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
        content.style.background = colors.canvas; content.style.cursor = 'pointer'; content.style.boxShadow = '0 4px 12px rgba(25,25,25,.18)';
      } else {
        content.textContent = layer ? layer.label : String(stop.number);
        content.style.minWidth = '34px'; content.style.height = '34px'; content.style.padding = '0 8px';
        content.style.borderRadius = '999px';
        content.style.background = colors.canvas; content.style.color = markerColor; content.style.fontWeight = '700';
        content.style.cursor = 'pointer'; content.style.boxShadow = '0 4px 12px rgba(25,25,25,.18)';
      }
      content.style.transition = 'transform 300ms cubic-bezier(.34,1.3,.64,1)';
      styleSelection(content, markerColor, stop.id === selectedId);
      markers[stop.id] = { el: content, color: markerColor };
      (function (stopId) { content.onclick = function () { post('select', stopId); }; })(stop.id);
      // 🔴 출발지·숙소 같은 표시는 번호 장소 아래에 깐다(S15P21E201-1788) — 가까우면 「출발지」가 1번을 덮었다.
      //    겹쳐도 글자가 더 넓어 옆으로 보인다. 웹 RouteMap.tsx 와 같은 값.
      var overlay = new maps.CustomOverlay({ position: position, content: content, yAnchor: 0.5, zIndex: layer ? 1 : 2 });
      overlay.setMap(map); overlays.push(overlay);
    }

    window.__moveKakaoLocation(data.currentLocation);

    // 🔴 일정 경로 선은 넓은 흰 테두리 위에 실선이다 — 웹 RouteMap.tsx 의 drawRouteLine 과 같다(S15P21E201-1656).
    //    어림 구간을 짧은 점선으로 그리던 것이 촘촘한 꺾임을 따라 끊기며 떨려 보였다. 어림은 옅게 그린다.
    lineRecords = [];
    var tolerance = metersPerPixel() * SIMPLIFY_PIXELS;
    // 흰 테두리는 경로 전체에 한 번, 그 위에 조각별 색 선 — 웹과 같다. parts = [{ points, color }].
    function drawRouteLine(linePoints, parts, opacity) {
      var casing = new maps.Polyline({ path: toPath(simplify(linePoints, tolerance)), strokeWeight: ROUTE_WEIGHT + CASING_EXTRA, strokeColor: colors.casing, strokeOpacity: 0.95, strokeStyle: 'solid' });
      casing.setMap(map); overlays.push(casing);
      lineRecords.push({ points: linePoints, lines: [casing] });
      for (var pi = 0; pi < parts.length; pi++) {
        var routeLine = new maps.Polyline({ path: toPath(simplify(parts[pi].points, tolerance)), strokeWeight: ROUTE_WEIGHT, strokeColor: parts[pi].color, strokeOpacity: opacity, strokeStyle: 'solid' });
        routeLine.setMap(map); overlays.push(routeLine);
        lineRecords.push({ points: parts[pi].points, lines: [routeLine] });
      }
    }
    for (var r = 0; r < routes.length; r++) {
      var route = routes[r];
      var points = (route.path && route.path.length) ? route.path : route.stops;
      if (points.length < 2) continue;
      // 실제 길 좌표가 있고 "추정 아님" 이라고 적혀 있을 때만 진하다. 나머지는 어림이라 옅다.
      var real = !!(route.path && route.path.length) && route.estimated === false;
      // 걷는 길의 조각 — 앱(RouteMap.native.tsx)이 routeGrading.ts 로 고른 조건대로 잘라 색을 붙여 segments 로 보낸다(S15P21E201-1658 · -1896).
      if (route.weight == null) { drawRouteLine(points, route.segments && route.segments.length ? route.segments : [{ points: points, color: route.color }], route.opacity != null ? route.opacity : (real ? REAL_OPACITY : ESTIMATED_OPACITY)); continue; }
      // 굵기를 직접 준 보조 선(전의 경사·그늘 겹)은 경로 아래 깔리는 옅은 띠라 그대로 그린다.
      var line = new maps.Polyline({ path: toPath(points), strokeWeight: route.weight, strokeColor: route.color, strokeOpacity: route.opacity != null ? route.opacity : (real ? 0.9 : 0.75), strokeStyle: real ? 'solid' : 'shortdash' });
      line.setMap(map); overlays.push(line);
    }

    // 와 같은 이유 — 점이 하나면 bounds 넓이가 0이라 최대 줌으로 튄다.
    // 여백은 앱이 셈해서 보낸다 — 아래가 창에 가려진 만큼 더(S15P21E201-1607, mapFocus.ts). 안 보내면 네 변 60.
    padNow = data.fitPadding || [60, 60, 60, 60];
    fit = function () {
      // 🔴 여백은 그릴 때의 지도 높이로 셈한 값이다(S15P21E201-1903). 폰을 가로로 돌리면 높이가 확 줄어 그 여백이 지도를 다 덮고, 카카오가 동아시아 전체로 물러났다.
      //    지금 높이로 다시 줄여 맞출 자리를 60 은 남긴다(mapFocus.fitPadding 과 같은 규칙).
      var pad = padNow.slice(); var el = document.getElementById('map'); var h = el.clientHeight || 0; var w = el.clientWidth || 0;
      // 위·아래 가림 띠는 앱이 따로 셈해 보낸다(mapFocus.fitPadding, S15P21E201-1988). 여기서는 지금 높이에서 맞출 자리가
      // 48 보다 좁아질 때만 아래부터(점 반지름 24 까지) 줄이고, 그래도 모자라면 위를 줄인다 — 같은 규칙.
      if (h > 0) { var over = pad[0] + pad[2] + 48 - h; if (over > 0) { var cut = Math.min(over, pad[2] - 24); pad[2] -= cut; over -= cut; } if (over > 0) pad[0] = Math.max(24, pad[0] - over); }
      if (fitStops.length <= 1) { map.setCenter(new maps.LatLng(fitStops[0].latitude, fitStops[0].longitude)); map.setLevel(5); } else map.setBounds(bounds, pad[0], pad[1], pad[2], pad[3]); focusOn(selectedNow); };
    fit();
    // 맞추면 줌이 바뀐다 — 줌 사건이 안 오는 환경도 있어 맞춘 뒤 한 번 더 셈한다.
    resimplify();

    post('ready', null);
  };

  // 여백만 새로 받아 같은 범위를 한 번 다시 맞춘다 — RN 쪽이 «지도 보기»로 창을 접었을 때만 부른다(S15P21E201-1754).
  // 창이 열린 채 맞춘 큰 아래 여백이 남아 경로가 화면 위쪽에 몰려 있었다. 다시 그리지는 않는다.
  window.__fitKakaoMap = function (data) {
    if (!map || !fit) return;
    padNow = data.fitPadding || padNow;
    shiftNow = data.shiftY || 0;
    fit();
    resimplify();
  };

  // 고른 곳만 바뀌었을 때 RN 쪽이 부른다 — 마커 모양만 바꾸고 panTo. 다시 그리지도, 다시 맞추지도 않는다.
  window.__selectKakaoMap = function (data) {
    if (!map) return;
    focusNow = !!data.focus;
    shiftNow = data.shiftY || 0;
    for (var id in markers) styleSelection(markers[id].el, markers[id].color, id === data.selectedId);
    if (selectedNow === data.selectedId) return;
    selectedNow = data.selectedId;
    // 🔴 고르면 그 장소가 보일 만큼 확대한다(S15P21E201-1903) — 전에는 옮기기만 해서 멀리서 본 채 핀이 겹쳐 있었다.
    //    이미 더 가까이 보고 있으면 그대로 둔다(사람이 맞춘 줌을 빼앗지 않는다). mapFocus.ts 의 FOCUS_LEVEL 과 같은 값.
    if (focusNow && map.getLevel() > FOCUS_LEVEL) map.setLevel(FOCUS_LEVEL);
    focusOn(selectedNow);
  };

  // 확대·축소 단추(RouteMap.native.tsx) — 스크롤 안에 든 지도(길 안내)는 두 손가락 확대가 스크롤과 다툰다.
  window.__zoomKakaoMap = function (delta) {
    if (!map) return;
    var next = Math.max(1, Math.min(14, map.getLevel() + delta));
    // 🔴 보이는 부분의 가운데를 기준으로 확대한다 — 지도 칸 가운데는 아래 창에 가려진 자리라, 그곳으로 확대하면 보이는 위쪽이 바다로만 찼다(실기기).
    var el = document.getElementById('map');
    var anchor = null;
    try { anchor = map.getProjection().coordsFromContainerPoint(new window.kakao.maps.Point(el.clientWidth / 2, el.clientHeight / 2 - (shiftNow || 0))); } catch (e) { anchor = null; }
    if (anchor) map.setLevel(next, { animate: true, anchor: anchor }); else map.setLevel(next, { animate: true });
  };

  // 현재 위치는 점만 옮긴다 — 움직일 때마다 전체를 다시 맞추면 걷는 내내 지도가 튄다.
  // follow = 길 안내 중(route-detail 의 탑승) — 내 위치를 따라간다(S15P21E201-1903). 처음 한 번은 동네가 보이게 확대한다.
  var followedOnce = false;
  window.__moveKakaoLocation = function (loc, follow) {
    if (!map || !window.kakao) return;
    var maps = window.kakao.maps;
    if (!loc) { if (locationOverlay) { locationOverlay.setMap(null); locationOverlay = null; } followedOnce = false; return; }
    var curPos = new maps.LatLng(loc.latitude, loc.longitude);
    if (follow) {
      if (!followedOnce) { if (map.getLevel() > FOCUS_LEVEL) map.setLevel(FOCUS_LEVEL); map.setCenter(curPos); followedOnce = true; }
      else map.panTo(curPos);
    } else followedOnce = false;
    if (locationOverlay) { locationOverlay.setPosition(curPos); return; }
    var curEl = document.createElement('div');
    curEl.setAttribute('aria-label', '현재 위치');
    curEl.style.width = '18px'; curEl.style.height = '18px'; curEl.style.borderRadius = '999px';
    curEl.style.border = '3px solid ' + colorsNow.canvas; curEl.style.background = colorsNow.navy;
    curEl.style.boxShadow = '0 0 0 2px rgba(25,25,25,.35), 0 4px 10px rgba(25,25,25,.28)';
    locationOverlay = new maps.CustomOverlay({ position: curPos, content: curEl, yAnchor: 0.5 });
    locationOverlay.setMap(map);
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
export type KakaoMapColors = { navy: string; selected: string; canvas: string; casing: string };
