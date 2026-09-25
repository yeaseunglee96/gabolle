// 폰 홈. 디자인 인계 `design_handoff_home_phone` 의 절충안(C).
import { useEffect, useRef, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Redirect, useLocalSearchParams, useRouter } from 'expo-router';

import { PlanStartBar } from '@/home/PlanStartBar';
import { ConditionsPromptModal, type ConditionsOutcome } from '@/plan/ConditionsPromptModal';
import { loadConditionsPrompt, shouldPromptBeforePlan, shouldPromptOnHome, type ConditionsPromptState } from '@/plan/conditionsPromptState';
import { usePlan } from '@/plan/PlanProvider';
import { EMPTY_START_BAR, startBarEditSection, startBarFromDraft, type StartBarValue } from '@/home/startBarValue';
import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { useTopNavShown } from '@/nav/TopNav';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Screen } from '@/components/Screen';
import { TAB_BAR_HEIGHT, TabBar, bottomDockPosition, tabBarBottomMargin } from '@/components/TabBar';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { PlaceRow, StoryRow } from '@/home/HomeBlocks';
import { useHomeData } from '@/home/useHomeData';
import { AssistantBackdrop, AssistantMenu } from '@/home/AssistantMenu';
import { dismissChecklist, loadChecklist, takeHomeCoach, type ChecklistState } from '@/onboarding/firstRun';
import { FirstTripChecklist } from '@/onboarding/FirstTripChecklist';
import { HomeCoach, type CoachHole } from '@/onboarding/HomeCoach';
import { useSavedPlaces } from '@/home/useSavedPlaces';
import { resolveHomeTripDestination } from '@/home/tripNavigation';
import { useLayout } from '@/layout/useLayout';
import { useI18n } from '@/i18n';
import { markdownToPlain } from '@/social/markdown';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { useHomeBellDot } from '@/notifications/useHomeBellDot';
import { relativeStoryTime } from '@/social/stories';
import { effectiveTripStatus, tripStatusLabel } from '@/trip/tripStatus';
import { tripDisplayTitle, type TripSummaryDto } from '@/trip/trips';
import { enCount, enPlural, txf } from '@/i18n/format';

const bellIcon = require('../../assets/icons/home/bell.png');
const heartIcon = require('../../assets/icons/home/heart.png');

/**
 * 폰의 카드 한 변. 한 화면에 두 장이 들어오고 세 번째가 살짝 보이는 크기다 —
 * 다음 장이 안 보이면 옆으로 더 있다는 것을 모른다.
 */
const MOBILE_CARD = 160;

/** 「09.25 ~ 09.26」 — 폰 홈 「내 여행」 카드의 날짜. 날짜를 모르면 「날짜 미정」. */
function homeTripDates(trip: TripSummaryDto, tx: (ko: string, en: string) => string): string {
  return trip.startDate && trip.endDate
    ? `${trip.startDate.slice(5).replace('-', '.')} ~ ${trip.endDate.slice(5).replace('-', '.')}`
    : tx('날짜 미정', 'Dates TBD');
}

/**
 * 「21° / 28°」 — 최저·최고가 다 있으면 둘, 하나뿐이면 그것만.
 *
 * 🔴 반올림한 «뒤에» 같은지 본다 — S15P21E201-1502. 기상청 단기예보는 남은 시간대가 짧으면
 *    최저와 최고를 같게 준다(밤에 부르면 자주 그렇다). 그대로 이으면 「22° / 22°」가 되고,
 *    실기에서 그것을 고장으로 읽었다. 21.6 과 22.4 처럼 원값이 달라도 화면에 같은 숫자가
 *    두 번 보이는 것은 마찬가지라, 비교는 반올림 뒤에 한다.
 */
export function weatherTemperatureText(weather: { minTemperature: number | null; maxTemperature: number | null }): string {
  const low = weather.minTemperature !== null ? Math.round(weather.minTemperature) : null;
  const high = weather.maxTemperature !== null ? Math.round(weather.maxTemperature) : null;
  if (low === null || high === null) return `${(high ?? low) as number}°`;
  return low === high ? `${low}°` : `${low}° / ${high}°`;
}

export default function Home() {
  const router = useRouter();
  const { tx } = useI18n();
  const { accessToken, user } = useAuth();
  const { draft: planDraft, update: updatePlan } = usePlan();
  // 여행 조건 모달. 로그인 후 홈 첫 진입에 한 번, 그리고
  // 「나중에」를 고른 사람에게는 「일정 물어보기」를 누를 때마다 다시 묻는다.
  const [promptState, setPromptState] = useState<ConditionsPromptState>('NEVER');
  const [conditions, setConditions] = useState<{ open: boolean; reprompt: boolean; pending: StartBarValue | null }>({ open: false, reprompt: false, pending: null });
  // 데스크톱 판인가 — 폭만이 아니라 폴드 펼침 가로까지, 판정은 useLayout 한 곳(S15P21E201-1563).
  const { width, desktop } = useLayout();
  // 위쪽 메뉴가 떠 있으면(폴드 펼침 등 태블릿) 로그인·종을 거기 맡긴다 — 머리에서 두 번 그리지 않는다.
  const topNav = useTopNavShown();
  const { hydrated, hasEnteredApp, markEnteredApp } = useOnboardingPreferences();
  // 시안 5 Home 의 「⊕ 한국어」 — 외국인이 홈에서 바로 언어를 바꾼다(S15P21E201-1372). 첫 화면의 언어 시트를 그대로 쓴다.
  const home = useHomeData(!desktop);
  // 안 본 알림이 있으면 종에 점 — 여행 활동을 마지막으로 본 시각과 견준다(S15P21E201-1380). 화면에 돌아올 때마다 다시 본다.
  // 🔴 홈 카드가 받은 여행 목록으로, 홈이 그려지고 몇 초 뒤에, 최근 여행 셋만 본다(S15P21E201-1686) — 전에는 여는 순간 여행마다 불렀다.
  const bellDot = useHomeBellDot({ userId: user?.userId ?? null, accessToken, trips: home.trips, visible: !topNav, tx });
  // 하트는 데스크톱 홈과 같은 자리에서 온다 — 베껴 두면 한쪽만 고쳐진다.
  const saved = useSavedPlaces(accessToken);
  const [assistantOpen, setAssistantOpen] = useState(false);
  const [openingTrip, setOpeningTrip] = useState(false);
  // 동백이 단추는 탭바 윗변에서 12 위 — 안전영역이 있는 폰이든 없는 웹이든 탭바와의 간격이 같다.
  const insets = useSafeAreaInsets();
  const assistantBottom = tabBarBottomMargin(insets.bottom) + TAB_BAR_HEIGHT + spacing[3];

  useEffect(() => {
    if (hydrated && !hasEnteredApp) markEnteredApp();
  }, [hydrated, hasEnteredApp, markEnteredApp]);

  // ── 신규 사용자 안내 — S15P21E201-1361. 앱 소개를 막 끝낸 사람에게만 (firstRun.ts) ──
  const [coach, setCoach] = useState<{ visible: boolean; startBar: CoachHole | null; assistant: CoachHole | null }>({ visible: false, startBar: null, assistant: null });
  const startBarRef = useRef<View>(null);
  const assistantRef = useRef<View>(null);
  const [checklist, setChecklist] = useState<ChecklistState | null>(null);
  const [coachQueued, setCoachQueued] = useState(false);
  // 여행 조건 모달이 뜰지 결정됐나 — 그 전에 코치를 띄우면 둘이 겹친다(실측: 모달 위에 막이 덮였다).
  const [promptChecked, setPromptChecked] = useState(false);
  useEffect(() => {
    if (!hydrated || desktop) return;
    let alive = true;
    void loadChecklist().then((state) => { if (alive) setChecklist(state); });
    void takeHomeCoach().then((show) => { if (alive && show) setCoachQueued(true); });
    return () => { alive = false; };
  }, [hydrated, desktop]);
  useEffect(() => {
    // 조건 모달이 닫힌 뒤에 — 모달이 안 뜨는 사람은 판정이 끝나는 즉시.
    if (!coachQueued || !promptChecked || conditions.open) return;
    let alive = true;
    // 구멍 자리는 실제로 잰다 — 첫 그리기가 끝난 다음 프레임에.
    const measure = (ref: React.RefObject<View | null>) => new Promise<CoachHole | null>((resolve) => {
      const node = ref.current;
      if (!node) { resolve(null); return; }
      node.measureInWindow((x, y, width, height) => resolve(width > 0 && height > 0 ? { x, y, width, height, radius: radius.md } : null));
    });
    const timer = setTimeout(() => {
      void Promise.all([measure(startBarRef), measure(assistantRef)]).then(([startBar, assistant]) => { if (alive) { setCoachQueued(false); setCoach({ visible: true, startBar, assistant }); } });
    }, 350);
    return () => { alive = false; clearTimeout(timer); };
  }, [coachQueued, promptChecked, conditions.open]);
  const closeCoach = () => setCoach((current) => ({ ...current, visible: false }));

  const openHomeTrip = async (trip: TripSummaryDto) => {
    if (openingTrip) return;
    setOpeningTrip(true);
    const destination = await resolveHomeTripDestination(trip, accessToken);
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
      setPromptChecked(true);
    });
    return () => { alive = false; };
  }, [accessToken, user?.userId]);

  // 🔴 열 문항 화면이 「날짜 정하기」로 보낸 사람은 고칠 칸을 열어 둔 채로 받는다 — S15P21E201-1350.
  //    접힌 바를 보여 주면 그 사람이 보기에는 아무 데도 안 간 것이다.
  const editSection = startBarEditSection(useLocalSearchParams().edit);

  // 🔴 시작 바의 값을 홈이 들고 있다.
  //
  // <p>알약은 화면 «안»에 있고 시트는 화면 «전체»를 덮어야 해서 탭바의 형제로 올라간다.
  // 한 부품이 두 자리에 동시에 있을 수 없으므로 둘로 나누고, 값은 홈이 들고 둘에게
  // 같은 것을 준다 — 안 그러면 시트에서 고른 것이 알약 요약에 안 나타난다.
  const [barValue, setBarValue] = useState<StartBarValue>(() => (editSection ? startBarFromDraft(planDraft) : EMPTY_START_BAR));
  // 「날짜 정하기」로 들어온 사람은 시트가 «열린 채로» 받는다 — S15P21E201-1350 과 같은 이유다.
  const [startBarSheet, setStartBarSheet] = useState(Boolean(editSection));

  const applyBarAndGo = (value: StartBarValue) => {
    updatePlan({
      origin: value.origin,
      originLat: value.originLat,
      originLng: value.originLng,
      lodging: value.lodging,
      lodgingLat: value.lodgingLat,
      lodgingLng: value.lodgingLng,
      lodgingPlace: value.lodgingPlace,
      startDate: value.startDate,
      endDate: value.endDate,
      adults: value.adults,
      children: value.children,
      travelers: value.adults + value.children,
    });
    // 🔴 /plan 의 「출발지 수정」으로 왔으면(edit 매개변수) 그 /plan 이 아래에 쌓여 있다 — push 하면 /plan 이 둘이 되어
    //    뒤로 가면 출발지 없던 옛 화면이 한 번 더 나온다(실기 빌드 28, S15P21E201-1439). 왔던 화면으로 돌아간다.
    if (editSection && router.canGoBack()) router.back();
    else router.push('/plan');
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
            {/* 🔴 폰 머리에 언어 알약을 두지 않는다 — S15P21E201-1402 · 1408. 로고·언어·날씨·종 넷이 폰 폭
                (360~411)에 안 들어가 종이 화면 밖으로 밀려났다. 시안 5 Home 도 로고·날씨·종 셋이다.
                언어는 첫 화면과 마이페이지 설정 「앱 언어」(AppLanguageSetting)에서 바꾼다. */}
            {signedIn ? (
              <>
                {/* 🔴 칩이 <View> 였다 — S15P21E201-1502. 알약 모양에 값이 들어 있으면 사람은 누른다.
                    실제로 실기에서 「눌러도 안 들어가진다」로 올라왔다. 누를 데가 아니면 모양을 바꿔야
                    하는데, 날씨·준비물 화면이 이미 있으므로 그리로 잇는 편이 맞다. */}
                {weather && (weather.maxTemperature !== null || weather.minTemperature !== null) ? (
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={tx('내 여행 날씨·준비물', 'Weather and packing for my trip')}
                    onPress={() => router.push('/field/translate')}
                    style={({ pressed }) => [styles.weatherChip, pressed && styles.pressed]}
                  >
                    <Text variant="caption" weight="bold" numberOfLines={1} style={styles.weatherWord}>
                      {weather.skyCondition === 'CLEAR' ? tx('맑음', 'Clear') : weather.skyCondition === 'CLOUDY' ? tx('흐림', 'Cloudy') : tx('구름 조금', 'Partly cloudy')}
                    </Text>
                    <Text variant="caption" numberOfLines={1} style={styles.weatherTemp}>{weatherTemperatureText(weather)}</Text>
                  </Pressable>
                ) : null}
                {/* 미읽음이 있는지 알려주는 조회가 없어 주황 점은 안 찍는다 — 늘 찍으면
                    읽을 것이 없는데도 있는 것처럼 보이고, 안 찍는 쪽이 거짓이 아니다.
                */}
              </>
            ) : topNav ? null : (
              <Pressable accessibilityRole="button" onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/home' } })} style={({ pressed }) => [styles.loginPill, pressed && styles.pressed]}>
                <Text weight="bold" color={color.brand.navy}>{tx('로그인', 'Sign in')}</Text>
              </Pressable>
            )}
            {/* 종은 늘 그 자리에(시안 5 Home). 손님이 누르면 알림 화면이 로그인을 안내한다 — 자리가 비면 「알림이 없는 앱」으로 읽힌다(2026-09-21 지적).
                🔴 위쪽 메뉴가 떠 있으면(폴드 펼침 등 태블릿) 로그인·종이 거기 이미 있다 — 두 번 그리지 않는다(S15P21E201-1547). */}
            {topNav ? null : (
              <Pressable accessibilityRole="button" accessibilityLabel={tx('알림 확인', 'Check notifications')} onPress={() => router.push('/notifications')} style={({ pressed }) => [styles.bell, pressed && styles.pressed]}>
                <Image source={bellIcon} resizeMode="contain" style={styles.bellIcon} />
                {bellDot ? <View style={styles.bellDot} /> : null}
              </Pressable>
            )}
          </View>
        </View>


        {/* 동백이 첫 여행 체크리스트 — 온보딩을 거친 사람, 로그인한 뒤, 셋 다 하기 전까지. */}
        {signedIn && checklist ? <FirstTripChecklist state={checklist} hasTrip={Boolean(home.trip)} onDismiss={() => { setChecklist({ ...checklist, dismissed: true }); void dismissChecklist(); }} /> : null}

        {/* ── 히어로 ── */}
        <View style={styles.hero}>
          <Text weight="bold" color={color.brand.navy} style={styles.heroTitle}>{tx('부산의 모든 여행,\n가볼래?', 'Every side of Busan,\nyours to explore.')}</Text>
          {/* 시안 p0 의 시작 바. 출발지·날짜·인원을 여기서 받아
              조건 화면으로 넘긴다. 여행지는 안 묻는다 — 부산 고정이다.
          */}
          <View ref={startBarRef} collapsable={false}>
            <PlanStartBar
              wide={false}
              accessToken={accessToken}
              onSubmit={startPlanFromBar}
              value={barValue}
              onChange={setBarValue}
              onOpenSheet={() => setStartBarSheet(true)}
            />
          </View>

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
              <Pressable accessibilityRole="button" accessibilityState={{ busy: openingTrip, disabled: openingTrip }} disabled={openingTrip} onPress={() => void openHomeTrip(home.trip!)} style={({ pressed }) => [styles.tripCard, pressed && styles.pressed]}>
                <Text variant="caption" weight="bold" color={color.state.success}>
                  {/* 여행 목록 카드와 같은 함수 — 날짜가 서버 상태를 이긴다(S15P21E201-1595). 오늘 여행에 「준비 완료」가 붙던 것. */}
                  {tripStatusLabel(effectiveTripStatus(home.trip), tx)}
                </Text>
                {/* 이름이 있으면 이름, 없으면 날짜 — 넓은 화면 카드(MyTripCard)·내 여행 목록과 같은 규칙(S15P21E201-1678).
                    전에는 이름 기능이 생기기 전의 옛 주석대로 날짜만 올려서, 이름을 붙여도 홈에는 안 보였다.
                    이름을 제목에 올리면 날짜는 둘째 줄로 내린다 — 같은 이름의 여행 둘을 날짜로 가린다. */}
                <Text variant="title" weight="bold">{tripDisplayTitle(home.trip, homeTripDates(home.trip, tx))}</Text>
                <Text color={color.text.body}>
                  {home.trip.title?.trim() && home.trip.startDate
                    ? txf(tx, '%s · %s일 · %s명', `%s · %s ${enPlural(home.trip.dayCount, 'day', 'days')} · %s ${enPlural(home.trip.partySize, 'traveler', 'travelers')}`, homeTripDates(home.trip, tx), home.trip.dayCount, home.trip.partySize)
                    : tx(`${home.trip.dayCount}일 · ${home.trip.partySize}명`, `${enCount(home.trip.dayCount, 'day', 'days')} · ${enCount(home.trip.partySize, 'traveler', 'travelers')}`)}
                </Text>
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
        {saved.consentPrompt}
        <ConditionsPromptModal visible={conditions.open} reprompt={conditions.reprompt} onClose={closeConditions} />
  </Screen>

      {/* 판이 먼저다 — 메뉴와 단추보다 아래에 깔려야 그 둘은 그대로 눌린다. */}
      <AssistantBackdrop open={assistantOpen} onClose={() => setAssistantOpen(false)} />
      <View ref={assistantRef} collapsable={false} style={[styles.assistantAnchor, { bottom: assistantBottom }]}>
        {/* 폰은 부제 없이 232 폭. 마스코트 위로 뜬다. */}
        <AssistantMenu compact open={assistantOpen} onClose={() => setAssistantOpen(false)} />
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ expanded: assistantOpen }}
          accessibilityLabel={tx('가볼래 여행 도우미 열기', 'Open GABOLLE travel assistant')}
          onPress={() => setAssistantOpen((open) => !open)}
          style={({ pressed }) => [styles.assistantButton, pressed && styles.pressed]}
        >
          {/* 위아래로 흔들리지 않는다 — 단추는 가만히 있어야 단추다(2026-09-21 지적).
              눌러서 메뉴가 열린 동안은 >.< 표정 — 「눌렸다」를 얼굴로 말한다(S15P21E201-1430). */}
          <GabolleMascot state={assistantOpen ? 'thinking' : 'idle'} still style={styles.assistantMascot} />
          {/* 시안 5 Home — 흰 원 위의 동백이, 오른쪽 위에 작은 「AI」 표. 원이 있어야 사진 위에서도 눌리는 것으로 보인다(S15P21E201-1381). */}
          <View style={styles.assistantBadge}><Text variant="micro" weight="bold" color={color.action.outline}>AI</Text></View>
        </Pressable>
      </View>

      {/* 🔴 시트는 탭바 «앞»에 그린다. 그래야 탭바(받침 zIndex 30)가 시트(25) 위로
          미끄러져 내려가는 것이 보인다. 뒤에 그리면 탭바가 시트에 가려 그냥 사라진다. */}
      {startBarSheet ? (
        <PlanStartBar
          sheet
          wide={false}
          accessToken={accessToken}
          onSubmit={startPlanFromBar}
          value={barValue}
          onChange={setBarValue}
          onClose={() => setStartBarSheet(false)}
          initialSection={editSection}
        />
      ) : null}

      <TabBar active="home" hidden={startBarSheet} />
      <HomeCoach visible={coach.visible} startBar={coach.startBar} assistant={coach.assistant} onClose={closeCoach} onStart={() => { closeCoach(); router.push('/plan'); }} />
    </View>
  );
}

const CARD_WIDTH = 240;

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.canvas },
  screenContent: { paddingHorizontal: 0, paddingTop: 0 },
  pressed: { opacity: 0.75 },

  header: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing[6], paddingTop: spacing[4] },
  logo: { width: 143, height: 26 },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], flexShrink: 1, minWidth: 0, marginLeft: spacing[2] },
  // 🔴 머리줄은 [로고][headerRight = 날씨 칩 · 종] 이다(손님이면 로그인 알약 · 종).
  //    React Native(Yoga)와 react-native-web 은 flexShrink 기본값이 «0» 이다(CSS 의 1 과 다르다).
  //    그래서 줄이 넘치면 아무것도 줄지 않고, 맨 끝의 종이 화면 오른쪽 «밖으로» 밀려난다 —
  //    실기(SM-G973N, versionCode 27)에서 종의 경계가 [1076,154][1080,270], 화면에 걸친 4px 이었다.
  //    그때는 언어 알약까지 넷이 서 있었다. 알약을 빼고(S15P21E201-1402 · 1408), 그래도 모자란 폭에
  //    대비해 headerRight 가 로고 옆 남은 폭에 맞춰 줄고(flexShrink:1 + minWidth:0) 그 안에서 날씨 칩이
  //    준다. 칩 안에서는 날씨 낱말이 말줄임되고 기온은 남는다. 종은 줄지 않는다(flexShrink:0).
  weatherChip: { flexDirection: 'row', alignItems: 'center', gap: spacing[1], height: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.soft, flexShrink: 1, minWidth: 0 },
  weatherWord: { flexShrink: 1, minWidth: 0 },
  weatherTemp: { flexShrink: 0 },
  bell: { width: 44, height: 44, flexShrink: 0, alignItems: 'center', justifyContent: 'center' },
  bellDot: { position: 'absolute', top: 9, right: 9, width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.action.outline, borderWidth: 1.5, borderColor: color.canvas },
  bellIcon: { width: 20, height: 20 },
  loginPill: { minHeight: 36, justifyContent: 'center', paddingHorizontal: 14, borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field },

  hero: { gap: spacing[3], paddingHorizontal: spacing[6], paddingTop: spacing[6] },
  heroBadge: { alignSelf: 'flex-start', paddingHorizontal: spacing[3], paddingVertical: 6, borderRadius: radius.full, backgroundColor: color.surface.tint },
  heroTitle: { fontSize: 36, lineHeight: 42, letterSpacing: -0.3 },
  primaryCta: { minHeight: 52, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.action.primary, marginTop: spacing[1] },

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
  heartOn: { tintColor: color.action.secondary },
  heartOff: { tintColor: color.text.muted },

  tripCard: { gap: spacing[1], padding: spacing[4], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  tripSkeleton: { height: 140 },
  tripGo: { marginTop: spacing[1] },
  tripEmpty: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  tripMascot: { width: 56, height: 56 },
  tripEmptyCopy: { flex: 1, gap: spacing[1] },

  saveFeedback: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], marginTop: spacing[6], marginHorizontal: spacing[6], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.brand.navy },

  // 메뉴가 이 상자 위에 뜬다. 절대 위치를 단추가 아니라 감싸는 상자가 가진다.
  // 🔴 탭바와 같은 기준으로 선다 — 웹에서는 보이는 창에 고정(S15P21E201-1601). 부모 기준이면 폰 크롬에서 주소창이
  //    접힐 때 탭바만 내려가고 이 단추는 남아 간격이 벌어졌다.
  assistantAnchor: { position: bottomDockPosition(), right: spacing[6], zIndex: 20 },
  assistantButton: { width: 64, height: 64, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, shadowColor: color.brand.navy, shadowOpacity: 0.14, shadowRadius: 10, shadowOffset: { width: 0, height: 4 }, elevation: 4 },
  assistantBadge: { position: 'absolute', top: -2, right: -2, minWidth: 22, height: 18, paddingHorizontal: 5, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card, borderWidth: 1.5, borderColor: color.action.outline },
  assistantMascot: { width: 50, height: 50 },
});
