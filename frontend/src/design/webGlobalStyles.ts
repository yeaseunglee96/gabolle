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
const CSS = `
input:-webkit-autofill,
input:-webkit-autofill:hover,
input:-webkit-autofill:focus {
  -webkit-text-fill-color: ${color.text.heading};
  caret-color: ${color.text.heading};
  transition: background-color 600000s 0s, color 600000s 0s;
}
`;

const STYLE_ID = 'gabolle-web-global-styles';

/** 웹에서만 한 번 붙인다. 두 번 불려도 하나만 남는다. 네이티브에서는 아무것도 안 한다. */
export function installWebGlobalStyles(doc: Document | undefined = typeof document === 'undefined' ? undefined : document) {
  if (Platform.OS !== 'web' || !doc || doc.getElementById(STYLE_ID)) return;
  const style = doc.createElement('style');
  style.id = STYLE_ID;
  style.textContent = CSS;
  doc.head.appendChild(style);
}
