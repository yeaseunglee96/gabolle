import { useEffect, useRef, useState } from 'react';
import { Image, ImageBackground, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Redirect, useRouter } from 'expo-router';

import { BrandLogoLink } from '@/components/BrandLogoLink';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, gutter, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { useI18n } from '@/i18n';

const RECOMMENDATIONS = [
  { id: 'haeundae', titleKo: '해운대 해수욕장', titleEn: 'Haeundae Beach', descriptionKo: '푸른 바다와 도시가 만나는 곳', descriptionEn: 'Where the blue sea meets the city', image: require('../../assets/home/haeundae.png') },
  { id: 'gwangalli', titleKo: '광안리 해수욕장', titleEn: 'Gwangalli Beach', descriptionKo: '야경과 함께하는 해변 산책', descriptionEn: 'A beach walk with a night view', image: require('../../assets/home/gwangalli.png') },
  { id: 'gamcheon', titleKo: '감천문화마을', titleEn: 'Gamcheon Culture Village', descriptionKo: '형형색색 감성 골목 여행', descriptionEn: 'A colorful, atmospheric alley trip', image: require('../../assets/home/gamcheon.png') },
] as const;
const bellIcon = require('../../assets/icons/home/bell.png');
const heartIcon = require('../../assets/icons/home/heart.png');
const arrowLeftIcon = require('../../assets/icons/home/arrow-left.png');
const arrowRightIcon = require('../../assets/icons/home/arrow-right.png');
const SAVED_PLACES_KEY = 'gabolle.saved-home-places';

function RecommendationCard({ item, index, liked, onToggleLike, desktop }: {
  item: (typeof RECOMMENDATIONS)[number]; index: number; liked: boolean; onToggleLike: () => void; desktop: boolean;
}) {
  const router = useRouter();
  const { tx } = useI18n();
  const title = tx(item.titleKo, item.titleEn);
  return (
    <View style={[styles.card, desktop && styles.desktopCard]}>
      <ImageBackground source={item.image} resizeMode="cover" style={styles.cardImage} imageStyle={styles.cardImageRadius}>
        <View style={styles.cardShade} />
        <Pressable accessibilityRole="button" accessibilityLabel={tx(`${title} 상세 보기`, `View details for ${title}`)} onPress={() => router.push(`/place/${item.id}`)} style={styles.cardLink} />
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
  const { width } = useLayout();
  const desktop = width >= 768;
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set());
  const carouselRef = useRef<ScrollView>(null);
  const dragStartX = useRef(0);
  const [activeCard, setActiveCard] = useState(0);
  const [saveFeedback, setSaveFeedback] = useState<string | null>(null);
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
  const toggleLike = (id: string) => setLikedIds((current) => {
    const next = new Set(current);
    const saved = !next.has(id);
    saved ? next.add(id) : next.delete(id);
    void AsyncStorage.setItem(SAVED_PLACES_KEY, JSON.stringify([...next]));
    setSaveFeedback(saved ? tx('이 기기에 여행지를 저장했어요.', 'Saved this place on this device.') : tx('이 기기에서 저장을 해제했어요.', 'Unsaved this place on this device.'));
    return next;
  });
  const goToCard = (index: number) => {
    const next = Math.max(0, Math.min(RECOMMENDATIONS.length - 1, index));
    carouselRef.current?.scrollTo({ x: next * 312, animated: true });
    setActiveCard(next);
  };

  if (desktop) {
    return <Redirect href="/" />;
  }

  return (
    <Screen wide style={styles.screenContent}>
      <View style={styles.header}>
        <BrandLogoLink href="/home" imageStyle={styles.logo} />
        <Pressable accessibilityRole="button" accessibilityLabel={tx('알림 확인', 'Check notifications')} onPress={() => router.push('/notifications')} style={styles.bell}><Image source={bellIcon} resizeMode="contain" style={styles.bellIcon} /></Pressable>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('부산 축제 찾아보기', 'Explore Busan festivals')} onPress={() => router.push('/festivals')} style={({ pressed }) => [styles.weatherBar, pressed && styles.weatherBarPressed]}>
          <Text variant="body" weight="medium" color={color.text.heading}>{tx('🌺  내 날짜에 열리는 부산 축제 찾기', '🌺  Find Busan festivals on my dates')}</Text><Text weight="bold" color={color.brand.orange}>›</Text>
        </Pressable>

        <Pressable accessibilityRole="button" accessibilityLabel={tx('지금 갈 곳 찾기', 'Find places to go now')} onPress={() => router.push('/now')} style={({ pressed }) => [styles.nowBar, pressed && styles.weatherBarPressed]}>
          <Text variant="body" weight="medium" color={color.text.onAction}>{tx('🧭  지금 남는 시간, 갈 곳 찾기', '🧭  Got free time now? Find a place')}</Text><Text weight="bold" color={color.text.onAction}>›</Text>
        </Pressable>

        <Pressable accessibilityRole="button" accessibilityLabel={tx('부산 로컬 탐색', 'Explore Busan like a local')} onPress={() => router.push('/explore')} style={({ pressed }) => [styles.exploreBar, pressed && styles.weatherBarPressed]}>
          <Text variant="body" weight="medium" color={color.text.heading}>{tx('🗺️  축제·야시장 등 로컬 8종 둘러보기', '🗺️  Explore 8 local categories')}</Text><Text weight="bold" color={color.brand.orange}>›</Text>
        </Pressable>

        <View style={styles.heading}>
          <Text variant="display" weight="bold" color={color.text.heading} style={styles.headingTitle}>{tx('오늘 어디 가볼래?', 'Where shall we go today?')}</Text>
          <Text variant="body" color={color.text.muted}>{tx('AI가 취향에 맞는 부산 여행을 제안해드려요', 'AI suggests a Busan trip that matches your taste')}</Text>
        </View>

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
          onMomentumScrollEnd={(event) => setActiveCard(Math.max(0, Math.min(RECOMMENDATIONS.length - 1, Math.round(event.nativeEvent.contentOffset.x / 312))))}
        >
          {RECOMMENDATIONS.map((item, index) => <RecommendationCard key={item.id} item={item} index={index} liked={likedIds.has(item.id)} onToggleLike={() => toggleLike(item.id)} desktop={false} />)}
        </ScrollView>

        <View style={styles.swipeHint}>
          <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 여행지', 'Previous place')} disabled={activeCard === 0} onPress={() => goToCard(activeCard - 1)} style={[styles.carouselButton, activeCard === 0 && styles.carouselButtonDisabled]}><Image source={arrowLeftIcon} resizeMode="contain" style={styles.swipeArrow} /></Pressable>
          <Text variant="caption" color={color.text.muted}>{tx(`${activeCard + 1} / ${RECOMMENDATIONS.length} · 한 장씩 넘겨보세요`, `${activeCard + 1} / ${RECOMMENDATIONS.length} · Swipe through one at a time`)}</Text>
          <Pressable accessibilityRole="button" accessibilityLabel={tx('다음 여행지', 'Next place')} disabled={activeCard === RECOMMENDATIONS.length - 1} onPress={() => goToCard(activeCard + 1)} style={[styles.carouselButton, activeCard === RECOMMENDATIONS.length - 1 && styles.carouselButtonDisabled]}><Image source={arrowRightIcon} resizeMode="contain" style={styles.swipeArrow} /></Pressable>
        </View>
        {saveFeedback && <Pressable accessibilityRole="button" accessibilityLabel={tx('저장 안내 닫기', 'Dismiss save notice')} accessibilityLiveRegion="polite" onPress={() => setSaveFeedback(null)} style={styles.saveFeedback}><Text variant="caption" weight="bold" color={color.text.onAction}>{saveFeedback}</Text><Text variant="caption" color={color.text.onAction}>{tx('닫기', 'Dismiss')}</Text></Pressable>}
      </ScrollView>

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

      <View style={styles.tabBar}><TabBar active="home" /></View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  screenContent: { paddingTop: spacing[2], paddingBottom: spacing[3] },
  header: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  logo: { width: 100, height: 22 },
  bell: { width: 40, height: 40, borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  bellIcon: { width: 20, height: 20 },
  scrollContent: { paddingBottom: spacing[6] },
  weatherBar: { minHeight: 44, marginTop: spacing[2], paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.state.warningBg, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  weatherBarPressed: { opacity: 0.78, transform: [{ scale: 0.99 }] },
  nowBar: { minHeight: 44, marginTop: spacing[2], paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.brand.navy, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  exploreBar: { minHeight: 44, marginTop: spacing[2], paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  heading: { gap: spacing[1], marginTop: spacing[4], marginHorizontal: spacing[1] },
  headingTitle: { fontSize: 28, lineHeight: 34 },
  carousel: { gap: spacing[3], paddingHorizontal: spacing[1], paddingVertical: spacing[4] },
  card: { width: 300, height: 340, borderRadius: 24, overflow: 'hidden', backgroundColor: color.surface.soft },
  desktopCard: { flex: 1, width: undefined, minWidth: 0 },
  cardImage: { flex: 1 },
  cardLink: { ...StyleSheet.absoluteFill },
  cardImageRadius: { borderRadius: 24 },
  cardShade: { ...StyleSheet.absoluteFill, backgroundColor: color.brand.navy, opacity: 0.2 },
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
  assistantButton: { position: 'absolute', right: spacing[2], bottom: 94, minWidth: 44, minHeight: 44, flexDirection: 'row', alignItems: 'center', zIndex: 3 },
  assistantButtonPressed: { opacity: 0.78, transform: [{ scale: 0.96 }] },
  assistantLabel: { marginRight: -spacing[2], paddingLeft: spacing[3], paddingRight: spacing[4], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, shadowColor: color.brand.navy, shadowOpacity: 0.12, shadowRadius: 8, shadowOffset: { width: 0, height: 3 }, elevation: 3 },
  assistantMascot: { width: 58, height: 58 },
  tabBar: { marginHorizontal: -gutter, borderRadius: radius.lg, overflow: 'hidden' },
});
