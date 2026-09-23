# Handoff: 회원가입 · 약관 동의 화면 개선 (`app/(auth)/sign-up.tsx`)

## Overview
가볼래 회원가입 화면(`/sign-up`)의 약관 동의 단계와 입력 폼을 다듬은 디자인. 동의는 **모달이 아니라 회원가입 페이지 안의 흰 상자**로 유지한다. 바뀌는 것은 6가지:

1. 「비회원으로 둘러보기」 버튼 위 간격 추가
2. 회원가입 버튼이 비활성인 **이유 목록**을 버튼 바로 위에 표시
3. 입력칸 **포커스 = 붉은 테두리**, 브라우저 기본 검은 포커스 외곽선 제거 (웹)
4. 입력값에 문제가 있으면 칸 안을 **아주 옅은 붉은색**으로 채움 — 단, **사용자가 입력한 뒤에만**
5. 브라우저 **자동완성 파란 배경 제거** (웹)
6. 데스크톱 왼쪽 소개 카드를 **부산 야경 사진 패널**로 교체, 어두운 사진 위 글자색 조정

## About the Design Files
`prototype/` 안의 파일은 **HTML로 만든 디자인 레퍼런스**다 — 의도한 모양과 동작을 보여주는 프로토타입이지 그대로 옮길 프로덕션 코드가 아니다. 기존 코드베이스(Expo Router + React Native / react-native-web, `src/design/tokens.ts`, `src/components/*`)의 패턴으로 **`sign-up.tsx` 안에서 다시 구현**할 것. `prototype/signup-consent.dc.html`은 브라우저에서 바로 열린다(같은 폴더의 `support.js` 필요).

## Fidelity
**High-fidelity.** 색·타이포·간격은 `tokens.ts`와 기존 `sign-up.tsx` 스타일을 그대로 쓴 값이다. 새로 생긴 값은 아래 「새 토큰」에 따로 적었다.

## Screens

### 모바일 (kind === 'phone', 390 기준) — `screenshots/mobile-390.png`
기존 4단계 패널 흐름 그대로. 마지막 패널(약관 동의 4/4)에서:
- 동의 상자(`styles.agreements`) → **이유 상자(새)** → `panelNav`(이전 + 회원가입) → 비회원 버튼 → 로그인 링크
- `panelNav.marginTop`: 16 → **4** (이유 상자와 form gap 16 이 이미 간격을 줌. 이유 상자가 없을 때도 form gap 16 + 4 = 20)
- 폰은 앞 패널의 「다음」이 이미 막으므로 이유 상자에는 **동의 3항목만** 나온다.

### 데스크톱 (kind === 'tablet', 1440 기준) — `screenshots/desktop-1440.png`
- 상단 `TopNav`(변경 없음) 아래 `Screen scroll wide`.
- `styles.columnsWide`: `alignItems: 'flex-start'` → **`'stretch'`** (사진 패널이 폼 높이만큼 늘어나도록)
- 왼쪽 `introCard` → **사진 패널** (아래 상세)
- 오른쪽 `formColumn`(maxWidth 480) 순서: 제목 → 부제 → form[이메일, 비밀번호+규칙, 비밀번호 확인, 이름, 언어, 동의 상자, **이유 상자(새)**, 회원가입] → 비회원 버튼 → 로그인 링크

## Components

### 1. 비회원으로 둘러보기 간격
`<Button label="비회원으로 둘러보기" variant="tertiary" containerStyle={{ marginTop: spacing[3] }} />` → 12px.

### 2. 비활성 이유 상자 (새)
- 표시 조건: 이유가 1개 이상일 때만. 0개면 렌더하지 않음(=회원가입 버튼 활성).
- 스타일: `padding: 12 16`, `borderRadius: radius.md (14)`, `backgroundColor: color.surface.tint (#F0F0F3)`, 내부 `gap: spacing[1] (4)`. 선·그림자 없음.
- 제목: caption 13/18 bold, `color.text.heading (#191919)` — 「회원가입하려면 아래를 마저 채워 주세요」
- 항목: caption 13/18 regular, `color.text.body (#444444)`, 앞에 `○ `.
- **빨강을 쓰지 않는다** — 아직 안 채운 것은 오류가 아님(tokens 규칙 4).
- 항목 문구와 순서(데스크톱; 조건이 false 인 것만):
  1. `!emailValid` → 이메일 형식이 올바르지 않아요
  2. `!passwordChecks.length` → 비밀번호를 8~64자로 입력해 주세요
  3. `!passwordChecks.letter` → 비밀번호에 영문을 넣어 주세요
  4. `!passwordChecks.number` → 비밀번호에 숫자를 넣어 주세요
  5. `!passwordChecks.special` → 비밀번호에 특수문자(!@#$% 등)를 넣어 주세요
  6. `password.length > 0 && !passwordMatches` → 비밀번호 확인이 일치하지 않아요
  7. `!nameValid` → 이름을 1~30자로 입력해 주세요
  8. `!ageAccepted` → 만 14세 이상인지 확인해 주세요
  9. `!termsAccepted` → 이용약관에 동의해 주세요
  10. `!privacyAccepted` → 개인정보 처리방침에 동의해 주세요
- 폰: 8~10만.
- i18n: 새 문자열은 `tx(ko, en)`로 감싸고 `src/i18n/translations.ts`에 ja/zhHans/zhHant 추가.
- 접근성: 상자에 `accessibilityLiveRegion="polite"` 권장(목록이 바뀔 때 읽힘).

### 3–4. 입력칸 상태 (`styles.inputRow`)
| 상태 | borderColor | borderWidth | background |
|---|---|---|---|
| 기본 | `surface.field #DADCE2` | 1 | `surface.card #FFFFFF` |
| 포커스 | `action.outline #D83A48` | **2** (프로토타입은 1 + 1px 그림자로 레이아웃 흔들림 없이 표현) | 문제 여부에 따름 |
| 입력 후 문제 | `state.danger #B02A38` | 1 | **`#FFFBFB` (새 토큰)** |
| 포커스 + 문제 | `#D83A48` | 2 | `#FFFBFB` |

- **「입력 후」 규칙:** 칸마다 `touched` 플래그. `onChangeText`에서 true. 빨간 테두리·채움·오류 문구는 `touched && value.length > 0 && !valid`일 때만. 처음 열었을 때는 전부 기본 상태.
  - 기존 코드의 `emailTouched`는 onBlur 기준 — onChangeText 기준으로 맞추거나 그대로 두되, 비밀번호/비밀번호 확인/이름에도 같은 플래그 추가.
- 비밀번호 칸의 「문제」 = 4개 규칙 중 하나라도 미충족. 규칙 줄(`Rule`)은 빨강 없이 ○ muted → ✓ success 그대로.
- 포커스 추적: 각 `TextInput`에 `onFocus={() => setFocused('email')}`, `onBlur={() => setFocused(null)}`.
- **검은 박스 제거(웹):** `styles.inputWithClear`에 `Platform.OS === 'web' && { outlineStyle: 'none' }` (react-native-web 은 `outlineStyle`을 CSS outline 으로 넘김). 포커스 표시는 행(`inputRow`)의 붉은 테두리가 대신한다. `oauth-signup.tsx`의 `styles.input`, `sign-in.tsx` 입력칸에도 같은 처리 권장.

### 5. 자동완성 파란 배경 제거 (웹)
`app/+html.tsx`(없으면 Expo Router 웹 HTML 템플릿으로 생성)의 `<style>`에 전역으로:
```css
input:-webkit-autofill,
input:-webkit-autofill:hover,
input:-webkit-autofill:focus {
  -webkit-text-fill-color: #191919;
  caret-color: #191919;
  transition: background-color 600000s 0s, color 600000s 0s;
}
```

### 6. 데스크톱 사진 패널 (`introCard` 대체)
- 컨테이너: `flex: 1`, `minHeight: 280`, `borderRadius: radius.md (14)`, `overflow: 'hidden'`, `padding: 64`, `backgroundColor: '#191919'`(이미지 로딩 전), 내용 **위쪽 정렬**(`justifyContent: 'flex-start'`), `gap: spacing[4] (16)`.
- 이미지: `ImageBackground` 또는 절대배치 `Image`, `resizeMode="cover"`, 초점은 가로 중앙·세로 약 35%(웹은 `backgroundPosition: 'center 35%'`; 네이티브는 cover 기본 중앙도 무방).
- 파일: `prototype/assets/busan-night.jpg` → 앱에는 `assets/home/` 등에 **압축본(~300KB 이하, 긴 변 ~2000px)** 으로 추가. 원본은 약 2350×1570.
- 글자는 사진 윗부분 검은 하늘 위에 놓임 — 오버레이 없이 대비 충분.
  - 눈썹 「가볼래 계정」: caption 13/18 bold, `text.onDarkMuted #DADADF`
  - 제목 「내 여행을 안전하게 저장하세요」: **hero 34/40 bold, letterSpacing -0.25**, `text.onAction #FFFFFF`, `maxWidth: 440`
  - 본문 「선택한 언어와 여행 조건을 이어서 사용할 수 있어요.」: body 15/23 regular, `#DADADF`, `maxWidth: 440`
- `accessibilityIgnoresInvertColors`, 이미지는 장식이므로 `accessible={false}`.

## State (sign-up.tsx 에 추가)
```ts
const [focused, setFocused] = useState<'email'|'password'|'confirm'|'name'|null>(null);
const [touched, setTouched] = useState({ email: false, password: false, confirm: false, name: false });
const blockers = useMemo(() => [...], [/* 위 조건들 */]); // 문자열 배열
// canSubmit 은 기존 그대로 (blockers.length === 0 && !submitting 와 동치)
```

## Design Tokens
기존(`src/design/tokens.ts`): canvas #F5F5F7 · surface.card #FFFFFF · surface.tint #F0F0F3 · surface.soft #E9E9EC · surface.field #DADCE2 · action.primary/outline #D83A48 · action.secondary #2B2B2E · action.tertiary #E9E9EC · text.heading #191919 · text.body #444444 · text.muted #6F6F6F · text.onAction #FFFFFF · text.onDarkMuted #DADADF · state.danger #B02A38 · state.success #2E9E5B. radius md 14 / sm 8 / full 999. spacing 4/8/12/16/24/32. type caption 13/18 · body 15/23 · display 26/34 · hero 34/40. 글꼴 Pretendard.

**새 토큰(추가 필요):**
- `state.dangerFieldBg: '#FFFBFB'` — 입력칸 문제 채움 전용. 기존 `state.dangerBg #FEF0F1`은 경고 상자용이라 입력칸에는 너무 진함.

## Assets
- `prototype/assets/busan-night.jpg` — 사용자 제공 사진(부산 야경). 새 자산.
- 로고·국기·종 아이콘·Pretendard — 기존 저장소 `assets/`에서 복사한 것(변경 없음).

## Files
- `prototype/signup-consent.dc.html` — 모바일(1a)·데스크톱(1b) 디자인. 입력칸·체크박스 동작 확인 가능. 로직은 파일 하단 `class Component` 참고.
- `screenshots/desktop-1440.png`, `screenshots/mobile-390.png` — 초기 상태(빈 입력).
- 대상 소스: `app/(auth)/sign-up.tsx` (주), `app/+html.tsx` (자동완성 CSS), `src/design/tokens.ts` (새 토큰), `src/i18n/translations.ts` (새 문구). 선택: `app/(auth)/oauth-signup.tsx`, `app/(auth)/sign-in.tsx` 에 입력칸 상태 동일 적용.
- 파란색 안내 표시(링·말풍선)는 설명용 주석이며 구현 대상이 아니다.
