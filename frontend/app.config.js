// EAS production 빌드에서 공개 설정값이 빠져도 Metro는 빈 문자열을 앱에 박고 성공한다.
// 그 결과 설치 후 Google 로그인이 즉시 실패하거나 모든 API가 localhost를 향한다.
// 깨진 AAB를 배포하는 대신 빌드 단계에서 원인을 정확히 보여 주고 중단한다.
module.exports = ({ config }) => {
  if (process.env.EAS_BUILD_PROFILE === 'production') {
    const required = [
      ['EXPO_PUBLIC_API_BASE_URL', process.env.EXPO_PUBLIC_API_BASE_URL],
      ['EXPO_PUBLIC_GOOGLE_CLIENT_ID', process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID],
    ];
    const missing = required.filter(([, value]) => !value?.trim()).map(([name]) => name);
    if (missing.length) {
      throw new Error(`Production app build is missing required public configuration: ${missing.join(', ')}`);
    }
  }

  // 안드로이드 지도 키 (S15P21E201-1140).
  //
  // 🔴 **없다고 빌드를 세우지 않는다.** 위의 둘과 성격이 다르다 — API 주소나 로그인 키가
  //    빠지면 앱이 통째로 못 쓰게 되지만, 지도 키가 빠지면 **지도 한 화면만** 안 된다.
  //    그걸로 배포를 막으면 멀쩡한 나머지 전부가 같이 멈춘다.
  //
  // 🔴 대신 **화면이 말한다.** 키가 없으면 안드로이드에서 회색 네모가 뜨는데, 회색 네모는
  //    "고장났다" 로 읽힌다. `RouteMap.native.tsx` 가 그 상태를 알아보고 이유를 적는다.
  //
  // 🔴 iOS 는 이 키가 필요 없다 — 애플 지도를 쓴다. 그래서 키가 없어도 아이폰에서는
  //    지도가 정상으로 뜬다.
  const googleMapsApiKey = process.env.GOOGLE_MAPS_ANDROID_API_KEY?.trim();

  return {
    ...config,
    android: {
      ...config.android,
      ...(googleMapsApiKey ? { config: { googleMaps: { apiKey: googleMapsApiKey } } } : {}),
    },
  };
};
