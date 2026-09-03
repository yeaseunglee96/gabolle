import { ProtectedRoute } from '@/auth/ProtectedRoute';
export default function TabsLayout() { return <ProtectedRoute publicPaths={['/home']} />; }
