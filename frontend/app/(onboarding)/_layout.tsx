import { ProtectedRoute } from '@/auth/ProtectedRoute';

export default function OnboardingLayout() {
  return <ProtectedRoute publicPaths={['/permissions']} />;
}
