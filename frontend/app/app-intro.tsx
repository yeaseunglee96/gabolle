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
  { eyebrow: 'AI TRAVEL', title: '조건만 알려주면\n일정을 만들어요', description: '날짜와 취향, 이동 조건을 반영해 나만의 부산 여행을 구성해요.' },
  { eyebrow: 'MENU TRANSLATE', title: '메뉴판을 찍고\n바로 이해해요', description: '카메라를 쓰기 직전에 이유를 설명하고, 허용한 경우에만 촬영해요.' },
  { eyebrow: 'FIELD TALK', title: '여행지에서 필요한 말을\n바로 보여주고 들려줘요', description: '식당과 택시에서 쓸 문장을 크게 보여주거나 한국어 음성으로 들려줘요.' },
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
  if (index === 1) return <View style={styles.preview}><Text variant="caption" weight="bold" color={color.text.muted}>메뉴판 카메라 번역</Text><View style={styles.menuFrame}><Text variant="title">📷</Text><Text weight="bold">메뉴를 화면 안에 맞춰주세요</Text></View><Text variant="caption" color={color.text.body}>사진은 번역에만 사용하고 기기에 저장하지 않아요.</Text></View>;
  return <View style={styles.ticket}><View style={styles.ticketTop}><Text variant="caption" weight="bold" color={color.text.onAction}>현장 말하기</Text><Text variant="title" weight="bold" color={color.text.onAction}>사진 한 장 부탁드려도 될까요?</Text></View><View style={styles.ticketBody}><Text variant="caption" color={color.text.muted}>sajin han jang butakdeuryeodo doelkkayo?</Text><View style={styles.dash} /><Text weight="bold" color={color.action.field}>▶ 한국어로 듣기</Text></View></View>;
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
  menuFrame: { minHeight: 112, alignItems: 'center', justifyContent: 'center', gap: spacing[2], borderRadius: radius.md, borderWidth: 2, borderStyle: 'dashed', borderColor: color.action.secondary, backgroundColor: color.surface.soft },
  ticket: { minHeight: 210, overflow: 'hidden', borderRadius: 28, backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: 0.08, shadowRadius: 16, shadowOffset: { width: 0, height: 8 }, elevation: 4 }, ticketTop: { gap: spacing[2], padding: spacing[6], backgroundColor: color.brand.navy }, ticketBody: { flex: 1, gap: spacing[3], padding: spacing[6] }, dash: { borderTopWidth: 1, borderStyle: 'dashed', borderColor: '#c9c3bb' },
  copy: { gap: spacing[3], marginTop: spacing[6] }, title: { fontSize: 30, lineHeight: 38 }, description: { maxWidth: 330, lineHeight: 24 },
  footer: { position: 'absolute', left: spacing[6], right: spacing[6], bottom: spacing[6], gap: spacing[4] },
  dots: { height: 10, flexDirection: 'row', justifyContent: 'center', gap: spacing[2] }, dot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: '#ded8cf' }, dotActive: { width: 24, backgroundColor: color.brand.orange }, next: { minHeight: 54, borderRadius: radius.full, backgroundColor: color.brand.orange },
});
