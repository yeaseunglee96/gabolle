import { useEffect, useRef, useState } from 'react';
import { AppState, Image, ImageBackground, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Text } from '@/components/Text';
import { ScenicVideo } from '@/components/ScenicVideo';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { type LanguageCode, useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { useI18n } from '@/i18n';

const logo = require('../assets/brand/gabolle-logo-figma.png');
const welcomeImage = require('../assets/images/welcome-busan.png');
const webHeroImage = require('../assets/home/web-hero.png');
const mobileWelcomeVideo = require('../assets/video/busan-tram-portrait.mp4');
const tramLandscapeVideo = require('../assets/video/busan-tram-landscape.mp4');
const tramSunsetVideo = require('../assets/video/busan-tram-sunset.mp4');
const webWelcomeVideo = require('../assets/video/busan-coast-sunset.mp4');
const beachNightVideo = require('../assets/video/busan-beach-night.mp4');
type WelcomeLanguage = { code: Extract<LanguageCode, 'ko' | 'en'>; label: string };
const LANGUAGES: WelcomeLanguage[] = [{ code: 'ko', label: '한국어' }, { code: 'en', label: 'English' }];
const FEATURES = [
  { icon: require('../assets/icons/home/wand.png'), title: 'AI 일정 만들기', body: 'AI가 위치와 조건을 분석해\n최적의 부산 여행 일정을 제안해요.' },
  { icon: require('../assets/icons/home/route.png'), title: '실시간 경로 안내', body: '현재 위치와 일정에 맞춰\n다음 이동 경로를 한눈에 확인해요.' },
  { icon: require('../assets/icons/home/users.png'), title: '함께 여행 설계', body: '동행자와 조건을 공유하고\n모두에게 맞는 일정을 만들어요.' },
] as const;

const VIDEO_BY_TIME = {
  dawn: [beachNightVideo, mobileWelcomeVideo],
  morning: [mobileWelcomeVideo, tramLandscapeVideo],
  day: [tramLandscapeVideo, mobileWelcomeVideo],
  sunset: [tramSunsetVideo, webWelcomeVideo],
  night: [beachNightVideo, tramSunsetVideo],
} as const;

function videosForCurrentTime() {
  const hour = new Date().getHours();
  if (hour < 7) return VIDEO_BY_TIME.dawn;
  if (hour < 11) return VIDEO_BY_TIME.morning;
  if (hour < 17) return VIDEO_BY_TIME.day;
  if (hour < 20) return VIDEO_BY_TIME.sunset;
  return VIDEO_BY_TIME.night;
}

function shouldUseLightWelcomeLogo() {
  const hour = new Date().getHours();
  return hour < 7 || hour >= 17;
}

function pickWelcomeVideo(previous?: number) {
  const candidates = videosForCurrentTime();
  const alternatives = previous === undefined ? candidates : candidates.filter((source) => source !== previous);
  return alternatives[Math.floor(Math.random() * alternatives.length)] ?? candidates[0];
}

export default function Welcome() {
  const router = useRouter();
  const { width } = useLayout();
  const { language, mobility, setPreferences } = useOnboardingPreferences();
  const { tx } = useI18n();
  const isDesktop = width >= 1120;
  const [welcomeVideo, setWelcomeVideo] = useState<number>(() => pickWelcomeVideo());
  const appState = useRef(AppState.currentState);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      if (appState.current !== 'active' && nextState === 'active') {
        setWelcomeVideo((current) => pickWelcomeVideo(current));
      }
      appState.current = nextState;
    });
    return () => subscription.remove();
  }, []);

  const chooseLanguage = (next: WelcomeLanguage['code']) => {
    setPreferences(next, mobility);
  };
  const start = (next: WelcomeLanguage['code'] = language === 'en' ? 'en' : 'ko') => {
    chooseLanguage(next);
    router.push({ pathname: isDesktop ? '/age-gate' : '/app-intro', params: { language: next, mobility } });
  };

  if (!isDesktop) {
    const lightLogo = shouldUseLightWelcomeLogo();
    return <View style={styles.mobileScreen}>
      <ScenicVideo poster={welcomeImage} source={welcomeVideo} />
      <StatusBar style="light" />
      <SafeAreaView edges={['top', 'bottom']} style={styles.mobileSafeArea}>
        <View style={styles.mobileBrand}><Pressable accessibilityRole="button" accessibilityLabel="GABOLLE 시작하기" accessibilityHint="연령 확인 화면으로 이동합니다" onPress={() => start()} style={({ pressed }) => [styles.logoLink, pressed && styles.pressed]}><Image source={logo} resizeMode="contain" style={[styles.mobileLogo, lightLogo && styles.mobileLogoLight]} /></Pressable><Text variant="display" weight="bold" color={color.brand.orange}>부산 가볼래?</Text></View>
        <View accessibilityRole="radiogroup" accessibilityLabel="시작할 언어 선택" style={styles.languageList}>{LANGUAGES.map((item) => <LanguageButton key={item.code} item={item} selected={language === item.code} onPress={() => start(item.code)} />)}</View>
      </SafeAreaView>
    </View>;
  }

  return <ScrollView style={styles.webScreen} contentContainerStyle={styles.webContent}>
    <StatusBar style="dark" />
    <SafeAreaView edges={['top']} style={styles.webHeader}>
      <Pressable accessibilityRole="link" accessibilityLabel="GABOLLE 홈" onPress={() => router.replace('/')} style={({ pressed }) => [styles.logoLink, pressed && styles.pressed]}><Image source={logo} resizeMode="contain" style={styles.webLogo} /></Pressable>
      <View style={styles.webNav}><NavItem label={tx('홈', 'Home')} onPress={() => router.replace('/')} /><NavItem label={tx('여행 만들기', 'Plan a trip')} onPress={() => start()} /><NavItem label={tx('내 여행', 'My trips')} onPress={() => router.push('/sign-in')} /><NavItem label={tx('부산 축제', 'Festivals')} onPress={() => router.push('/festivals')} /></View>
      <View style={styles.accountActions}>
        <Pressable accessibilityRole="button" accessibilityLabel={`언어를 ${language === 'ko' ? 'English' : '한국어'}로 변경`} onPress={() => chooseLanguage(language === 'ko' ? 'en' : 'ko')} style={styles.localeButton}><Text variant="caption" weight="bold">{language.toUpperCase()}</Text></Pressable>
        <Pressable accessibilityRole="button" onPress={() => router.push('/sign-in')} style={styles.loginButton}><Text variant="caption" weight="bold">{tx('로그인', 'Sign in')}</Text></Pressable>
        <Pressable accessibilityRole="button" onPress={() => start()} style={styles.signupButton}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('회원가입', 'Sign up')}</Text></Pressable>
      </View>
    </SafeAreaView>
    <ImageBackground source={webHeroImage} resizeMode="cover" style={styles.heroSection}>
      <View style={styles.heroBackdrop} />
      <View style={styles.webVideoFrame}><ScenicVideo poster={webHeroImage} source={webWelcomeVideo} /></View>
      <View style={styles.heroCopy}><View style={styles.heroInner}>
        <View style={styles.heroBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>AI TRAVEL PLANNER · BUSAN</Text></View>
        <Text variant="hero" weight="bold" color={color.text.onAction} style={styles.heroTitle}>{tx(`부산의 모든 여행,\n가볼래?`, `Every side of Busan,\nyours to explore.`)}</Text>
        <Text variant="body" color="rgba(255,255,255,0.78)" style={styles.heroDescription}>{tx('취향과 이동 조건을 반영해 당신만의 부산 여행을 만들어요.', 'Build a Busan trip around your taste and mobility needs.')}</Text>
        <View style={styles.heroActions}><Pressable accessibilityRole="button" onPress={() => start()} style={styles.primaryCta}><Text variant="body" weight="bold" color={color.text.onAction}>{tx('여행 계획 시작하기', 'Start planning')}</Text></Pressable><Pressable accessibilityRole="button" onPress={() => router.push('/festivals')} style={styles.secondaryCta}><Text variant="body" weight="bold" color={color.text.onAction}>{tx('부산 축제 보기', 'Explore festivals')}</Text></Pressable></View>
        <View style={styles.heroChips}><HeroChip dot="#64d68a" label="맞춤 일정" /><HeroChip dot="#5ba5ff" label="지금 갈 곳" /><HeroChip dot="#ff976f" label="설명 가능한 추천" /></View>
      </View></View>
    </ImageBackground>
    <View style={styles.featureSection}>
      <View style={styles.featureHeadingRow}><View><Text variant="eyebrow" weight="bold">— EXCLUSIVE FEATURES</Text><Text variant="display" weight="bold" style={styles.featureHeading}>똑똑하고 아름답게{`\n`}설계되는 맞춤형 여정</Text></View></View>
      <View style={styles.featureGrid}>{FEATURES.map((feature) => <View key={feature.title} style={styles.featureCard}><View style={styles.featureIcon}><Image source={feature.icon} resizeMode="contain" style={styles.featureIconImage} /></View><Text variant="title" weight="bold">{feature.title}</Text><Text variant="body" style={styles.featureBody}>{feature.body}</Text></View>)}</View>
    </View>
  </ScrollView>;
}

function LanguageButton({ item, selected, onPress }: { item: WelcomeLanguage; selected: boolean; onPress: () => void }) {
  return <Pressable accessibilityRole="radio" accessibilityState={{ selected }} accessibilityLabel={`${item.label}로 시작하기`} onPress={onPress} style={({ pressed }) => [styles.languageButton, selected && styles.languageButtonSelected, pressed && styles.pressed]}><Text variant="title" weight="bold" color={selected ? color.brand.navy : color.text.onAction}>{item.label}</Text><View style={[styles.languageAction, selected && styles.languageActionSelected]}><Text variant="body" weight="bold" color={selected ? color.text.onAction : color.text.onAction}>{selected ? '✓' : '→'}</Text></View></Pressable>;
}
function NavItem({ label, onPress }: { label: string; onPress: () => void }) { return <Pressable accessibilityRole="link" onPress={onPress} style={styles.navItem}><Text variant="caption" weight="medium">{label}</Text></Pressable>; }
function HeroChip({ dot, label }: { dot: string; label: string }) { return <View style={styles.heroChip}><View style={[styles.chipDot, { backgroundColor: dot }]} /><Text variant="caption" color="rgba(255,255,255,0.78)">{label}</Text></View>; }

const styles = StyleSheet.create({
  pressed: { opacity: 0.78 }, logoLink: { borderRadius: radius.sm }, mobileScreen: { flex: 1, width: '100%', height: '100%', overflow: 'hidden', backgroundColor: color.brand.navy }, mobileBackgroundImage: { ...StyleSheet.absoluteFill, width: '100%', height: '100%' },
  mobileSafeArea: { flex: 1 },
  mobileBrand: { position: 'absolute', top: '13%', left: 0, right: 0, alignItems: 'center', gap: spacing[3] },
  mobileLogo: { width: 280, height: 70 },
  mobileLogoLight: { tintColor: '#ffffff' },
  languageList: { position: 'absolute', left: 24, right: 24, bottom: 44, alignSelf: 'center', gap: spacing[3] },
  languageButton: { height: 58, borderRadius: 18, borderWidth: 1, borderColor: 'rgba(255,255,255,0.48)', backgroundColor: 'rgba(11,29,58,0.30)', paddingHorizontal: 20, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  languageButtonSelected: { borderWidth: 2, borderColor: color.brand.orange, backgroundColor: 'rgba(255,253,248,0.96)', shadowColor: color.brand.navy, shadowOpacity: 0.16, shadowRadius: 10, shadowOffset: { width: 0, height: 4 }, elevation: 4 },
  languageAction: { width: 30, height: 30, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.12)' },
  languageActionSelected: { backgroundColor: color.brand.orange },
  webScreen: { flex: 1, backgroundColor: color.brand.ivory }, webContent: { minHeight: '100%' }, webHeader: { minHeight: 72, paddingHorizontal: 72, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: color.brand.ivory, borderBottomWidth: 1, borderBottomColor: '#e8e3da' }, webLogo: { width: 113, height: 28 }, webNav: { flexDirection: 'row', alignItems: 'center', gap: 44 }, navItem: { paddingVertical: spacing[3] }, accountActions: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  localeButton: { minWidth: 38, height: 38, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: '#f6efe6' }, loginButton: { minWidth: 76, minHeight: 38, borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4] }, signupButton: { minWidth: 82, minHeight: 38, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4], backgroundColor: color.brand.navy },
  heroSection: { height: 500, justifyContent: 'center', backgroundColor: color.brand.navy }, heroBackdrop: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(11,29,58,0.32)' }, webVideoFrame: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 620, overflow: 'hidden', backgroundColor: color.brand.navy }, heroCopy: { width: 620, height: '100%', justifyContent: 'center', paddingLeft: 72, paddingRight: 64, backgroundColor: color.brand.navy }, heroInner: { width: 548 }, heroBadge: { alignSelf: 'flex-start', borderWidth: 1, borderColor: 'rgba(255,255,255,0.20)', borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.10)', paddingHorizontal: spacing[4], paddingVertical: 6 }, heroTitle: { marginTop: 28, fontSize: 68, lineHeight: 71, letterSpacing: -2.4 }, heroDescription: { marginTop: 28, fontSize: 18, lineHeight: 29 }, heroActions: { flexDirection: 'row', gap: spacing[4], marginTop: 28 }, primaryCta: { minHeight: 52, borderRadius: radius.full, backgroundColor: color.brand.orange, paddingHorizontal: 40, alignItems: 'center', justifyContent: 'center' }, secondaryCta: { minHeight: 52, borderRadius: radius.full, borderWidth: 1, borderColor: 'rgba(255,255,255,0.20)', backgroundColor: 'rgba(255,255,255,0.08)', paddingHorizontal: 28, alignItems: 'center', justifyContent: 'center' }, heroChips: { flexDirection: 'row', gap: spacing[3], marginTop: 20 }, heroChip: { flexDirection: 'row', alignItems: 'center', gap: 6, borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.07)', paddingHorizontal: 14, paddingVertical: 6 }, chipDot: { width: 6, height: 6, borderRadius: radius.full },
  featureSection: { paddingHorizontal: 72, paddingTop: 80, paddingBottom: 72, maxWidth: 1440, width: '100%', alignSelf: 'center' }, featureHeadingRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end' }, featureHeading: { marginTop: spacing[2], fontSize: 38, lineHeight: 46 }, featureGrid: { flexDirection: 'row', gap: spacing[6], marginTop: 48 }, featureCard: { flex: 1, minHeight: 220, gap: spacing[3], borderWidth: 1, borderColor: '#ede8df', borderRadius: radius.lg, backgroundColor: color.surface.card, padding: 36 }, featureIcon: { width: 52, height: 52, borderRadius: radius.md, backgroundColor: '#fff0e8', alignItems: 'center', justifyContent: 'center' }, featureIconImage: { width: 26, height: 26 }, featureBody: {},
});
