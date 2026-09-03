import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { Redirect, Slot, usePathname } from 'expo-router';
import { color } from '@/design/tokens';
import { useAuth } from './AuthProvider';
export function ProtectedRoute({ publicPaths = [] }: { publicPaths?: string[] }) {
  const { user, ready } = useAuth();
  const pathname = usePathname();
  if (publicPaths.includes(pathname)) return <Slot />;
  if (!ready) return <View style={styles.loading}><ActivityIndicator color={color.action.primary} /></View>;
  if (!user) return <Redirect href={{ pathname: '/sign-in', params: { returnTo: pathname } }} />;
  return <Slot />;
}
const styles = StyleSheet.create({ loading: { flex: 1, alignItems: 'center', justifyContent: 'center' } });
