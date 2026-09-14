import { useEffect, useRef, useState } from 'react';
import { Image, ImageBackground, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Redirect, useRouter } from 'expo-router';

import { sendAppEvent } from '@/analytics/appEvents';
import { useAuth } from '@/auth/AuthProvider';
import { searchPlacesByName } from '@/discovery/places';
import { getSpendProfile } from '@/onboarding/spendProfile';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { useI18n } from '@/i18n';

// S15P21E201-938 — 사진·문구는 그대로 큐레이션(직접 고른 셋)이다. 진짜 추천 API 가 아직
// 없어서 "무엇을 보여줄까"는 여전히 여기서 정한다. 다만 예전에는 그 세 자리에 'haeundae'
// 같은 화면용 이름표를 넣어 뒀고, 그 이름표는 서버의 어떤 장소 행도 가리키지 않았다 —
// 좋아요를 누르면 존재하지 않는 장소에 행동 기록이 붙었다. 이제는 마운트 시
// searchPlacesByName 으로 실제 이름을 찾아 진짜 UUID 를 구해서 쓴다(RecommendationSeed
// 자체는 화면이 무엇을 "원하는지" 만 담고, 실재하는지는 아래에서 확인한다).
const RECOMMENDATION_SEEDS = [
  { key: 'haeundae', searchName: '해운대 해수욕장', titleKo: '해운대 해수욕장', titleEn: 'Haeundae Beach', descriptionKo: '푸른 바다와 도시가 만나는 곳', descriptionEn: 'Where the blue sea meets the city', image: require('../../assets/home/haeundae.png') },
  { key: 'gwangalli', searchName: '광안리 해수욕장', titleKo: '광안리 해수욕장', titleEn: 'Gwangalli Beach', descriptionKo: '야경과 함께하는 해변 산책', descriptionEn: 'A beach walk with a night view', image: require('../../assets/home/gwangalli.png') },
  { key: 'gamcheon', searchName: '감천문화마을', titleKo: '감천문화마을', titleEn: 'Gamcheon Culture Village', descriptionKo: '형형색색 감성 골목 여행', descriptionEn: 'A colorful, atmospheric alley trip', image: require('../../assets/home/gamcheon.png') },
] as const;

type RecommendationSeed = (typeof RECOMMENDATION_SEEDS)[number];
type RecommendationCardData = RecommendationSeed & { placeId: string };

// 큐레이션 셋 중 서버에 실제로 있는 것만 남긴다. 이름 검색이 실패하거나(오프라인·서버 오류)
// 그 이름의 장소가 카탈로그에 아직 없으면 그 카드는 그냥 빠진다 — 자리표시 값을 대신
// 넣지 않는다(빈 것과 틀린 것을 구분하는 것이 이 티켓의 요점이다).
async function resolveRecommendationCards(): Promise<RecommendationCardData[]> {
  const resolved = await Promise.all(RECOMMENDATION_SEEDS.map(async (seed) => {
    try {
      const matches = await searchPlacesByName(seed.searchName);
      return matches[0] ? { ...seed, placeId: matches[0].placeId } : null;
    } catch {
      return null;
    }
  }));
  return resolved.filter((card): card is RecommendationCardData => card !== null);
}

const bellIcon = require('../../assets/icons/home/bell.png');
const heartIcon = require('../../assets/icons/home/heart.png');
const arrowLeftIcon = require('../../assets/icons/home/arrow-left.png');
const arrowRightIcon = require('../../assets/icons/home/arrow-right.png');
const mapIcon = require('../../assets/icons/home/map.png');
const speakerIcon = require('../../assets/icons/common/speaker.png');
const SAVED_PLACES_KEY = 'gabolle.saved-home-places';

function RecommendationCard({ item, index, liked, onToggleLike, desktop }: {
  item: RecommendationCardData; index: number; liked: boolean; onToggleLike: () => void; desktop: boolean;
}) {
  const router = useRouter();
  const { tx } = useI18n();
  const title = tx(item.titleKo, item.titleEn);
  return (
    <View style={[styles.card, desktop && styles.desktopCard]}>
      <ImageBackground source={item.image} resizeMode="cover" style={styles.cardImage} imageStyle={styles.cardImageRadius}>
        {/* S15P21E201-919: 카드 전체를 20% 남색으로 덮으면 밝은 낮 사진에는 괜찮지만, 광안리처럼
            원래 어두운 야경 사진에는 카드 전체가 거의 새까맣게 보인다. 위쪽은 거의 안 덮고
            글자가 있는 아래쪽만 진하게 덮어서 사진 자체는 보이게 한다. */}
        <View style={styles.cardShadeTop} />
        <View style={styles.cardShadeBottom} />
        <Pressable accessibilityRole="button" accessibilityLabel={tx(`${title} 상세 보기`, `View details for ${title}`)} onPress={() => router.push(`/place/${item.placeId}`)} style={styles.cardLink} />
        <View style={styles.cardCounter}><Text variant="caption" weight="bold" color={color.text.onAction}>{index + 1}/3</Text></View>
        <Pressable accessibilityRole="button" accessibilityLabel={liked ? tx(`${title} 저장 취소`, `Unsave ${title}`) : tx(`${title} 저장`, `Save ${title}`)} onPress={(event) => { event.stopPropagation(); onToggleLike(); }} style={[styles.heartButton, liked && styles.heartButtonSelected]}>
          <Image source={heartIcon} resizeMode="contain" style={styles.heartIcon} />
        </Pressable>
        <View style={styles.cardCopy}>
          <Text variant="title" weight="bold" color={color.text.onAction}>{title}</Text>
          <Text variant="caption" color={color.text.onAction} style={styles.cardDescription}>{tx(item.descriptionKo, item.descriptionEn)}</Text>
        </View>
      </ImageBackground>
    </View>
  );
}

export default function Home() {
  const router = useRouter();
  const { tx } = useI18n();
  const { accessToken, ready } = useAuth();
  const { width } = useLayout();
  const desktop = isAtLeast(width, 'md');
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set());
  const carouselRef = useRef<ScrollView>(null);
  const dragStartX = useRef(0);
  const [activeCard, setActiveCard] = useState(0);
  const [saveFeedback, setSaveFeedback] = useState<string | null>(null);
  // null = 아직 서버에 물어보는 중. 빈 배열은 "물어봤는데 실제로 존재하는 카드가 하나도
  // 없었다"는 뜻이라 로딩과 구분한다(S15P21E201-938).
  const [cards, setCards] = useState<RecommendationCardData[] | null>(null);
  useEffect(() => {
    let active = true;
    void resolveRecommendationCards().then((resolved) => { if (active) setCards(resolved); });
    return () => { active = false; };
  }, []);
  useEffect(() => {
    void AsyncStorage.getItem(SAVED_PLACES_KEY).then((raw) => {
      if (!raw) return;
      try {
        const ids = JSON.parse(raw) as unknown;
        if (Array.isArray(ids)) setLikedIds(new Set(ids.filter((id): id is string => typeof id === 'string')));
      } catch {
        void AsyncStorage.removeItem(SAVED_PLACES_KEY);
      }
    });
  }, []);
  // 온보딩 세 질문(S15P21E201-807) — 계정이 아직 한 번도 답한 적 없으면(UNKNOWN) 홈을
  // 보여주기 전에 그 화면으로 보낸다. 로컬 플래그를 따로 두지 않고 서버 상태만 본다 —
  // 여러 로그인 경로(이메일·구글·카카오·네이버)를 전부 건드리지 않고 이 한 곳에서만
  // 게이트하기 위해서다. 이미 답했거나(SELECTED) 건너뛴 적 있으면(SKIPPED) 다시 안 보낸다.
  useEffect(() => {
    if (!ready || !accessToken) return;
    let active = true;
    void getSpendProfile(accessToken).then((result) => {
      if (active && result.status === 'UNKNOWN') router.replace('/spend-profile');
    }).catch(() => {
      // 조회에 실패해도 홈은 그대로 보여준다 — 이 화면 하나 때문에 앱 전체가 막히면 안 된다.
    });
    return () => { active = false; };
  }, [ready, accessToken, router]);
  // 하트는 기기에만 남아 있었다. 이제 저장할 때 서버에도 신호를 보낸다.
  //
  // 🔴 저장을 해제한 것은 "싫다" 가 아니라 "취소" 다. 추천 화면의 제외 버튼과 달리 이 하트에는
  //    싫다는 뜻이 없어서, 해제에는 아무 이벤트도 보내지 않는다 — 없는 뜻을 만들지 않는다.
  // 🔴 화면은 전송을 기다리지 않는다. 하트는 누른 즉시 채워지고 이벤트는 뒤에서 간다.
  // 🟢 S15P21E201-938 — place_id 는 이제 진짜다(resolveRecommendationCards 가 구해 온 것).
  //    카드가 화면에 있다는 것 자체가 실재를 이미 확인했다는 뜻이라 여기서 다시 안 가린다.
  // 🔴 전송을 setLikedIds 의 갱신 함수 안에서 하지 않는다. 그 함수는 React 가 두 번 부를 수
  //    있고, 그러면 하트 한 번에 이벤트가 두 건 적힌다(eventId 가 매번 달라 중복으로도 안 걸린다).
  const toggleLike = (key: string, placeId: string) => {
    const saved = !likedIds.has(key);
    const next = new Set(likedIds);
    saved ? next.add(key) : next.delete(key);
    setLikedIds(next);
    void AsyncStorage.setItem(SAVED_PLACES_KEY, JSON.stringify([...next]));
    setSaveFeedback(saved ? tx('이 기기에 여행지를 저장했어요.', 'Saved this place on this device.') : tx('이 기기에서 저장을 해제했어요.', 'Unsaved this place on this device.'));
    if (saved) sendAppEvent({ type: 'place_like', accessToken, payload: { place_id: placeId, surface: 'home' } });
  };
  const goToCard = (index: number) => {
    const next = Math.max(0, Math.min((cards?.length ?? 1) - 1, index));
    carouselRef.current?.scrollTo({ x: next * 312, animated: true });
    setActiveCard(next);
  };

  if (desktop) {
    return <Redirect href="/" />;
  }

  return (
    <View style={styles.shell}>
    <Screen wide withTabBar style={styles.screenContent}>
      <View style={styles.header}>
        <BrandLogoLink href="/home" imageStyle={styles.logo} />
        <Pressable accessibilityRole="button" accessibilityLabel={tx('알림 확인', 'Check notifications')} onPress={() => router.push('/notifications')} style={styles.bell}><Image source={bellIcon} resizeMode="contain" style={styles.bellIcon} /></Pressable>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        {/* S15P21E201-900: "부산 축제 찾아보기"(→/festivals)·"지금 갈 곳"(→/now) 바는 최초
            배포에서 뺐다 — 축제 기간 자료가 전부 만료됐고 지금 갈 곳 API가 운영에서 404라
            둘 다 진짜 값을 못 준다. 화면·라우트는 그대로 있어 자료가 들어오면 되돌리면 된다. */}

        <Pressable accessibilityRole="button" accessibilityLabel={tx('부산 로컬 탐색', 'Explore Busan like a local')} onPress={() => router.push('/explore')} style={({ pressed }) => [styles.exploreBar, pressed && styles.weatherBarPressed]}>
          {/* S15P21E201-914: 갈래 개수는 GET /api/v1/places/facets 가 정한다(explore.tsx) —
              여기서 숫자를 박으면 백엔드가 갈래를 늘리거나 줄일 때마다 다시 어긋난다. */}
          <View style={styles.barLabel}><Image source={mapIcon} resizeMode="contain" style={styles.barIcon} /><Text variant="body" weight="medium" color={color.text.heading}>{tx('축제·야시장 등 로컬 카테고리 둘러보기', 'Explore local categories')}</Text></View><Text weight="bold" color={color.brand.orange}>›</Text>
        </Pressable>

        {/* 예전에는 이 자리 대신 채팅 버튼 위에 떠 있는 작은 칩이었다 — 챗봇 버튼과 겹쳐 쌓여
            있어 하나의 메뉴처럼 보이는데 실제로는 서로 다른 곳(채팅 대 현장 도구 화면)으로
            가서 헷갈린다는 지적(S15P21E201 사용자 리포트)이 있었다. 위 로컬 탐색 줄과 같은
            자리로 옮겨, 챗봇 버튼과는 시각적으로 완전히 분리했다. */}
        <Pressable accessibilityRole="button" accessibilityLabel={tx('현장 도구 열기', 'Open on-the-go tools')} accessibilityHint={tx('현장 말하기와 메뉴판 번역을 한곳에서 씁니다', 'Use on-the-go phrases and menu translation in one place')} onPress={() => router.push('/field/translate')} style={({ pressed }) => [styles.fieldToolsBar, pressed && styles.weatherBarPressed]}>
          <View style={styles.barLabel}><Image source={speakerIcon} resizeMode="contain" style={styles.barIcon} /><Text variant="body" weight="medium" color={color.text.heading}>{tx('현장 도구 — 말하기·번역', 'On-the-go tools — phrases & translation')}</Text></View><Text weight="bold" color={color.brand.orange}>›</Text>
        </Pressable>

        <View style={styles.heading}>
          <Text variant="display" weight="bold" color={color.text.heading} style={styles.headingTitle}>{tx('오늘 어디 가볼래?', 'Where shall we go today?')}</Text>
          <Text variant="body" color={color.text.muted}>{tx('AI가 취향에 맞는 부산 여행을 제안해드려요', 'AI suggests a Busan trip that matches your taste')}</Text>
        </View>

        {cards === null ? (
          <View style={styles.recommendationStatus}>
            <Text variant="body" color={color.text.muted}>{tx('여행지를 불러오는 중...', 'Loading places...')}</Text>
          </View>
        ) : cards.length === 0 ? (
          // S15P21E201-938 — 셋 다 실재를 확인 못 했을 때(오프라인, 서버 오류, 카탈로그에
          // 아직 없음)의 화면. 빈 캐러셀을 그대로 그리지 않는다 — 빈 것과 틀린 것을 헷갈리지
          // 않는 것이 이 티켓의 요점이다.
          <View style={styles.recommendationStatus}>
            <Text variant="body" weight="medium" color={color.text.heading}>{tx('지금은 추천할 여행지가 없어요.', "We don't have a place to suggest right now.")}</Text>
            <Text variant="caption" color={color.text.muted}>{tx('위의 로컬 카테고리 둘러보기에서 찾아보세요.', 'Try exploring local categories above instead.')}</Text>
          </View>
        ) : (
          <>
            <ScrollView
              ref={carouselRef}
              horizontal
              pagingEnabled={false}
              snapToInterval={312}
              snapToAlignment="start"
              disableIntervalMomentum
              decelerationRate="fast"
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.carousel}
              onScrollBeginDrag={(event) => { dragStartX.current = event.nativeEvent.contentOffset.x; }}
              onScrollEndDrag={(event) => {
                const distance = event.nativeEvent.contentOffset.x - dragStartX.current;
                if (Math.abs(distance) < 36) goToCard(activeCard);
                else goToCard(activeCard + (distance > 0 ? 1 : -1));
              }}
              onMomentumScrollEnd={(event) => setActiveCard(Math.max(0, Math.min(cards.length - 1, Math.round(event.nativeEvent.contentOffset.x / 312))))}
            >
              {cards.map((item, index) => <RecommendationCard key={item.key} item={item} index={index} liked={likedIds.has(item.key)} onToggleLike={() => toggleLike(item.key, item.placeId)} desktop={false} />)}
            </ScrollView>

            <View style={styles.swipeHint}>
              <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 여행지', 'Previous place')} disabled={activeCard === 0} onPress={() => goToCard(activeCard - 1)} style={[styles.carouselButton, activeCard === 0 && styles.carouselButtonDisabled]}><Image source={arrowLeftIcon} resizeMode="contain" style={styles.swipeArrow} /></Pressable>
              <Text variant="caption" color={color.text.muted}>{tx(`${activeCard + 1} / ${cards.length} · 한 장씩 넘겨보세요`, `${activeCard + 1} / ${cards.length} · Swipe through one at a time`)}</Text>
              <Pressable accessibilityRole="button" accessibilityLabel={tx('다음 여행지', 'Next place')} disabled={activeCard === cards.length - 1} onPress={() => goToCard(activeCard + 1)} style={[styles.carouselButton, activeCard === cards.length - 1 && styles.carouselButtonDisabled]}><Image source={arrowRightIcon} resizeMode="contain" style={styles.swipeArrow} /></Pressable>
            </View>
          </>
        )}
        {saveFeedback && <Pressable accessibilityRole="button" accessibilityLabel={tx('저장 안내 닫기', 'Dismiss save notice')} accessibilityLiveRegion="polite" onPress={() => setSaveFeedback(null)} style={styles.saveFeedback}><Text variant="caption" weight="bold" color={color.text.onAction}>{saveFeedback}</Text><Text variant="caption" color={color.text.onAction}>{tx('닫기', 'Dismiss')}</Text></Pressable>}
      </ScrollView>

      <View style={styles.floatingStack}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={tx('가볼래 여행 도우미 열기', 'Open GABOLLE travel assistant')}
          accessibilityHint={tx('현재 이용할 수 있는 여행 도움 기능을 확인합니다', 'Check the travel help features available right now')}
          onPress={() => router.push('/chat')}
          style={({ pressed }) => [styles.assistantButton, pressed && styles.assistantButtonPressed]}
        >
          <View style={styles.assistantLabel}><Text variant="caption" weight="bold" color={color.text.heading}>{tx('도움이 필요해?', 'Need help?')}</Text></View>
          <GabolleMascot state="idle" style={styles.assistantMascot} />
        </Pressable>
      </View>
    </Screen>
    <TabBar active="home" />
    </View>
  );
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.canvas },
  screenContent: { paddingTop: spacing[2], paddingBottom: spacing[3] },
  // marginTop: 우측 상단에 전역 언어 배지(GlobalLanguageBadge)가 떠 있어서, 이 화면처럼
  // 알림종을 헤더 오른쪽 끝에 두면 배지가 그 위에 겹쳐 종이 안 보인다(S15P21E201 사용자
  // 리포트). 배지가 끝나는 자리 아래로 헤더를 내려서 겹치지 않게 한다.
  header: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  logo: { width: 100, height: 22 },
  bell: { width: 40, height: 40, borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  bellIcon: { width: 20, height: 20 },
  scrollContent: { paddingBottom: spacing[6] },
  barLabel: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], flexShrink: 1 },
  barIcon: { width: 18, height: 18 },
  weatherBarPressed: { opacity: 0.78, transform: [{ scale: 0.99 }] },
  exploreBar: { minHeight: 44, marginTop: spacing[2], paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  fieldToolsBar: { minHeight: 44, marginTop: spacing[2], paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.soft, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  heading: { gap: spacing[1], marginTop: spacing[4], marginHorizontal: spacing[1] },
  headingTitle: { fontSize: 28, lineHeight: 34 },
  carousel: { gap: spacing[3], paddingHorizontal: spacing[1], paddingVertical: spacing[4] },
  recommendationStatus: { gap: spacing[1], marginHorizontal: spacing[1], marginVertical: spacing[6], paddingVertical: spacing[4], paddingHorizontal: spacing[4], borderRadius: 16, backgroundColor: color.surface.soft },
  card: { width: 300, height: 340, borderRadius: 24, overflow: 'hidden', backgroundColor: color.surface.soft },
  desktopCard: { flex: 1, width: undefined, minWidth: 0 },
  cardImage: { flex: 1 },
  cardLink: { ...StyleSheet.absoluteFill },
  // S15P21E201-924: width/height를 명시하지 않으면 배경 이미지가 카드 크기(300x340)가
  // 아니라 원본 사진의 실제 픽셀 크기(예: 720x926)로 렌더링되고, 카드는 그 중 왼쪽 위만
  // 잘라서 보여준다. 세로로 긴 사진(광안리·감천)은 왼쪽 위가 하늘뿐이라 내용이 안 보였다.
  cardImageRadius: { borderRadius: 24, width: '100%', height: '100%' },
  cardShadeTop: { ...StyleSheet.absoluteFill, backgroundColor: color.brand.navy, opacity: 0.06 },
  cardShadeBottom: { position: 'absolute', left: 0, right: 0, bottom: 0, height: '42%', backgroundColor: color.brand.navy, opacity: 0.55 },
  cardCounter: { position: 'absolute', right: spacing[4], top: spacing[3], paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.brand.navy },
  heartButton: { position: 'absolute', right: spacing[3], top: 48, width: 40, height: 40, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.primary },
  heartButtonSelected: { backgroundColor: color.brand.orange },
  heartIcon: { width: 18, height: 18 },
  cardCopy: { position: 'absolute', left: spacing[4], right: spacing[4], bottom: spacing[4], gap: spacing[1] },
  cardDescription: { opacity: 0.84 },
  swipeHint: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: spacing[2], marginTop: -spacing[2] },
  swipeArrow: { width: 14, height: 14 },
  carouselButton: { width: 36, height: 36, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  carouselButtonDisabled: { opacity: 0.35 },
  saveFeedback: { minHeight: 44, marginHorizontal: spacing[2], marginTop: spacing[4], paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.brand.navy, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  // 하단 탭 바로 위에 붙는 떠 있는 버튼 묶음 — 챗봇 하나였을 때는 bottom:94로 떨어뜨려
  // 뒀었는데, 그 값이 탭바 높이를 감안 안 해서 화면 중간쯤에 떠 있었다(실기기 확인).
  // 이제 탭바 바로 위(spacing[2])에 붙이고, 그 위로 자주 쓰는 현장 도구 두 개를 쌓는다.
  floatingStack: { position: 'absolute', right: spacing[2], bottom: spacing[2], alignItems: 'flex-end', gap: spacing[2], zIndex: 3 },
  assistantButton: { minWidth: 44, minHeight: 44, flexDirection: 'row', alignItems: 'center' },
  assistantButtonPressed: { opacity: 0.78, transform: [{ scale: 0.96 }] },
  assistantLabel: { marginRight: -spacing[2], paddingLeft: spacing[3], paddingRight: spacing[4], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, shadowColor: color.brand.navy, shadowOpacity: 0.12, shadowRadius: 8, shadowOffset: { width: 0, height: 3 }, elevation: 3 },
  assistantMascot: { width: 58, height: 58 },
});
