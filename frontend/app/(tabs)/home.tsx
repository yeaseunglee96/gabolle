import { useState } from 'react';
import { ImageBackground, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, gutter, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';

const RECOMMENDATIONS = [
  { id: 'haeundae', title: '해운대 해수욕장', description: '푸른 바다와 도시가 만나는 곳', image: require('../../assets/home/haeundae.png') },
  { id: 'gwangalli', title: '광안리 해수욕장', description: '야경과 함께하는 해변 산책', image: require('../../assets/home/gwangalli.png') },
  { id: 'gamcheon', title: '감천문화마을', description: '형형색색 감성 골목 여행', image: require('../../assets/home/gamcheon.png') },
] as const;

function RecommendationCard({ item, index, liked, onToggleLike, desktop }: {
  item: (typeof RECOMMENDATIONS)[number]; index: number; liked: boolean; onToggleLike: () => void; desktop: boolean;
}) {
  const router = useRouter();
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={`${item.title} 상세 보기`} onPress={() => router.push(`/place/${item.id}`)} style={[styles.card, desktop && styles.desktopCard]}>
      <ImageBackground source={item.image} resizeMode="cover" style={styles.cardImage} imageStyle={styles.cardImageRadius}>
        <View style={styles.cardShade} />
        <View style={styles.cardCounter}><Text variant="caption" weight="bold" color={color.text.onAction}>{index + 1}/3</Text></View>
        <Pressable accessibilityRole="button" accessibilityLabel={liked ? `${item.title} 저장 취소` : `${item.title} 저장`} onPress={(event) => { event.stopPropagation(); onToggleLike(); }} style={styles.heartButton}>
          <Text variant="title" color={color.text.onAction}>{liked ? '♥' : '♡'}</Text>
        </Pressable>
        <View style={styles.cardCopy}>
          <Text variant="title" weight="bold" color={color.text.onAction}>{item.title}</Text>
          <Text variant="caption" color={color.text.onAction} style={styles.cardDescription}>{item.description}</Text>
        </View>
      </ImageBackground>
    </Pressable>
  );
}

export default function Home() {
  const router = useRouter();
  const { width } = useLayout();
  const desktop = width >= 768;
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set());
  const toggleLike = (id: string) => setLikedIds((current) => {
    const next = new Set(current);
    next.has(id) ? next.delete(id) : next.add(id);
    return next;
  });

  return (
    <Screen wide style={styles.screenContent}>
      <View style={styles.header}>
        <BrandLogoLink imageStyle={styles.logo} />
        <Pressable accessibilityRole="button" accessibilityLabel="알림 확인" style={styles.bell}><Text variant="title">♧</Text></Pressable>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <Pressable accessibilityRole="button" style={styles.weatherBar}>
          <Text variant="body" weight="medium" color={color.text.heading}>☀️  오늘 부산은 맑음  23°C</Text>
          <View style={styles.weatherArrow}><Text variant="body" weight="bold" color={color.brand.orange}>›</Text></View>
        </Pressable>

        <View style={styles.heading}>
          <Text variant="hero" weight="bold">오늘 어디 가볼래?</Text>
          <Text variant="body" color={color.text.muted}>AI가 취향에 맞는 부산 여행을 제안해드려요</Text>
        </View>

        {desktop ? (
          <View style={styles.desktopGrid}>
            {RECOMMENDATIONS.map((item, index) => <RecommendationCard key={item.id} item={item} index={index} liked={likedIds.has(item.id)} onToggleLike={() => toggleLike(item.id)} desktop />)}
          </View>
        ) : (
          <ScrollView horizontal snapToInterval={312} decelerationRate="fast" showsHorizontalScrollIndicator={false} contentContainerStyle={styles.carousel}>
            {RECOMMENDATIONS.map((item, index) => <RecommendationCard key={item.id} item={item} index={index} liked={likedIds.has(item.id)} onToggleLike={() => toggleLike(item.id)} desktop={false} />)}
          </ScrollView>
        )}

        {!desktop ? <Text variant="caption" color={color.text.muted} style={styles.swipeHint}>←  스와이프해서 더 보기  →</Text> : null}

        <View style={styles.actions}>
          <Pressable accessibilityRole="button" onPress={() => router.push('/plan/basic')} style={styles.primaryAction}>
            <Text variant="body" weight="bold" color={color.text.onAction}>＋ 여행 만들기</Text>
            <Text variant="caption" color={color.text.onAction}>내 조건에 맞는 부산 일정을 만들어요</Text>
          </Pressable>
          <Pressable accessibilityRole="button" onPress={() => router.push('/trips')} style={styles.secondaryAction}>
            <Text variant="body" weight="bold">내 여행 보기</Text>
            <Text variant="caption" color={color.text.body}>저장한 일정과 지난 여행을 확인해요</Text>
          </Pressable>
        </View>
      </ScrollView>

      <View style={styles.tabBar}><TabBar active="home" /></View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  screenContent: { paddingTop: spacing[2], paddingBottom: spacing[3] },
  header: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  logo: { width: 100, height: 22 },
  bell: { width: 40, height: 40, borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  scrollContent: { paddingBottom: spacing[6] },
  weatherBar: { minHeight: 44, marginTop: spacing[2], paddingLeft: spacing[4], paddingRight: spacing[3], borderRadius: radius.full, backgroundColor: color.state.warningBg, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  weatherArrow: { width: 28, height: 28, borderRadius: radius.full, backgroundColor: color.state.dangerBg, alignItems: 'center', justifyContent: 'center' },
  heading: { gap: spacing[1], marginTop: spacing[4], marginHorizontal: spacing[1] },
  carousel: { gap: spacing[3], paddingHorizontal: spacing[1], paddingVertical: spacing[4] },
  desktopGrid: { flexDirection: 'row', gap: spacing[4], marginTop: spacing[6] },
  card: { width: 300, height: 340, borderRadius: 24, overflow: 'hidden', backgroundColor: color.surface.soft },
  desktopCard: { flex: 1, width: undefined, minWidth: 0 },
  cardImage: { flex: 1 },
  cardImageRadius: { borderRadius: 24 },
  cardShade: { ...StyleSheet.absoluteFill, backgroundColor: color.brand.navy, opacity: 0.2 },
  cardCounter: { position: 'absolute', right: spacing[4], top: spacing[3], paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.brand.navy },
  heartButton: { position: 'absolute', right: spacing[3], top: 48, width: 40, height: 40, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.primary },
  cardCopy: { position: 'absolute', left: spacing[4], right: spacing[4], bottom: spacing[4], gap: spacing[1] },
  cardDescription: { opacity: 0.84 },
  swipeHint: { textAlign: 'center', marginTop: -spacing[2] },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3], marginTop: spacing[6], paddingHorizontal: spacing[1] },
  primaryAction: { flexGrow: 1, flexBasis: 260, borderRadius: radius.lg, backgroundColor: color.brand.orange, padding: spacing[4], gap: spacing[1] },
  secondaryAction: { flexGrow: 1, flexBasis: 260, borderRadius: radius.lg, backgroundColor: color.surface.soft, padding: spacing[4], gap: spacing[1] },
  tabBar: { marginHorizontal: -gutter, borderRadius: radius.lg, overflow: 'hidden' },
});
