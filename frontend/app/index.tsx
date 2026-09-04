import { Image, ImageBackground, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { type LanguageCode, useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { useI18n } from '@/i18n';

const logo = require('../assets/brand/gabolle-logo-figma.png');
const welcomeImage = require('../assets/images/welcome-busan.png');
type WelcomeLanguage = { code: Extract<LanguageCode, 'ko' | 'en'>; label: string; glyph: string };
const LANGUAGES: WelcomeLanguage[] = [{ code: 'ko', label: '한국어', glyph: '가' }, { code: 'en', label: 'English', glyph: 'A' }];
const FEATURES = [
  { icon: '✦', eyebrow: 'AI 맞춤 추천', title: '취향을 읽는 여행', body: '좋아하는 분위기와 동행 조건을 반영해 나만의 부산 코스를 만들어요.' },
  { icon: '⌁', eyebrow: '실시간 경로', title: '길 위에서도 유연하게', body: '날씨와 현재 위치에 맞춰 다음 장소와 이동 흐름을 한눈에 확인해요.' },
  { icon: '◎', eyebrow: '설명 가능한 추천', title: '이유를 아는 선택', body: '왜 이 장소가 어울리는지 확인하고 내 기준에 맞게 다시 선택해요.' },
] as const;

export default function Welcome() {
  const router = useRouter();
  const { width } = useLayout();
  const { language, mobility, setPreferences } = useOnboardingPreferences();
  const { tx } = useI18n();
  const isDesktop = width >= 1120;

  const chooseLanguage = (next: WelcomeLanguage['code']) => {
    setPreferences(next, mobility);
  };
  const start = (next: WelcomeLanguage['code'] = language === 'en' ? 'en' : 'ko') => {
    chooseLanguage(next);
    router.push({ pathname: '/age-gate', params: { language: next, mobility } });
  };

  if (!isDesktop) {
    return <ImageBackground source={welcomeImage} resizeMode="cover" style={styles.mobileScreen}>
      <StatusBar style="light" />
      <SafeAreaView edges={['top', 'bottom']} style={styles.mobileSafeArea}>
        <View style={styles.mobileBrand}><Pressable accessibilityRole="button" accessibilityLabel="GABOLLE 시작하기" accessibilityHint="연령 확인 화면으로 이동합니다" onPress={() => start()} style={({ pressed }) => [styles.logoLink, pressed && styles.pressed]}><Image source={logo} resizeMode="contain" style={styles.mobileLogo} /></Pressable><Text variant="display" weight="bold" color={color.brand.orange}>부산 가볼래?</Text></View>
        <View accessibilityRole="radiogroup" accessibilityLabel="시작할 언어 선택" style={styles.languageList}>{LANGUAGES.map((item) => <LanguageButton key={item.code} item={item} selected={language === item.code} onPress={() => start(item.code)} />)}</View>
      </SafeAreaView>
    </ImageBackground>;
  }

  return <ScrollView style={styles.webScreen} contentContainerStyle={styles.webContent}>
    <StatusBar style="dark" />
    <SafeAreaView edges={['top']} style={styles.webHeader}>
      <Pressable accessibilityRole="button" accessibilityLabel="GABOLLE 시작하기" accessibilityHint="연령 확인 화면으로 이동합니다" onPress={() => start()} style={({ pressed }) => [styles.logoLink, pressed && styles.pressed]}><Image source={logo} resizeMode="contain" style={styles.webLogo} /></Pressable>
      <View style={styles.webNav}><NavItem label={tx('홈', 'Home')} onPress={() => start()} /><NavItem label={tx('여행 만들기', 'Plan a trip')} onPress={() => start()} /><NavItem label={tx('내 여행', 'My trips')} onPress={() => router.push('/sign-in')} /><NavItem label={tx('여행지 둘러보기', 'Explore')} onPress={() => start()} /></View>
      <View style={styles.accountActions}>
        <Pressable accessibilityRole="button" accessibilityLabel={`언어를 ${language === 'ko' ? 'English' : '한국어'}로 변경`} onPress={() => chooseLanguage(language === 'ko' ? 'en' : 'ko')} style={styles.localeButton}><Text variant="caption" weight="bold">{language.toUpperCase()}</Text></Pressable>
        <Pressable accessibilityRole="button" onPress={() => router.push('/sign-in')} style={styles.loginButton}><Text variant="caption" weight="bold">{tx('로그인', 'Sign in')}</Text></Pressable>
        <Pressable accessibilityRole="button" onPress={() => start()} style={styles.signupButton}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('회원가입', 'Sign up')}</Text></Pressable>
      </View>
    </SafeAreaView>
    <View style={styles.heroSection}>
      <View style={styles.heroCopy}><View style={styles.heroInner}>
        <View style={styles.heroBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>AI TRAVEL PLANNER · BUSAN</Text></View>
        <Text variant="hero" weight="bold" color={color.text.onAction} style={styles.heroTitle}>{tx(`부산의 모든 여행,\n가볼래?`, `Every side of Busan,\nyours to explore.`)}</Text>
        <Text variant="body" color="rgba(255,255,255,0.78)" style={styles.heroDescription}>{tx('취향과 이동 조건을 반영해 당신만의 부산 여행을 만들어요.', 'Build a Busan trip around your taste and mobility needs.')}</Text>
        <View style={styles.heroActions}><Pressable accessibilityRole="button" onPress={() => start()} style={styles.primaryCta}><Text variant="body" weight="bold" color={color.text.onAction}>{tx('여행 계획 시작하기', 'Start planning')}</Text></Pressable><Pressable accessibilityRole="button" onPress={() => start()} style={styles.secondaryCta}><Text variant="body" weight="bold" color={color.text.onAction}>{tx('여행지 둘러보기', 'Explore Busan')}</Text></Pressable></View>
        <View style={styles.heroChips}><HeroChip dot="#64d68a" label="맞춤 일정" /><HeroChip dot="#5ba5ff" label="지금 갈 곳" /><HeroChip dot="#ff976f" label="설명 가능한 추천" /></View>
      </View></View>
      <ImageBackground source={welcomeImage} resizeMode="cover" style={styles.heroVisual}><View style={styles.heroVisualShade} /></ImageBackground>
    </View>
    <View style={styles.featureSection}>
      <View style={styles.featureHeadingRow}><View><Text variant="eyebrow" weight="bold">— EXCLUSIVE FEATURES</Text><Text variant="display" weight="bold" style={styles.featureHeading}>똑똑하고 아름답게{`\n`}설계되는 맞춤형 여정</Text></View><Pressable accessibilityRole="button" onPress={() => start()} style={styles.allFeaturesButton}><Text variant="caption" weight="bold" color={color.text.onAction}>모든 기능 보기 →</Text></Pressable></View>
      <View style={styles.featureGrid}>{FEATURES.map((feature) => <View key={feature.title} style={styles.featureCard}><View style={styles.featureIcon}><Text variant="title" weight="bold" color={color.brand.orange}>{feature.icon}</Text></View><Text variant="eyebrow" weight="bold">{feature.eyebrow}</Text><Text variant="title" weight="bold">{feature.title}</Text><Text variant="body">{feature.body}</Text></View>)}</View>
    </View>
  </ScrollView>;
}

function LanguageButton({ item, selected, onPress }: { item: WelcomeLanguage; selected: boolean; onPress: () => void }) {
  return <Pressable accessibilityRole="radio" accessibilityState={{ selected }} accessibilityLabel={`${item.label}로 시작하기`} onPress={onPress} style={({ pressed }) => [styles.languageButton, selected && styles.languageButtonSelected, pressed && styles.pressed]}><View style={styles.languageLeft}><View style={styles.languageGlyph}><Text variant="caption" weight="bold">{item.glyph}</Text></View><Text variant="title" weight="bold" color={color.text.onAction}>{item.label}</Text></View><Text variant="title" weight="medium" color={color.text.onAction}>→</Text></Pressable>;
}
function NavItem({ label, onPress }: { label: string; onPress: () => void }) { return <Pressable accessibilityRole="link" onPress={onPress} style={styles.navItem}><Text variant="caption" weight="medium">{label}</Text></Pressable>; }
function HeroChip({ dot, label }: { dot: string; label: string }) { return <View style={styles.heroChip}><View style={[styles.chipDot, { backgroundColor: dot }]} /><Text variant="caption" color="rgba(255,255,255,0.78)">{label}</Text></View>; }

const styles = StyleSheet.create({
  pressed: { opacity: 0.78 }, logoLink: { borderRadius: radius.sm }, mobileScreen: { flex: 1, backgroundColor: color.brand.navy },
  mobileSafeArea: { flex: 1, justifyContent: 'space-between', paddingHorizontal: spacing[6], paddingTop: 172, paddingBottom: spacing[8] }, mobileBrand: { alignItems: 'center', gap: spacing[3] }, mobileLogo: { width: 280, height: 72 },
  languageList: { alignSelf: 'center', width: '100%', maxWidth: 328, gap: spacing[3] }, languageButton: { minHeight: 56, borderRadius: radius.lg, borderWidth: 1, borderColor: 'rgba(255,255,255,0.32)', backgroundColor: 'rgba(11,29,58,0.20)', paddingHorizontal: spacing[4], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, languageButtonSelected: { borderColor: color.text.onAction, backgroundColor: 'rgba(11,29,58,0.38)' }, languageLeft: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, languageGlyph: { width: 32, height: 32, borderRadius: radius.sm, backgroundColor: color.text.onAction, alignItems: 'center', justifyContent: 'center' },
  webScreen: { flex: 1, backgroundColor: color.brand.ivory }, webContent: { minHeight: '100%' }, webHeader: { minHeight: 76, paddingHorizontal: spacing[8], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: color.brand.ivory }, webLogo: { width: 116, height: 36 }, webNav: { flexDirection: 'row', alignItems: 'center', gap: spacing[8] }, navItem: { paddingVertical: spacing[3] }, accountActions: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  localeButton: { minWidth: 38, height: 38, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: '#f6efe6' }, loginButton: { minWidth: 76, minHeight: 38, borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4] }, signupButton: { minWidth: 82, minHeight: 38, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4], backgroundColor: color.brand.navy },
  heroSection: { minHeight: 510, flexDirection: 'row', backgroundColor: color.brand.navy }, heroCopy: { width: '46%', minWidth: 480, justifyContent: 'center', paddingHorizontal: spacing[8] }, heroInner: { width: '100%', maxWidth: 560, alignSelf: 'flex-end' }, heroBadge: { alignSelf: 'flex-start', borderWidth: 1, borderColor: 'rgba(255,255,255,0.26)', borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.10)', paddingHorizontal: spacing[3], paddingVertical: spacing[1] }, heroTitle: { marginTop: spacing[6], fontSize: 58, lineHeight: 64, letterSpacing: -2.2 }, heroDescription: { marginTop: spacing[6], fontSize: 17 }, heroActions: { flexDirection: 'row', gap: spacing[3], marginTop: spacing[6] }, primaryCta: { minHeight: 52, borderRadius: radius.full, backgroundColor: color.brand.orange, paddingHorizontal: spacing[6], alignItems: 'center', justifyContent: 'center' }, secondaryCta: { minHeight: 52, borderRadius: radius.full, borderWidth: 1, borderColor: 'rgba(255,255,255,0.28)', backgroundColor: 'rgba(255,255,255,0.10)', paddingHorizontal: spacing[6], alignItems: 'center', justifyContent: 'center' }, heroChips: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[6] }, heroChip: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.09)', paddingHorizontal: spacing[3], paddingVertical: spacing[2] }, chipDot: { width: 6, height: 6, borderRadius: radius.full }, heroVisual: { flex: 1, minWidth: 0, overflow: 'hidden' }, heroVisualShade: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, backgroundColor: 'rgba(10,29,58,0.08)' },
  featureSection: { paddingHorizontal: spacing[8], paddingTop: spacing[8], paddingBottom: 72, maxWidth: 1440, width: '100%', alignSelf: 'center' }, featureHeadingRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end' }, featureHeading: { marginTop: spacing[2], fontSize: 34, lineHeight: 42 }, allFeaturesButton: { minHeight: 44, borderRadius: radius.full, backgroundColor: color.brand.navy, justifyContent: 'center', paddingHorizontal: spacing[6] }, featureGrid: { flexDirection: 'row', gap: spacing[6], marginTop: spacing[8] }, featureCard: { flex: 1, minHeight: 210, gap: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.lg, backgroundColor: color.surface.card, padding: spacing[6] }, featureIcon: { width: 44, height: 44, borderRadius: radius.md, backgroundColor: '#fff0e8', alignItems: 'center', justifyContent: 'center' },
});
