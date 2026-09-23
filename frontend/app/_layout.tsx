// 라우터 뼈대의 진입점 — 모든 화면이 이 Stack 을 거쳐 뜬다.
// 헤더는 화면마다 다르게 만들 것이므로 기본은 꺼둔다.
import { QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack, useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { Platform, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { color, fontFamily } from '@/design/tokens';
import { shouldWaitForFonts } from '@/design/fontGate';
import { installWebGlobalStyles } from '@/design/webGlobalStyles';
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
import { attachNotificationNavigation, installNotificationHandler } from '@/notifications/pushToken';

// 앱이 앞에 떠 있을 때도 알림이 보이게 — 모듈이 처음 읽힐 때 한 번(S15P21E201-1429).
installNotificationHandler();
// 웹 자동완성의 파란 배경 등 StyleSheet 로 못 닿는 브라우저 기본값 — 웹에서만 한 번(S15P21E201-1518).
installWebGlobalStyles();

// 웹에서만 의미가 있다 — 스크린 리더가 어느 언어 발음 규칙을 쓸지, 브라우저가 어느
// 언어의 맞춤법 검사·번역 제안을 띄울지가 이 값을 본다. 네이티브
// (iOS/Android)에는 `<html>` 자체가 없어 손댈 대상이 없다.
// 알림을 누르면 그 주소로(S15P21E201-1429). 서버가 data.href 에 앱 안 주소를 넣어 준다 — 없으면 그냥 앱이 열린다.
function PushNotificationNavigation() {
  const router = useRouter();
  useEffect(() => attachNotificationNavigation((href) => router.push(href as never)), [router]);
  return null;
}

function HtmlLangSync() {
  const { language } = useOnboardingPreferences();
  useEffect(() => {
    if (Platform.OS !== 'web') return;
    document.documentElement.lang = toHtmlLang(language);
  }, [language]);
  return null;
}

export default function RootLayout() {
  const [fontsLoaded, fontError] = useFonts({
    [fontFamily.regular]: require('../assets/fonts/Pretendard-Regular.ttf'),
    [fontFamily.medium]: require('../assets/fonts/Pretendard-Medium.ttf'),
    [fontFamily.bold]: require('../assets/fonts/Pretendard-Bold.ttf'),
  });

  // 폰트가 아직이면 기기 기본 글꼴로 잠깐 그렸다가 바뀌는 깜빡임을 막으려고 화면을
  // 비워 둔다 — 로딩이 보통 수십 ms 라 스플래시 화면 붙이는 것보다 이쪽이 간단하다.
  //
  // 🔴 «실패했을 때는 기다리지 않는다.» 예전에는 useFonts 의 오류를 안 받아서, 글꼴이
  //    한 번 실패하면 fontsLoaded 가 영영 false 로 남고 이 빈 View 만 그렸다 — 화면은
  //    한 판 색이고 접근성 트리에 요소가 0개인데 프로세스는 멀쩡히 살아 있어서, 밖에서
  //    보면 그냥 「검은 화면」이다. 게다가 이 분기는 AppErrorBoundary «바깥»이라 오류
  //    화면조차 못 띄운다 (S15P21E201-1510).
  //
  //    글꼴은 «있으면 예쁜 것»이지 앱이 뜨는 조건이 아니다. 실패하면 기기 기본 글꼴로
  //    그냥 띄운다 — 글자 모양이 다른 앱이 아무것도 없는 앱보다 낫다.
  if (shouldWaitForFonts(fontsLoaded, fontError)) {
    return <View style={{ flex: 1, backgroundColor: color.canvas }} />;
  }

  return (
    <AppErrorBoundary>
      <SafeAreaProvider>
        {/* 상태바 글자색의 기본값을 여기서 한 번만 정한다.
            `style` 은 배경이 아니라 글자·아이콘 색이다 — `dark` 가 검은 글자다.
        */}
        <StatusBar style="dark" />
        {/* 서버 데이터 보관소는 가장 바깥에 둔다 — 아래 공급자들도
            이 보관소를 쓸 수 있어야 하고, 화면이 바뀌어도 이 층은 안 사라진다.
        */}
        <QueryClientProvider client={queryClient}>
        <OnboardingPreferencesProvider>
          <HtmlLangSync />
          <PushNotificationNavigation />
          <ApiAvailabilityBanner />
          <BuildInfoBadge />
          <AuthProvider>
            <PlanProvider>
              <CollectionProvider>
                {/* 넓은 화면 상단 바는 여기, 앱 뼈대에서 한 번만 붙인다
                     화면이나 하위 레이아웃에서 또 붙이지 않는다 — 전에는
                    네 군데에서 따로 붙였고, 그래서 50개 넘는 화면에 바가 없었다.
                    좁은 화면(폰)에서는 TopNav 자신이 아무것도 안 그린다.
                */}
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
