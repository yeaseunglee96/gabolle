// 🔴 피드를 열었다 (S15P21E201-970). 전에는 로그인해야 볼 수 있었는데, 처음 온 사람에게
// 보여줄 것이 서비스 소개뿐이라 홈이 비어 보이는 원인 중 하나였다.
//
// 열 수 있는 근거는 셋이다.
//   · 서버에 익명 출입증이 있다 (S15P21E201-303, `/api/v1/auth/anonymous`)
//   · api/client.ts 가 그 토큰을 알아서 붙인다 — 비로그인 요청도 서버에서 통과한다
//   · feed.tsx 가 이미 `signedIn` 분기·캐시 키·로그아웃용 빈 상태를 갖고 있다
//
// 계정이 필요한 것(좋아요·팔로우·글쓰기·신고)은 그 자리에서 로그인을 물어본다 —
// 그 유도 모달은 S15P21E201-971 몫이다.
import { ProtectedRoute } from '@/auth/ProtectedRoute';
export default function TabsLayout() { return <ProtectedRoute publicPaths={['/home', '/feed']} />; }
