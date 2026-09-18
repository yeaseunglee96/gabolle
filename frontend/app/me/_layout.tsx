// 마이페이지 하위 화면은 로그인한 사람만 본다 (S15P21E201-960).
//
// 🔴 이 파일이 없으면 로그아웃 상태로 /me/preferences 주소를 직접 열었을 때 화면이 그냥
// 뜬다 — 계정 취향을 보여주는 자리인데 계정이 없다. 저장을 눌러야 비로소 실패한다.
// 다른 보호 화면들과 같은 장치를 쓴다.
//
// 🔴 ?preview=ui 통로도 여기서 같이 따라온다 — 개발 중에 로그인 없이 화면 모양만 볼 때
// 쓰는 자리다(ProtectedRoute). 그 통로가 없으면 화면을 고칠 때마다 로그인을 해야 한다.
//
// 🔴 상단 바는 여기서 붙이지 않는다 (S15P21E201-994). -970 에서 한 번 여기에 손으로
// 붙였는데, 그건 증상만 막은 것이었다 — 마이페이지에만 없던 게 아니라 **50개 넘는 화면에
// 없었다.** 이제 앱 뼈대(app/_layout.tsx)가 모든 화면에 한 번만 붙인다.
import { ProtectedRoute } from '@/auth/ProtectedRoute';

export default function MeLayout() {
  return <ProtectedRoute />;
}
