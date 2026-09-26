/**
 * 🔴 **자동화가 찾는 이름표가 사라지지 않게 지킨다** — S15P21E201-1336.
 *
 * <h2>왜 이 시험이 있나</h2>
 *
 * iOS 자동화(XCUITest)와 Appium 은 화면 요소를 **`testID`** 로 찾는다
 * (`testID` 는 리액트 네이티브의 속성이고, iOS 로 나갈 때 **accessibilityIdentifier** —
 * 「사람 눈에 안 보이는 이름표」 — 가 된다). 이름표가 없으면 **화면에 보이는 글자**로
 * 찾을 수밖에 없는데, **그 글자는 언어를 바꾸면 통째로 달라진다.**
 *
 * 2026-09-17 밤에 자동화가 40분을 쓰고 기능 검증 0% 로 끝났다. 원인 둘 다 여기 있다.
 *
 * <ul>
 *   <li>탭을 「여행·일정·현장·부슐랭」으로 찾았다 — 진짜 이름은
 *       「홈·피드·여행 만들기·내 여행·마이페이지」다</li>
 *   <li>글자를 «포함»으로 찾았다. 온보딩 3페이지 <b>본문</b>에 「부슐랭」이라는 낱말이
 *       있어서 걸렸고, 로그에는 「✅ 탭: 부슐랭 · 6/6 완료」가 찍혔다.
 *       <b>같은 순간 스크린샷은 온보딩 화면이었다.</b></li>
 * </ul>
 *
 * 그래서 이름표를 심었고, 이 시험이 그것을 지킨다. 누가 이름을 바꾸거나 지우면
 * **CI 가 먼저 빨개진다** — 자동화가 조용히 거짓 성공을 찍기 전에.
 *
 * <h2>왜 화면을 띄우지 않고 «글자»로 세나</h2>
 *
 * 이 화면들은 지도·카메라·서버를 부른다. 띄워서 세려면 그것들을 전부 가짜로 만들어야
 * 하고, 그 가짜가 낡으면 **시험이 화면이 아니라 가짜를 지키게 된다.** 여기서 지킬 것은
 * 「이름표가 이 파일에 적혀 있는가」 하나뿐이라, 원문을 읽는 것이 가장 정확하고 안 낡는다.
 *
 * <p>진입 경로의 이름표(`lang-*` `start-gabolle` `app-intro-*` `age-gate-*`
 * `permissions-*` `tab-*`)는 {@code entryTestIds.test.tsx} 가 따로 지킨다. 여기서는
 * 그 뒤의 화면들을 지킨다.
 *
 * @see docs/IOS-XCUITEST-A-Z.md — 이름표별로 «무엇을 누르는 것인지»가 적혀 있다
 */

// 저장소의 다른 원문 검사 시험과 같은 방식이다 (suggestionEcho.test.ts 등) —
// 이 tsconfig 에는 node 타입이 없어서 import 대신 require 를 쓴다.
declare const __dirname: string;
const { readFileSync, existsSync } = require('fs');
const { join, resolve } = require('path');

const FRONTEND_ROOT: string = resolve(__dirname, '../../..');

/** 화면 → 그 화면에 반드시 있어야 하는 이름표. */
const REQUIRED: Record<string, readonly string[]> = {
  'app/(auth)/sign-in.tsx': ['sign-in-email', 'sign-in-password', 'sign-in-submit'],
  'app/(auth)/sign-up.tsx': [
    'sign-up-email', 'sign-up-password', 'sign-up-confirm', 'sign-up-name',
    'sign-up-next', 'sign-up-submit',
  ],
  // 글쓰기 본문은 여행 화면 창과 같이 쓰려고 부품으로 옮겼다(S15P21E201-1760) — 글쓰기 화면도 이것을 그린다.
  'src/social/StoryComposeForm.tsx': ['compose-body', 'compose-add-photo', 'compose-submit'],
  'app/trips/[id]/itinerary.tsx': [
    'itinerary-reorder', 'itinerary-reorder-button',
    'itinerary-save-order', 'itinerary-cancel-order', 'itinerary-undo',
  ],
};

/**
 * 값이 끼어 만들어지는 이름표 — 글자 그대로는 파일에 없다.
 *
 * 🔴 `itinerary-day-1` 은 소스에 그렇게 안 적혀 있다. `` `itinerary-day-${index + 1}` `` 로
 * 적혀 있고 그릴 때 번호가 붙는다. 그래서 「그 «틀»이 있는가」를 센다.
 * 틀을 지우면 번호가 붙은 이름표도 전부 함께 사라지므로, 이 한 줄이 그 전부를 지킨다.
 */
const REQUIRED_TEMPLATES: Record<string, readonly string[]> = {
  'app/trips/[id]/itinerary.tsx': ['itinerary-day-${index + 1}'],
  'app/field/translate.tsx': ['field-${tool.key}'],
};

/** 현장 도구 허브의 타일 다섯 — 이름표가 `field-<key>` 로 만들어진다. */
const FIELD_TOOL_KEYS = ['menu', 'phrase', 'exchange', 'bus', 'weather'] as const;

function read(relative: string): string {
  const full = join(FRONTEND_ROOT, relative);
  expect(existsSync(full)).toBe(true);
  return readFileSync(full, 'utf8') as string;
}

describe('자동화가 찾는 이름표(testID)가 화면에 남아 있다', () => {
  for (const [file, ids] of Object.entries(REQUIRED)) {
    describe(file, () => {
      const source = read(file);
      for (const id of ids) {
        it(`「${id}」 이름표가 있다`, () => {
          expect(source).toContain(`testID="${id}"`);
        });
      }
    });
  }

  for (const [file, templates] of Object.entries(REQUIRED_TEMPLATES)) {
    describe(`${file} (값이 끼는 이름표)`, () => {
      const source = read(file);
      for (const template of templates) {
        it(`「${template}」 틀이 있다`, () => {
          expect(source).toContain(template);
        });
      }
    });
  }

  it('현장 도구 타일 다섯의 key 가 그대로다 — 이름표가 여기서 만들어진다', () => {
    const source = read('app/field/translate.tsx');
    for (const key of FIELD_TOOL_KEYS) {
      expect(source).toContain(`key: '${key}'`);
    }
  });

  /**
   * 🔴 이 시험이 실제로 무언가를 가르고 있는지 본다.
   * 있지도 않은 이름표를 찾아 «통과»하면, 위의 시험 전부가 아무것도 안 지키는 것이다.
   */
  it('없는 이름표는 못 찾는다 — 이 시험이 진짜로 가르고 있다', () => {
    const source = read('app/(auth)/sign-in.tsx');
    expect(source).not.toContain('testID="sign-in-이런건없다"');
  });
});
