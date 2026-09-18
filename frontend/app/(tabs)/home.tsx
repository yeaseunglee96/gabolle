// 폰 홈. 디자인 인계 `design_handoff_home_phone` 의 절충안(C).
import { useEffect, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Redirect, useRouter } from 'expo-router';

import { PlanStartBar } from '@/home/PlanStartBar';
import { ConditionsPromptModal, type ConditionsOutcome } from '@/plan/ConditionsPromptModal';
import { loadConditionsPrompt, shouldPromptBeforePlan, shouldPromptOnHome, type ConditionsPromptState } from '@/plan/conditionsPromptState';
import { usePlan } from '@/plan/PlanProvider';
import type { StartBarValue } from '@/home/startBarValue';
import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { PlaceRow, StoryRow } from '@/home/HomeBlocks';
import { useHomeData } from '@/home/useHomeData';
import { AssistantBackdrop, AssistantMenu } from '@/home/AssistantMenu';
import { useSavedPlaces } from '@/home/useSavedPlaces';
import { resolveHomeTripDestination } from '@/home/tripNavigation';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { useI18n } from '@/i18n';
import { markdownToPlain } from '@/social/markdown';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { relativeStoryTime } from '@/social/stories';

const bellIcon = require('../../assets/icons/home/bell.png');
const heartIcon = require('../../assets/icons/home/heart.png');

/**
 * 폰의 카드 한 변. 한 화면에 두 장이 들어오고 세 번째가 살짝 보이는 크기다 —
 * 다음 장이 안 보이면 옆으로 더 있다는 것을 모른다.
 */
const MOBILE_CARD = 160;

export default function Home() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { accessToken, user } = useAuth();
  const { update: updatePlan } = usePlan();
  // 여행 조건 모달. 로그인 후 홈 첫 진입에 한 번, 그리고
  // 「나중에」를 고른 사람에게는 「일정 물어보기」를 누를 때마다 다시 묻는다.
  const [promptState, setPromptState] = useState<ConditionsPromptState>('NEVER');
  const [conditions, setConditions] = useState<{ open: boolean; reprompt: boolean; pending: StartBarValue | null }>({ open: false, reprompt: false, pending: null });
  const { width } = useLayout();
  const desktop = isAtLeast(width, 'lg');
  const { hydrated, hasEnteredApp, markEnteredApp } = useOnboardingPreferences();
  const home = useHomeData(!desktop);
  // 하트는 데스크톱 홈과 같은 자리에서 온다 — 베껴 두면 한쪽만 고쳐진다.
  const saved = useSavedPlaces(accessToken, 'home-mobile');
  const [assistantOpen, setAssistantOpen] = useState(false);
  const [openingTrip, setOpeningTrip] = useState(false);

  useEffect(() => {
    if (hydrated && !hasEnteredApp) markEnteredApp();
  }, [hydrated, hasEnteredApp, markEnteredApp]);

  const openHomeTrip = async (tripId: string) => {
    if (openingTrip) return;
    setOpeningTrip(true);
    const destination = await resolveHomeTripDestination(tripId, accessToken);
    setOpeningTrip(false);
    router.push(destination as never);
  };

  // 홈에서 받은 출발지·날짜·인원을 초안에 넣고 조건 화면으로 보낸다.
  // 조건 화면은 이 셋을 다시 묻지 않는다 — 칩 줄로만 보여 준다.
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

  // 「나중에」를 고른 사람에게는 여기서 한 번 더 묻는다. 건너뛰어도 일정은 만들 수 있다
  // 막으면 조건을 안 적은 사람이 앱을 아예 못 쓴다.
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
    // 건너뛰든 저장하든 가려던 곳으로 간다. 조건을 안 적었다고 길을 막지 않는다.
    if (pending) applyBarAndGo(pending);
  };

  if (desktop) return <Redirect href="/" />;

  const signedIn = home.signedIn;
  const weather = home.weather;

  return (
    <View style={styles.shell}>
      <Screen scroll withTabBar style={styles.screenContent}>
        {/* ── 머리 ── */}
        <View style={styles.header}>
          <BrandLogoLink href="/home" imageStyle={styles.logo} />
          <View style={styles.headerRight}>
            {signedIn ? (
              <>
                {weather && (weather.maxTemperature !== null || weather.minTemperature !== null) ? (
                  <View style={styles.weatherChip}>
                    <Text variant="caption" weight="bold">
                      {weather.skyCondition === 'CLEAR' ? tx('맑음', 'Clear') : weather.skyCondition === 'CLOUDY' ? tx('흐림', 'Cloudy') : tx('구름 조금', 'Partly cloudy')}
                    </Text>
                    <Text variant="caption">
                      {weather.minTemperature !== null && weather.maxTemperature !== null
                        ? `${Math.round(weather.minTemperature)}° / ${Math.round(weather.maxTemperature)}°`
                        : `${Math.round((weather.maxTemperature ?? weather.minTemperature) as number)}°`}
                    </Text>
                  </View>
                ) : null}
                {/* 미읽음이 있는지 알려주는 조회가 없어 주황 점은 안 찍는다 — 늘 찍으면
                    읽을 것이 없는데도 있는 것처럼 보이고, 안 찍는 쪽이 거짓이 아니다.
                */}
                <Pressable accessibilityRole="button" accessibilityLabel={tx('알림 확인', 'Check notifications')} onPress={() => router.push('/notifications')} style={({ pressed }) => [styles.bell, pressed && styles.pressed]}>
                  <Image source={bellIcon} resizeMode="contain" style={styles.bellIcon} />
                </Pressable>
              </>
            ) : (
              <Pressable accessibilityRole="button" onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/home' } })} style={({ pressed }) => [styles.loginPill, pressed && styles.pressed]}>
                <Text weight="bold" color={color.brand.navy}>{tx('로그인', 'Sign in')}</Text>
              </Pressable>
            )}
          </View>
        </View>

        {/* ── 히어로 ── */}
        <View style={styles.hero}>
          <Text weight="bold" color={color.brand.navy} style={styles.heroTitle}>{tx('부산의 모든 여행,\n가볼래?', 'Every side of Busan,\nyours to explore.')}</Text>
          {/* 시안 p0 의 시작 바. 출발지·날짜·인원을 여기서 받아
              조건 화면으로 넘긴다. 여행지는 안 묻는다 — 부산 고정이다.
          */}
          <PlanStartBar wide={false} accessToken={accessToken} onSubmit={startPlanFromBar} />

 {/* 「현장 도구」 카드를 뺐다 (2026-09-18 지시). 화면(/field/translate)과
              챗봇의 진입점은 그대로 있다 — 이 카드만 안 그린다. */}
        </View>

        {/* 순서: 피드가 먼저, 로컬 탐색이 그다음이다 (2026-09-18 지시).
            시안 design_handoff_home_airbnb_rows — 제목을 display bold 로 키우고 「전체 →」
            글자 링크를 화살표 원으로 바꿨다. 칩 줄과 「부산 둘러보기」는 없앴다.
            데스크톱과 같은 줄 부품을 쓴다 — 두 화면이 어긋나면 나란히 놓고 봐야만 보인다. */}
        <StoryRow stories={home.stories} width={width} cardWidth={MOBILE_CARD} gutter={spacing[6]} arrows={false} />

        {home.facetRows.map((row) => (
          <PlaceRow
            key={row.facetKey}
            row={row}
            width={width}
            cardWidth={MOBILE_CARD}
            gutter={spacing[6]}
            arrows={false}
            likedIds={saved.likedIds}
            onToggleLike={saved.toggle}
          />
        ))}

        {/* ── 내 여행 (로그인만) ── */}
        {signedIn ? (
          <View style={styles.sectionPadded}>
            <Text variant="eyebrow" weight="bold">{tx('내 여행', 'My trip')}</Text>
            {!home.tripsLoaded ? <View style={[styles.tripCard, styles.tripSkeleton]} /> : home.trip ? (
              <Pressable accessibilityRole="button" accessibilityState={{ busy: openingTrip, disabled: openingTrip }} disabled={openingTrip} onPress={() => void openHomeTrip(home.trip!.tripId)} style={({ pressed }) => [styles.tripCard, pressed && styles.pressed]}>
                <Text variant="caption" weight="bold" color={color.state.success}>
                  {home.trip.status === 'IN_PROGRESS' ? tx('진행 중', 'In progress') : home.trip.status === 'READY' ? tx('준비 완료', 'Ready') : tx('예정', 'Upcoming')}
                </Text>
                {/* 여행에 제목이 없다 — 날짜를 제목 자리에 올린다. */}
                <Text variant="title" weight="bold">
                  {home.trip.startDate && home.trip.endDate
                    ? `${home.trip.startDate.slice(5).replace('-', '.')} ~ ${home.trip.endDate.slice(5).replace('-', '.')}`
                    : tx('날짜 미정', 'Dates TBD')}
                </Text>
                <Text color={color.text.body}>{tx(`${home.trip.dayCount}일 · ${home.trip.partySize}명`, `${home.trip.dayCount} days · ${home.trip.partySize} travelers`)}</Text>
                <Text weight="bold" color={color.brand.navy} style={styles.tripGo}>{openingTrip ? tx('일정 찾는 중…', 'Finding itinerary…') : tx('일정 보기 →', 'View itinerary →')}</Text>
              </Pressable>
            ) : (
              <View style={styles.tripEmpty}>
                <GabolleMascot state="idle" style={styles.tripMascot} />
                <View style={styles.tripEmptyCopy}>
                  <Text weight="bold">{tx('아직 만든 여행이 없어요', 'No trips yet')}</Text>
                  <Pressable accessibilityRole="button" onPress={() => router.push('/plan/basic')}>
                    <Text weight="bold" color={color.brand.navy}>{tx('첫 여행 만들기 →', 'Plan your first trip →')}</Text>
                  </Pressable>
                </View>
              </View>
            )}
          </View>
        ) : null}

        {/* 하트를 누른 결과를 한 줄로 알린다 — 서버에 못 보냈으면 그것도 사실대로. */}
        {saved.feedback ? (
          <View accessibilityLiveRegion="polite" style={styles.saveFeedback}>
            <Text variant="caption" weight="bold" color={color.text.onAction}>{saved.feedback}</Text>
          </View>
        ) : null}
        <ConditionsPromptModal visible={conditions.open} reprompt={conditions.reprompt} onClose={closeConditions} />
  </Screen>

      {/* 판이 먼저다 — 메뉴와 단추보다 아래에 깔려야 그 둘은 그대로 눌린다. */}
      <AssistantBackdrop open={assistantOpen} onClose={() => setAssistantOpen(false)} />
      <View style={styles.assistantAnchor}>
        {/* 폰은 부제 없이 232 폭. 마스코트 위로 뜬다. */}
        <AssistantMenu compact open={assistantOpen} onClose={() => setAssistantOpen(false)} />
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ expanded: assistantOpen }}
          accessibilityLabel={tx('가볼래 여행 도우미 열기', 'Open GABOLLE travel assistant')}
          onPress={() => setAssistantOpen((open) => !open)}
          style={({ pressed }) => [styles.assistantButton, pressed && styles.pressed]}
        >
          <GabolleMascot state="idle" style={styles.assistantMascot} />
        </Pressable>
      </View>

      <TabBar active="home" />
    </View>
  );
}

const CARD_WIDTH = 240;

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.brand.ivory },
  screenContent: { paddingHorizontal: 0, paddingTop: 0 },
  pressed: { opacity: 0.75 },

  header: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing[6], paddingTop: spacing[4] },
  logo: { width: 110, height: 26 },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  weatherChip: { flexDirection: 'row', alignItems: 'center', gap: spacing[1], height: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.soft },
  bell: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  bellIcon: { width: 20, height: 20 },
  loginPill: { minHeight: 36, justifyContent: 'center', paddingHorizontal: 14, borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field },

  hero: { gap: spacing[3], paddingHorizontal: spacing[6], paddingTop: spacing[6] },
  heroBadge: { alignSelf: 'flex-start', paddingHorizontal: spacing[3], paddingVertical: 6, borderRadius: radius.full, backgroundColor: color.surface.tint },
  heroTitle: { fontSize: 36, lineHeight: 42, letterSpacing: -0.3 },
  primaryCta: { minHeight: 52, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.orange, marginTop: spacing[1] },

  section: { gap: spacing[3], paddingTop: spacing[8] },
  sectionPadded: { gap: spacing[3], paddingTop: spacing[8], paddingHorizontal: spacing[6] },
  sectionHead: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[6] },
  sectionSub: { marginTop: -spacing[1], paddingHorizontal: spacing[6] },
  sectionNote: { paddingHorizontal: spacing[6] },
  rail: { gap: spacing[3], paddingHorizontal: spacing[6] },

  storyCard: { width: CARD_WIDTH, borderRadius: radius.lg, overflow: 'hidden', borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  storySkeleton: { height: 260, backgroundColor: color.surface.soft },
  storyImage: { width: '100%', aspectRatio: 4 / 3, backgroundColor: color.surface.soft },
  // 사진이 없을 때 — 회색 빈 칸 대신 tint 에 본문을 크게. 시안 「자주 틀리는 것」 4번.
  // 빈 회색은 「사진을 못 불러왔다」로 읽힌다. feed.tsx 의 coverEmpty 와 같은 규칙이다.
  storyCoverEmpty: { alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: color.surface.tint },
  storyCoverEmptyText: { textAlign: 'center' },
  storyBody: { gap: spacing[1], paddingHorizontal: spacing[4], paddingTop: spacing[3], paddingBottom: spacing[4] },

  chip: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.soft },

  placeGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3] },
  placeCard: { width: '47%', gap: spacing[1] },
  placeThumbWrap: { position: 'relative' },
  heartButton: { position: 'absolute', top: 0, right: 0, width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  heartBackdrop: { width: 28, height: 28, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full },
  heartBackdropOn: { backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: 0.15, shadowRadius: 4, shadowOffset: { width: 0, height: 1 }, elevation: 2 },
  heartIcon: { width: 16, height: 16 },
  heartOn: { tintColor: color.brand.orange },
  heartOff: { tintColor: color.text.muted },

  tripCard: { gap: spacing[1], padding: spacing[4], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  tripSkeleton: { height: 140 },
  tripGo: { marginTop: spacing[1] },
  tripEmpty: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  tripMascot: { width: 56, height: 56 },
  tripEmptyCopy: { flex: 1, gap: spacing[1] },

  saveFeedback: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], marginTop: spacing[6], marginHorizontal: spacing[6], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.brand.navy },

  // 메뉴가 이 상자 위에 뜬다. 절대 위치를 단추가 아니라 감싸는 상자가 가진다.
  assistantAnchor: { position: 'absolute', right: spacing[6], bottom: 100, zIndex: 20 },
  assistantButton: { width: 68, height: 68, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full },
  assistantMascot: { width: 60, height: 60 },
});
