// 피드 사진을 눌러 크게 본다 — S15P21E201-1811.
//
// 🔴 `PhotoGrid` 에 `onPressPhoto` 라는 자리는 있는데 그것을 넘기는 화면이 «한 곳도»
//    없었다(2026-09-27 확인). 그래서 원글 사진도 댓글 사진도 눌러도 아무 일이 없었다.
//    크게 생긴 사진은 사람이 누른다 — 눌러도 반응이 없으면 앱이 멈춘 것으로 읽힌다.
//
//    소스 검사로 본다. 이 화면은 서버·인증·라우터를 모두 물고 있어 렌더 시험을 세우려면
//    가짜를 여섯 개쯤 만들어야 하는데, 정작 확인하려는 것은 「연결돼 있나」 한 가지다.
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');

const source = readFileSync(join(__dirname, '..', 'feed', '[id].tsx'), 'utf8') as string;

describe('피드 사진 크게 보기', () => {
  it('PhotoViewer 를 들여온다', () => {
    expect(source).toMatch(/import \{ PhotoViewer \} from '@\/components\/PhotoViewer'/);
  });

  // 🔴 원글 사진은 -1787 에서 격자 → PhotoCarousel(옆으로 넘기기)로 바뀌었다. 그래서
  //    「격자 칸이 Pressable 인가」가 아니라 「캐러셀에 누르는 길을 줬는가」를 본다.
  it('원글 사진을 누르면 «그» 사진을 연다 — 넘겨 보다 눌러도 첫 장이 열리면 안 된다', () => {
    expect(source).toMatch(/<PhotoCarousel[\s\S]{0,300}onPressPhoto=\{\(at\) => setViewing\(images\[at\]\?\.url \?\? null\)\}/);
  });

  it('댓글 사진은 PhotoGrid 의 onPressPhoto 자리를 쓴다', () => {
    expect(source).toMatch(/onPressPhoto=\{\(index\) => setViewingReplyPhoto/);
  });

  it.each([
    ['원글', /visible=\{viewing !== null\}/],
    ['댓글', /visible=\{viewingReplyPhoto !== null\}/],
  ])('%s 창은 열렸을 때만 그려진다 — 닫힌 채로 그리면 뒤가 가려진다', (_name, pattern) => {
    expect(source).toMatch(pattern);
  });

  it('닫는 길이 있다', () => {
    expect(source).toMatch(/onClose=\{\(\) => setViewing\(null\)\}/);
    expect(source).toMatch(/onClose=\{\(\) => setViewingReplyPhoto\(null\)\}/);
  });
});

// 🔴 캐러셀이 «몇 번째»를 안 주면 위 연결이 조용히 첫 장만 열게 된다 (S15P21E201-1811).
describe('PhotoCarousel 이 누른 자리를 알려 준다', () => {
  const carousel = readFileSync(join(__dirname, '..', '..', 'src', 'social', 'PhotoCarousel.tsx'), 'utf8') as string;

  it('onPressPhoto 가 index 를 받는다', () => {
    expect(carousel).toMatch(/onPressPhoto\?: \(index: number\) => void/);
  });

  it('누를 때 그 자리 번호를 넘긴다', () => {
    expect(carousel).toMatch(/onPress=\{\(\) => onPressPhoto\(at\)\}/);
  });
});
