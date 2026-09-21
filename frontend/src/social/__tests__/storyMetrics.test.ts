// 글 지표(조회·인용) 문구 — S15P21E201-1213.

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
