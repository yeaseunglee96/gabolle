// 알림 화면은 스크롤되어야 한다 (S15P21E201-1788).
//
// 🔴 왜 소스 검사인가 — 이건 화면을 그려도 안 잡힌다.
//
// Screen 은 `scroll` 이 있을 때만 ScrollView 를 만든다. 없으면 그냥 View 라서
// 내용이 넘쳐도 «잘릴 뿐 오류가 없다». 렌더 시험으로 잡으려면 실제 높이를 재야
// 하는데 jsdom 에는 높이가 없다. 그래서 「prop 이 붙어 있는가」를 글자로 본다.
//
// 이 화면만의 문제였던 이유: NotificationsBody 에는 제 스크롤이 없고,
// 마이페이지에서 패널로 열 때는 MyPageSheet 의 ScrollView 가 감싸 준다.
// 그래서 마이페이지로만 시험하면 멀쩡해 보인다.
// 앱 tsconfig 에는 node 타입이 없다 — 다른 소스 검사 시험과 같은 방식으로 읽는다
// (S15P21E201-1782 에서 맞춰 둔 모양).
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');

const source = readFileSync(join(__dirname, '..', 'notifications.tsx'), 'utf8') as string;

describe('알림 화면', () => {
  it('Screen 에 scroll 을 준다 — 알림이 한 화면을 넘으면 아래가 영영 안 보인다', () => {
    expect(source).toMatch(/<Screen\s+scroll\b/);
  });

  it('본문은 제 스크롤이 없다 — 그래서 바깥이 스크롤을 맡아야 한다', () => {
    const body = readFileSync(
      join(__dirname, '..', '..', 'src', 'me', 'panels', 'NotificationsBody.tsx'),
      'utf8',
    ) as string;
    expect(body).not.toContain('<ScrollView');
  });
});
