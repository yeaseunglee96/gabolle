// 라우터 뼈대의 진입점 — 모든 화면이 이 Stack 을 거쳐 뜬다.
// 헤더는 화면마다 다르게 만들 것이므로 기본은 꺼둔다.
import { Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { color } from '@/design/tokens';
import { AuthProvider } from '@/auth/AuthProvider';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <AuthProvider><OnboardingPreferencesProvider>
        <Stack
          screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: color.canvas },
          }}
        />
      </OnboardingPreferencesProvider></AuthProvider>
    </SafeAreaProvider>
  );
}
