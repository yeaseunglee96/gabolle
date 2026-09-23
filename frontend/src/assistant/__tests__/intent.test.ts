// 챗봇 칩 「부산 로컬 스팟 보여줘」가 「새 여행 만들기」로 가던 것 — S15P21E201-1517.
//
// 🔴 원인 — 로그인한 사람의 칩은 서버 AI 로 가는데, 서버는 /explore 로 보낼 수 없다
//    (허용 목록 다섯에 없고, 안내문이 「장소 요청은 /plan 또는 /trips 로」라고 정한다).
//    그래서 규칙대로 /plan 이 나왔다. 무작위 실수가 아니라 설계상 그렇게 된다.
//
// 🔴 이 시험이 지키는 것 셋.
//    ① 칩 중 「서버가 못 가는 화면」으로 가는 것은 로컬 스팟 «하나뿐»이다 — 나머지 칩은
//       전처럼 서버로 간다(바뀌는 범위가 넓어지지 않는다)
//    ② 앱이 직접 답할 때도 일본어·중국어로 나온다 — 전에는 늘 영어였다
//    ③ 직접 친 글은 가로채지 않는다 — 로컬 해석기는 낱말만 보는 거친 도구다
declare const require: (id: string) => any;
declare const __dirname: string;

import { isAllowedNavigateHref } from '@/assistant/assistantApi';
import { understandAssistantMessage } from '@/assistant/intent';
import { setCurrentLanguage } from '@/i18n/languages';

const { readFileSync } = require('fs');
const { join } = require('path');

const CHAT = readFileSync(join(__dirname, '..', '..', '..', 'app', 'chat.tsx'), 'utf8') as string;
/** chat.tsx 의 SUGGESTIONS 한국어 원문들. 칩이 늘면 여기도 저절로 늘어난다. */
const CHIPS = [...CHAT.slice(CHAT.indexOf('const SUGGESTIONS'), CHAT.indexOf('] as const;')).matchAll(/ko: '([^']+)'/g)].map((m) => m[1]);

const appOnly = (text: string) => {
  const action = understandAssistantMessage(text);
  return action.kind === 'navigate' && !isAllowedNavigateHref(action.href);
};

afterEach(() => setCurrentLanguage('ko'));

describe('챗봇 칩 — 서버가 못 가는 화면은 앱이 직접 연다', () => {
  it('칩 목록을 소스에서 읽었다', () => {
    expect(CHIPS).toContain('부산 로컬 스팟 보여줘');
    expect(CHIPS.length).toBeGreaterThanOrEqual(3);
  });

  it('🔴 「부산 로컬 스팟 보여줘」는 로컬 탐색(/explore)으로 — 서버 허용 목록에는 없는 화면이다', () => {
    const action = understandAssistantMessage('부산 로컬 스팟 보여줘');
    expect(action.kind).toBe('navigate');
    expect(action.kind === 'navigate' && action.href).toBe('/explore');
    expect(isAllowedNavigateHref('/explore')).toBe(false);
    expect(appOnly('부산 로컬 스팟 보여줘')).toBe(true);
  });

  it('🔴 바뀌는 칩은 로컬 스팟 하나뿐이다 — 나머지 칩은 전처럼 서버로 간다', () => {
    expect(CHIPS.filter(appOnly)).toEqual(['부산 로컬 스팟 보여줘']);
  });

  it('🔴 일본어를 고른 사람에게 영어로 답하지 않는다 — 번역표의 줄을 쓴다', () => {
    setCurrentLanguage('ja');
    const action = understandAssistantMessage('부산 로컬 스팟 보여줘');
    expect(action.reply).toContain('釜山のローカル');
    expect(action.kind === 'navigate' && action.label).toContain('ローカル探索');
    expect(action.reply).not.toContain("I'll show you");
  });

  it('영어를 고른 사람에게는 영어로', () => {
    setCurrentLanguage('en');
    expect(understandAssistantMessage('부산 로컬 스팟 보여줘').reply).toBe("I'll show you local Busan spots.");
  });

  it('🔴 가로채는 것은 칩에서 온 것뿐이다 — 직접 친 글은 서버로 간다', () => {
    // 로컬 해석기는 「로컬」 낱말만 보고 이 글도 /explore 로 보낸다. 사람은 일정을 원했다.
    expect(appOnly('로컬 맛집 일정 짜줘')).toBe(true);
    // 그래서 chat.tsx 는 칩(fixed)일 때만 로컬 해석기를 먼저 본다.
    expect(CHAT).toContain('const local = fixed ? understandAssistantMessage(content) : null;');
  });
});

describe('지역·취향만 말해도 일정 조건으로 받는다 — S15P21E201-1542', () => {
  // 손님은 서버 AI 없이 이 해석기만 쓴다. 「일정·여행…」 낱말이 없다고 일반 안내로 떨어졌다.
  it('🔴 「해운대 근처 맛집 알려줘」 → 해운대 · 맛집을 조건으로 보여주고 묻는다', () => {
    const action = understandAssistantMessage('해운대 근처 맛집 알려줘');
    expect(action.kind).toBe('plan');
    if (action.kind !== 'plan') return;
    expect(action.patch.travelAreas).toEqual(['HAEUNDAE']);
    expect(action.patch.preferences).toEqual(['FOOD']);
  });

  it('취향만 — 「카페 가고 싶어」', () => {
    const action = understandAssistantMessage('카페 가고 싶어');
    expect(action.kind === 'plan' && action.patch.preferences).toEqual(['CAFE_HEALING']);
  });

  it('지역도 취향도 없으면 전처럼 안내한다', () => {
    expect(understandAssistantMessage('안녕하세요').kind).toBe('help');
  });

  it('로컬 탐색·사투리처럼 앞에서 가르는 요청은 그대로다', () => {
    const action = understandAssistantMessage('해운대 야시장 둘러보기');
    expect(action.kind === 'navigate' && action.href).toBe('/explore');
  });
});
