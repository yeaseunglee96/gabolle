// 장소 제안의 위치 고르기 지도(앱 WebView 판) — S15P21E201-2031.
//
// 핀은 지도 가운데에 고정되어 있고 사람이 지도를 끌어 핀 아래에 장소를 맞춘다. 지도가 멈출 때마다(idle) 가운데 좌표를
// 앱으로 보낸다. 순수 문자열 조립이라 보통 jest 에서 시험할 수 있다(kakaoMapHtml.ts 와 같은 이유).
export const BUSAN_CENTER = { lat: 35.1796, lng: 129.0756 } as const;

export function buildLocationPickHtml(appKey: string, lat: number, lng: number): string {
  const src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&autoload=false`;
  const safeLat = Number.isFinite(lat) ? lat : BUSAN_CENTER.lat;
  const safeLng = Number.isFinite(lng) ? lng : BUSAN_CENTER.lng;
  return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<style>html,body,#map{width:100%;height:100%;margin:0;padding:0;}</style>
</head>
<body>
<div id="map"></div>
<script>
  function send(type, payload) {
    if (window.ReactNativeWebView) window.ReactNativeWebView.postMessage(JSON.stringify({ type: type, payload: payload }));
  }
  var map = null;
  function moveTo(lat, lng) {
    if (map && window.kakao) map.setCenter(new kakao.maps.LatLng(lat, lng));
  }
  window.__moveTo = moveTo;
  var s = document.createElement('script');
  s.src = ${JSON.stringify(src)};
  s.onerror = function () { send('error', 'sdk'); };
  s.onload = function () {
    kakao.maps.load(function () {
      map = new kakao.maps.Map(document.getElementById('map'), { center: new kakao.maps.LatLng(${safeLat}, ${safeLng}), level: 3 });
      kakao.maps.event.addListener(map, 'idle', function () {
        var c = map.getCenter();
        send('center', { lat: c.getLat(), lng: c.getLng() });
      });
      var c0 = map.getCenter();
      send('center', { lat: c0.getLat(), lng: c0.getLng() });
    });
  };
  document.head.appendChild(s);
</script>
</body>
</html>`;
}
