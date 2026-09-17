import AsyncStorage from '@react-native-async-storage/async-storage';
import { useRef, useState } from 'react';
import { Image, ImageBackground, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';

const INTRO_SEEN_KEY = '@gabolle/app-intro-seen';
const logo = require('../assets/brand/gabolle-logo-hd.png');
const INTRO_IMAGES = {
  'ai-travel': require('../assets/home/haeundae.png'),
  'local-discovery': require('../assets/home/gamcheon.png'),
  'field-talk': require('../assets/home/gwangalli.png'),
} as const;
// S15P21E201-907로 메뉴판 카메라 번역 기능 자체를 출시 전 배포에서 뺐다 — 이 온보딩
// 소개 화면에 "메뉴판을 찍고 바로 이해해요" 페이지가 남아 있으면, 앱에 없는 기능을
// 광고하는 셈이라 chat.tsx QUICK_TOOLS와 같은 원칙(실제로 동작하는 화면만 올린다)에
// 어긋난다. 그 페이지를 빼고 실제로 있는 기능 둘만 남긴다.
const PAGES = [
  { id: 'ai-travel', eyebrowKo: 'AI 여행', eyebrowEn: 'AI travel', titleKo: '조건만 알려주면\n일정을 만들어요', titleEn: 'Tell us your conditions,\nwe build the itinerary', descriptionKo: '날짜와 취향, 이동 조건을 반영해 나만의 부산 여행을 구성해요.', descriptionEn: 'We put together your Busan trip using your dates, tastes, and mobility needs.' },
  { id: 'local-discovery', eyebrowKo: '부산 둘러보기', eyebrowEn: 'Explore Busan', titleKo: '장소를 찾고 저장해\n내 여행으로 이어가요', titleEn: 'Find and save places,\nthen add them to your trip', descriptionKo: '이름으로 장소를 찾고, 마음에 든 곳은 부슐랭과 꼭 갈 장소에 담을 수 있어요.', descriptionEn: 'Search places by name, then save favorites or add them as must-visits.' },
  { id: 'field-talk', eyebrowKo: '현장 말하기', eyebrowEn: 'Field talk', titleKo: '여행지에서 필요한 말을\n바로 보여주고 들려줘요', titleEn: 'The phrases you need on the road,\nshown and spoken instantly', descriptionKo: '식당과 택시에서 쓸 문장을 크게 보여주거나 한국어 음성으로 들려줘요.', descriptionEn: 'Restaurant and taxi phrases shown in large text or read aloud in Korean.' },
] as const;

export default function AppIntro() {
  const router = useRouter();
  const { tx } = useI18n();
  const { language = 'ko', mobility = 'none', returnTo } = useLocalSearchParams<{ language?: string; mobility?: string; returnTo?: string }>();
  const { width } = useLayout();
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
    if (returnTo === '/help') { router.replace('/help'); return; }
    router.replace({ pathname: '/age-gate', params: { language, mobility } });
  };

  return <SafeAreaView style={styles.screen}>
    <View style={styles.frame} onLayout={(event) => { const nextWidth = event.nativeEvent.layout.width; setPageWidth(nextWidth); pager.current?.scrollTo({ x: page * nextWidth, animated: false }); }}>
    <View style={styles.top}><Pressable accessibilityRole="link" accessibilityLabel={tx('GABOLLE 시작 화면으로 이동', 'Go to the GABOLLE start screen')} onPress={() => router.replace('/')} style={({ pressed }) => [styles.logoButton, pressed && styles.pressed]}><Image source={logo} resizeMode="contain" style={styles.logo} /></Pressable><Pressable accessibilityRole="button" onPress={() => void finish()} style={({ pressed }) => [styles.skip, pressed && styles.pressed]}><Text variant="caption" weight="bold" color={color.text.body}>{tx('건너뛰기', 'Skip')}</Text></Pressable></View>
    {/* S15P21E201-928: snapToInterval + 수동 scrollTo(settle) 조합이 iOS 네이티브 스크롤
        모멘텀과 겹쳐 스와이프가 멈추는 결함으로 실기기에서 보고됐다. 네이티브
        pagingEnabled 하나로 바꾸면 페이지 스냅을 OS가 직접 처리해 이 충돌이 없다. */}
    <ScrollView ref={pager} horizontal pagingEnabled bounces={false} showsHorizontalScrollIndicator={false} decelerationRate="fast" scrollEventThrottle={16} onScroll={(event) => setPage(Math.max(0, Math.min(PAGES.length - 1, Math.round(event.nativeEvent.contentOffset.x / pageWidth))))} onMomentumScrollEnd={(event) => setPage(Math.max(0, Math.min(PAGES.length - 1, Math.round(event.nativeEvent.contentOffset.x / pageWidth))))}>
      {PAGES.map((item) => <ScrollView key={item.id} style={{ width: pageWidth }} contentContainerStyle={styles.page} showsVerticalScrollIndicator={false}><FeaturePreview id={item.id} /><View style={styles.copy}><Eyebrow>{tx(item.eyebrowKo, item.eyebrowEn)}</Eyebrow><Text variant="display" weight="bold" style={styles.title}>{tx(item.titleKo, item.titleEn)}</Text><Text variant="body" color={color.text.body} style={styles.description}>{tx(item.descriptionKo, item.descriptionEn)}</Text></View></ScrollView>)}
    </ScrollView>
    <View style={styles.footer}><View accessibilityLabel={tx(`${PAGES.length}개 중 ${page + 1}번째`, `${page + 1} of ${PAGES.length}`)} style={styles.dots}>{PAGES.map((item, index) => <View key={item.id} style={[styles.dot, index === page && styles.dotActive]} />)}</View><Button label={page === PAGES.length - 1 ? tx('시작하기', 'Get started') : tx('다음', 'Next')} onPress={() => page === PAGES.length - 1 ? void finish() : go(page + 1)} variant="accent" pill /></View>
    </View>
  </SafeAreaView>;
}

function FeaturePreview({ id }: { id: (typeof PAGES)[number]['id'] }) {
  const { tx } = useI18n();
  return <ImageBackground source={INTRO_IMAGES[id]} resizeMode="cover" imageStyle={styles.previewImage} style={styles.photoPreview}>
    <View style={styles.photoScrim} />
    {id === 'ai-travel' ? <View style={styles.previewPanel}><Text variant="caption" weight="bold" color={color.text.heading}>{tx('어떤 여행을 좋아하세요?', 'What kind of trip do you like?')}</Text><View style={styles.chips}><View style={styles.selectedChip}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('바다', 'Sea')}</Text></View><View style={styles.chip}><Text variant="caption" weight="bold">{tx('미식', 'Food')}</Text></View><View style={styles.chip}><Text variant="caption" weight="bold">{tx('골목', 'Alleys')}</Text></View></View><View style={styles.progress}><View style={styles.progressFill} /></View></View> : null}
    {id === 'local-discovery' ? <><View style={styles.searchPreview}><Text color={color.text.heading}>{tx('감천문화마을', 'Gamcheon Culture Village')}</Text><Text weight="bold" color={color.brand.orange}>⌕</Text></View><View style={styles.placePreview}><View style={styles.placeMark}><Text>📍</Text></View><View style={styles.placeCopy}><Text weight="bold">{tx('감천문화마을', 'Gamcheon Culture Village')}</Text><Text variant="caption" color={color.text.muted}>{tx('부산 사하구', 'Saha-gu, Busan')}</Text></View><View style={styles.savedBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>＋</Text></View></View></> : null}
    {id === 'field-talk' ? <View style={[styles.previewPanel, styles.fieldPanel]}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('현장 말하기', 'Field talk')}</Text><Text variant="title" weight="bold" color={color.text.onAction}>{tx('사진 한 장 부탁드려도 될까요?', 'Could you take a photo for us?')}</Text><Text variant="caption" color={color.text.onDarkMuted}>▶ {tx('한국어로 듣기', 'Listen in Korean')}</Text></View> : null}
  </ImageBackground>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: color.brand.ivory },
  frame: { flex: 1, minHeight: 0, width: '100%', maxWidth: 720, alignSelf: 'center' },
  // 🔴 marginTop — 전역 언어 배지(GlobalLanguageBadge)가 모든 화면 우측 상단에
  //    절대좌표(top: insets.top + 8)로 떠 있다. 이 줄이 원래 화면 맨 위(marginTop
  //    없음)에 있어서 오른쪽의 "건너뛰기" 버튼과 배지가 같은 자리에 겹쳐, 실사용
  //    리포트로 "각각 누르기 어렵다"는 결함이 나왔다(2026-09-12). home.tsx 의 종 모양
  //    버튼도 같은 이유로 이미 한 번 겹쳤었다(S15P21E201 사용자 리포트) — 같은 값으로
  //    내려 배지 아래로 피한다.
  top: { height: 56, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing[6] },
  logoButton: { minWidth: 100, minHeight: 44, alignItems: 'flex-start', justifyContent: 'center', borderRadius: radius.sm }, logo: { width: 100, height: 24 },
  skip: { minWidth: 64, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full }, pressed: { opacity: 0.65, transform: [{ scale: 0.97 }] },
  page: { flexGrow: 1, justifyContent: 'center', paddingHorizontal: spacing[6], paddingVertical: spacing[4] },
  photoPreview: { minHeight: 220, justifyContent: 'flex-end', gap: spacing[3], overflow: 'hidden', padding: spacing[4], borderRadius: 28, backgroundColor: color.surface.soft, shadowColor: color.brand.navy, shadowOpacity: 0.12, shadowRadius: 16, shadowOffset: { width: 0, height: 8 }, elevation: 4 },
  previewImage: { borderRadius: 28 },
  photoScrim: { pointerEvents: 'none', position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, backgroundColor: 'rgba(11,29,58,0.18)' },
  previewPanel: { gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: 'rgba(255,255,255,0.94)' },
  fieldPanel: { backgroundColor: 'rgba(11,29,58,0.92)' },
  searchPreview: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.soft },
  placePreview: { minHeight: 72, flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint }, placeMark: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.card }, placeCopy: { flex: 1, gap: spacing[1] }, savedBadge: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.orange },
  chips: { flexDirection: 'row', gap: spacing[2] }, chip: { paddingHorizontal: spacing[4], paddingVertical: spacing[2], borderRadius: radius.full, borderWidth: 1, borderColor: '#e3ddd4' }, selectedChip: { paddingHorizontal: spacing[4], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: color.brand.orange },
  progress: { height: 6, overflow: 'hidden', borderRadius: radius.full, backgroundColor: '#eee9e1' }, progressFill: { width: '64%', height: 6, borderRadius: radius.full, backgroundColor: color.brand.orange },
  previewRow: { minHeight: 42, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#e8e2d9' },
  copy: { gap: spacing[3], marginTop: spacing[6] }, title: { fontSize: 30, lineHeight: 38 }, description: { maxWidth: 330, lineHeight: 24 },
  footer: { flexShrink: 0, gap: spacing[4], paddingHorizontal: spacing[6], paddingTop: spacing[2], paddingBottom: spacing[6] },
  dots: { height: 10, flexDirection: 'row', justifyContent: 'center', gap: spacing[2] }, dot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: '#ded8cf' }, dotActive: { width: 24, backgroundColor: color.brand.orange },
  // 🔴 next 를 지웠다 (2026-09-17) — age-gate.tsx 와 같은 이유다. 껍데기에 칠한 주황이
  // 남색 버튼 뒤로 삐져나왔다. 지금은 버튼이 `variant="accent" pill` 로 직접 그린다.
});
