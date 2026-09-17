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

  return config;
};
