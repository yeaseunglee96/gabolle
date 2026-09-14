// 라우터 뼈대의 진입점 — 모든 화면이 이 Stack 을 거쳐 뜬다.
// 헤더는 화면마다 다르게 만들 것이므로 기본은 꺼둔다.
import { QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import { View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { color, fontFamily } from '@/design/tokens';
import { AuthProvider } from '@/auth/AuthProvider';
import { AppErrorBoundary } from '@/components/AppErrorBoundary';
import { ApiAvailabilityBanner } from '@/components/ApiAvailabilityBanner';
import { BuildInfoBadge } from '@/components/BuildInfoBadge';
import { CollectionProvider } from '@/collection/CollectionProvider';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { PlanProvider } from '@/plan/PlanProvider';
import { queryClient } from '@/api/queryClient';

export default function RootLayout() {
  const [fontsLoaded] = useFonts({
    [fontFamily.regular]: require('../assets/fonts/Pretendard-Regular.ttf'),
    [fontFamily.medium]: require('../assets/fonts/Pretendard-Medium.ttf'),
    [fontFamily.bold]: require('../assets/fonts/Pretendard-Bold.ttf'),
  });

  // 폰트가 아직이면 기기 기본 글꼴로 잠깐 그렸다가 바뀌는 깜빡임을 막으려고 화면을
  // 비워 둔다 — 로딩이 보통 수십 ms 라 스플래시 화면 붙이는 것보다 이쪽이 간단하다.
  if (!fontsLoaded) return <View style={{ flex: 1, backgroundColor: color.canvas }} />;

  return (
    <AppErrorBoundary>
      <SafeAreaProvider>
        {/* 🔴 서버 데이터 보관소는 가장 바깥에 둔다 (S15P21E201-957) — 아래 공급자들도
            이 보관소를 쓸 수 있어야 하고, 화면이 바뀌어도 이 층은 안 사라진다. */}
        <QueryClientProvider client={queryClient}>
        <OnboardingPreferencesProvider>
          <ApiAvailabilityBanner />
          <BuildInfoBadge />
          <AuthProvider>
            <PlanProvider>
              <CollectionProvider>
                <Stack
                  screenOptions={{
                    headerShown: false,
                    contentStyle: { backgroundColor: color.canvas },
                  }}
                />
              </CollectionProvider>
            </PlanProvider>
          </AuthProvider>
        </OnboardingPreferencesProvider>
        </QueryClientProvider>
      </SafeAreaProvider>
    </AppErrorBoundary>
  );
}
