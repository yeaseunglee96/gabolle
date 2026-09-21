// 운영(j15e201.p.ssafy.io)에서 실제로 불러 보니, 새로 발급한 익명 출입증으로도
// `GET /api/v1/stories` 가 401 이다(`/api/v1/weather` 도 같다). 익명 인증이 *통과하는 것*과
// 그 엔드포인트가 *익명을 허용하는 것*은 다르다 — 스토리·날씨는 실제 계정을 요구한다.
import { ProtectedRoute } from '@/auth/ProtectedRoute';

// 탭 화면은 모두 둘러볼 수 있다. 개인 여행·계정 데이터는 각 화면이 비회원 상태로 감추고
// 저장·작성 같은 계정 작업을 누르는 순간에만 원래 목적지를 보존해 로그인으로 보낸다.
export default function TabsLayout() { return <ProtectedRoute publicPaths={['/home', '/feed', '/trips', '/me']} />; }
