import { Platform, type TextStyle } from 'react-native';

import { color } from './tokens';

/**
 * 입력칸의 웹 기본 포커스 외곽선(검은 상자)을 끈다 — 입력칸 style 에 펼쳐 넣는다 (S15P21E201-1518 시안 3번).
 *
 * 🔴 끄기만 하면 키보드 사용자가 지금 어디에 있는지 잃는다. **이것을 쓰는 칸은 반드시 포커스를
 *    따로 그린다** — 회원가입·로그인은 칸을 감싼 행의 붉은 2px 선(action.outline)이 그 자리다.
 *
 * RN 의 타입은 outlineStyle 에 'none' 을 안 받지만(solid·dotted·dashed 뿐) react-native-web 은
 * 그 값을 CSS outline-style 로 그대로 넘긴다. 그래서 한 번만, 여기서 넓혀 준다.
 */
export const webInputNoOutline: TextStyle | null = Platform.OS === 'web'
  ? ({ outlineStyle: 'none' } as unknown as TextStyle)
  : null;

/**
 * 웹 전역 CSS — StyleSheet 로는 못 닿는 브라우저 기본값을 덮는다 (S15P21E201-1518, 회원가입 시안 5번).
 *
 * 🔴 브라우저 자동완성이 입력칸을 **파랗게** 칠한다. `-webkit-autofill` 은 가상 선택자라
 *    인라인 스타일(react-native-web 이 만드는 것)로는 못 덮는다 — 문서에 규칙을 둬야 한다.
 *    배경을 투명하게 할 방법이 없어서, 그 색이 들어오는 전환을 사실상 멈춰 둔다(600000초).
 *
 * 🔴 왜 `app/+html.tsx` 가 아닌가. 그 파일은 **정적 렌더링(web.output: 'static')에서만** 읽힌다.
 *    이 앱은 web.output 을 안 정해 단일 페이지(single)로 내보내므로 거기 적어도 아무 데도 안 간다.
 *    JS 로 한 번 붙이면 개발 서버·내보내기·정적 어느 쪽이든 같다.
 */
/**
 * 🔴 일본어·중국어 화면에서는 `word-break: keep-all` 을 푼다 — S15P21E201-1525.
 *
 * `Text` 는 한국어가 낱말 중간에서 끊기지 않게 모든 글에 keep-all 을 준다(S15P21E201-1360).
 * 그런데 keep-all 은 띄어쓰기에서만 끊으라는 뜻이라, 띄어쓰기가 없는 일본어·중국어 문장은
 * **통째로 한 낱말**이 된다. 그러면 안전장치 `overflow-wrap: anywhere` 가 줄 끝에서 아무 데나
 * 자르고, 이때는 「。」「、」를 줄 머리에 두지 않는 규칙(금칙)이 안 걸린다 — 리스트 빈 화면의
 * 「…作成してみましょう / 。」, 알림의 「ここで見ら / れます。」(2026-09-23 일본어 웹).
 *
 * 언어는 `<html lang>` 으로 가른다(app/_layout.tsx 의 HtmlLangSync). 속성이 바뀌는 즉시 걸려서
 * 다시 그릴 필요가 없다 — `Text` 가 렌더 때 언어를 읽으면 첫 로드의 언어가 렌더 뒤(effect)에
 * 정해지는 탓에 첫 화면이 한국어 규칙으로 남는다. keep-all 은 인라인 style 로 붙으므로
 * !important 가 아니면 못 덮는다. keep-all 을 단 글자만 고른다.
 *
 * `line-break: strict` 는 장음 「ー」·작은 가나까지 줄 머리에 못 오게 한다. 기본값(auto)은 이것을
 * 허용해 「ベ / ース」처럼 낱말이 갈렸다(개인정보 화면 · 여행 만들기 칩).
 *
 * 일본어는 한 걸음 더 — `word-break: auto-phrase`(브라우저가 문장을 **어절(文節)** 로 나눠 그 사이에서만
 * 끊는다. 크롬 119+). 글자 사이 아무 데서나 끊으면 「…問い合わせ先が違い / ます。」처럼 끝 두세 글자가
 * 다음 줄에 혼자 남았다(2026-09-30 전 화면 점검, 일본어 폰 폭에서 40곳 남짓). 모르는 브라우저는
 * 이 줄을 버리고 위의 normal 로 간다. 중국어에는 이 값이 없다.
 */
const CJK_LINE_BREAK = `
html:lang(ja) [style*="word-break: keep-all"],
html:lang(zh) [style*="word-break: keep-all"] {
  word-break: normal !important;
  line-break: strict;
}
html:lang(ja) [style*="word-break: keep-all"] {
  word-break: auto-phrase !important;
}
`;

const CSS = `
input:-webkit-autofill,
input:-webkit-autofill:hover,
input:-webkit-autofill:focus {
  -webkit-text-fill-color: ${color.text.heading};
  caret-color: ${color.text.heading};
  transition: background-color 600000s 0s, color 600000s 0s;
}

${CJK_LINE_BREAK}`;

const STYLE_ID = 'gabolle-web-global-styles';

/** 웹에서만 한 번 붙인다. 두 번 불려도 하나만 남는다. 네이티브에서는 아무것도 안 한다. */
export function installWebGlobalStyles(doc: Document | undefined = typeof document === 'undefined' ? undefined : document) {
  if (Platform.OS !== 'web' || !doc || doc.getElementById(STYLE_ID)) return;
  const style = doc.createElement('style');
  style.id = STYLE_ID;
  style.textContent = CSS;
  doc.head.appendChild(style);
}
