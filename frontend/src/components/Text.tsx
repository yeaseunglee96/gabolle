// 화면마다 폰트 크기·줄높이·기본 색을 반복해서 고르지 않으려고 타이포 토큰을 감싼 텍스트.
// variant 를 고르면 색까지 기본값이 따라온다 — 색이 필요하면 그때만 color prop 으로 덮어쓴다.
//
// 🔴 한국어 줄바꿈도 여기서 잡는다 — S15P21E201-1360.
//
// 브라우저·iOS·안드로이드 모두 한글은 **음절 사이 어디서든** 끊어도 된다는 것이 기본 규칙이다
// (중국어·일본어와 같은 취급). 그래서 폰 폭에서 「잠시 후 다시 시도해 주」 다음 줄에 「세요.」가
// 온다. 화면 폭이 바뀌면 어느 문장에서든 생기므로 화면마다 고칠 수 있는 종류가 아니고,
// 모든 화면이 쓰는 이 부품 한 곳에서 낱말 단위로만 끊게 한다.
//
//   웹        word-break: keep-all — 띄어쓰기에서만 끊는다. overflow-wrap: anywhere 는 낱말 하나가
//             줄 전체보다 길 때(긴 주소 같은 것)만 끊는 안전장치. text-wrap: pretty 는 마지막 줄에
//             낱말 하나만 외톨이로 남는 것을 브라우저가 피하게 한다(지원하는 브라우저만)
//   iOS       lineBreakStrategyIOS="hangul-word" — 운영체제가 주는 바로 그 스위치
//   안드로이드  그런 스위치가 리액트 네이티브에 없다. 대신 한글 음절 사이에 **낱말 이음표**
//             (U+2060 WORD JOINER — 보이지 않고 폭도 없지만 「여기서 끊지 마라」는 뜻의 글자)를
//             넣어 낱말을 통째로 붙인다. 안드로이드에서만 하므로 시험(jest 는 iOS 로 돈다)과
//             글자 비교에는 영향이 없다. textBreakStrategy="balanced" 는 여러 줄을 고르게 나눠
//             외톨이 낱말을 줄인다
import { Platform, Text as RNText, type TextProps as RNTextProps } from 'react-native';

import { color, fontFamilyStack, type as typeTokens } from '@/design/tokens';

type Variant = 'hero' | 'display' | 'title' | 'body' | 'util' | 'caption' | 'eyebrow' | 'micro';

type Weight = 'regular' | 'medium' | 'bold';

export type TextProps = RNTextProps & {
  variant?: Variant;
  /** 토큰 기본 색을 덮어써야 할 때만 쓴다 (예: 버튼 배경 위 흰 글자). */
  color?: string;
  weight?: Weight;
};

const DEFAULT_COLOR: Record<Variant, string> = {
  hero: color.text.onAction,
  display: color.text.heading,
  title: color.text.heading,
  body: color.text.body,
  util: color.text.muted,
  caption: color.text.muted,
  eyebrow: color.text.eyebrow,
  micro: color.text.muted,
};

const SIZE: Record<Variant, { size: number; lineHeight: number; letterSpacing: number }> = {
  hero: typeTokens.hero,
  display: typeTokens.display,
  title: typeTokens.title,
  body: typeTokens.body,
  util: typeTokens.util,
  caption: typeTokens.caption,
  eyebrow: typeTokens.caption,
  /** 눈썹·탭 라벨·배지 — caption 보다 작고 자간이 넓다 (S15P21E201-1343). */
  micro: typeTokens.micro,
};

const FONT_WEIGHT: Record<Weight, '400' | '500' | '700'> = {
  regular: '400',
  medium: '500',
  bold: '700',
};

const FONT_FAMILY: Record<Weight, string> = {
  regular: fontFamilyStack.regular,
  medium: fontFamilyStack.medium,
  bold: fontFamilyStack.bold,
};

const WORD_JOINER = String.fromCharCode(0x2060); // 보이지 않는 글자라 이스케이프 대신 코드로 적는다
/** 한글 음절(가–힣)·자모(ㄱ–ㅣ) 뒤에 또 한글이 오는 자리. 낱말 안에서만 잡힌다 — 띄어쓰기·문장부호 앞뒤는 그대로다.
 *  (뒤돌아보기(lookbehind) 없이 쓴다 — 헤르메스(안드로이드 JS 엔진) 판에 따라 없을 수 있다.) */
const HANGUL_THEN_HANGUL = /([가-힣ㄱ-ㆎ])(?=[가-힣ㄱ-ㆎ])/g;

/** 한글 낱말 안의 음절을 낱말 이음표로 붙인다. 문자열이 아닌 자식(다른 Text 등)은 그대로 둔다. */
export function joinHangulSyllables(text: string): string {
  return text.replace(HANGUL_THEN_HANGUL, `$1${WORD_JOINER}`);
}

function joinHangulChildren(children: React.ReactNode): React.ReactNode {
  if (typeof children === 'string') return joinHangulSyllables(children);
  if (Array.isArray(children)) return children.map((child) => (typeof child === 'string' ? joinHangulSyllables(child) : child));
  return children;
}

// 🔴 웹의 세 속성은 react-native-web 스타일 표에 없는 것도 있어(text-wrap) 그 판에서는 조용히
//    버려진다. 그래도 keep-all 만으로 낱말 중간 끊김은 사라진다 — 나머지는 되면 좋은 것이다.
const WEB_LINE_BREAK = Platform.OS === 'web'
  ? ({ wordBreak: 'keep-all', overflowWrap: 'anywhere', textWrap: 'pretty' } as unknown as RNTextProps['style'])
  : null;

export function Text({ variant = 'body', color: colorOverride, weight = 'regular', style, children, ...rest }: TextProps) {
  const { size, lineHeight, letterSpacing } = SIZE[variant];
  return (
    <RNText
      lineBreakStrategyIOS="hangul-word"
      textBreakStrategy="balanced"
      {...rest}
      style={[
        {
          fontFamily: FONT_FAMILY[weight],
          fontSize: size,
          lineHeight,
          letterSpacing,
          fontWeight: FONT_WEIGHT[weight],
          color: colorOverride ?? DEFAULT_COLOR[variant],
        },
        WEB_LINE_BREAK,
        style,
      ]}
    >
      {Platform.OS === 'android' ? joinHangulChildren(children) : children}
    </RNText>
  );
}
