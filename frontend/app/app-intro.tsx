import AsyncStorage from '@react-native-async-storage/async-storage';
import { useRef, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Redirect, useLocalSearchParams, useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

const INTRO_SEEN_KEY = '@gabolle/app-intro-seen';
const logo = require('../assets/brand/gabolle-logo-figma.png');
const PAGES = [
  { eyebrow: 'AI TRAVEL', title: '내 취향으로 만드는\n부산 여행', description: '날짜와 관심사를 알려주면 조건에 맞는 여행 후보를 구성해요.' },
  { eyebrow: 'SAFE ROUTE', title: '이동 부담과 안전 조건도\n꼼꼼하게', description: '보행 거리와 경사, 알레르기처럼 반드시 지켜야 할 조건을 함께 확인해요.' },
  { eyebrow: 'READY TO GO', title: '확인하고 떠나는\n나만의 일정', description: '추천 이유와 이동 순서를 확인하고 부산 여행을 시작해 보세요.' },
] as const;

export default function AppIntro() {
  const router = useRouter();
  const { language = 'ko', mobility = 'none' } = useLocalSearchParams<{ language?: string; mobility?: string }>();
  const { width } = useWindowDimensions();
  const [pageWidth, setPageWidth] = useState(width);
  const pager = useRef<ScrollView>(null);
  const dragStart = useRef(0);
  const latestOffset = useRef(0);
  const settleTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [page, setPage] = useState(0);
  const go = (index: number) => {
    const next = Math.max(0, Math.min(PAGES.length - 1, index));
    pager.current?.scrollTo({ x: next * pageWidth, animated: true });
    setPage(next);
  };
  const settle = (offset = latestOffset.current) => {
    if (settleTimer.current) clearTimeout(settleTimer.current);
    const nearest = Math.max(0, Math.min(PAGES.length - 1, Math.round(offset / pageWidth)));
    pager.current?.scrollTo({ x: nearest * pageWidth, animated: true });
    setPage(nearest);
  };
  const finish = async () => {
    await AsyncStorage.setItem(INTRO_SEEN_KEY, 'true');
    router.replace({ pathname: '/age-gate', params: { language, mobility } });
  };

  if (width >= 768) return <Redirect href="/" />;

  return <SafeAreaView style={styles.screen} onLayout={(event) => setPageWidth(event.nativeEvent.layout.width)}>
    <View style={styles.top}><Pressable accessibilityRole="link" accessibilityLabel="GABOLLE 시작 화면으로 이동" onPress={() => router.replace('/')} style={({ pressed }) => [styles.logoButton, pressed && styles.pressed]}><Image source={logo} resizeMode="contain" style={styles.logo} /></Pressable><Pressable accessibilityRole="button" onPress={() => void finish()} style={({ pressed }) => [styles.skip, pressed && styles.pressed]}><Text variant="caption" weight="bold" color={color.text.body}>건너뛰기</Text></Pressable></View>
    <ScrollView ref={pager} horizontal bounces={false} showsHorizontalScrollIndicator={false} snapToInterval={pageWidth} snapToAlignment="start" disableIntervalMomentum decelerationRate="fast" scrollEventThrottle={16} onScroll={(event) => { latestOffset.current = event.nativeEvent.contentOffset.x; setPage(Math.max(0, Math.min(PAGES.length - 1, Math.round(latestOffset.current / pageWidth)))); }} onScrollBeginDrag={(event) => { dragStart.current = event.nativeEvent.contentOffset.x; if (settleTimer.current) clearTimeout(settleTimer.current); }} onScrollEndDrag={() => { settleTimer.current = setTimeout(() => settle(), 120); }} onMomentumScrollEnd={(event) => settle(event.nativeEvent.contentOffset.x)}>
      {PAGES.map((item, index) => <View key={item.eyebrow} style={[styles.page, { width: pageWidth }]}><FeaturePreview index={index} /><View style={styles.copy}><Text variant="caption" weight="bold" color={color.brand.orange}>{item.eyebrow}</Text><Text variant="display" weight="bold" style={styles.title}>{item.title}</Text><Text variant="body" color={color.text.body} style={styles.description}>{item.description}</Text></View></View>)}
    </ScrollView>
    <View style={styles.footer}><View accessibilityLabel={`${PAGES.length}개 중 ${page + 1}번째`} style={styles.dots}>{PAGES.map((item, index) => <View key={item.eyebrow} style={[styles.dot, index === page && styles.dotActive]} />)}</View><Button label={page === PAGES.length - 1 ? '시작하기' : '다음'} onPress={() => page === PAGES.length - 1 ? void finish() : go(page + 1)} containerStyle={styles.next} /></View>
  </SafeAreaView>;
}

function FeaturePreview({ index }: { index: number }) {
  if (index === 0) return <View style={styles.preview}><Text variant="caption" weight="bold" color={color.text.muted}>어떤 여행을 좋아하세요?</Text><View style={styles.chips}><View style={styles.selectedChip}><Text variant="caption" weight="bold" color={color.text.onAction}>바다</Text></View><View style={styles.chip}><Text variant="caption" weight="bold">미식</Text></View><View style={styles.chip}><Text variant="caption" weight="bold">골목</Text></View></View><View style={styles.progress}><View style={styles.progressFill} /></View></View>;
  if (index === 1) return <View style={styles.preview}><Text variant="caption" weight="bold" color={color.text.muted}>반드시 지킬 조건</Text><View style={styles.previewRow}><Text weight="bold">알레르기</Text><Text variant="caption" color={color.brand.orange}>확인 완료</Text></View><View style={styles.previewRow}><Text weight="bold">보행 거리</Text><Text variant="caption">1km 이내</Text></View><View style={styles.previewRow}><Text weight="bold">경사·계단</Text><Text variant="caption">피하기</Text></View></View>;
  return <View style={styles.ticket}><View style={styles.ticketTop}><Text variant="caption" weight="bold" color={color.text.onAction}>GABOLLE TRIP PASS</Text><Text variant="title" weight="bold" color={color.text.onAction}>부산 1일 여행</Text></View><View style={styles.ticketBody}><Text variant="caption" color={color.text.muted}>오늘의 일정</Text><Text weight="bold">해변 → 골목 → 저녁 식사</Text><View style={styles.dash} /><Text variant="caption" color={color.text.body}>선택한 조건을 반영해 추천 이유와 동선을 확인해요.</Text></View></View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: color.brand.ivory },
  top: { height: 56, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing[6] },
  logoButton: { minWidth: 100, minHeight: 44, alignItems: 'flex-start', justifyContent: 'center', borderRadius: radius.sm }, logo: { width: 100, height: 24 },
  skip: { minWidth: 64, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full }, pressed: { opacity: 0.65, transform: [{ scale: 0.97 }] },
  page: { flex: 1, justifyContent: 'center', paddingHorizontal: spacing[6], paddingBottom: 140 },
  preview: { minHeight: 210, justifyContent: 'center', gap: spacing[3], padding: spacing[6], borderRadius: 28, borderWidth: 1, borderColor: '#eee5da', backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: 0.08, shadowRadius: 16, shadowOffset: { width: 0, height: 8 }, elevation: 4 },
  chips: { flexDirection: 'row', gap: spacing[2] }, chip: { paddingHorizontal: spacing[4], paddingVertical: spacing[2], borderRadius: radius.full, borderWidth: 1, borderColor: '#e3ddd4' }, selectedChip: { paddingHorizontal: spacing[4], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: color.brand.orange },
  progress: { height: 6, overflow: 'hidden', borderRadius: radius.full, backgroundColor: '#eee9e1' }, progressFill: { width: '64%', height: 6, borderRadius: radius.full, backgroundColor: color.brand.orange },
  previewRow: { minHeight: 42, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#e8e2d9' },
  ticket: { minHeight: 210, overflow: 'hidden', borderRadius: 28, backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: 0.08, shadowRadius: 16, shadowOffset: { width: 0, height: 8 }, elevation: 4 }, ticketTop: { gap: spacing[2], padding: spacing[6], backgroundColor: color.brand.navy }, ticketBody: { flex: 1, gap: spacing[3], padding: spacing[6] }, dash: { borderTopWidth: 1, borderStyle: 'dashed', borderColor: '#c9c3bb' },
  copy: { gap: spacing[3], marginTop: spacing[6] }, title: { fontSize: 30, lineHeight: 38 }, description: { maxWidth: 330, lineHeight: 24 },
  footer: { position: 'absolute', left: spacing[6], right: spacing[6], bottom: spacing[6], gap: spacing[4] },
  dots: { height: 10, flexDirection: 'row', justifyContent: 'center', gap: spacing[2] }, dot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: '#ded8cf' }, dotActive: { width: 24, backgroundColor: color.brand.orange }, next: { minHeight: 54, borderRadius: radius.full, backgroundColor: color.brand.orange },
});
