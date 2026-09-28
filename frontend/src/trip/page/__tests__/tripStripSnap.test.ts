// 여행 화면을 접었을 때 지도 위 장소 카드 줄이 카드 «한 장» 단위로 멈춘다 (S15P21E201-1800).
//
// 🔴 전에는 멈추는 자리를 정하지 않아 카드가 반쯤 잘린 채 섰다.
//    snapToInterval(**손을 뗀 뒤 이 값의 배수 자리에서만 서게 하는 설정**)이
//    카드 폭 + 카드 사이 간격과 같아야 한 장씩 맞아 선다. 홈 줄(S15P21E201-1797)과 같은 방식.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');

describe('여행 화면 장소 카드 줄 — 카드 단위로 멈춘다', () => {
  const source: string = readFileSync(`${__dirname}/../TripPageMobile.tsx`, 'utf8');

  it('🔴 한 칸은 카드 폭 + 줄 안쪽 간격이다', () => {
    expect(source).toMatch(/const STRIP_CARD = 150;/);
    expect(source).toMatch(/const STRIP_STEP = STRIP_CARD \+ spacing\[2\];/);
    expect(source).toMatch(/stripInner: \{ gap: spacing\[2\], paddingHorizontal: spacing\[4\]/);
  });

  it('🔴 카드 줄 ScrollView 에 멈춤 설정이 붙어 있다', () => {
    const strip = source.slice(source.indexOf('<Reanimated.ScrollView', source.indexOf("panel === 'collapsed' && items.length")));
    const props = strip.slice(0, strip.indexOf('>\n'));
    expect(props).toMatch(/horizontal/);
    expect(props).toMatch(/snapToInterval=\{STRIP_STEP\}/);
    expect(props).toMatch(/snapToAlignment="start"/);
    expect(props).toMatch(/decelerationRate="fast"/);
  });
});
