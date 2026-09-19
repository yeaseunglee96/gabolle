import { useEffect, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { Redirect, useRouter } from 'expo-router';
import { PlanStartBar } from '@/home/PlanStartBar';
import { ConditionsPromptModal, type ConditionsOutcome } from '@/plan/ConditionsPromptModal';
import { loadConditionsPrompt, shouldPromptBeforePlan, shouldPromptOnHome, type ConditionsPromptState } from '@/plan/conditionsPromptState';
import { usePlan } from '@/plan/PlanProvider';
import type { StartBarValue } from '@/home/startBarValue';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Text } from '@/components/Text';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { MyTripCard, PlaceRow, StoryRow, WeatherLine } from '@/home/HomeBlocks';
import { useHomeData } from '@/home/useHomeData';
import { AssistantBackdrop, AssistantMenu, assistantSubtitle } from '@/home/AssistantMenu';
import { useSavedPlaces } from '@/home/useSavedPlaces';
import { color, desktopGutter, radius, spacing } from '@/design/tokens';
import { LANGUAGE_OPTIONS, needsTranslationNotice, type LanguageOption } from '@/i18n/languages';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { type LanguageCode, useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { useI18n } from '@/i18n';
import { useAuth } from '@/auth/AuthProvider';

const logo = require('../assets/brand/gabolle-logo-hd.png');
const nightLogo = require('../assets/brand/gabolle-logo-night.png');
const welcomeImage = require('../assets/images/welcome-busan.png');
// 국기는 유니코드 그림문자(🇰🇷)가 아니라 실제 이미지를 쓴다 — 윈도우 브라우저는
// 국가 그림문자를 정책적으로 지원하지 않아 KR·US 같은 두 글자로 떨어진다.
// 폰트로는 못 고치는 문제라 flagcdn.com 국기 그림을 내려받아 assets/flags 에 넣었다.
const FLAG_IMAGES: Record<LanguageCode, ReturnType<typeof require>> = {
  ko: require('../assets/flags/kr.png'),
  en: require('../assets/flags/us.png'),
  ja: require('../assets/flags/jp.png'),
  'zh-Hans': require('../assets/flags/cn.png'),
  'zh-Hant': require('../assets/flags/tw.png'),
};
// 언어 목록은 src/i18n/languages.ts 한 곳에 있다. 여기 다시 적으면
// 언어를 늘릴 때 한쪽만 늘어난다.
// 「특별한 기능」 카드 셋(AI 일정 만들기 · 실시간 경로 안내 · 함께 여행 설계)은 뺐다
// 로그인해도 안 바뀌는 소개였고, 그 자리에 실제 데이터인 장소와 내 여행이
// 들어왔다. 되살릴 일이 있으면 git 이력에 그대로 있다.

// 부산 시간대별로 로고 밝기를 고르느라 기기의 로컬 타임존이 아니라 Asia/Seoul 시각을 쓴다
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
  const { user, ready, accessToken } = useAuth();
  const { update: updatePlan } = usePlan();
  const [promptState, setPromptState] = useState<ConditionsPromptState>('NEVER');
  const [conditions, setConditions] = useState<{ open: boolean; reprompt: boolean; pending: StartBarValue | null }>({ open: false, reprompt: false, pending: null });
  const isDesktop = isAtLeast(width, 'lg');
  // 홈이 쓰는 값(기록·갈래·날씨·장소·내 여행)을 한곳에서 읽는다. 폰 분기에서도 훅 순서가
  // 바뀌면 안 되므로 조건 없이 위에서 부른다 — 폰에서는 그린 것이 없어 값만 놀고 끝난다.
  const home = useHomeData(isDesktop);
  // 하트는 화면이 한 번 쥐고 줄 둘에 내려 준다 — 줄마다 따로 쥐면 같은 장소가
  // 두 줄에 있을 때 한쪽만 켜진다.
  const saved = useSavedPlaces(accessToken, 'home-desktop');
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
      startDate: value.startDate,
      endDate: value.endDate,
      adults: value.adults,
      children: value.children,
      travelers: value.adults + value.children,
    });
    router.push('/plan');
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
    const lightLogo = shouldUseLightWelcomeLogo();
    return <View style={styles.mobileScreen}>
      <Image source={welcomeImage} resizeMode="cover" style={styles.mobileBackgroundImage} />
      <StatusBar style="light" />
      <SafeAreaView edges={['top', 'bottom', 'left', 'right']} style={styles.mobileSafeArea}>
        <ScrollView style={styles.mobileSafeArea} contentContainerStyle={styles.mobileContent}>
        <View style={styles.mobileBrand}><Pressable testID="start-gabolle" accessibilityRole="button" accessibilityLabel={tx('GABOLLE 시작하기', 'Start GABOLLE')} accessibilityHint={tx('서비스 소개 화면으로 이동합니다', 'Goes to the service introduction screen')} onPress={() => startOnboarding()} style={({ pressed }) => [styles.logoLink, pressed && styles.pressed]}><Image source={lightLogo ? nightLogo : logo} resizeMode="contain" style={styles.mobileLogo} /></Pressable><Text variant="display" weight="bold" color={color.text.onAction}>{tx('부산 가볼래?', 'Shall we go to Busan?')}</Text></View>
        {/* 사용자 요청(2026-09-17): 예전 흰 시트 목록이 화면을 너무 많이 차지했고, 이 화면은
            로그인 화면이 아닌데 "비회원으로 둘러보기" 가 있는 것도 어색했다 — 그 선택지는
            로그인 화면(sign-in.tsx)에 이미 있다. 국기 동그라미 다섯 줄로 압축하고, 배경 사진이
            보이도록 남는 자리를 그대로 둔다. 누르면 바로 시작하는 것은 그대로다. */}
        <View style={styles.mobileActions}>
          <View accessibilityRole="radiogroup" accessibilityLabel={tx('시작할 언어 선택', 'Select a language to start')} style={styles.languageFlagRow}>
            {LANGUAGE_OPTIONS.map((item) => <LanguageFlag key={item.code} item={item} selected={language === item.code} onPress={() => startOnboarding(item.code)} />)}
          </View>
          {/* 번역이 아직 없다는 사실을 숨기지 않는다. 다 된 척하면 고른 사람이 영어를 보고
              "왜 안 바뀌지" 로 읽는다. 미리 말하면 그건 선택이 된다.
          */}
          {needsTranslationNotice(language) ? <Text variant="caption" color="rgba(255,255,255,0.86)" style={styles.languageNotice}>Menus are in English for now. Place names and guides come in your language.</Text> : null}
        </View>
        </ScrollView>
      </SafeAreaView>
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
          <PlanStartBar wide accessToken={accessToken} onSubmit={startPlanFromBar} />
        </View>
 {/* 「처음 오셨나요? 사용법 보기」는 뺐다 (2026-09-18 사용자 지시).
            시안 p0 에는 날씨 줄 오른쪽에 그 링크가 있지만, 첫 화면에서 안내부터 권하지 않기로 했다.
            안내 화면(/help)은 그대로 있고 마이페이지에서 들어간다. */}
        <View style={styles.headerMeta}>
          <WeatherLine forecast={home.weather} />
        </View>
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

    {/* 로그인 안 했으면 내 여행 자리를 통째로 접는다 — 빈 여백 띠만 남으면 고장으로 보인다. */}
    {user ? (
      <View style={styles.lowerSection}>
        <MyTripCard trip={home.trip} signedIn loaded={home.tripsLoaded} />
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
        <GabolleMascot state="idle" style={styles.webAssistantMascot} />
      </Pressable>
    </View>
    <ConditionsPromptModal visible={conditions.open} reprompt={conditions.reprompt} onClose={closeConditions} />
  </View>;
}

/**
 * 국기 그림 하나만 두지 않는다 — 실제 국기 이미지를 써도 56px 짜리 작은 동그라미에서는
 * 국기끼리 순간적으로 헷갈릴 수 있다(특히 처음 보는 사용자에게). 그래서 동그라미 아래에
 * 그 언어로 쓴 이름(endonym)을 작게 같이 적는다 — 그 한 줄이 정체를 한 번 더 말해준다.
 */
function LanguageFlag({ item, selected, onPress }: { item: LanguageOption; selected: boolean; onPress: () => void }) {
  // 언어 버튼은 code 로 찾는다. 라벨(한국어·日本語…)로 찾으면
  // 고르려는 언어가 곧 찾을 이름이라 자동화가 닭과 달걀에 빠진다.
  return <Pressable testID={`lang-${item.code}`} accessibilityRole="radio" accessibilityState={{ selected }} accessibilityLabel={item.englishName === item.endonym ? item.endonym : `${item.endonym} · ${item.englishName}`} onPress={onPress} style={styles.languageFlagItem}>
    {({ pressed }) => <>
      <View style={[styles.languageFlagCircle, selected && styles.languageFlagCircleSelected, pressed && styles.pressed]}>
        <Image source={FLAG_IMAGES[item.code]} resizeMode="contain" style={styles.languageFlagImage} />
        {selected ? <View style={styles.languageFlagCheck}><Text style={styles.languageFlagCheckMark}>✓</Text></View> : null}
      </View>
      <Text variant="caption" weight="bold" numberOfLines={1} color={color.text.onAction} style={styles.languageFlagLabel}>{item.endonym}</Text>
    </>}
  </Pressable>;
}
function NavItem({ label, onPress }: { label: string; onPress: () => void }) { return <Pressable accessibilityRole="link" onPress={onPress} style={styles.navItem}><Text variant="caption" weight="medium">{label}</Text></Pressable>; }

const styles = StyleSheet.create({
  webShell: { flex: 1, backgroundColor: color.brand.ivory },
  pressed: { opacity: 0.78 }, logoLink: { borderRadius: radius.sm }, mobileScreen: { flex: 1, width: '100%', height: '100%', overflow: 'hidden', backgroundColor: color.brand.navy }, mobileBackgroundImage: { ...StyleSheet.absoluteFill, width: '100%', height: '100%' },
  mobileSafeArea: { flex: 1 },
  mobileContent: { flexGrow: 1, justifyContent: 'space-between', gap: spacing[8], paddingHorizontal: spacing[6], paddingTop: spacing[8], paddingBottom: spacing[6] },
  mobileBrand: { alignItems: 'center', gap: spacing[3], paddingVertical: spacing[8] },
  mobileLogo: { width: 280, height: 70 },
  mobileActions: { width: '100%', maxWidth: 480, alignSelf: 'center', gap: spacing[2] },
  languageFlagRow: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing[2] },
  languageFlagItem: { flex: 1, alignItems: 'center', gap: spacing[1] },
  // 뱃지는 항상 불투명한 흰 배경이다 — 사진 밝기가 자리마다 달라도 국기가 늘 또렷하다.
  // 처음엔 동그라미 + resizeMode「cover」로 만들었더니 국기(3:2 비율, 미국만 1.9:1)의
  // 좌우가 잘려 나갔다(실측 — 특히 미국 성조기 별밭이 잘림). 국기는 어느 나라든 전체 모양이
  // 곧 그 나라를 가리키는 표식이라 일부가 잘리면 다른 나라 국기로 오인될 수 있다. 그래서
  // 동그라미를 접고 국기 비율에 맞춘 둥근 네모 + resizeMode「contain」으로 바꿔
  // 어떤 국기도 잘리지 않게 한다.
  languageFlagCircle: { position: 'relative', width: 56, height: 40, borderRadius: radius.sm, overflow: 'hidden', alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card, borderWidth: 2, borderColor: 'transparent' },
  languageFlagCircleSelected: { borderColor: color.action.secondary },
  languageFlagImage: { width: '86%', height: '86%' },
  languageFlagCheck: { position: 'absolute', right: -2, bottom: -2, width: 20, height: 20, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.secondary, borderWidth: 2, borderColor: color.brand.navy },
  languageFlagCheckMark: { fontSize: 11, fontWeight: '700', color: color.text.onAction },
  languageFlagLabel: { textAlign: 'center' },
  languageNotice: { marginTop: spacing[1], textAlign: 'center', lineHeight: 18 },
  webScreen: { flex: 1, backgroundColor: color.brand.ivory }, webContent: { minHeight: '100%' }, webHeader: { minHeight: 72, paddingHorizontal: 72, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: color.brand.ivory, borderBottomWidth: 1, borderBottomColor: color.surface.border }, webLogo: { width: 113, height: 28 }, webNav: { flexDirection: 'row', alignItems: 'center', gap: 44 }, navItem: { paddingVertical: spacing[3] }, accountActions: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
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
  headerMeta: { marginTop: spacing[6], gap: spacing[3], alignItems: 'center' },
  // 메뉴가 이 상자를 기준으로 위에 뜬다(bottom: '100%'). 그래서 절대 위치를 단추가 아니라
  // 감싸는 상자가 가진다 — 단추가 가지면 메뉴가 붙을 기준이 없다.
  webAssistantAnchor: { position: 'absolute', right: spacing[8], bottom: spacing[8], zIndex: 20 },
  webAssistantButton: { minWidth: 64, minHeight: 64, flexDirection: 'row', alignItems: 'center' },
  webAssistantLabel: { minWidth: 210, gap: spacing[1], marginRight: -spacing[3], paddingLeft: spacing[6], paddingRight: spacing[8], paddingVertical: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: 0.16, shadowRadius: 12, shadowOffset: { width: 0, height: 5 }, elevation: 5 },
  webAssistantMascot: { width: 84, height: 84 },
});
