// 답이 와도 맨 아래로 안 내려가던 것 — S15P21E201-1349.
//
// 🔴 실기(SM-G973N, versionCode 23, 영어)에서 확인했다. 조수에게 물으면 화면에 남는 것은
//    잘린 한 문장뿐이었다:
//
//      I found these conditions in your request. I'll apply them to trip planning
//
//    그 아래의 「Conditions found」 칸과 **「Apply to itinerary and review」 단추가 통째로
//    화면 밖**에 있었다. 이 흐름의 결론이 그 단추다.
declare const require: (id: string) => any;
declare const __dirname: string;

import { isNearBottom, STICK_TO_END_SLACK_PX } from '@/assistant/chatScroll';

const { readFileSync } = require('fs');
const { join } = require('path');

const CHAT = readFileSync(join(__dirname, '..', '..', '..', 'app', 'chat.tsx'), 'utf8') as string;

function at(offsetY: number, viewport = 800, content = 2000) {
  return {
    layoutMeasurement: { height: viewport },
    contentOffset: { y: offsetY },
    contentSize: { height: content },
  };
}

describe('바닥에 붙어 있는가', () => {
  it('맨 아래면 붙어 있다', () => {
    expect(isNearBottom(at(1200))).toBe(true);
  });

  it('한 줄쯤 올렸어도 아직 붙어 있다 — 손가락이 살짝 스친 것으로 읽던 자리를 잃지 않는다', () => {
    expect(isNearBottom(at(1200 - (STICK_TO_END_SLACK_PX - 1)))).toBe(true);
  });

  it('🔴 지난 말을 읽으려고 올려 두었으면 안 붙어 있다 — 새 답이 와도 끌어내리지 않는다', () => {
    expect(isNearBottom(at(300))).toBe(false);
  });

  it('내용이 화면보다 짧으면 언제나 붙어 있다 — 올려 둘 자리 자체가 없다', () => {
    expect(isNearBottom(at(0, 800, 400))).toBe(true);
  });

  it('튕기는 중(음수 여백)도 붙어 있다', () => {
    expect(isNearBottom(at(1400))).toBe(true);
  });

  it('여유 값을 0으로 주면 정확히 바닥일 때만 참이다 — 이 시험이 실제로 무언가를 가른다', () => {
    expect(isNearBottom(at(1200), 0)).toBe(true);
    expect(isNearBottom(at(1199), 0)).toBe(false);
  });
});

describe('화면이 그 규칙을 실제로 쓰고 있다', () => {
  it('🔴 목록에 ref 를 달고 내용이 늘어나면 바닥으로 보낸다', () => {
    expect(CHAT).toContain('ref={listRef}');
    expect(CHAT).toContain('onContentSizeChange');
    expect(CHAT).toContain('scrollToEnd');
  });

  it('🔴 스크롤을 지켜보며 「바닥 근처인가」를 갱신한다', () => {
    expect(CHAT).toContain('isNearBottom');
    expect(CHAT).toContain('onScroll');
  });

  it('🔴 내가 보낸 것에 대한 답은 무조건 따라간다 — send 가 붙임을 다시 켠다', () => {
    // 이것이 없으면 위로 올려 둔 채 질문한 사람은 자기 답을 영영 못 본다.
    expect(CHAT).toContain('stickToEnd.current = true');
  });
});
