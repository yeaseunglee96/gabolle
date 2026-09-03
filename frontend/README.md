# Frontend

**Expo(React Native) 기반** 프론트엔드 작업 영역입니다. (React + TypeScript 웹이 아닙니다.)

## 실행

```bash
npm install
npx expo start
```

터미널에 뜨는 QR 을 Expo Go 로 스캔하거나, `a`(Android 에뮬레이터) / `w`(웹) 를 누릅니다.

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
