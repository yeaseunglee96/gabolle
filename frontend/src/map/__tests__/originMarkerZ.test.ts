// — 「출발지」 같은 표시가 가까운 번호 장소(1번)를 덮던 것(S15P21E201-1788).
//   카카오 SDK 는 jest 에서 못 띄우므로, 두 벌(웹·네이티브)이 같은 겹침 순서를 적는지 소스로 잰다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

const read = (name: string): string => readFileSync(join(__dirname, '..', name), 'utf8');

describe('지도 표시의 겹침 순서', () => {
  it('네이티브 WebView 스크립트 — 출발지·숙소 표시는 번호 장소보다 아래다', () => {
    expect(read('kakaoMapHtml.ts')).toMatch(/new maps\.CustomOverlay\(\{ position: position, content: content, yAnchor: 0\.5, zIndex: layer \? 1 : 2 \}\)/);
  });

  it('웹 지도 — 같은 값', () => {
    expect(read('RouteMap.tsx')).toMatch(/new maps\.CustomOverlay\(\{ position, content, yAnchor: 0\.5, zIndex: pointLayer \? 1 : 2 \}\)/);
  });
});
