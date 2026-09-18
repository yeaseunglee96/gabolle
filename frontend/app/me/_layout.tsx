// 마이페이지 하위 화면은 로그인한 사람만 본다.
import { ProtectedRoute } from '@/auth/ProtectedRoute';

export default function MeLayout() {
  return <ProtectedRoute />;
}
