// 이 파일을 `config.local.js` 로 복사하고 키를 채우세요.
//
//   cp bigData/demo/wheelchair-route/config.example.js bigData/demo/wheelchair-route/config.local.js
//
// `config.local.js` 는 .gitignore 에 들어 있어 커밋되지 않습니다.
// 키는 사람마다 다르고, 저장소에 올라가면 남이 내 할당량을 쓰게 됩니다.
//
// 키 만드는 곳: https://developers.kakao.com/console/app
//   1. 애플리케이션 추가 → "앱 키" 의 **JavaScript 키** 를 복사
//   2. 같은 앱의 [플랫폼] → [Web] → 사이트 도메인에 http://localhost:5173 을 그대로 등록
//      🔴 등록한 주소와 실제로 여는 주소가 다르면 지도가 안 뜹니다. 포트까지 같아야 합니다.

window.DEMO_CONFIG = {
  kakaoAppKey: 'YOUR_KAKAO_JAVASCRIPT_KEY',
};
