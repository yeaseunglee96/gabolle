// 비회원이 눌러도 그냥 되던 것들을 로그인으로 보낸다 (S15P21E201-1795).
//
// 🔴 왜 이 시험이 필요한가 — 이 결함은 «화면이 멀쩡히 그려지고 오류도 안 난다».
//    비회원이 인용을 누르면 인용 수가 올라갔고, 하트를 누르면 「장소를 저장했어요」가
//    떴다. 실패가 아니라 «성공처럼 보이는 것»이라 어떤 자동 검사도 안 잡았다.
//
//    같은 줄의 좋아요·저장은 이미 로그인으로 보내고 있었다. 셋이 나란히 있는데
//    하나만 다르게 굴면 사람은 그것을 「되는 기능」으로 읽는다.
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');

const read = (...parts: string[]) =>
  readFileSync(join(__dirname, '..', '..', '..', ...parts), 'utf8') as string;

/** `if (!accessToken) { ... '/sign-in' ... }` 한 줄이 있는가. */
const guardsWithSignIn = (body: string) => /!accessToken[\s\S]{0,120}\/sign-in/.test(body);

/**
 * 이름으로 함수 본문을 대충 떼어 온다.
 *
 * 🔴 넉넉히 떼어야 한다 — 이 저장소는 「왜 이렇게 했나」를 주석으로 길게 적는다.
 *    600 자로 자르면 주석만 읽고 정작 가드는 못 보고 지나친다.
 */
const bodyOf = (source: string, declaration: string) => {
  const start = source.indexOf(declaration);
  if (start < 0) return '';
  return source.slice(start, start + 1600);
};

describe('비회원 행동은 로그인으로 보낸다', () => {
  it('기록 상세 — 인용(링크 복사)', () => {
    const source = read('app', 'feed', '[id].tsx');
    expect(guardsWithSignIn(bodyOf(source, 'const copyLink = async'))).toBe(true);
  });

  it('피드 목록 — 인용(링크 복사)', () => {
    const source = read('app', '(tabs)', 'feed.tsx');
    expect(guardsWithSignIn(bodyOf(source, 'const quote = async'))).toBe(true);
  });

  it('같은 줄의 좋아요·저장도 그대로 막혀 있다 — 인용만 고치고 나머지가 풀리지 않았는가', () => {
    const source = read('app', 'feed', '[id].tsx');
    expect(guardsWithSignIn(bodyOf(source, 'const react = async (reaction: Reaction)'))).toBe(true);
    expect(guardsWithSignIn(bodyOf(source, 'const toggleSave = async'))).toBe(true);
  });

  it('장소 하트 — 비회원이면 저장하지 않고 부름만 넘긴다', () => {
    const hook = read('src', 'home', 'useSavedPlaces.ts');
    // 저장하기 «전에» 비회원을 걸러야 한다. setLikedIds 보다 앞에 있어야 한다.
    const guardAt = hook.indexOf('if (!accessToken && onRequireSignIn)');
    const writeAt = hook.indexOf('setLikedIds((current)');
    expect(guardAt).toBeGreaterThan(-1);
    expect(guardAt).toBeLessThan(writeAt);
  });

  it('하트를 그리는 두 화면이 로그인 이동을 넘긴다 — 한쪽만 고치면 화면마다 규칙이 달라진다', () => {
    for (const screen of [['app', '(tabs)', 'home.tsx'], ['app', 'index.tsx']]) {
      const source = read(...screen);
      expect(source).toMatch(/useSavedPlaces\(accessToken,[\s\S]{0,120}\/sign-in/);
    }
  });

  it('장소 상세 — 저장', () => {
    // 🔴 여기는 「내 여행 후보에 저장했어요. 일정을 만들 때 이 장소를 먼저 넣어요」라고
    //    «약속»까지 했다. 기기에만 남는데 서버가 반영한다고 말한 셈이다.
    const source = read('app', 'place', '[id].tsx');
    expect(guardsWithSignIn(bodyOf(source, 'const toggleSaved = async'))).toBe(true);
  });

  it('후기 화면 — 방문 인증이 위치를 묻기 «전»에 로그인을 본다', () => {
    const source = read('app', 'place-reviews', '[id].tsx');
    const body = bodyOf(source, 'const requestVerification = async');
    expect(guardsWithSignIn(body)).toBe(true);
    // 순서가 핵심이다 — 끝내 못 할 일 때문에 GPS 를 받으면 안 된다.
    expect(body.indexOf('!accessToken')).toBeLessThan(body.indexOf('locationGate.request'));
  });

  it('후기 화면 — 평가 제출', () => {
    const source = read('app', 'place-reviews', '[id].tsx');
    expect(guardsWithSignIn(bodyOf(source, 'const submit = async'))).toBe(true);
  });
});

describe('기록 상세 ⋯ 메뉴 — 비회원에게 열려 있던 것들', () => {
  // 🔴 이 화면은 공유 링크가 도착하는 곳이라 비회원이 가장 먼저 닿는다. ⋯ 단추는
  //    로그인 여부와 상관없이 늘 그려져서, 팔로우·신고·차단이 비회원에게도 보였고
  //    눌러도 «아무 일도 안 일어났다» — 오류도, 로그인 안내도 없었다.
  const source = () => read('app', 'feed', '[id].tsx');

  it('로그인으로 보내는 공용 함수가 있다', () => {
    // accessToken 이 있으면 false(그대로 진행), 없으면 로그인으로 보내고 true.
    expect(source()).toMatch(/const needsSignIn = \(\) => \{[\s\S]{0,200}\/sign-in/);
  });

  // 🔴 같은 줄 안에서 본다. 넓게 잡으면 댓글 카드의 「신고」(reply.id)까지 걸려서,
  //    원글 가드를 지워도 시험이 통과한다 — 실제로 그렇게 새는 것을 확인했다.
  it.each([
    ['팔로우', /onPress: \(\) => \{ if \(!needsSignIn\(\)\) void toggleFollow\(\); \}/],
    ['글 신고', /onPress: \(\) => \{ if \(!needsSignIn\(\)\) setReportingTargetId\(story\.id\); \}/],
    ['사용자 차단', /onPress: \(\) => \{ if \(!needsSignIn\(\)\) setConfirmingBlock\(true\); \}/],
  ])('%s 가 그 함수를 거친다', (_name, pattern) => {
    expect(source()).toMatch(pattern);
  });

  it('댓글 신고도 거친다 — 원글만 막고 댓글이 열려 있으면 같은 구멍이다', () => {
    expect(source()).toMatch(/onReport=\{\(replyId\)[\s\S]{0,80}needsSignIn\(\)/);
  });

  it('링크 복사는 메뉴에 그대로 있다 — 막은 것은 계정이 필요한 셋뿐이다', () => {
    expect(source()).toMatch(/key: 'copy-link'/);
  });
});

describe('내 여행 탭 — 같은 곳으로 가는 단추가 둘이 아니다', () => {
  it('비회원 안내 카드에 「여행 만들기 둘러보기」 단추가 없다 — 헤더의 「새 여행」과 겹쳤다', () => {
    const source = read('app', '(tabs)', 'trips.tsx');
    // 🔴 문구만 찾으면 안 된다 — 카드 «제목»이 「비회원으로 여행 만들기 화면을 둘러볼 수
    //    있어요」라서 같은 글자가 들어 있다. 지운 것은 tx() 로 감싼 «단추 이름»이다.
    expect(source).not.toContain("tx('여행 만들기 둘러보기'");
  });

  it('헤더의 「새 여행」은 그대로 있다 — 지운 쪽은 카드다', () => {
    const source = read('app', '(tabs)', 'trips.tsx');
    expect(source).toContain("tx('새 여행', 'New trip')");
  });

  it('안내 카드의 로그인 단추는 남아 있다 — 카드가 할 일이 그것이다', () => {
    const source = read('app', '(tabs)', 'trips.tsx');
    expect(source).toMatch(/tx\('로그인', 'Sign in'\)[\s\S]{0,160}returnTo: '\/trips'/);
  });
});
