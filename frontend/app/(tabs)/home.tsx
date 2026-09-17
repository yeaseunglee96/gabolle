// 폰 홈 (S15P21E201-970). 디자인 인계 `design_handoff_home_phone` 의 **절충안(C)**.
//
// 데스크톱 홈(app/index.tsx)과 짝이다. 데이터는 같은 훅(useHomeData)에서 읽는다.
//
// 🔴 1차 시안(2a)은 **지금 있는 기능 다섯을 말없이 지웠다** — 현장 도구·로컬 탐색·알림 종·
// 하트·챗봇. 그대로 만들면 현장 도구는 들어갈 길이 아예 없어진다(다른 진입점이 없고, 이 바는
// "챗봇과 겹쳐 헷갈린다"는 사용자 리포트 때문에 일부러 이 자리로 옮긴 것이다). 그래서 2차를
// 다시 받아 아래처럼 정했다.
//
//   현장 도구  남김 → 히어로 CTA 아래 2열 카드(통역 · 메뉴판 번역)
//   로컬 탐색  옮김 → 「로컬 탐색」 칩 구역 + 「전체 →」 (바는 없앰). 2026-09-15 에 기록 피드
//                     위로 올렸다 — 스크롤해야 보이는 자리라 사실상 숨어 있었다(-989)
//   알림 종    남김 → 머리 오른쪽. 🔴 미읽음 조회 API 가 없어 **점은 안 찍는다**
//   큐레이션   교체 → 실제 기록 카드 3장 (사진·문구가 코드에 박혀 있던 것을 대체)
//   하트       옮김 → 「부산 둘러보기」 카드. 같은 **장소** 엔티티라 저장·행동 이벤트가 그대로 맞는다
//   챗봇       남김 → 우하단 플로팅
import { useEffect, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Redirect, useRouter } from 'expo-router';

import { sendAppEvent } from '@/analytics/appEvents';
import { useAuth } from '@/auth/AuthProvider';
import { loadSavedPlaceIds, setSavedPlace } from '@/discovery/savedPlaces';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { PlaceVisual } from '@/components/PlaceVisual';
import { Screen } from '@/components/Screen';
import { GettingStartedGuide } from '@/components/GettingStartedGuide';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useHomeData } from '@/home/useHomeData';
import { resolveHomeTripDestination } from '@/home/tripNavigation';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { useI18n } from '@/i18n';
import { localFacetLabel } from '@/discovery/localExplore';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { relativeStoryTime } from '@/social/stories';

const bellIcon = require('../../assets/icons/home/bell.png');
const heartIcon = require('../../assets/icons/home/heart.png');
const speakerIcon = require('../../assets/icons/common/speaker.png');

export default function Home() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { accessToken } = useAuth();
  const { width } = useLayout();
  const desktop = isAtLeast(width, 'lg');
  const { hydrated, hasEnteredApp, markEnteredApp } = useOnboardingPreferences();
  const home = useHomeData(!desktop);
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set());
  const [saveFeedback, setSaveFeedback] = useState<string | null>(null);
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

  // S15P21E201-1013 — 계정 것과 기기 것을 합쳐서 본다(로그인 안 했으면 기기 것만).
  useEffect(() => {
    void loadSavedPlaceIds(accessToken).then((ids) => setLikedIds(new Set(ids)));
  }, [accessToken]);

  // 하트는 계정에 남는다(로그인 안 했으면 기기에만). 저장할 때 분석용 신호도 함께 보낸다.
  //
  // 🔴 저장을 해제한 것은 "싫다"가 아니라 "취소"다. 추천 화면의 제외 버튼과 달리 이 하트에는
  //    싫다는 뜻이 없어서 해제에는 아무 이벤트도 보내지 않는다 — 없는 뜻을 만들지 않는다.
  // 🔴 전송을 setLikedIds 의 갱신 함수 안에서 하지 않는다. React 가 그 함수를 두 번 부를 수
  //    있고, 그러면 하트 한 번에 이벤트가 두 건 적힌다.
  const toggleLike = (placeId: string) => {
    const saved = !likedIds.has(placeId);
    const next = new Set(likedIds);
    saved ? next.add(placeId) : next.delete(placeId);
    setLikedIds(next);
    setSaveFeedback(saved ? tx('장소를 저장했어요.', 'Saved this place.') : tx('저장을 해제했어요.', 'Unsaved this place on this device.'));
    // 🔴 S15P21E201-1081 — 서버가 못 받았으면 뒤늦게라도 사실대로 고쳐 말한다.
    //    하트는 즉시 반응해야 하므로 먼저 낙관적으로 그리고, 결과가 오면 문구만 바꾼다.
    void setSavedPlace(placeId, saved, accessToken).then(({ sync }) => {
      if (sync === 'failed') setSaveFeedback(tx('이 기기에만 저장했어요. 서버에 아직 반영하지 못했어요.', 'Saved on this device only — not synced to the server yet.'));
    });
    if (saved) sendAppEvent({ type: 'place_like', accessToken, payload: { place_id: placeId, surface: 'home' } });
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
                {/* 🔴 미읽음이 있는지 알려주는 조회가 없어 주황 점은 안 찍는다 — 늘 찍으면
                    읽을 것이 없는데도 있는 것처럼 보이고, 안 찍는 쪽이 거짓이 아니다. */}
                <Pressable accessibilityRole="button" accessibilityLabel={tx('알림 확인', 'Check notifications')} onPress={() => router.push('/notifications')} style={({ pressed }) => [styles.bell, pressed && styles.pressed]}>
                  <Image source={bellIcon} resizeMode="contain" style={styles.bellIcon} />
                </Pressable>
              </>
            ) : (
              <Pressable accessibilityRole="button" onPress={() => router.push('/sign-in')} style={({ pressed }) => [styles.loginPill, pressed && styles.pressed]}>
                <Text weight="bold" color={color.brand.navy}>{tx('로그인', 'Sign in')}</Text>
              </Pressable>
            )}
          </View>
        </View>

        {/* ── 히어로 ── */}
        <GettingStartedGuide />
        <View style={styles.hero}>
          <View style={styles.heroBadge}><Text variant="caption" weight="bold" color={color.brand.orange}>AI TRAVEL PLANNER · BUSAN</Text></View>
          <Text weight="bold" color={color.brand.navy} style={styles.heroTitle}>{tx('부산의 모든 여행,\n가볼래?', 'Every side of Busan,\nyours to explore.')}</Text>
          <Text>{tx('취향과 이동 조건을 반영해 당신만의 부산 여행을 만들어요.', 'Build a Busan trip around your taste and mobility needs.')}</Text>
          <Pressable accessibilityRole="button" onPress={() => router.push('/plan/basic')} style={({ pressed }) => [styles.primaryCta, pressed && styles.pressed]}>
            <Text variant="title" weight="bold" color={color.text.onAction}>{tx('여행 계획 시작하기', 'Start planning')}</Text>
          </Pressable>

          {/* 현장 도구 — 로그인 없이도 쓸 수 있다(확인함). 챗봇 버튼과 화면 반대편이라
              「겹쳐 보인다」는 리포트가 다시 나지 않는다.

              두 번째 카드가 「메뉴판 번역」이었다 — S15P21E201-981. 메뉴판 카메라 번역은
              번역 API 업체가 안 정해져 이번 배포에서 통째로 뺐는데(-907, 2026-09-13) 홈의
              버튼만 남아 있었다. 누르면 메뉴판 번역이 없는 현장 도구 화면으로 간다. 없는
              기능을 이름으로 약속하는 것이 링크가 죽은 것보다 나쁘다 — 사용자는 자기가 길을
              잘못 찾았다고 생각한다. 가는 곳(`/field/translate`)의 실제 제목으로 맞춘다.
              업체가 정해져 그 기능이 생기면 그때 이름을 되돌린다. */}
          <View style={styles.fieldTools}>
            {[
              { path: '/field/speak', ko: '통역', en: 'Phrases', subKo: '택시·식당에서 바로', subEn: 'Taxis and restaurants' },
              { path: '/field/translate', ko: '현장 도구', en: 'On-the-go tools', subKo: '한국어 문장·날씨', subEn: 'Phrases and weather' },
            ].map((tool) => (
              <Pressable key={tool.path} accessibilityRole="button" onPress={() => router.push(tool.path)} style={({ pressed }) => [styles.fieldTool, pressed && styles.pressed]}>
                <View style={styles.fieldToolIcon}><Image source={speakerIcon} resizeMode="contain" style={styles.fieldToolIconImage} /></View>
                <View style={styles.fieldToolCopy}>
                  <Text weight="bold" numberOfLines={1}>{tx(tool.ko, tool.en)}</Text>
                  <Text variant="caption" numberOfLines={1}>{tx(tool.subKo, tool.subEn)}</Text>
                </View>
              </Pressable>
            ))}
          </View>
        </View>

        {/* ── 로컬 탐색 (옛 로컬 탐색 바 자리) ──
            🔴 2026-09-15 에 기록 피드 **아래**에서 여기로 올렸다. 로컬 탐색으로 가는 길이 이
            칩과 챗봇 둘뿐인데(하단 탭에도 데스크톱 상단 바에도 없다) 스크롤해야 보이는 자리에
            있어서 사실상 숨어 있었다. 제목도 「갈래로 찾기」라 눌렀을 때 어디로 가는지 알 수
            없었다 — 목적지 이름을 그대로 적는다. 탭·상단 바로 꺼내는 것은 내비게이션 구조를
            건드리는 일이라 디자인 재작업 뒤로 미뤘다(S15P21E201-989). */}
        {home.chips.length ? (
          <View style={styles.section}>
            <View style={styles.sectionHead}>
              <Text variant="title" weight="bold">{tx('로컬 탐색', 'Explore locally')}</Text>
              <Pressable accessibilityRole="link" onPress={() => router.push('/explore')}><Text weight="bold" color={color.brand.navy}>{tx('전체 →', 'See all →')}</Text></Pressable>
            </View>
            {/* 칩 글자만으로는 무엇을 하는 곳인지 모른다 — 한 줄로 적어 둔다. */}
            <Text variant="caption" style={styles.sectionSub}>{tx('축제·전통시장·야경처럼 갈래로 부산을 둘러봐요.', 'Browse Busan by festivals, markets, night views and more.')}</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.rail}>
              {home.chips.map((chip) => (
                <Pressable key={chip.featureKey} accessibilityRole="link" onPress={() => router.push({ pathname: '/explore', params: { facet: chip.featureKey } })} style={({ pressed }) => [styles.chip, pressed && styles.pressed]}>
                  <Text weight="medium" color={color.text.heading}>{localFacetLabel(chip, language)}</Text>
                </Pressable>
              ))}
            </ScrollView>
          </View>
        ) : null}

        {/* ── 지금 부산에서 남긴 기록 ── */}
        <View style={styles.section}>
          <View style={styles.sectionHead}>
            <Text variant="eyebrow" weight="bold">{tx('지금 부산에서 남긴 기록', 'Just shared in Busan')}</Text>
            {signedIn ? (
              <Pressable accessibilityRole="link" onPress={() => router.push('/feed')}><Text weight="bold" color={color.brand.navy}>{tx('피드 전체 →', 'See all →')}</Text></Pressable>
            ) : null}
          </View>

          {/* 🔴 로그인 안 한 사람에게 「아직 기록이 없어요」라고 하면 거짓말이다 — 기록은 있는데
              서버가 익명에게는 안 준다(스토리 조회가 401). 못 보는 이유를 그대로 적는다. */}
          {!signedIn ? (
            <View style={styles.signInCard}>
              <GabolleMascot state="thinking" style={styles.signInMascot} />
              <View style={styles.signInCopy}>
                <Text>{tx('다른 여행자들이 남긴 기록은 로그인하면 볼 수 있어요.', 'Sign in to see what other travelers shared.')}</Text>
                <Pressable accessibilityRole="button" onPress={() => router.push('/sign-in')} style={({ pressed }) => [styles.signInButton, pressed && styles.pressed]}>
                  <Text weight="bold" color={color.text.onAction}>{tx('로그인하고 보기', 'Sign in to view')}</Text>
                </Pressable>
              </View>
            </View>
          ) : home.stories === null ? (
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.rail}>
              {[0, 1, 2].map((slot) => <View key={slot} style={[styles.storyCard, styles.storySkeleton]} />)}
            </ScrollView>
          ) : home.stories.length === 0 ? (
            <Text variant="caption" style={styles.sectionNote}>{tx('아직 남겨진 기록이 없어요. 첫 기록을 남겨 보세요.', 'No records yet — be the first to share one.')}</Text>
          ) : (
            <ScrollView horizontal showsHorizontalScrollIndicator={false} snapToAlignment="start" decelerationRate="fast" contentContainerStyle={styles.rail}>
              {home.stories.map((story) => {
                const where = story.place?.name ?? story.region ?? '';
                return (
                  <Pressable key={story.id} accessibilityRole="button" onPress={() => router.push(`/feed/${story.id}`)} style={({ pressed }) => [styles.storyCard, pressed && styles.pressed]}>
                    {story.images.length
                      ? <Image source={{ uri: story.images[0].url }} resizeMode="cover" accessibilityLabel={tx('여행 기록 사진', 'Trip record photo')} style={styles.storyImage} />
                      : <View style={styles.storyImage} />}
                    <View style={styles.storyBody}>
                      <Text variant="caption" numberOfLines={1}>{where ? `${relativeStoryTime(story.createdAt, tx)} · ${where}` : relativeStoryTime(story.createdAt, tx)}</Text>
                      <Text numberOfLines={2} color={color.text.heading}>{story.body}</Text>
                      {/* 작성자 프로필 사진은 계정에 없다 — 이름만 적는다. */}
                      <Text variant="caption" weight="bold" color={color.text.body} numberOfLines={1}>{story.author.displayName}</Text>
                    </View>
                  </Pressable>
                );
              })}
            </ScrollView>
          )}
        </View>

        {/* ── 부산 둘러보기 (하트가 여기로 옮겨 왔다) ── */}
        {home.places.length ? (
          <View style={styles.sectionPadded}>
            {/* 🔴 「부산 대표 장소」였다가 2026-09-15 에 낮췄다. 고르는 방법이 부산 중심에서
                가까운 순 넷이라 대표를 판정하는 자리가 없었다 — 제목만 대표를 약속하고 있었다. */}
            <Text variant="title" weight="bold">{tx('부산 둘러보기', 'Browse Busan')}</Text>
            <View style={styles.placeGrid}>
              {home.places.map((place) => {
                const liked = likedIds.has(place.placeId);
                return (
                  <View key={place.placeId} style={styles.placeCard}>
                    <Pressable accessibilityRole="button" onPress={() => router.push(`/place/${place.placeId}`)} style={({ pressed }) => [styles.placeThumbWrap, pressed && styles.pressed]}>
                      <PlaceVisual name={place.nameKo} address={place.address} photoUrl={place.photoUrl} photoSource={place.photoSource} />
                      <Pressable
                        accessibilityRole="button"
                        accessibilityState={{ selected: liked }}
                        accessibilityLabel={liked ? tx(`${place.nameKo} 저장 취소`, `Unsave ${place.nameKo}`) : tx(`${place.nameKo} 저장`, `Save ${place.nameKo}`)}
                        onPress={() => (signedIn ? toggleLike(place.placeId) : router.push('/sign-in'))}
                        style={styles.heartButton}
                      >
                        {/* 색만으로 저장 여부를 나타내지 않는다(팀 UX 가이드라인 11번) —
                            저장했을 때만 배경 원이 함께 나타난다. */}
                        <View style={[styles.heartBackdrop, liked && styles.heartBackdropOn]}>
                          <Image source={heartIcon} resizeMode="contain" style={[styles.heartIcon, liked ? styles.heartOn : styles.heartOff]} />
                        </View>
                      </Pressable>
                    </Pressable>
                    <Text weight="bold" numberOfLines={1}>{place.nameKo}</Text>
                    {/* 갈래(`category`)는 「FOOD」 같은 코드로 와서 그대로 못 쓴다 — 주소를 쓴다. */}
                    <Text variant="caption" numberOfLines={1}>{place.address ?? ''}</Text>
                  </View>
                );
              })}
            </View>
          </View>
        ) : null}

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

        {saveFeedback ? (
          <Pressable accessibilityRole="button" accessibilityLabel={tx('저장 안내 닫기', 'Dismiss save notice')} accessibilityLiveRegion="polite" onPress={() => setSaveFeedback(null)} style={styles.saveFeedback}>
            <Text variant="caption" weight="bold" color={color.text.onAction}>{saveFeedback}</Text>
            <Text variant="caption" color={color.text.onAction}>{tx('닫기', 'Dismiss')}</Text>
          </Pressable>
        ) : null}
      </Screen>

      <Pressable
        accessibilityRole="button"
        accessibilityLabel={tx('가볼래 여행 도우미 열기', 'Open GABOLLE travel assistant')}
        onPress={() => router.push('/chat')}
        style={({ pressed }) => [styles.assistantButton, pressed && styles.pressed]}
      >
        <GabolleMascot state="idle" style={styles.assistantMascot} />
      </Pressable>

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

  fieldTools: { flexDirection: 'row', gap: spacing[2] },
  fieldTool: { flex: 1, minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingHorizontal: 14, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  fieldToolIcon: { width: 28, height: 28, borderRadius: radius.sm, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.tint },
  fieldToolIconImage: { width: 16, height: 16 },
  fieldToolCopy: { flex: 1, minWidth: 0 },

  section: { gap: spacing[3], paddingTop: spacing[8] },
  sectionPadded: { gap: spacing[3], paddingTop: spacing[8], paddingHorizontal: spacing[6] },
  sectionHead: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[6] },
  sectionSub: { marginTop: -spacing[1], paddingHorizontal: spacing[6] },
  sectionNote: { paddingHorizontal: spacing[6] },
  rail: { gap: spacing[3], paddingHorizontal: spacing[6] },

  storyCard: { width: CARD_WIDTH, borderRadius: radius.lg, overflow: 'hidden', borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  storySkeleton: { height: 260, backgroundColor: color.surface.soft },
  storyImage: { width: '100%', aspectRatio: 4 / 3, backgroundColor: color.surface.soft },
  storyBody: { gap: spacing[1], paddingHorizontal: spacing[4], paddingTop: spacing[3], paddingBottom: spacing[4] },

  signInCard: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginHorizontal: spacing[6], padding: spacing[4], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  signInMascot: { width: 64, height: 64 },
  signInCopy: { flex: 1, gap: spacing[2] },
  signInButton: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.brand.navy },

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

  // 마스코트 자체가 이미 원형 배지라 바깥에 흰 원을 또 두르지 않는다(이중 테두리로
  // 시선만 끔) — 그 결정은 그대로 두되, 사용자 실사용 리포트(2026-09-16)로 "챗봇
  // 아이콘이 너무 작다"는 지적을 받아 버튼·마스코트 크기 자체를 키운다.
  assistantButton: { position: 'absolute', right: spacing[6], bottom: 100, width: 68, height: 68, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full },
  assistantMascot: { width: 60, height: 60 },
});
