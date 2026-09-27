import { useEffect, useState } from 'react';
import { AccessibilityInfo, Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useVideoPlayer, VideoView } from 'expo-video';
import Svg, { Circle, Path } from 'react-native-svg';
import { Redirect, useIsFocused, useLocalSearchParams, useRouter } from 'expo-router';
import { PlanStartBar } from '@/home/PlanStartBar';
import { startBarEditSection, startBarEndDate, startBarFromDraft } from '@/home/startBarValue';
import { ConditionsPromptModal, type ConditionsOutcome } from '@/plan/ConditionsPromptModal';
import { loadConditionsPrompt, shouldPromptBeforePlan, shouldPromptOnHome, type ConditionsPromptState } from '@/plan/conditionsPromptState';
import { usePlan } from '@/plan/PlanProvider';
import type { StartBarValue } from '@/home/startBarValue';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Text } from '@/components/Text';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { MyTripCard, PlaceRow, StoryRow } from '@/home/HomeBlocks';
import { useHomeData } from '@/home/useHomeData';
import { AssistantBackdrop, AssistantMenu, assistantSubtitle } from '@/home/AssistantMenu';
import { useSavedPlaces } from '@/home/useSavedPlaces';
import { color, desktopGutter, radius, spacing } from '@/design/tokens';
import { LANGUAGE_OPTIONS } from '@/i18n/languages';
import { FLAG_IMAGES, WelcomeLanguageSheet } from '@/onboarding/WelcomeLanguageSheet';
import { useLayout } from '@/layout/useLayout';
import { type LanguageCode, useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { useI18n } from '@/i18n';
import { useAuth } from '@/auth/AuthProvider';
import { txf } from '@/i18n/format';

const nightLogo = require('../assets/brand/gabolle-logo-night.png');
const welcomeImage = require('../assets/images/welcome-busan.png');
// 첫 화면의 배경 영상 — 바다를 끼고 달리는 부산 전차(assets/video/README.md 에 출처). 소리 없이
// 돈다. 사진(welcomeImage)이 그 밑에 그대로 있어서 영상이 못 뜨거나 동작 줄이기가 켜져
// 있으면 사진만 보인다 — 시안 4 의 00a.
const welcomeVideo = require('../assets/video/busan-tram-portrait.mp4');
// 국기 그림과 언어 목록은 WelcomeLanguageSheet 가 가진다 — 시트와 카드가 같은 그림을 쓴다.
// 「특별한 기능」 카드 셋(AI 일정 만들기 · 실시간 경로 안내 · 함께 여행 설계)은 뺐다
// 로그인해도 안 바뀌는 소개였고, 그 자리에 실제 데이터인 장소와 내 여행이
// 들어왔다. 되살릴 일이 있으면 git 이력에 그대로 있다.

// 예전엔 부산 시각(Asia/Seoul)으로 낮·밤을 갈라 로고 밝기를 골랐다. 이제 배경이 영상이고
// 그 위에 어둠막을 깔아 글자가 읽히게 하므로 로고는 언제나 흰색이다.

export default function Welcome() {
  const router = useRouter();
  const focused = useIsFocused();
  const { width, desktop } = useLayout();
  const { language, mobility, setPreferences, hydrated, hasEnteredApp } = useOnboardingPreferences();
  const { tx } = useI18n();
  const { user, ready, accessToken } = useAuth();
  const { draft: planDraft, update: updatePlan } = usePlan();
  // 🔴 열 문항 화면이 「날짜 정하기」로 보낸 사람은 고칠 칸을 열어 둔 채로 받는다 — S15P21E201-1350.
  const editSection = startBarEditSection(useLocalSearchParams().edit);
  const [promptState, setPromptState] = useState<ConditionsPromptState>('NEVER');
  const [conditions, setConditions] = useState<{ open: boolean; reprompt: boolean; pending: StartBarValue | null }>({ open: false, reprompt: false, pending: null });
  // 데스크톱 판인가 — 폭만이 아니라 폴드 펼침 가로까지, 판정은 useLayout 한 곳(S15P21E201-1563).
  const isDesktop = desktop;
  // 홈이 쓰는 값(기록·갈래·날씨·장소·내 여행)을 한곳에서 읽는다. 폰 분기에서도 훅 순서가
  // 바뀌면 안 되므로 조건 없이 위에서 부른다 — 폰에서는 그린 것이 없어 값만 놀고 끝난다.
  const home = useHomeData(isDesktop);
  // 하트는 화면이 한 번 쥐고 줄 둘에 내려 준다 — 줄마다 따로 쥐면 같은 장소가
  // 두 줄에 있을 때 한쪽만 켜진다.
  const saved = useSavedPlaces(accessToken);
  const [assistantOpen, setAssistantOpen] = useState(false);
  const onToggleLike = (placeId: string) => {
    // 로그인 안 한 사람도 기기에 저장된다 — 로그인으로 밀어내지 않는다.
    saved.toggle(placeId);
  };

  const chooseLanguage = (next: LanguageCode) => {
    setPreferences(next, mobility);
  };
  const startOnboarding = (next: LanguageCode = language) => {
    chooseLanguage(next);
    router.push({ pathname: isDesktop ? '/age-gate' : '/app-intro', params: { language: next, mobility } });
  };
  const [languageSheetOpen, setLanguageSheetOpen] = useState(false);
  // 동작 줄이기(OS 접근성 설정)가 켜져 있으면 영상을 안 돌린다 — 움직이는 배경이 곧 그 설정이 막으려는 것이다.
  const [reduceMotion, setReduceMotion] = useState(false);
  useEffect(() => {
    let alive = true;
    void AccessibilityInfo.isReduceMotionEnabled().then((enabled) => { if (alive) setReduceMotion(enabled); }).catch(() => {});
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    return () => { alive = false; sub.remove(); };
  }, []);
  const player = useVideoPlayer(welcomeVideo, (instance) => {
    instance.loop = true;
    instance.muted = true;
  });
  const showVideo = !isDesktop && !reduceMotion;
  useEffect(() => {
    if (!showVideo) { player.pause(); return; }
    // 웹에서는 만든 직후의 play() 가 조용히 무시된다(아직 읽는 중). 준비되면 그때 다시 튼다.
    player.play();
    const sub = player.addListener('statusChange', ({ status }) => { if (status === 'readyToPlay') player.play(); });
    return () => sub.remove();
  }, [player, showVideo]);
  const startPlanning = () => router.push('/plan');

  useEffect(() => {
    let alive = true;
    void loadConditionsPrompt(user?.userId ?? null, accessToken).then(({ state }) => {
      if (!alive) return;
      setPromptState(state);
      if (shouldPromptOnHome(state)) setConditions({ open: true, reprompt: false, pending: null });
    });
    return () => { alive = false; };
  }, [accessToken, user?.userId]);

  const applyBarAndGo = (value: StartBarValue) => {
    updatePlan({
      origin: value.origin,
      originLat: value.originLat,
      originLng: value.originLng,
      lodging: value.lodging,
      lodgingLat: value.lodgingLat,
      lodgingLng: value.lodgingLng,
      lodgingPlace: value.lodgingPlace,
      // 화면용 영어 이름 — 여행 만들기 화면의 칩이 쓴다(S15P21E201-1795). 서버로 안 간다.
      originEnglish: value.originEnglish ?? null,
      lodgingEnglish: value.lodgingEnglish ?? null,
      startDate: value.startDate,
      endDate: startBarEndDate(value),
      adults: value.adults,
      children: value.children,
      travelers: value.adults + value.children,
    });
    // 🔴 /plan 의 「출발지 수정」으로 왔으면(edit 매개변수) 그 /plan 이 아래에 쌓여 있다 — push 하면 /plan 이 둘이 되어
    //    뒤로 가면 출발지 없던 옛 화면이 한 번 더 나온다(실기 빌드 28, S15P21E201-1439). 왔던 화면으로 돌아간다.
    if (editSection && router.canGoBack()) router.back();
    else router.push('/plan');
  };

  const startPlanFromBar = (value: StartBarValue) => {
    if (shouldPromptBeforePlan(promptState)) { setConditions({ open: true, reprompt: true, pending: value }); return; }
    applyBarAndGo(value);
  };

  const closeConditions = (outcome: ConditionsOutcome) => {
    const pending = conditions.pending;
    setConditions({ open: false, reprompt: false, pending: null });
    if (outcome !== 'DISMISSED') {
      const next = outcome === 'SAVED' ? 'SAVED' : outcome === 'NEVER' ? 'NEVER' : 'LATER';
      // 값을 실제로 적는 것은 모달이다. 여기서 상태만 따로
      // 적던 것이 사고였다 — 「물어봤다」는 기록만 남고 답은 아무 데도 안 남았다.
      setPromptState(next);
    }
    if (pending) applyBarAndGo(pending);
  };

  if (!isDesktop) {
    if (!hydrated || !ready) return <View style={styles.mobileScreen} />;
    if (user || hasEnteredApp) return <Redirect href="/home" />;
    const current = LANGUAGE_OPTIONS.find((item) => item.code === language) ?? LANGUAGE_OPTIONS[0];
    return <View style={styles.mobileScreen}>
      {/* 사진이 먼저, 영상이 그 위 — 영상이 늦게 뜨거나 못 뜨면 사진이 그대로 보인다. */}
      <Image source={welcomeImage} resizeMode="cover" style={styles.mobileBackgroundImage} />
      {showVideo ? <VideoView player={player} contentFit="cover" nativeControls={false} allowsPictureInPicture={false} style={styles.mobileBackgroundImage} /> : null}
      {/* 어둠막 — 위는 옅게, 아래로 갈수록 짙게. 흰 글자와 단추가 어떤 장면에서도 읽히게 한다. */}
      <LinearGradient pointerEvents="none" colors={['rgba(25,25,25,0.35)', 'rgba(25,25,25,0.15)', 'rgba(25,25,25,0.40)', 'rgba(25,25,25,0.92)']} locations={[0, 0.3, 0.6, 1]} style={styles.mobileBackgroundImage} />
      {/* 🔴 흰 상태바는 영상 위에서만이다. 온보딩은 router.push 로 넘어가서 이 화면이 스택에 남는다 —
          그대로 두면 밝은 온보딩·나이 확인·권한 화면 내내 시계와 배터리가 흰 글자로 안 보였다(S15P21E201-1747). */}
      {focused ? <StatusBar style="light" /> : null}
      <SafeAreaView edges={['top', 'bottom', 'left', 'right']} style={styles.mobileSafeArea}>
        <ScrollView style={styles.mobileSafeArea} contentContainerStyle={styles.mobileContent}>
        <View style={styles.mobileBrand}>
          <Image source={nightLogo} resizeMode="contain" accessibilityLabel="GABOLLE" style={styles.mobileLogo} />
          <Text variant="body" weight="medium" color={color.text.onAction} style={styles.mobileTagline}>{tx('현지인이 다니는 부산으로,\n취향과 이동 조건에 맞춰 떠나요.', 'Busan the way locals know it —\nplanned around your taste and how you get around.')}</Text>
        </View>
        {/* 시안 4 의 00a — 언어 카드 하나 · 흰 시작하기 · 로그인 한 줄. 예전 국기 다섯 줄은
            시트(WelcomeLanguageSheet)로 들어갔다. 고르는 것과 시작하는 것을 나눈 이유는 거기 적었다. */}
        <View style={styles.mobileActions}>
          <Pressable testID="lang-picker" accessibilityRole="button" accessibilityLabel={txf(tx, '언어 선택: %s', 'Language: %s', current.endonym)} accessibilityHint={tx('눌러서 다른 언어를 고릅니다', 'Opens the language list')} onPress={() => setLanguageSheetOpen(true)} style={({ pressed }) => [styles.languageCard, pressed && styles.pressed]}>
            <Image source={FLAG_IMAGES[current.code]} resizeMode="contain" style={styles.languageCardFlag} accessibilityIgnoresInvertColors />
            <View style={styles.languageCardCopy}>
              <Text variant="body" weight="bold" color={color.text.onAction}>{current.endonym}</Text>
              <Text variant="caption" color="rgba(255,255,255,0.75)">{tx('눌러서 바꾸기', 'Tap to change')}</Text>
            </View>
            <GlobeIcon />
          </Pressable>
          <Pressable testID="start-gabolle" accessibilityRole="button" accessibilityLabel={tx('GABOLLE 시작하기', 'Start GABOLLE')} accessibilityHint={tx('서비스 소개 화면으로 이동합니다', 'Goes to the service introduction screen')} onPress={() => startOnboarding()} style={({ pressed }) => [styles.startButton, pressed && styles.pressed]}>
            <Text variant="title" weight="bold" color={color.text.heading}>{tx('시작하기', 'Get started')}</Text>
            <Svg width={18} height={18} viewBox="0 0 24 24" fill="none"><Path d="M9 5l7 7-7 7" stroke={color.text.heading} strokeWidth={2.6} strokeLinecap="round" strokeLinejoin="round" /></Svg>
          </Pressable>
          <Pressable accessibilityRole="link" onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/home' } })} style={({ pressed }) => [styles.signInLink, pressed && styles.pressed]}>
            <Text variant="util" weight="bold" color="rgba(255,255,255,0.9)">{tx('이미 계정이 있어요 · 로그인', 'Already have an account · Sign in')}</Text>
          </Pressable>
        </View>
        </ScrollView>
      </SafeAreaView>
      <WelcomeLanguageSheet visible={languageSheetOpen} language={language} onSelect={chooseLanguage} onClose={() => setLanguageSheetOpen(false)} onStart={() => { setLanguageSheetOpen(false); startOnboarding(language); }} />
    </View>;
  }

  return <View style={styles.webShell}><ScrollView style={styles.webScreen} contentContainerStyle={styles.webContent}>
    <StatusBar style="dark" />
    {/* 상단 바는 이 파일에 없다. 앱 뼈대(app/_layout.tsx)가 모든 화면에 한 번만 붙인다
         전에는 이 파일 안에 내비가 하나 더 박혀 있어서 내비가 두 벌이었고
        그래서 랜딩만 옛 모양(72px · 가운데 정렬 · 작은 글자 · 활성 표식 없음)으로 남아
        확정안 2c 가 안 먹었다(-970). 그 뒤 한 벌로 합쳤지만 붙이는 자리는 여전히 화면마다
        손으로 정했고, 그래서 이번엔 바가 아예 없는 화면이 50개 넘게 생겼다(-994).
    */}
    {/* 배경 사진을 되살린다. 시안 1a 를 옮기면서 오른쪽 영상을 기록 카드로
        바꿨는데, 그때 배경까지 통째로 걷어내서 네이비 단색 판이 됐다. 사진은 남기되 글자가
        읽히도록 네이비를 덮는다 — 덮개가 없으면 흰 글자가 하늘·물빛 위에서 안 읽힌다.
    */}
    {/* 시안 p0 (PlanFlow.dc.html) — 사진 히어로가 없다. 아이보리 바탕에 제목과
        시작 바를 가운데 세우고, 그 아래로 내용 줄을 전폭으로 쌓는다.
    */}
    <View style={styles.headerSection}>
      <View style={styles.headerInner}>
 {/* 색을 반드시 적는다. `hero` 변형의 기본색은 흰색이라(사진 위에 얹던 시절의
            기본값) 아이보리 바탕에서는 글자가 통째로 안 보인다 — 2026-09-18 화면을 띄워
            보고 찾았다. 타입도 시험도 안 잡는다. 프로필 카드에서도 같은 일이 났었다. */}
        <Text variant="hero" weight="bold" color={color.text.heading} style={styles.headerTitle}>{tx('부산의 모든 여행, 가볼래?', 'Every side of Busan, yours to explore.')}</Text>
        <Text variant="body" color={color.text.muted} style={styles.headerSubtitle}>{tx('언제, 누구와, 어떻게 다닐지만 알려주세요. 일정은 가볼래가 짜요.', 'Just tell us when, with whom and how you travel — we build the itinerary.')}</Text>
        <View style={styles.startBar}>
          <PlanStartBar wide accessToken={accessToken} onSubmit={startPlanFromBar} initialSection={editSection} initialValue={editSection ? startBarFromDraft(planDraft) : undefined} />
        </View>
        {/* 🔴 이 자리에 있던 것 둘이 지금은 없다. 왜 없는지를 남긴다 —
            안 적어 두면 다음 사람이 「빠뜨렸나」 하고 다시 넣는다.

            · 「처음 오셨나요? 사용법 보기」 — 2026-09-18 사용자 지시로 뺐다. 시안 p0 에는
              날씨 줄 오른쪽에 그 링크가 있지만, 첫 화면에서 안내부터 권하지 않기로 했다.
              안내 화면(/help)은 그대로 있고 마이페이지에서 들어간다.
            · 날씨 줄 — 2026-09-21 상단 바 오른쪽으로 옮겼다(S15P21E201-1369).
              여기 가로줄이 시작 바와 첫 기록 줄 사이를 갈라놓고 있었다. TopNav 를 본다.
        */}
      </View>
    </View>

    {/* 시안 design_handoff_home_airbnb_rows — 줄을 전폭으로 쌓는다. 본문 폭 1200 을 버리고
        좌우는 상단 바와 같은 40 이다. 카드는 화면 가장자리까지 흘러간다.
        로그인 안 해도 남의 기록이 보인다 (진미리).
    */}
    <StoryRow stories={home.stories} width={width} />

    {home.facetRows.map((row) => (
      <PlaceRow key={row.facetKey} row={row} width={width} likedIds={saved.likedIds} onToggleLike={onToggleLike} />
    ))}

    {/* 하트를 누른 결과는 말로 한 번 알린다 — 서버에 못 보냈으면 그것도 사실대로. */}
    {saved.feedback ? (
      <View accessibilityLiveRegion="polite" style={styles.savedFeedback}>
        <Text variant="caption" color={color.text.body}>{saved.feedback}</Text>
      </View>
    ) : null}
    {saved.consentPrompt}

    {/* 로그인 안 했으면 내 여행 자리를 통째로 접는다 — 빈 여백 띠만 남으면 고장으로 보인다. */}
    {user ? (
      <View style={styles.lowerSection}>
        <MyTripCard trip={home.trip} signedIn loaded={home.tripsLoaded} hasTrips={home.hasTrips} />
      </View>
    ) : null}
  </ScrollView>
    {/* 판이 먼저다 — 메뉴와 단추보다 아래에 깔려야 그 둘은 그대로 눌린다. */}
    <AssistantBackdrop open={assistantOpen} onClose={() => setAssistantOpen(false)} />
    <View style={styles.webAssistantAnchor}>
      <AssistantMenu open={assistantOpen} onClose={() => setAssistantOpen(false)} />
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded: assistantOpen }}
        accessibilityLabel={tx('가볼래 AI 여행 도우미 열기', 'Open Gabolle AI travel assistant')}
        onPress={() => setAssistantOpen((open) => !open)}
        style={({ pressed }) => [styles.webAssistantButton, pressed && styles.pressed]}
      >
        {/* 부제는 메뉴에 실제로 있는 것을 적는다. 전에는 「일정 · 통역 · 여행 도움」이라고
            적어 두고 챗봇 한 곳으로만 갔다 — 셋을 약속하고 하나만 줬다. */}
        <View style={styles.webAssistantLabel}><Text variant="body" weight="bold">{tx('AI에게 물어보기', 'Ask AI')}</Text><Text variant="caption" color={color.text.muted}>{assistantSubtitle(tx)}</Text></View>
        {/* 메뉴가 열린 동안 >.< — 폰과 같은 규칙(S15P21E201-1430). 흔들리지 않는다. */}
        <GabolleMascot state={assistantOpen ? 'thinking' : 'idle'} still style={styles.webAssistantMascot} />
      </Pressable>
    </View>
    <ConditionsPromptModal visible={conditions.open} reprompt={conditions.reprompt} onClose={closeConditions} />
  </View>;
}

/** 지구본 — 언어 카드 오른쪽. 「여기서 언어를 바꾼다」는 표식이다. */
function GlobeIcon() {
  return (
    <Svg width={20} height={20} viewBox="0 0 24 24" fill="none">
      <Circle cx={12} cy={12} r={8.5} stroke={color.text.onAction} strokeWidth={1.9} />
      <Path d="M3.5 12h17M12 3.5c3 3 3 14 0 17M12 3.5c-3 3-3 14 0 17" stroke={color.text.onAction} strokeWidth={1.9} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}
function NavItem({ label, onPress }: { label: string; onPress: () => void }) { return <Pressable accessibilityRole="link" onPress={onPress} style={styles.navItem}><Text variant="caption" weight="medium">{label}</Text></Pressable>; }

const styles = StyleSheet.create({
  webShell: { flex: 1, backgroundColor: color.canvas },
  pressed: { opacity: 0.78 }, logoLink: { borderRadius: radius.sm }, mobileScreen: { flex: 1, width: '100%', height: '100%', overflow: 'hidden', backgroundColor: color.brand.navy }, mobileBackgroundImage: { ...StyleSheet.absoluteFill, width: '100%', height: '100%' },
  mobileSafeArea: { flex: 1 },
  mobileContent: { flexGrow: 1, justifyContent: 'space-between', gap: spacing[8], paddingHorizontal: spacing[6], paddingTop: spacing[8], paddingBottom: spacing[6] },
  // 로고는 위에서 약 1/4 지점 — 시안 4 의 00a. 아래 남는 자리는 영상이 보이는 자리다.
  mobileBrand: { alignItems: 'center', gap: spacing[4], paddingTop: 120, paddingBottom: spacing[8] },
  mobileLogo: { width: 300, height: 55 },
  mobileTagline: { textAlign: 'center', lineHeight: 26 },
  mobileActions: { width: '100%', maxWidth: 480, alignSelf: 'center', gap: spacing[3] },
  // 언어 카드 — 반투명 흰 판. 국기는 3:2 그대로(잘리면 다른 나라로 오인된다).
  languageCard: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 54, paddingHorizontal: spacing[4], borderRadius: radius.md, borderWidth: 1, borderColor: 'rgba(255,255,255,0.35)', backgroundColor: 'rgba(255,255,255,0.14)' },
  languageCardFlag: { width: 30, height: 20, borderRadius: 4 },
  languageCardCopy: { flex: 1, gap: 1 },
  // 시작하기 — 흰 판에 검은 글자. 영상 위에서 가장 잘 읽히는 조합이고, 빨강 채움은 여기 안 쓴다.
  startButton: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: spacing[2], minHeight: 56, borderRadius: radius.md, backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: 0.25, shadowRadius: 14, shadowOffset: { width: 0, height: 10 }, elevation: 6 },
  signInLink: { alignSelf: 'center', minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[3] },
  webScreen: { flex: 1, backgroundColor: color.canvas }, webContent: { minHeight: '100%' }, webHeader: { minHeight: 72, paddingHorizontal: 72, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: color.brand.ivory, borderBottomWidth: 1, borderBottomColor: color.surface.border }, webLogo: { width: 113, height: 28 }, webNav: { flexDirection: 'row', alignItems: 'center', gap: 44 }, navItem: { paddingVertical: spacing[3] }, accountActions: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  localeButton: { minWidth: 38, height: 38, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.soft }, loginButton: { minWidth: 76, minHeight: 38, borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4] }, signupButton: { minWidth: 82, minHeight: 38, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4], backgroundColor: color.brand.navy },
  // 남는 공간이 없을 때는 아무 일도 안 한다 — 그래서 로그아웃 화면은 지금과 똑같다.
  // 「특별한 기능」 카드 셋이 있던 자리다. 로그인해도 내용이 안 바뀌는 소개였고, 그 자리에
  // 장소와 내 여행이 들어왔다.
  // 줄 배치로 바뀌면서 maxWidth 1200 과 좌우 80 을 버렸다 — 줄은 화면 폭을 다 쓰고
  // 좌우는 상단 바와 같은 40(desktopGutter)이다.
  lowerSection: { gap: desktopGutter, paddingHorizontal: desktopGutter, paddingTop: desktopGutter, paddingBottom: 64, width: '100%' },
  savedFeedback: { paddingHorizontal: desktopGutter, paddingTop: spacing[3] },
  // 시안 p0 의 머리 — 아이보리 바탕에 가운데 정렬. 본문 폭은 1200 이다.
  // 시안: 히어로는 그대로 두고 아래에 경계선만 더한다 — 줄 배치가 시작되는 자리를
  // 한 줄로 알린다. 없으면 히어로와 첫 줄이 같은 덩어리로 읽힌다.
  headerSection: { width: '100%', backgroundColor: color.brand.ivory, paddingTop: 56, paddingBottom: 32, paddingHorizontal: desktopGutter, alignItems: 'center', borderBottomWidth: 1, borderBottomColor: color.surface.border },
  headerInner: { width: '100%', maxWidth: 1200, alignItems: 'center' },
  headerTitle: { textAlign: 'center' },
  startBar: { width: '100%', maxWidth: 900, alignItems: 'center' },
  headerSubtitle: { marginTop: spacing[2], marginBottom: spacing[6], textAlign: 'center' },
  // 메뉴가 이 상자를 기준으로 위에 뜬다(bottom: '100%'). 그래서 절대 위치를 단추가 아니라
  // 감싸는 상자가 가진다 — 단추가 가지면 메뉴가 붙을 기준이 없다.
  webAssistantAnchor: { position: 'absolute', right: spacing[8], bottom: spacing[8], zIndex: 20 },
  webAssistantButton: { minWidth: 64, minHeight: 64, flexDirection: 'row', alignItems: 'center' },
  webAssistantLabel: { minWidth: 210, gap: spacing[1], marginRight: -spacing[3], paddingLeft: spacing[6], paddingRight: spacing[8], paddingVertical: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: 0.16, shadowRadius: 12, shadowOffset: { width: 0, height: 5 }, elevation: 5 },
  webAssistantMascot: { width: 84, height: 84 },
});
