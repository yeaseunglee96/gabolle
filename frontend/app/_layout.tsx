// 라우터 뼈대의 진입점 — 모든 화면이 이 Stack 을 거쳐 뜬다.
// 헤더는 화면마다 다르게 만들 것이므로 기본은 꺼둔다.
import { QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { Platform, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { color, fontFamily } from '@/design/tokens';
import { toHtmlLang } from '@/i18n/languages';
import { AuthProvider } from '@/auth/AuthProvider';
import { AppErrorBoundary } from '@/components/AppErrorBoundary';
import { ApiAvailabilityBanner } from '@/components/ApiAvailabilityBanner';
import { BuildInfoBadge } from '@/components/BuildInfoBadge';
import { CollectionProvider } from '@/collection/CollectionProvider';
import { OnboardingPreferencesProvider, useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { PlanProvider } from '@/plan/PlanProvider';
import { TopNav } from '@/nav/TopNav';
import { queryClient } from '@/api/queryClient';

// 🔴 웹에서만 의미가 있다 — 스크린 리더가 어느 언어 발음 규칙을 쓸지, 브라우저가 어느
// 언어의 맞춤법 검사·번역 제안을 띄울지가 이 값을 본다(S15P21E201-1109). 네이티브
// (iOS/Android)에는 `<html>` 자체가 없어 손댈 대상이 없다.
function HtmlLangSync() {
  const { language } = useOnboardingPreferences();
  useEffect(() => {
    if (Platform.OS !== 'web') return;
    document.documentElement.lang = toHtmlLang(language);
  }, [language]);
  return null;
}

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
        {/* 🔴 상태바 글자색의 **기본값**을 여기서 한 번만 정한다 (S15P21E201-1151).
            `style` 은 배경이 아니라 **글자·아이콘 색**이다 — `dark` 가 검은 글자다.

            없으면 어떻게 되나. expo-status-bar 는 네이티브 상태바를 **전역으로, 명령형으로**
            바꾸고 화면을 떠나도 **안 되돌아온다.** 그런데 이 뼈대에 기본값이 없어서,
            배경이 어두운 환영 화면(app/index.tsx 의 style="light")을 한 번 지나면
            **흰 글자가 그대로 남았다.** 그 뒤 화면은 전부 canvas(#fffdf8, 거의 흰색)라
            시각·배터리가 배경에 묻혀 안 보였다 — 실기기 리포트 2026-09-17.
            모달이 화면을 어둡게 덮은 화면에서만 글자가 또렷했던 것이 그 증거다.

            어두운 배경을 쓰는 화면은 지금처럼 자기 자리에서 style="light" 로 덮는다. */}
        <StatusBar style="dark" />
        {/* 🔴 서버 데이터 보관소는 가장 바깥에 둔다 (S15P21E201-957) — 아래 공급자들도
            이 보관소를 쓸 수 있어야 하고, 화면이 바뀌어도 이 층은 안 사라진다. */}
        <QueryClientProvider client={queryClient}>
        <OnboardingPreferencesProvider>
          <HtmlLangSync />
          <ApiAvailabilityBanner />
          <BuildInfoBadge />
          <AuthProvider>
            <PlanProvider>
              <CollectionProvider>
                {/* 🔴 넓은 화면 상단 바는 여기, 앱 뼈대에서 **한 번만** 붙인다
                    (S15P21E201-994). 화면이나 하위 레이아웃에서 또 붙이지 않는다 — 전에는
                    네 군데에서 따로 붙였고, 그래서 50개 넘는 화면에 바가 없었다.
                    좁은 화면(폰)에서는 TopNav 자신이 아무것도 안 그린다. */}
                <View style={{ flex: 1 }}>
                  <TopNav />
                  <Stack
                    screenOptions={{
                      headerShown: false,
                      contentStyle: { backgroundColor: color.canvas },
                    }}
                  />
                </View>
              </CollectionProvider>
            </PlanProvider>
          </AuthProvider>
        </OnboardingPreferencesProvider>
        </QueryClientProvider>
      </SafeAreaProvider>
    </AppErrorBoundary>
  );
}
