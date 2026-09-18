// 비서 제안을 누르면 말풍선에 한국어가 뜨던 것 — S15P21E201-1327.
//
// 🔴 실기기(SM-G973N, versionCode 20, 일본어)에서 확인했다.
//
//      누른 제안   海雲台と広安里で2人分のグルメ日程を組んで
//      내 말풍선   해운대와 광안리 2명 맛집 일정 짜줘      ← 자기가 하지 않은 말
//
// 「보여 준 것」과 「보낸 것」이 갈라져 있는데 말풍선이 보낸 것을 쓰고 있었다.
//
// 🔴 보내는 값을 번역문으로 바꾸면 안 된다. 비회원은 서버 대신 앱 안의 키워드 매처로
//    답하는데(chat.tsx 의 accessToken 갈림), 그 매처는 한국어만 알아듣는다.
//    그래서 **보내는 것은 한국어, 보여 주는 것만 사용자가 본 문구**다.
declare const require: (id: string) => any;
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');

const CHAT = readFileSync(join(__dirname, '..', '..', '..', 'app', 'chat.tsx'), 'utf8') as string;

describe('비서 제안을 누르면 내가 본 문구가 말풍선에 남는다', () => {
  it('🔴 제안을 누를 때 보여 준 문구를 함께 넘긴다', () => {
    // 넘기지 않으면(send(suggestion.ko) 만) 말풍선이 한국어가 된다.
    expect(CHAT).toContain('send(suggestion.ko, tx(suggestion.ko, suggestion.en))');
  });

  it('🔴 말풍선은 보여 준 문구를 쓴다 — 없으면 보낸 글로 떨어진다', () => {
    expect(CHAT).toContain("role: 'user', text: (shown ?? content).trim()");
  });

  it('🔴 보내는 값은 한국어 원문 그대로다 — 비회원 키워드 매처가 한국어만 알아듣는다', () => {
    // 첫 인수가 suggestion.ko 여야 한다. tx(...) 로 바뀌면 비회원이 답을 못 받는다.
    expect(CHAT).not.toContain('send(tx(suggestion.ko');
  });

  it('제안 목록은 한국어와 영어를 함께 들고 있다 — 번역표가 이 한국어를 열쇠로 쓴다', () => {
    const block = CHAT.slice(CHAT.indexOf('const SUGGESTIONS'), CHAT.indexOf('] as const;'));
    const pairs = block.match(/\{\s*ko:/g) || [];
    expect(pairs.length).toBeGreaterThanOrEqual(3);
    expect(block).toContain('en:');
  });
});
