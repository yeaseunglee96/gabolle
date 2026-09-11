# Frontend

**Expo(React Native) 기반** 프론트엔드 작업 영역입니다. (React + TypeScript 웹이 아닙니다.)

## 처음 받은 뒤 한 명령으로 실행

```bash
npm run setup:web
```

의존성을 설치한 뒤 웹 개발 서버를 엽니다. 두 번째 실행부터는 설치를 건너뛰고
`npm run web`을 사용해도 됩니다. 앱으로 확인하려면 `npm run setup:web`을 한 번
실행한 뒤 `npm run android` 또는 `npm run ios`를 사용합니다.

## 빌드와 타입 검사

```bash
npm run build:web
npm run typecheck
```

두 검사를 한 번에 실행하려면 `npm run verify`를 사용합니다. 프로덕션 웹 결과물은
`dist/`에 만들어집니다.

## 왜 Expo 인가

iOS 빌드는 macOS 에서만 되는데, 팀 개발 환경은 Windows 입니다.
Expo 를 쓰면 EAS Build 로 클라우드에서 iOS 를 빌드할 수 있어 Windows 에서도 iOS 배포가 가능합니다 —
Windows 환경에서 사실상 유일한 경로입니다.

## 구조

- `app/` — expo-router 파일 기반 라우팅. 화면 하나당 파일 하나.
- `src/design/tokens.ts` — 색·타이포·반경·간격 토큰. 화면에서 값을 직접 하드코딩하지 않습니다.
- `src/layout/` — 폴드8 등 화면비 대응(`useLayout`, `Split`).
- `src/components/` — 화면 간 공통 컴포넌트.

## MR 빌드 검사 (S15P21E201-269)

`frontend/`가 바뀐 MR에서는 `.gitlab-ci.yml`의 `frontend:build` 잡이 자동으로
`npx expo export --platform web`을 돌려 웹 번들이 실제로 만들어지는지 확인합니다.

## MR 화면 스모크 검사 (S15P21E201-252)

`frontend:build`는 번들이 만들어지는지만 봅니다 — 타입 검사·문법 검사와 같은 층이라,
브라우저에서 실행돼야만 터지는 문제(예: `let` 선언이 첫 사용처보다 아래에 있어
부트스트랩이 죽는 것)는 못 잡습니다. `frontend:smoke` 잡이 헤드리스 브라우저로
실제로 화면을 열어서 (1) 텍스트가 그려졌는지 (2) 콘솔에 error가 안 찍혔는지를 봅니다
(`tools/smoke-check.mjs`). 백엔드 없는 환경이라 API 호출 실패("Failed to load
resource")는 실패로 안 잡습니다 — 그건 이 검사 환경의 특성이지 앱 버그가 아닙니다.

로컬에서 같은 걸 확인하려면:

```bash
cd frontend
npx expo export --platform web
npx serve dist -l 3000 &
npm run smoke -- http://localhost:3000
```
