// 여기서 재는 것은 "지도가 실제로 뜨는가" 가 아니다 — 그건 카카오 SDK 를 실행할 수 있는
// 환경(WebView)이 있어야 알 수 있고, 이 저장소의 jest(웹 흉내) 환경에는 그게 없다.
// 여기서 재는 것은 문자열 조립이 안전한가 다 — 키에 `&`·따옴표가 섞여도 그 뒤
// 쿼리 파라미터나 스크립트 태그를 깨뜨리지 않는가.
import { buildKakaoMapHtml } from '@/map/kakaoMapHtml';

describe('카카오 지도 HTML 조립', () => {
  it('키를 스크립트 주소 안에 넣는다', () => {
    const html = buildKakaoMapHtml('test-key-123');
    expect(html).toContain('dapi.kakao.com/v2/maps/sdk.js');
    expect(html).toContain('appkey=test-key-123');
    expect(html).toContain('autoload=false');
  });

  it('🔴 키에 &·공백·따옴표가 섞여도 쿼리·스크립트 태그를 안 깨뜨린다', () => {
    // 실제로 카카오 키에 이런 문자가 들어갈 일은 없지만, 값을 그대로 문자열에 이어 붙이면
    // (encodeURIComponent 를 빼먹으면) '&' 하나로 뒤의 autoload=false 파라미터가
    // 별개 파라미터로 갈라지거나, 따옴표로 JSON.stringify 결과 밖으로 값이 새어나갈 수 있다.
    const html = buildKakaoMapHtml('weird&key="value" here');
    expect(html).toContain('appkey=weird%26key%3D%22value%22%20here');
    expect(html).not.toContain('appkey=weird&key');
  });

  it('RN 쪽이 injectJavaScript 로 부를 렌더 함수 이름을 노출한다', () => {
    const html = buildKakaoMapHtml('test-key');
    expect(html).toContain('window.__renderKakaoMap');
  });

  it('네이티브 쪽 postMessage 다리를 쓴다 — ReactNativeWebView 전역이 없으면 아무것도 안 보낸다', () => {
    const html = buildKakaoMapHtml('test-key');
    expect(html).toContain('window.ReactNativeWebView');
  });
});
