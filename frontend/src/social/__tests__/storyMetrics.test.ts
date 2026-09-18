// 글 지표(조회·인용) 문구 — S15P21E201-1213.
//
// 🔴 여기서 재는 것은 「숫자가 예쁘게 나오나」가 아니라 **모르는 것을 아는 척하지 않는가**다.
//
//    서버가 이 칸을 안 주는 판이 있다. 그때 0 을 그리면 **「아무도 안 봤다」는 주장**이
//    되는데, 실제로는 **서버가 아직 안 세는 것**이다. 이 저장소가 여러 번 데인 자리다 —
//    안 조사한 것을 「없다」로 말하는 것(S15P21E201-996).
//
// 🔴 그리고 그 반대도 재야 한다. **진짜 0 은 말해야 한다.**
//
//    `if (story.viewCount)` 로 쓰면 **0 이 없는 것으로 삼켜진다.** 아무도 안 본 글과
//    서버가 안 세는 글이 화면에서 똑같아진다. 그래서 `typeof === 'number'` 로 본다.
//    아래 시험 둘이 그 차이를 붙든다.

import { storyMetricLabels } from '../stories';

const tx = (ko: string) => ko;

describe('storyMetricLabels', () => {
  it('서버가 둘 다 주면 둘 다 말한다', () => {
    expect(storyMetricLabels({ viewCount: 12, linkCopyCount: 3 }, tx)).toEqual(['조회 12', '인용 3']);
  });

  it('🔴 진짜 0 은 말한다 — 아무도 안 본 것도 사실이다', () => {
    expect(storyMetricLabels({ viewCount: 0, linkCopyCount: 0 }, tx)).toEqual(['조회 0', '인용 0']);
  });

  it('🔴 서버가 안 주면 아무 말도 안 한다 — 0 을 지어내지 않는다', () => {
    expect(storyMetricLabels({}, tx)).toEqual([]);
  });

  it('하나만 주면 그 하나만 말한다 — 배포 순서를 안 탄다', () => {
    expect(storyMetricLabels({ viewCount: 5 }, tx)).toEqual(['조회 5']);
    expect(storyMetricLabels({ linkCopyCount: 2 }, tx)).toEqual(['인용 2']);
  });

  it('영어로도 같은 규칙이다', () => {
    const txEn = (_ko: string, en: string) => en;
    expect(storyMetricLabels({ viewCount: 0 }, txEn)).toEqual(['0 views']);
    expect(storyMetricLabels({}, txEn)).toEqual([]);
  });
});
