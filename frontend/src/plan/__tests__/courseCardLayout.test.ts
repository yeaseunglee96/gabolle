// 코스 카드가 겹쳐 보이던 것 — S15P21E201-1351.
//
// 🔴 2026-09-20 실기(SM-G973N, versionCode 23, 영어)에서 이렇게 보였다.
//
//    · 「✓ My …」 배지 위에 ☆ 동그라미가 겹쳐 글자를 가렸다
//    · 그 겹친 덩어리가 제목을 밀어 「2026-09-20 ~ …」로 잘렸다
//    · 「Build this itinerary →」 단추가 카드 오른쪽 경계를 넘어 잘렸다
//
// 🔴 **자동 검사는 전부 초록이었다.** tsc·시험·배색 모두. 겹침과 넘침은 띄워 보지
//    않으면 안 보인다 — 그래서 여기서는 «스타일 값 자체»를 잰다. 값이 규칙을 지키면
//    겹칠 수가 없다.
//
// ── 왜 겹쳤나 ───────────────────────────────────────────────────────────────
//
//    cover      width 200   (사진 있을 때)
//    coverEmpty width  96   (사진 없을 때)
//
//    배지와 ☆ 는 둘 다 cover 안에 position:'absolute' 로 얹혀 있었다. 96px 짜리 칸에
//    얹히면 배지(글자 폭만 150px 쯤)가 ☆ 밑을 지나 본문까지 넘어간다.
declare const require: (id: string) => any;
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');

const SOURCE = readFileSync(join(__dirname, '..', 'CourseCard.tsx'), 'utf8') as string;

/** `styles` 안에서 이름 하나의 본문을 꺼낸다. */
function styleBlock(name: string): string {
  const at = SOURCE.indexOf(`\n  ${name}: {`);
  expect(at).toBeGreaterThan(-1);
  const close = SOURCE.indexOf('\n  }', at);
  const oneLine = SOURCE.indexOf('},\n', at);
  const end = close > -1 && close < oneLine ? close : oneLine;
  return SOURCE.slice(at, end);
}

describe('표지에 얹는 것은 사진이 있을 때만이다', () => {
  it('🔴 배지 자체는 절대 위치가 아니다 — 흐름 안에 있으면 겹칠 자리가 없다', () => {
    expect(styleBlock('badge')).not.toContain("position: 'absolute'");
  });

  it('🔴 ☆ 도 마찬가지다 — 예전에는 둘 다 96px 칸에 얹혀 서로를 덮었다', () => {
    expect(styleBlock('save')).not.toContain("position: 'absolute'");
  });

  it('얹는 것은 따로 둔 스타일이 맡는다', () => {
    expect(styleBlock('badgeOverlay')).toContain("position: 'absolute'");
    expect(styleBlock('saveOverlay')).toContain("position: 'absolute'");
  });

  it('🔴 그 둘은 «사진이 있을 때만» 붙는다', () => {
    expect(SOURCE).toContain('const overlayOnCover = Boolean(cover);');
    expect(SOURCE).toContain('overlayOnCover ? styles.badgeOverlay : null');
    expect(SOURCE).toContain('overlayOnCover ? styles.saveOverlay : null');
  });

  it('사진이 없으면 본문 줄에 세운다 — 어디에도 안 그리면 저장을 못 한다', () => {
    expect(SOURCE).toContain('overlayOnCover ? null : <View style={styles.markRow}>{badgeAndSave}</View>');
    expect(SOURCE).toContain('overlayOnCover ? badgeAndSave : null');
  });

  it('배지는 줄어들 수 있다 — 안 줄면 좁은 화면에서 또 넘친다', () => {
    expect(styleBlock('badge')).toContain('flexShrink: 1');
    expect(styleBlock('badge')).toContain('minWidth: 0');
  });
});

describe('아래 줄이 카드 밖으로 넘치지 않는다', () => {
  it('🔴 긴 글자가 오면 접힌다 — 카드가 overflow: hidden 이라 안 접히면 «조용히» 잘린다', () => {
    expect(styleBlock('bottom')).toContain("flexWrap: 'wrap'");
  });

  it('비용 칸이 줄어들 수 있다 — 안 줄면 단추가 밀려난다', () => {
    expect(styleBlock('costBlock')).toContain('flexShrink: 1');
  });

  it('카드는 여전히 넘친 것을 감춘다 — 이 시험이 지키려는 전제다', () => {
    expect(styleBlock('card')).toContain("overflow: 'hidden'");
  });
});
