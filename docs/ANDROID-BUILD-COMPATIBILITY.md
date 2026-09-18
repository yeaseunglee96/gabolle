# Android 빌드 호환성 확인

## 결론

2026-09-05 기준 프론트엔드의 Expo SDK 57.0.19와 React Native 0.86.3 조합은
Android `compileSdk`와 `targetSdk`를 모두 36으로 생성한다. 2026-08-31부터 적용되는
Google Play 신규 앱 및 업데이트 제출 요건인 API 36 이상을 충족한다.

## 확인 방법

1. `frontend/`에서 `npm install`을 실행한다.
2. `npx expo prebuild --platform android --no-install`로 Android 프로젝트를 생성한다.
3. 생성된 `android/app/build.gradle`이 `rootProject.ext.targetSdkVersion`을 사용하는지 확인한다.
4. Expo 루트 Gradle 플러그인이 읽는 React Native 버전 카탈로그
   `node_modules/react-native/gradle/libs.versions.toml`에서 `compileSdk = "36"`,
   `targetSdk = "36"`을 확인한다.
5. 평소에는 `npm run verify:android-target-sdk`를 실행해 같은 값을 자동 검사한다.

실측 결과:

```text
Android compileSdk=36, targetSdk=36, required>=36
```

## 근거와 갱신 조건

- Google의 Android Developers 문서는 2026-08-31부터 일반 신규 앱과 업데이트가
  Android 16, 즉 API 36 이상을 대상으로 해야 한다고 안내한다.
- Expo SDK 또는 React Native를 올리거나 내릴 때 자동 검사를 다시 실행한다.
- 실제 AAB를 만들면 Play Console의 앱 번들 탐색기에서도 대상 SDK 36 이상인지
  재확인한다. 이 문서는 첫 빌드 전 설정 확인 기록이며, Play Console 업로드 확인을
  대신하지 않는다.

공식 근거: https://developer.android.com/google/play/requirements/target-sdk
