// 「로컬 점수 있음」 알약을 숨긴다 — 장소 상세·저장 목록(S15P21E201-1704, 사용자 결정).
//
// 🔴 점수의 값이 아니라 「점수가 있다」는 사실만 말해서 사용자에게 뜻이 없었다. 영어판은 「Has locality score」.
//    추천 계산(서버)과 장소 자료의 점수 값은 건드리지 않는다 — 화면에 그리던 알약만 뺀다.

// tsconfig 가 node 타입을 안 들고 있어서 import 로 쓰면 타입 검사가 막힌다 —
// 이 저장소의 다른 파일 검사 시험과 같은 방식이다(app/__tests__/teamReportFixes.test.ts).
declare const require: (id: string) => any;
declare const __dirname: string;

function read(relative: string): string {
  const { readFileSync } = require('fs');
  const { join } = require('path');
  return readFileSync(join(__dirname, '..', '..', relative), 'utf8') as string;
}

describe('「로컬 점수 있음」 알약이 없다', () => {
  it.each(['app/place/[id].tsx', 'app/(tabs)/saved.tsx', 'src/me/panels/SavedPlacesBody.tsx', 'src/discovery/savedPlaceCards.ts'])('%s 에 알약이 없다', (file) => {
    const source = read(file);
    expect(source).not.toContain('로컬 점수 있음');
    expect(source).not.toContain('Has locality score');
    expect(source).not.toContain('hasLocalityScore');
  });

  it('알약에만 쓰던 판정 함수와 번역표 줄도 없다', () => {
    expect(read('src/discovery/places.ts')).not.toContain('export function hasLocalityScore');
    expect(read('src/i18n/translations.ts')).not.toContain("'로컬 점수 있음'");
  });

  it('알레르기 확인 알림은 그대로 있다 — 같이 있던 다른 알림은 안 뺀다', () => {
    expect(read('src/me/panels/SavedPlacesBody.tsx')).toContain("tx('알레르기·식단 확인 필요', 'Check allergy/dietary info')");
  });
});
