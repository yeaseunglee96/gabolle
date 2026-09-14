import AsyncStorage from '@react-native-async-storage/async-storage';
import { useRef, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Redirect, useLocalSearchParams, useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';

const INTRO_SEEN_KEY = '@gabolle/app-intro-seen';
const logo = require('../assets/brand/gabolle-logo-figma.png');
const cameraIcon = require('../assets/icons/common/camera.png');
const PAGES = [
  { id: 'ai-travel', eyebrowKo: 'AI 여행', eyebrowEn: 'AI travel', titleKo: '조건만 알려주면\n일정을 만들어요', titleEn: 'Tell us your conditions,\nwe build the itinerary', descriptionKo: '날짜와 취향, 이동 조건을 반영해 나만의 부산 여행을 구성해요.', descriptionEn: 'We put together your Busan trip using your dates, tastes, and mobility needs.' },
  { id: 'menu-translate', eyebrowKo: '메뉴 번역', eyebrowEn: 'Menu translate', titleKo: '메뉴판을 찍고\n바로 이해해요', titleEn: 'Snap the menu,\nunderstand it instantly', descriptionKo: '카메라를 쓰기 직전에 이유를 설명하고, 허용한 경우에만 촬영해요.', descriptionEn: 'We explain why right before using the camera, and only shoot once you allow it.' },
  { id: 'field-talk', eyebrowKo: '현장 말하기', eyebrowEn: 'Field talk', titleKo: '여행지에서 필요한 말을\n바로 보여주고 들려줘요', titleEn: 'The phrases you need on the road,\nshown and spoken instantly', descriptionKo: '식당과 택시에서 쓸 문장을 크게 보여주거나 한국어 음성으로 들려줘요.', descriptionEn: 'Restaurant and taxi phrases shown in large text or read aloud in Korean.' },
] as const;

export default function AppIntro() {
  const router = useRouter();
  const { tx } = useI18n();
  const { language = 'ko', mobility = 'none' } = useLocalSearchParams<{ language?: string; mobility?: string }>();
  const { width } = useWindowDimensions();
  const [pageWidth, setPageWidth] = useState(width);
  const pager = useRef<ScrollView>(null);
  const [page, setPage] = useState(0);
  const go = (index: number) => {
    const next = Math.max(0, Math.min(PAGES.length - 1, index));
    pager.current?.scrollTo({ x: next * pageWidth, animated: true });
    setPage(next);
  };
  const finish = async () => {
    await AsyncStorage.setItem(INTRO_SEEN_KEY, 'true');
    router.replace({ pathname: '/age-gate', params: { language, mobility } });
  };

  if (isAtLeast(width, 'md')) return <Redirect href="/" />;

  return <SafeAreaView style={styles.screen} onLayout={(event) => setPageWidth(event.nativeEvent.layout.width)}>
    <View style={styles.top}><Pressable accessibilityRole="link" accessibilityLabel={tx('GABOLLE 시작 화면으로 이동', 'Go to the GABOLLE start screen')} onPress={() => router.replace('/')} style={({ pressed }) => [styles.logoButton, pressed && styles.pressed]}><Image source={logo} resizeMode="contain" style={styles.logo} /></Pressable><Pressable accessibilityRole="button" onPress={() => void finish()} style={({ pressed }) => [styles.skip, pressed && styles.pressed]}><Text variant="caption" weight="bold" color={color.text.body}>{tx('건너뛰기', 'Skip')}</Text></Pressable></View>
    {/* S15P21E201-928: snapToInterval + 수동 scrollTo(settle) 조합이 iOS 네이티브 스크롤
        모멘텀과 겹쳐 스와이프가 멈추는 결함으로 실기기에서 보고됐다. 네이티브
        pagingEnabled 하나로 바꾸면 페이지 스냅을 OS가 직접 처리해 이 충돌이 없다. */}
    <ScrollView ref={pager} horizontal pagingEnabled bounces={false} showsHorizontalScrollIndicator={false} decelerationRate="fast" scrollEventThrottle={16} onScroll={(event) => setPage(Math.max(0, Math.min(PAGES.length - 1, Math.round(event.nativeEvent.contentOffset.x / pageWidth))))} onMomentumScrollEnd={(event) => setPage(Math.max(0, Math.min(PAGES.length - 1, Math.round(event.nativeEvent.contentOffset.x / pageWidth))))}>
      {PAGES.map((item, index) => <View key={item.id} style={[styles.page, { width: pageWidth }]}><FeaturePreview index={index} /><View style={styles.copy}><Eyebrow>{tx(item.eyebrowKo, item.eyebrowEn)}</Eyebrow><Text variant="display" weight="bold" style={styles.title}>{tx(item.titleKo, item.titleEn)}</Text><Text variant="body" color={color.text.body} style={styles.description}>{tx(item.descriptionKo, item.descriptionEn)}</Text></View></View>)}
    </ScrollView>
    <View style={styles.footer}><View accessibilityLabel={tx(`${PAGES.length}개 중 ${page + 1}번째`, `${page + 1} of ${PAGES.length}`)} style={styles.dots}>{PAGES.map((item, index) => <View key={item.id} style={[styles.dot, index === page && styles.dotActive]} />)}</View><Button label={page === PAGES.length - 1 ? tx('시작하기', 'Get started') : tx('다음', 'Next')} onPress={() => page === PAGES.length - 1 ? void finish() : go(page + 1)} containerStyle={styles.next} /></View>
  </SafeAreaView>;
}

function FeaturePreview({ index }: { index: number }) {
  const { tx } = useI18n();
  if (index === 0) return <View style={styles.preview}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('어떤 여행을 좋아하세요?', 'What kind of trip do you like?')}</Text><View style={styles.chips}><View style={styles.selectedChip}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('바다', 'Sea')}</Text></View><View style={styles.chip}><Text variant="caption" weight="bold">{tx('미식', 'Food')}</Text></View><View style={styles.chip}><Text variant="caption" weight="bold">{tx('골목', 'Alleys')}</Text></View></View><View style={styles.progress}><View style={styles.progressFill} /></View></View>;
  if (index === 1) return <View style={styles.preview}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('메뉴판 카메라 번역', 'Menu camera translation')}</Text><View style={styles.menuFrame}><Image source={cameraIcon} resizeMode="contain" style={styles.menuFrameIcon} /><Text weight="bold">{tx('메뉴를 화면 안에 맞춰주세요', 'Fit the menu inside the frame')}</Text></View><Text variant="caption" color={color.text.body}>{tx('사진은 번역에만 사용하고 기기에 저장하지 않아요.', 'Photos are used only for translation and are not saved on the device.')}</Text></View>;
  return <View style={styles.ticket}><View style={styles.ticketTop}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('현장 말하기', 'Field talk')}</Text><Text variant="title" weight="bold" color={color.text.onAction}>{tx('사진 한 장 부탁드려도 될까요?', 'Could you take a photo for us?')}</Text></View><View style={styles.ticketBody}><Text variant="caption" color={color.text.muted}>sajin han jang butakdeuryeodo doelkkayo?</Text><View style={styles.dash} /><Text weight="bold" color={color.action.field}>{tx('▶ 한국어로 듣기', '▶ Listen in Korean')}</Text></View></View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: color.brand.ivory },
  // 🔴 marginTop — 전역 언어 배지(GlobalLanguageBadge)가 모든 화면 우측 상단에
  //    절대좌표(top: insets.top + 8)로 떠 있다. 이 줄이 원래 화면 맨 위(marginTop
  //    없음)에 있어서 오른쪽의 "건너뛰기" 버튼과 배지가 같은 자리에 겹쳐, 실사용
  //    리포트로 "각각 누르기 어렵다"는 결함이 나왔다(2026-09-12). home.tsx 의 종 모양
  //    버튼도 같은 이유로 이미 한 번 겹쳤었다(S15P21E201 사용자 리포트) — 같은 값으로
  //    내려 배지 아래로 피한다.
  top: { height: 56, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing[6] },
  logoButton: { minWidth: 100, minHeight: 44, alignItems: 'flex-start', justifyContent: 'center', borderRadius: radius.sm }, logo: { width: 100, height: 24 },
  skip: { minWidth: 64, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full }, pressed: { opacity: 0.65, transform: [{ scale: 0.97 }] },
  page: { flex: 1, justifyContent: 'center', paddingHorizontal: spacing[6], paddingBottom: 140 },
  preview: { minHeight: 210, justifyContent: 'center', gap: spacing[3], padding: spacing[6], borderRadius: 28, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: 0.08, shadowRadius: 16, shadowOffset: { width: 0, height: 8 }, elevation: 4 },
  chips: { flexDirection: 'row', gap: spacing[2] }, chip: { paddingHorizontal: spacing[4], paddingVertical: spacing[2], borderRadius: radius.full, borderWidth: 1, borderColor: '#e3ddd4' }, selectedChip: { paddingHorizontal: spacing[4], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: color.brand.orange },
  progress: { height: 6, overflow: 'hidden', borderRadius: radius.full, backgroundColor: '#eee9e1' }, progressFill: { width: '64%', height: 6, borderRadius: radius.full, backgroundColor: color.brand.orange },
  previewRow: { minHeight: 42, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#e8e2d9' },
  menuFrame: { minHeight: 112, alignItems: 'center', justifyContent: 'center', gap: spacing[2], borderRadius: radius.md, borderWidth: 2, borderStyle: 'dashed', borderColor: color.action.secondary, backgroundColor: color.surface.soft },
  menuFrameIcon: { width: 28, height: 28 },
  ticket: { minHeight: 210, overflow: 'hidden', borderRadius: 28, backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: 0.08, shadowRadius: 16, shadowOffset: { width: 0, height: 8 }, elevation: 4 }, ticketTop: { gap: spacing[2], padding: spacing[6], backgroundColor: color.brand.navy }, ticketBody: { flex: 1, gap: spacing[3], padding: spacing[6] }, dash: { borderTopWidth: 1, borderStyle: 'dashed', borderColor: '#c9c3bb' },
  copy: { gap: spacing[3], marginTop: spacing[6] }, title: { fontSize: 30, lineHeight: 38 }, description: { maxWidth: 330, lineHeight: 24 },
  footer: { position: 'absolute', left: spacing[6], right: spacing[6], bottom: spacing[6], gap: spacing[4] },
  dots: { height: 10, flexDirection: 'row', justifyContent: 'center', gap: spacing[2] }, dot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: '#ded8cf' }, dotActive: { width: 24, backgroundColor: color.brand.orange }, next: { minHeight: 54, borderRadius: radius.full, backgroundColor: color.brand.orange },
});
