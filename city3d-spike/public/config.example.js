// 항공사진(브이월드 정사영상 — 하늘에서 찍어 지도처럼 펴 놓은 사진)을 받는 데 쓰는 인증키.
//
// 🔴 이 파일은 **자리표시자**다. 진짜 키는 여기 적지 않는다 — 이 파일은 git 에 올라간다.
//
//   1. 이 파일을 같은 폴더에 config.local.js 라는 이름으로 복사한다
//        (Windows)  copy public\config.example.js public\config.local.js
//        (mac/linux) cp public/config.example.js public/config.local.js
//   2. 복사본의 vworldKey 에 키를 적는다. config.local.js 는 .gitignore 에 있어 커밋되지 않는다
//   3. 키가 없거나 이 파일이 없으면 **항공사진만 꺼진다.** 도시·다리·그림자는 그대로 뜬다
//
// 키 받는 곳 — 브이월드(vworld.kr) 로그인 → 오픈API → 인증키 발급. WMTS/TMS 를 체크해야 한다.
//   https://www.vworld.kr/dev/v4dv_apikey_s001.do
//
// 키는 브라우저가 타일 주소에 그대로 붙여 보내므로 화면을 여는 누구나 볼 수 있다.
// 그래서 브이월드는 키를 등록한 서비스 주소(도메인)로 묶는다. git 에 안 올리는 이유는
// "비밀이라서" 보다 "저장소를 fork 하는 사람마다 남의 키를 물려받지 않게" 다.
window.CITY3D = {
  vworldKey: '여기에-브이월드-인증키',
};
