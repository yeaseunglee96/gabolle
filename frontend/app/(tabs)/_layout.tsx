// 운영(j15e201.p.ssafy.io)에서 실제로 불러 보니, 새로 발급한 익명 출입증으로도
// `GET /api/v1/stories` 가 401 이다(`/api/v1/weather` 도 같다). 익명 인증이 *통과하는 것*과
// 그 엔드포인트가 *익명을 허용하는 것*은 다르다 — 스토리·날씨는 실제 계정을 요구한다.
import { Slot } from 'expo-router';

// 탭 화면은 모두 둘러볼 수 있다. 개인 여행·계정 데이터는 각 화면이 비회원 상태로 감추고
// 저장·작성 같은 계정 작업을 누르는 순간에만 원래 목적지를 보존해 로그인으로 보낸다.
//
// 🔴 그래서 여기에는 로그인 문(ProtectedRoute)을 두지 않는다 — 2026-09-21 정정.
//
// 전에는 `<ProtectedRoute publicPaths={['/home', '/feed', '/trips', '/me']} />` 였다.
// 「탭은 모두 둘러볼 수 있다」고 써 놓고 여섯 탭 중 넷만 허용 목록에 넣었고, 그 목록은
// 경로가 **글자까지 같을 때만** 통과시켰다. 그래서 iOS 에서 상세 화면을 스와이프로 닫고
// 탭으로 돌아오는 찰나 — 네이티브 화면은 이미 돌아왔는데 라우터의 pathname 은 아직
// 안 바뀐 그 한 프레임 — 에 비회원이 마이페이지를 본 직후 로그인 화면으로 튕겼다
// (TestFlight build 37, 실기기). S15P21E201-1242·1292 가 같은 증상을 두 번 고쳤지만
// 둘 다 안드로이드 하드웨어 뒤로가기와 화면 안 단추만 봤고, 이 문 자체는 남겨 뒀다.
//
// 비회원을 로그인으로 자동으로 보내는 코드는 앱 전체에서 ProtectedRoute 의 Redirect
// 하나뿐이다. 각 탭 화면은 이미 비회원을 스스로 다룬다 — me.tsx 는 로그인 단추를
// 보여주고 `enabled: Boolean(accessToken)` 으로 API 를 안 부르며, feed.tsx 는
// `signedIn = Boolean(accessToken)` 으로 가른다. 문이 할 일이 없었다.
export default function TabsLayout() { return <Slot />; }
