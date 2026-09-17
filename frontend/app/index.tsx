import { Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { Redirect, useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Text } from '@/components/Text';
import { GettingStartedGuide } from '@/components/GettingStartedGuide';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { HeroStories, MyTripCard, PlacePicks, WeatherLine } from '@/home/HomeBlocks';
import { useHomeData } from '@/home/useHomeData';
import { color, radius, spacing } from '@/design/tokens';
import { LANGUAGE_OPTIONS, needsTranslationNotice, type LanguageOption } from '@/i18n/languages';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { type LanguageCode, useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { useI18n } from '@/i18n';
import { useAuth } from '@/auth/AuthProvider';

const logo = require('../assets/brand/gabolle-logo-hd.png');
const nightLogo = require('../assets/brand/gabolle-logo-night.png');
const welcomeImage = require('../assets/images/welcome-busan.png');
const webHeroImage = require('../assets/home/web-hero.png');
// 🔴 국기는 유니코드 그림문자(🇰🇷)가 아니라 실제 이미지를 쓴다 — 윈도우 브라우저는
// 국가 그림문자를 정책적으로 지원하지 않아 KR·US 같은 두 글자로 떨어진다(S15P21E201-1109).
// 폰트로는 못 고치는 문제라 flagcdn.com 국기 그림을 내려받아 assets/flags 에 넣었다.
const FLAG_IMAGES: Record<LanguageCode, ReturnType<typeof require>> = {
  ko: require('../assets/flags/kr.png'),
  en: require('../assets/flags/us.png'),
  ja: require('../assets/flags/jp.png'),
  'zh-Hans': require('../assets/flags/cn.png'),
  'zh-Hant': require('../assets/flags/tw.png'),
};
// 🔴 언어 목록은 src/i18n/languages.ts 한 곳에 있다 (S15P21E201-1109). 여기 다시 적으면
//    언어를 늘릴 때 한쪽만 늘어난다.
// 「특별한 기능」 카드 셋(AI 일정 만들기 · 실시간 경로 안내 · 함께 여행 설계)은 뺐다
// (S15P21E201-970). 로그인해도 안 바뀌는 소개였고, 그 자리에 실제 데이터인 장소와 내 여행이
// 들어왔다. 되살릴 일이 있으면 git 이력에 그대로 있다.

// 부산 시간대별로 로고 밝기를 고르느라 기기의 로컬 타임존이 아니라 Asia/Seoul 시각을 쓴다 —
// 그렇지 않으면 해외에서 접속한 여행자에게는 시간대가 어긋난 로고가 뜬다.
function seoulHour(date = new Date()) {
  return Number(new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Seoul', hourCycle: 'h23', hour: 'numeric' }).format(date));
}

function shouldUseLightWelcomeLogo() {
  const hour = seoulHour();
  return hour < 7 || hour >= 17;
}

export default function Welcome() {
  const router = useRouter();
  const { width } = useLayout();
  const { language, mobility, setPreferences, hydrated, hasEnteredApp } = useOnboardingPreferences();
  const { tx } = useI18n();
  const { user, ready } = useAuth();
  const isDesktop = isAtLeast(width, 'lg');
  // 홈이 쓰는 값(기록·갈래·날씨·장소·내 여행)을 한곳에서 읽는다. 폰 분기에서도 훅 순서가
  // 바뀌면 안 되므로 조건 없이 위에서 부른다 — 폰에서는 그린 것이 없어 값만 놀고 끝난다.
  const home = useHomeData(isDesktop);

  const chooseLanguage = (next: LanguageCode) => {
    setPreferences(next, mobility);
  };
  // 아무것도 안 고르고 로고를 눌렀을 때는 **지금 언어 그대로** 간다 — 예전에는 한국어·영어
  // 둘뿐이라 'en' 이 아니면 'ko' 로 접었는데, 이제 일본어를 고른 사람이 한국어로 떨어진다.
  const startOnboarding = (next: LanguageCode = language) => {
    chooseLanguage(next);
    router.push({ pathname: isDesktop ? '/age-gate' : '/app-intro', params: { language: next, mobility } });
  };
  const startPlanning = () => router.push('/plan/basic');

  if (!isDesktop) {
    if (!hydrated || !ready) return <View style={styles.mobileScreen} />;
    if (user || hasEnteredApp) return <Redirect href="/home" />;
    const lightLogo = shouldUseLightWelcomeLogo();
    // S15P21E201-925: 실기기(저사양·데이터 절약 모드 아님에도)에서 배경 영상 화질이
    // 너무 나쁘다는 실사용 리포트로 영상을 뺐다 — 처음에 로고와 같이 쓰던 정적 사진으로 되돌린다.
    return <View style={styles.mobileScreen}>
      <Image source={welcomeImage} resizeMode="cover" style={styles.mobileBackgroundImage} />
      <StatusBar style="light" />
      <SafeAreaView edges={['top', 'bottom', 'left', 'right']} style={styles.mobileSafeArea}>
        <ScrollView style={styles.mobileSafeArea} contentContainerStyle={styles.mobileContent}>
        <View style={styles.mobileBrand}><Pressable testID="start-gabolle" accessibilityRole="button" accessibilityLabel={tx('GABOLLE 시작하기', 'Start GABOLLE')} accessibilityHint={tx('서비스 소개 화면으로 이동합니다', 'Goes to the service introduction screen')} onPress={() => startOnboarding()} style={({ pressed }) => [styles.logoLink, pressed && styles.pressed]}><Image source={lightLogo ? nightLogo : logo} resizeMode="contain" style={styles.mobileLogo} /></Pressable><Text variant="display" weight="bold" color={color.brand.orange}>{tx('부산 가볼래?', 'Shall we go to Busan?')}</Text></View>
        {/* 사용자 요청(2026-09-17): 예전 흰 시트 목록이 화면을 너무 많이 차지했고, 이 화면은
            로그인 화면이 아닌데 "비회원으로 둘러보기" 가 있는 것도 어색했다 — 그 선택지는
            로그인 화면(sign-in.tsx)에 이미 있다. 국기 동그라미 다섯 줄로 압축하고, 배경 사진이
            보이도록 남는 자리를 그대로 둔다. 누르면 바로 시작하는 것은 그대로다. */}
        <View style={styles.mobileActions}>
          <View accessibilityRole="radiogroup" accessibilityLabel={tx('시작할 언어 선택', 'Select a language to start')} style={styles.languageFlagRow}>
            {LANGUAGE_OPTIONS.map((item) => <LanguageFlag key={item.code} item={item} selected={language === item.code} onPress={() => startOnboarding(item.code)} />)}
          </View>
          {/* 🔴 번역이 아직 없다는 사실을 숨기지 않는다. 다 된 척하면 고른 사람이 영어를 보고
              "왜 안 바뀌지" 로 읽는다. 미리 말하면 그건 선택이 된다. */}
          {needsTranslationNotice(language) ? <Text variant="caption" color="rgba(255,255,255,0.86)" style={styles.languageNotice}>Menus are in English for now. Place names and guides come in your language.</Text> : null}
        </View>
        </ScrollView>
      </SafeAreaView>
    </View>;
  }

  return <View style={styles.webShell}><ScrollView style={styles.webScreen} contentContainerStyle={styles.webContent}>
    <StatusBar style="dark" />
    {/* 🔴 상단 바는 이 파일에 없다. 앱 뼈대(app/_layout.tsx)가 모든 화면에 한 번만 붙인다
        (S15P21E201-994). 전에는 이 파일 안에 내비가 하나 더 박혀 있어서 **내비가 두 벌**이었고,
        그래서 랜딩만 옛 모양(72px · 가운데 정렬 · 작은 글자 · 활성 표식 없음)으로 남아
        확정안 2c 가 안 먹었다(-970). 그 뒤 한 벌로 합쳤지만 붙이는 자리는 여전히 화면마다
        손으로 정했고, 그래서 이번엔 **바가 아예 없는 화면이 50개 넘게** 생겼다(-994).

        S15P21E201-900: "부산 축제" 내비 항목은 최초 배포에서 뺐다 — 가진 축제 기간 자료가
        전부 만료돼 화면을 열어도 보여줄 게 없다. /festivals 라우트는 그대로 있다.
        S15P21E201-906: "피드"도 출시 전 제품 결정으로 뺐다가 2026-09-14 에 다시 켰다 —
        뒤집은 결정이라는 사실이 보이도록 이 문장을 남긴다. "부산 축제"는 그대로 빠져 있다. */}
    {/* 배경 사진을 되살린다 (S15P21E201-970). 시안 1a 를 옮기면서 오른쪽 영상을 기록 카드로
        바꿨는데, 그때 배경까지 통째로 걷어내서 네이비 단색 판이 됐다. 사진은 남기되 글자가
        읽히도록 네이비를 덮는다 — 덮개가 없으면 흰 글자가 하늘·물빛 위에서 안 읽힌다. */}
    <View style={styles.heroSection}>
      {/* 🔴 ImageBackground 를 쓰지 않는다. RN 웹에서는 안쪽 사진이 **제 원본 크기(1536×672)**
          그대로 왼쪽 위에 앉아서, 칸이 그보다 넓으면 오른쪽이 빈 네이비로 남았다(배포본에서
          95px, 2026-09-15 실측). 절대배치로 네 변을 칸에 묶고 cover 로 채운다 — 이러면 폭이
          얼마든 사진이 칸을 덮고 넘치는 쪽만 잘린다. */}
      <Image source={webHeroImage} resizeMode="cover" style={styles.heroPhoto} />
      <View style={styles.heroBackdrop} />
      <View style={styles.heroCopy}><View style={styles.heroInner}>
        <View style={styles.heroBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>AI TRAVEL PLANNER · BUSAN</Text></View>
        <Text variant="hero" weight="bold" color={color.text.onAction} style={styles.heroTitle}>{tx(`부산의 모든 여행,\n가볼래?`, `Every side of Busan,\nyours to explore.`)}</Text>
        <Text variant="body" color="rgba(255,255,255,0.78)" style={styles.heroDescription}>{tx('취향과 이동 조건을 반영해 당신만의 부산 여행을 만들어요.', 'Build a Busan trip around your taste and mobility needs.')}</Text>
        {/* S15P21E201-900: "부산 축제 보기" CTA는 최초 배포에서 뺐다 — 축제 기간 자료가
            전부 만료돼 눌러도 빈 화면이 나온다. /festivals 라우트는 그대로 있다. */}
        <View style={styles.heroActions}>
          <Pressable accessibilityRole="button" accessibilityHint={tx('로그인 없이 여행 조건 입력을 시작합니다.', 'Start entering trip details without signing in.')} onPress={startPlanning} style={styles.primaryCta}><Text variant="body" weight="bold" color={color.text.onAction}>{tx('여행 계획 시작하기', 'Start planning')}</Text></Pressable>
          {/* 🔴 피드는 아직 로그인해야 볼 수 있다 — 서버가 익명 출입증을 받아 주지 않는다
              (운영에서 /api/v1/stories 가 401). 로그인 안 했으면 로그인으로 보낸다. */}
          <Pressable accessibilityRole="link" onPress={() => router.push('/feed')} style={styles.ghostCta}><Text variant="body" weight="bold" color={color.text.onAction}>{tx('피드 둘러보기 →', 'Browse the feed →')}</Text></Pressable>
        </View>
        {/* S15P21E201-900: "지금 갈 곳" 칩도 CTA와 같은 이유로 뺐다 — 지금 갈 곳 진입점을
            숨겨 놓고 이 문구만 남기면 약속하는 것과 실제가 어긋난다(MR !708 리뷰 코멘트). */}
        <View style={styles.heroChips}><HeroChip dot={color.state.success} label={tx('맞춤 일정', 'Tailored itinerary')} /><HeroChip dot={color.state.rating} label={tx('설명 가능한 추천', 'Explainable picks')} /></View>
        <WeatherLine forecast={home.weather} />
        <GettingStartedGuide />
      </View></View>
      {/* 히어로 오른쪽은 영상이었다. 로그인해도 본문이 그대로라 「내 것이 하나도 없다」는
          문제가 여기서 시작됐다 — 그 자리에 지금 올라온 기록을 넣는다 (S15P21E201-970).
          🔴 비로그인에게도 보여준다. 익명 출입증으로 공개 글은 그대로 온다. */}
      <HeroStories stories={home.stories} chips={home.chips} signedIn={home.signedIn} />
    </View>
    {/* 두 블록이 다 비면(장소를 못 받았고 로그인도 안 했으면) 구역을 통째로 접는다 —
        안 그러면 아무것도 없는 여백 띠만 남아 화면이 고장난 것처럼 보인다. */}
    {home.places.length > 0 || user ? (
      <View style={styles.lowerSection}>
        <PlacePicks places={home.places} />
        <MyTripCard trip={home.trip} signedIn={Boolean(user)} loaded={home.tripsLoaded} />
      </View>
    ) : null}
  </ScrollView>
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={tx('가볼래 AI 여행 도우미 열기', 'Open Gabolle AI travel assistant')}
      onPress={() => router.push('/chat')}
      style={({ pressed }) => [styles.webAssistantButton, pressed && styles.pressed]}
    >
      <View style={styles.webAssistantLabel}><Text variant="body" weight="bold">{tx('AI에게 물어보기', 'Ask AI')}</Text><Text variant="caption" color={color.text.muted}>{tx('일정 · 통역 · 여행 도움', 'Plans · phrases · travel help')}</Text></View>
      <GabolleMascot state="idle" style={styles.webAssistantMascot} />
    </Pressable>
  </View>;
}

/**
 * 국기 동그라미 하나 = 언어 하나 (S15P21E201-1109, 2026-09-17 재도입).
 *
 * 🔴 국기 그림 하나만 두지 않는다 — 실제 국기 이미지를 써도 56px 짜리 작은 동그라미에서는
 * 국기끼리 순간적으로 헷갈릴 수 있다(특히 처음 보는 사용자에게). 그래서 동그라미 아래에
 * 그 언어로 쓴 이름(endonym)을 작게 같이 적는다 — 그 한 줄이 정체를 한 번 더 말해준다.
 *
 * 누르면 바로 시작한다. 고르는 것과 시작하는 것을 나누면 탭이 한 번 더 늘어난다.
 */
function LanguageFlag({ item, selected, onPress }: { item: LanguageOption; selected: boolean; onPress: () => void }) {
  // 🔴 언어 버튼은 code 로 찾는다 (S15P21E201-1193). 라벨(한국어·日本語…)로 찾으면
  //    고르려는 언어가 곧 찾을 이름이라 자동화가 닭과 달걀에 빠진다.
  return <Pressable testID={`lang-${item.code}`} accessibilityRole="radio" accessibilityState={{ selected }} accessibilityLabel={item.englishName === item.endonym ? item.endonym : `${item.endonym} · ${item.englishName}`} onPress={onPress} style={styles.languageFlagItem}>
    {({ pressed }) => <>
      <View style={[styles.languageFlagCircle, selected && styles.languageFlagCircleSelected, pressed && styles.pressed]}>
        <Image source={FLAG_IMAGES[item.code]} resizeMode="contain" style={styles.languageFlagImage} />
        {selected ? <View style={styles.languageFlagCheck}><Text style={styles.languageFlagCheckMark}>✓</Text></View> : null}
      </View>
      <Text variant="caption" weight="bold" numberOfLines={1} color="#ffffff" style={styles.languageFlagLabel}>{item.endonym}</Text>
    </>}
  </Pressable>;
}
function NavItem({ label, onPress }: { label: string; onPress: () => void }) { return <Pressable accessibilityRole="link" onPress={onPress} style={styles.navItem}><Text variant="caption" weight="medium">{label}</Text></Pressable>; }
function HeroChip({ dot, label }: { dot: string; label: string }) { return <View style={styles.heroChip}><View style={[styles.chipDot, { backgroundColor: dot }]} /><Text variant="caption" color="rgba(255,255,255,0.78)">{label}</Text></View>; }

const styles = StyleSheet.create({
  webShell: { flex: 1, backgroundColor: color.brand.ivory },
  pressed: { opacity: 0.78 }, logoLink: { borderRadius: radius.sm }, mobileScreen: { flex: 1, width: '100%', height: '100%', overflow: 'hidden', backgroundColor: color.brand.navy }, mobileBackgroundImage: { ...StyleSheet.absoluteFill, width: '100%', height: '100%' },
  mobileSafeArea: { flex: 1 },
  mobileContent: { flexGrow: 1, justifyContent: 'space-between', gap: spacing[8], paddingHorizontal: spacing[6], paddingTop: spacing[8], paddingBottom: spacing[6] },
  mobileBrand: { alignItems: 'center', gap: spacing[3], paddingVertical: spacing[8] },
  mobileLogo: { width: 280, height: 70 },
  mobileActions: { width: '100%', maxWidth: 480, alignSelf: 'center', gap: spacing[2] },
  // 국기 동그라미 다섯 줄 (2026-09-17 재도입). 흰 시트 목록보다 세로 자리를 훨씬 덜 쓴다 —
  // 배경 사진(부산 바다)이 보이는 자리를 넉넉히 남기는 것이 이번 요청의 핵심이다.
  languageFlagRow: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing[2] },
  languageFlagItem: { flex: 1, alignItems: 'center', gap: spacing[1] },
  // 뱃지는 항상 불투명한 흰 배경이다 — 사진 밝기가 자리마다 달라도 국기가 늘 또렷하다.
  // 🔴 처음엔 동그라미 + resizeMode「cover」로 만들었더니 국기(3:2 비율, 미국만 1.9:1)의
  // 좌우가 잘려 나갔다(실측 — 특히 미국 성조기 별밭이 잘림). 국기는 어느 나라든 전체 모양이
  // 곧 그 나라를 가리키는 표식이라 일부가 잘리면 다른 나라 국기로 오인될 수 있다. 그래서
  // 동그라미를 접고 국기 비율에 맞춘 둥근 네모 + resizeMode「contain」으로 바꿔
  // **어떤 국기도 잘리지 않게** 한다.
  languageFlagCircle: { position: 'relative', width: 56, height: 40, borderRadius: radius.sm, overflow: 'hidden', alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card, borderWidth: 2, borderColor: 'transparent' },
  languageFlagCircleSelected: { borderColor: color.brand.orange },
  languageFlagImage: { width: '86%', height: '86%' },
  languageFlagCheck: { position: 'absolute', right: -2, bottom: -2, width: 20, height: 20, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.orange, borderWidth: 2, borderColor: color.brand.navy },
  languageFlagCheckMark: { fontSize: 11, fontWeight: '700', color: color.text.onAction },
  languageFlagLabel: { textAlign: 'center' },
  languageNotice: { marginTop: spacing[1], textAlign: 'center', lineHeight: 18 },
  webScreen: { flex: 1, backgroundColor: color.brand.ivory }, webContent: { minHeight: '100%' }, webHeader: { minHeight: 72, paddingHorizontal: 72, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: color.brand.ivory, borderBottomWidth: 1, borderBottomColor: '#e8e3da' }, webLogo: { width: 113, height: 28 }, webNav: { flexDirection: 'row', alignItems: 'center', gap: 44 }, navItem: { paddingVertical: spacing[3] }, accountActions: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  localeButton: { minWidth: 38, height: 38, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: '#f6efe6' }, loginButton: { minWidth: 76, minHeight: 38, borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4] }, signupButton: { minWidth: 82, minHeight: 38, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4], backgroundColor: color.brand.navy },
  // 히어로는 더 이상 영상 배경이 아니라 **좌우 두 칸**이다 (S15P21E201-970) — 왼쪽은 소개와
  // 날씨, 오른쪽은 지금 올라온 기록. 높이를 고정하지 않는다: 오른쪽 카드 셋이 내용에 따라
  // 늘어나는데 500 으로 묶어 두면 카드가 잘린다.
  // 🔴 overflow 를 반드시 잘라야 한다. ImageBackground 안의 사진은 칸 너비에 맞춰 늘어나는데
  //    세로는 제 비율을 지켜서, 좌우가 넓으면 섹션보다 세로로 길어진다. 안 자르면 그 초과분이
  //    섹션 밖으로 흘러 **아래 구역을 덮는다** — 배포본에서 실제로 「부산 둘러보기」와
  //    「내 여행」 글자가 사진에 가렸다(섹션 546 · 사진 672 · 126 초과, 2026-09-15 실측).
  //    덮개는 섹션 크기라 그 초과분에는 닿지도 않아 사진이 날것으로 보였다.
  heroSection: { flexDirection: 'row', alignItems: 'stretch', overflow: 'hidden', backgroundColor: color.brand.navy },
  // 사진 위 덮개. 0.78 이면 흰 글자가 읽히면서 광안대교 윤곽이 남는다 — 1.0 이면 사진이
  // 있으나 마나이고, 0.5 근처면 제목이 하늘빛에 묻힌다.
  heroPhoto: { ...StyleSheet.absoluteFill, width: '100%', height: '100%' },
  heroBackdrop: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(11,29,58,0.78)' },
  // 🔴 가운데로 세운다 (S15P21E201-1099). 히어로는 두 칸을 나란히 놓고 `alignItems: 'stretch'`
  // 라서 **높은 쪽이 전체 높이를 정한다.** 로그아웃일 때는 이 왼쪽 칸이 높이를 정해 딱 맞는데,
  // 로그인하면 오른쪽에 기록 카드(실측 359px)가 들어와 오른쪽이 높이를 정한다. 그러면 왼쪽에
  // 남는 47px 이 **전부 아래에 쌓여** 글 밑에 빈 띠가 생긴다(배포본 실측 2026-09-16:
  // 아래 여백 64 → 111). 가운데로 세우면 그 남는 만큼이 위아래로 나뉜다(95 / 87).
  //
  // 남는 공간이 없을 때는 아무 일도 안 한다 — 그래서 로그아웃 화면은 지금과 똑같다.
  heroCopy: { width: 560, paddingLeft: 80, paddingRight: 40, paddingTop: 72, paddingBottom: 64, justifyContent: 'center' }, heroInner: { width: '100%' }, heroBadge: { alignSelf: 'flex-start', borderWidth: 1, borderColor: 'rgba(255,255,255,0.20)', borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.10)', paddingHorizontal: spacing[4], paddingVertical: 6 }, heroTitle: { marginTop: 28, fontSize: 68, lineHeight: 71, letterSpacing: -2.4 }, heroDescription: { marginTop: 28, fontSize: 18, lineHeight: 29 }, heroActions: { flexDirection: 'row', gap: spacing[4], marginTop: 28 }, primaryCta: { minHeight: 52, borderRadius: radius.full, backgroundColor: color.brand.orange, paddingHorizontal: 40, alignItems: 'center', justifyContent: 'center' }, ghostCta: { minHeight: 52, borderRadius: radius.full, borderWidth: 1, borderColor: 'rgba(255,255,255,0.3)', paddingHorizontal: spacing[6], alignItems: 'center', justifyContent: 'center' }, heroChips: { flexDirection: 'row', gap: spacing[3], marginTop: 20 }, heroChip: { flexDirection: 'row', alignItems: 'center', gap: 6, borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.07)', paddingHorizontal: 14, paddingVertical: 6 }, chipDot: { width: 6, height: 6, borderRadius: radius.full },
  // 「특별한 기능」 카드 셋이 있던 자리다. 로그인해도 내용이 안 바뀌는 소개였고, 그 자리에
  // 장소와 내 여행이 들어왔다 (S15P21E201-970).
  lowerSection: { flexDirection: 'row', alignItems: 'flex-start', gap: 40, paddingHorizontal: 80, paddingTop: 48, paddingBottom: 64, maxWidth: 1440, width: '100%', alignSelf: 'center' },
  webAssistantButton: { position: 'absolute', right: spacing[8], bottom: spacing[8], minWidth: 64, minHeight: 64, flexDirection: 'row', alignItems: 'center', zIndex: 20 },
  webAssistantLabel: { minWidth: 210, gap: spacing[1], marginRight: -spacing[3], paddingLeft: spacing[6], paddingRight: spacing[8], paddingVertical: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: 0.16, shadowRadius: 12, shadowOffset: { width: 0, height: 5 }, elevation: 5 },
  webAssistantMascot: { width: 84, height: 84 },
});
