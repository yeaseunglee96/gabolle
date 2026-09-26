// 차단 문구 네 곳이 양방향을 말하는지 — S15P21E201-1722.
//
// 🔴 이 시험이 지키는 것은 「말이 예쁜가」가 아니라 «차단이 실제로 하는 일과 문구가 어긋나지 않는가»이다.
//    S15P21E201-1714(가이드라인 1.2)로 차단이 「상대가 내 글을 못 본다」+「나도 상대 글을 못 본다」
//    양방향이 됐는데, 문구 넷(차단 확인 대화상자·차단 직후 안내 둘·설정의 「차단된 계정」 설명)은
//    옛 한 방향 문구 그대로였다 — 웹 실기(2026-09-26)로 발견했다. 코드가 아니라 사람이 읽는 자리라
//    타입 검사도 다른 시험도 이 어긋남을 안 잡는다.
//
// tsconfig 가 node 타입을 안 들고 있어서 import 로 쓰면 타입 검사가 막힌다 — 이 저장소의 다른 파일
// 검사 시험(placeButtonWords.test.ts)과 같은 방식으로 우회한다.
declare const require: (id: string) => any;
declare const __dirname: string;

function read(relative: string): string {
  const { readFileSync } = require('fs');
  const { join } = require('path');
  return readFileSync(join(__dirname, '..', '..', '..', relative), 'utf8') as string;
}

const SOURCES: Record<string, string> = {
  'BlockUserDialog.tsx (차단 확인 대화상자)': read('src/social/BlockUserDialog.tsx'),
  'feed/[id].tsx (글 상세에서 차단 직후 안내)': read('app/feed/[id].tsx'),
  'user/[id].tsx (프로필에서 차단 직후 안내)': read('app/user/[id].tsx'),
  'myPanels.tsx (설정의 「차단된 계정」 설명)': read('src/me/myPanels.tsx'),
};

describe('차단 문구는 양방향을 말한다 — 나도 상대 글이 안 보인다는 것까지', () => {
  it.each(Object.entries(SOURCES))('%s', (_label, source) => {
    // 「보이지 않아요」/「안 보여요」 앞에 "피드"(내가 안 본다는 뜻)가 같은 문장 다발 안에 있어야 한다.
    expect(source).toMatch(/피드에도[^"]*(안 보여요|보이지 않아요)/);
  });

  it('🔴 옛 한 방향 문구(「상대가 내 글을 못 본다」만)가 다시 생기지 않았다', () => {
    for (const source of Object.values(SOURCES)) {
      expect(source).not.toMatch(/tx\('이 사용자에게 내 글이 보이지 않아요\.'/);
      expect(source).not.toMatch(/tx\('이제 이 사용자에게 내 글이 보이지 않아요\.'/);
      expect(source).not.toMatch(/tx\('차단한 사람에게는 내 글이 보이지 않아요\.'/);
    }
  });

  it('영어판도 두 절(this user / their posts)을 다 담는다', () => {
    for (const source of Object.values(SOURCES)) {
      const m = source.match(/"([^"]*can no longer see your posts[^"]*|[^"]*won't see your posts[^"]*)"/);
      if (m) expect(m[1]).toMatch(/feed either/);
    }
  });
});

describe('바뀐 한국어 문구는 번역 카탈로그에 ja·zhHans·zhHant 를 가진다', () => {
  const translations = read('src/i18n/translations.ts');
  const koLines = [
    '이제 이 사용자에게 내 글이 안 보이고, 내 피드에도 이 사람 글이 안 보여요.',
    '차단한 사람에게는 내 글이 안 보이고, 내 피드에도 그 사람 글이 안 보여요.',
    '이 사용자에게 내 글이 안 보이고, 내 피드에도 이 사람 글이 안 보여요.',
  ];

  it.each(koLines)('%s', (line) => {
    const escaped = line.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const re = new RegExp(`'${escaped}':\\s*\\{[^}]*ja:[^}]*zhHans:[^}]*zhHant:`);
    expect(translations).toMatch(re);
  });

  it('옛 문구의 번역 줄이 카탈로그에 남아 있지 않다', () => {
    expect(translations).not.toContain("'이 사용자에게 내 글이 보이지 않아요.'");
    expect(translations).not.toContain("'이제 이 사용자에게 내 글이 보이지 않아요.'");
    expect(translations).not.toContain("'차단한 사람에게는 내 글이 보이지 않아요.'");
  });
});
