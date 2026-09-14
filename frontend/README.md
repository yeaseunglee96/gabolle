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

## 네이티브 E2E — Maestro (S15P21E201-777)

지금까지의 스모크·타입체크는 전부 웹 번들 기준이다. 실제 iOS·Android 빌드에서
핵심 플로우(회원가입·로그인·여행 생성)가 도는지 자동으로 확인한 적이 없었다.
[Maestro](https://maestro.mobile.dev)는 Detox처럼 매번 네이티브 재빌드·복잡한
설정이 필요 없고, 접근성 트리 기반 YAML 플로우라 소규모 팀이 빠르게 시작하기
적합해서 골랐다. 플로우 파일은 `frontend/.maestro/`에 있다.

### 설치

Maestro CLI는 JVM 위에서 돈다 — **Java 17 이상**이 먼저 있어야 한다.

**macOS·Linux·WSL:**

```bash
curl -fsSL "https://get.maestro.mobile.dev" | bash
```

**Windows(WSL 없이 네이티브로):** [GitHub 릴리스](https://github.com/mobile-dev-inc/maestro/releases)에서
`maestro.zip`을 내려받아 `C:\maestro` 같은 고정 위치에 풀고, PATH에
`C:\maestro\bin`을 더한다(`setx PATH "%PATH%;C:\maestro\bin"` 후 터미널 재시작).

```powershell
maestro --version   # 설치 확인
```

### 대상 기기 — 에뮬레이터·시뮬레이터가 없을 때

이 저장소는 팀 개발 환경이 Windows라 iOS 시뮬레이터를 못 쓴다(macOS 전용). Android
쪽도 Android Studio를 새로 깔지 않고 명령줄 도구만으로 에뮬레이터를 띄울 수 있다.

```powershell
# 명령줄 도구 + 에뮬레이터 + 시스템 이미지 (한 번만)
sdkmanager "platform-tools" "emulator" "system-images;android-34;google_apis;x86_64" "platforms;android-34"
avdmanager create avd -n gabolle_test -k "system-images;android-34;google_apis;x86_64" -d "pixel_6"

# 매번 — 에뮬레이터를 띄우고 앱을 설치한다
emulator -avd gabolle_test
adb install path\to\gabolle-preview.apk
```

🔴 **BlueStacks(실기기 대안, S15P21E201-250)로 대신하지 않는다.** 실측(2026-09-11)
— BlueStacks·`avdmanager` 에뮬레이터·**Galaxy S10 실기기**(같은 날 추가 확인)
셋 다, 앱이 보내는 네트워크 요청이 서버에 닿지 못하는 같은 증상을 보였다(브라우저·
curl로는 같은 호스트가 멀쩡히 응답했다). 원인은 에뮬레이터 한정이 아니라 서버
TLS 인증서 체인이 최신 루트(ISRG Root X2)만 타서, OS 신뢰 저장소가 오래된
기기(제조사 지원 종료 등)에서 전부 겪는 문제로 확정됐다(S15P21E201-827) — 고치는
자리는 앱이 아니라 서버다. Maestro 자체는 세 기기 모두에서 화면 탐색·입력은
끝까지 정확히 해낸다(단, 실기기는 화면이 좁아 `signup.yaml`에 scroll 한 줄이
더 필요했다 — 아래 실측 참고).

### 실행

```powershell
maestro --device <기기ID> test frontend/.maestro/signup.yaml
```

`<기기ID>`는 `adb devices`(Android) 또는 `xcrun simctl list`(iOS)로 확인한다.
기기가 하나뿐이면 `--device`를 생략해도 된다.

### 지금 있는 플로우

- `signup.yaml` — 회원가입 핵심 플로우. 언어 선택 → 앱 소개 건너뛰기 → 연령 확인 →
  알림 권한 안내 → 가입 폼 작성 → 약관 동의 → 제출까지, 화면 탐색과 입력은 실기기
  (Galaxy S10)에서 전부 검증됐다. 마지막 "이메일을 확인해 주세요" 단정은 위에서
  설명한 서버 TLS 문제(S15P21E201-827) 때문에 지금은 실패한다 — 흉내 내어 통과시키지
  않았다. 서버가 고쳐지면 같은 파일이 그대로 통과해야 한다.

### CI 연동은 아직 안 한다

매 MR마다 돌리기엔 무겁다(에뮬레이터 기동 자체가 몇 분이다). 배포 전이나 야간
스케줄 같은 별도 파이프라인으로 붙이는 것은 후속 과제로 남긴다.
