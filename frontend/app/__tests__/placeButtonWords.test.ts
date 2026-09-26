// 장소 상세 영어판 단추 문구 — S15P21E201-1709 (사용자 결정 B (다)의 첫째, 문구는 조율 세션).
//
// 🔴 2열 단추 칸에서 「Remove from candidates」(글자 175px)가 단추 글자 폭(165px, 작은 폰 150px)을 넘어
//    두 줄로 꺾여 옆 「Speak Korean」보다 키가 컸다. 공용 단추에 좌우 여백이 생기면 폭이 126~141px 로 줄어
//    「Save to candidates」(134)·「Show to a taxi driver」(143)도 꺾인다(2026-09-26 실측).
//    - 이 단추는 일정이 아니라 「내 여행 후보」(저장한 장소 목록)에 넣고 뺀다 — 「trip」이라 쓰면 일정으로 읽힌다.
//    - 앱의 다른 곳(StoryReactionRow)은 「Save」·「Remove from saved」인데, 후자는 139px 라 작은 폰에 안 들어가 「Unsave」.
//    - 한국어는 그대로.
//
// 🔴 한국어도 줄였다(S15P21E201-1723, 사용자 결정). 공용 단추에 좌우 여백 16px(!1712)이 들어간 뒤 글자 폭이
//    폰 390 133px · 360 118px · 넓은 1280 122px 로 줄어, 「택시 기사에게 보여주기」(137px)가 세 폭 모두,
//    「내 여행 후보에서 빼기」(127px)가 360·1280 에서 두 줄로 꺾였다(운영 실측). 「기사님께 보여주기」(107px) ·
//    「후보에서 빼기」(81px)는 세 폭 모두 한 줄이다. 택시 카드 화면과 길 안내 화면의 택시 단추는 그대로.

// tsconfig 가 node 타입을 안 들고 있어서 import 로 쓰면 타입 검사가 막힌다 — 이 저장소의 다른 파일 검사 시험과 같은 방식.
declare const require: (id: string) => any;
declare const __dirname: string;

function read(relative: string): string {
  const { readFileSync } = require('fs');
  const { join } = require('path');
  return readFileSync(join(__dirname, '..', '..', relative), 'utf8') as string;
}

describe('장소 상세 단추 — 영어도 한 줄', () => {
  const source = read('app/place/[id].tsx');

  it('저장 단추는 「Save」·「Unsave」 — 한국어 빼기는 「후보에서 빼기」', () => {
    expect(source).toContain("tx('후보에서 빼기', 'Unsave') : tx('내 여행 후보에 저장', 'Save')");
    // 저장 뒤 안내 문장(「Saved to your trip candidates — …」)은 단추가 아니라 줄이 넉넉해서 그대로 둔다.
    expect(source).not.toContain("'Remove from candidates'");
    expect(source).not.toContain("'Save to candidates'");
  });

  it('택시 단추는 「기사님께 보여주기」·「Show to driver」', () => {
    expect(source).toContain("tx('기사님께 보여주기', 'Show to driver')");
  });

  it('길 안내 화면의 택시 단추(한 줄 전체 폭)는 그대로 — 거기는 꺾이지 않는다', () => {
    expect(read('app/route-detail.tsx')).toContain("tx('택시 기사에게 보여주기', 'Show to a taxi driver')");
  });
});
