// 번들에 박히는 공개 설정값이 비어 있으면 **소리 내어** 알린다.
//
// 🔴 왜 필요한가 — 2026-09-07 배포본에서 웹 지도가 통째로 사라져 있었다.
//    `EXPO_PUBLIC_KAKAO_MAP_JS_KEY` 가 빈 문자열인 채로 빌드됐고, 이 값들은
//    **번들(앱 소스를 브라우저가 읽을 수 있는 파일 한 덩어리로 합친 것)을 만드는
//    순간 문자열로 코드에 새겨지므로**, 값이 비면 그 값을 쓰는 가지가 절대 실행될 수
//    없는 코드가 된다. 번들을 줄이는 도구는 그런 가지를 통째로 지운다(죽은 코드 제거).
//    그래서 배포된 파일 안에 카카오 지도 주소가 **한 글자도 없었다.**
//
//    아무도 몰랐던 이유는 단순하다 — **빈 값이 아무 소리도 안 냈다.**
//
// 🔴 실패시키지 않는다(종료 코드 0으로 끝난다). 이 값들 없이도 앱은 도는 것이 정상이고,
//    여기서 막으면 키가 없는 사람과 CI 가 아무것도 못 빌드하게 된다.
//    이 파일이 하는 일은 **조용히 비는 것을 시끄럽게 비는 것으로 바꾸는 것**뿐이다.
//
// 어디서 도는가: `frontend/Dockerfile` 이 `expo export` 바로 앞에서 부른다
// (배포되는 번들을 만드는 그 빌드다). 손으로는 `npm run check:env`.

// 값이 없으면 무엇이 죽는지를 **결과로** 적는다. 변수 이름만 적으면 읽는 사람이
// 그게 무슨 뜻인지 다시 찾아봐야 한다.
const WATCHED = [
  {
    name: 'EXPO_PUBLIC_API_BASE_URL',
    lost: '서버 주소가 코드 기본값(http://localhost:8080)으로 굳는다 — 배포본이 아무 API 도 못 부른다',
  },
  {
    name: 'EXPO_PUBLIC_KAKAO_MAP_JS_KEY',
    lost: '웹 지도가 번들에서 통째로 지워진다 — 지도 화면이 목록 모드로만 뜬다',
  },
  { name: 'EXPO_PUBLIC_GOOGLE_CLIENT_ID', lost: '구글 로그인 버튼이 설정 안내로 떨어진다' },
  { name: 'EXPO_PUBLIC_NAVER_CLIENT_ID', lost: '네이버 로그인 버튼이 설정 안내로 떨어진다' },
  { name: 'EXPO_PUBLIC_KAKAO_CLIENT_ID', lost: '카카오 로그인 버튼이 설정 안내로 떨어진다' },
  { name: 'EXPO_PUBLIC_APPLE_CLIENT_ID', lost: '애플 로그인 버튼이 설정 안내로 떨어진다' },
];

const missing = WATCHED.filter(({ name }) => !(process.env[name] ?? '').trim());

if (!missing.length) {
  console.log('[공개 설정값] 지켜보는 값이 모두 채워져 있습니다.');
  process.exit(0);
}

const bar = '='.repeat(72);
console.warn(`\n${bar}`);
console.warn(`  ⚠ 이 빌드에 공개 설정값 ${missing.length}개가 비어 있습니다.`);
console.warn('  이 값들은 지금 만드는 번들에 그대로 박힙니다. 나중에 바꾸려면 다시 빌드해야 합니다.');
console.warn(bar);
for (const { name, lost } of missing) {
  console.warn(`  - ${name}`);
  console.warn(`      비면: ${lost}`);
}
console.warn(bar);
console.warn('  넘기는 곳: frontend/Jenkinsfile 의 docker build --build-arg');
console.warn('  받는 곳:   frontend/Dockerfile 의 ARG / ENV');
console.warn(`${bar}\n`);

// 🔴 일부러 0 이다. 위 설명을 읽고 나서 이 줄을 1 로 바꾸지 마라 —
//    그러면 키를 못 가진 사람이 로컬에서 웹 빌드를 아예 못 한다.
process.exit(0);
