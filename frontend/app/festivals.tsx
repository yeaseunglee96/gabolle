import { useCallback, useEffect, useMemo, useState } from 'react';
import { Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { color, radius, spacing } from '@/design/tokens';
import { festivalDisplayTitle, getFestivals, type Festival } from '@/discovery/festivals';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';

type SortMode = 'soon' | 'name';
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function dateInputValue(offsetDays = 0) {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  return date.toISOString().slice(0, 10);
}

export default function Festivals() {
  const router = useRouter();
  const { tx } = useI18n();
  const { width } = useLayout();
  const [from, setFrom] = useState(() => dateInputValue());
  const [to, setTo] = useState(() => dateInputValue(30));
  const [sort, setSort] = useState<SortMode>('soon');
  const [festivals, setFestivals] = useState<Festival[]>([]);
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [errorMessage, setErrorMessage] = useState('');
  const dateValid = ISO_DATE.test(from) && ISO_DATE.test(to) && from <= to;

  const load = useCallback(async () => {
    if (!dateValid) return;
    const controller = new AbortController();
    setState('loading');
    setErrorMessage('');
    try {
      setFestivals(await getFestivals(from, to, controller.signal));
      setState('ready');
    } catch (cause) {
      setFestivals([]);
      setErrorMessage(cause instanceof ApiClientError ? cause.message : tx('축제 정보를 불러오지 못했어요.', 'Could not load festival information.'));
      setState('error');
    }
    return () => controller.abort();
  }, [dateValid, from, to]);

  useEffect(() => { void load(); }, []); // 첫 화면의 기본 기간만 자동 조회한다.

  const sorted = useMemo(() => [...festivals].sort((a, b) => sort === 'soon'
    ? a.startDate.localeCompare(b.startDate)
    : festivalDisplayTitle(a).localeCompare(festivalDisplayTitle(b), 'ko')), [festivals, sort]);

  return <Screen scroll wide style={styles.screen}>
    <View style={styles.topBar}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}><Text variant="title" weight="bold">‹</Text></Pressable>
      <BrandLogoLink href="/home" imageStyle={styles.logo} />
      <View style={styles.spacer} />
    </View>
    <View style={styles.heading}><Eyebrow>{tx('부산 축제', 'Busan festival')}</Eyebrow><Text variant="display" weight="bold">{tx('여행 날짜에 열리는 축제', 'Festivals during your trip dates')}</Text><Text color={color.text.body}>{tx('선택한 기간에 실제로 열리는 축제만 보여드려요.', 'We only show festivals actually running in the period you pick.')}</Text></View>

    <View style={[styles.filterCard, isAtLeast(width, 'md') && styles.filterCardWide]}>
      <View style={styles.dateField}><Text variant="caption" weight="bold">{tx('시작일', 'Start date')}</Text><TextInput accessibilityLabel={tx('축제 조회 시작일', 'Festival search start date')} value={from} onChangeText={setFrom} placeholder="YYYY-MM-DD" maxLength={10} style={[styles.input, !dateValid && styles.inputError]} /></View>
      <View style={styles.dateField}><Text variant="caption" weight="bold">{tx('종료일', 'End date')}</Text><TextInput accessibilityLabel={tx('축제 조회 종료일', 'Festival search end date')} value={to} onChangeText={setTo} placeholder="YYYY-MM-DD" maxLength={10} style={[styles.input, !dateValid && styles.inputError]} /></View>
      <Button label={tx('이 기간으로 조회', 'Search this period')} disabled={!dateValid || state === 'loading'} onPress={() => void load()} containerStyle={[styles.searchButton, styles.primaryAction]} />
      {!dateValid && <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{tx('YYYY-MM-DD 형식으로 시작일이 종료일보다 빠르게 입력해 주세요.', 'Please use YYYY-MM-DD format, with the start date before the end date.')}</Text>}
    </View>

    <View style={styles.sortRow} accessibilityRole="radiogroup">
      {([{ key: 'soon', label: tx('곧 시작순', 'Starting soon') }, { key: 'name', label: tx('이름순', 'By name') }] as const).map((item) => <Pressable key={item.key} accessibilityRole="radio" accessibilityState={{ selected: sort === item.key }} onPress={() => setSort(item.key)} style={[styles.sortButton, sort === item.key && styles.sortSelected]}><Text variant="caption" weight="bold" color={sort === item.key ? color.text.onAction : color.text.heading}>{item.label}</Text></Pressable>)}
    </View>

    {state === 'loading' && <View accessibilityLiveRegion="polite" style={styles.stateCard}><Text variant="title" weight="bold">{tx('축제를 확인하고 있어요', 'Checking festivals')}</Text><Text color={color.text.body}>{tx('선택한 기간과 부산 지역을 기준으로 조회합니다.', 'Searching based on your selected period and the Busan area.')}</Text></View>}
    {state === 'error' && <View accessibilityRole="alert" style={styles.stateCard}><Text variant="title" weight="bold">{tx('불러오지 못했습니다', 'Could not load')}</Text><Text color={color.text.body}>{errorMessage}</Text><Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void load()} /></View>}
    {state === 'ready' && sorted.length === 0 && <View style={styles.stateCard}><Text variant="title" weight="bold">{tx('이 기간에 열리는 축제가 없습니다', 'No festivals run during this period')}</Text><Text color={color.text.body}>{tx('날짜를 바꿔 다시 조회해 보세요. 기간과 무관한 축제는 대신 보여드리지 않아요.', "Try different dates. We don't show festivals outside the period instead.")}</Text></View>}
    {state === 'ready' && sorted.length > 0 && sorted.some((festival) => 'isSample' in festival) && (
      <View style={styles.sampleNotice}><Text variant="caption" weight="bold">{tx('축제 API 연동 전이라 예시 일정을 보여드려요. 실제 날짜와 다를 수 있어요.', "The festival API isn't connected yet, so these are example dates — actual dates may differ.")}</Text></View>
    )}
    {state === 'ready' && sorted.length > 0 && <View style={styles.grid}>{sorted.map((festival) => <View key={festival.placeId} style={[styles.card, isAtLeast(width, 'md') && styles.cardWide]}>
      {festival.imageUrl ? <Image source={{ uri: festival.imageUrl }} resizeMode="cover" style={styles.image} /> : <View style={styles.imageFallback}><Text weight="bold" color={color.brand.orange}>GABOLLE</Text></View>}
      <View style={styles.cardBody}>
        <View style={styles.cardTopRow}>
          <Text variant="caption" weight="bold" color={color.brand.orange}>{festival.startDate} — {festival.endDate}</Text>
          {'isSample' in festival && <View style={styles.sampleBadge}><Text variant="caption" weight="bold">{tx('샘플', 'Sample')}</Text></View>}
        </View>
        <Text variant="title" weight="bold">{tx(festivalDisplayTitle(festival), festival.titleEn ?? festivalDisplayTitle(festival))}</Text><Text color={color.text.body}>{festival.address}</Text><Text variant="caption" color={color.text.muted}>{festival.admissionFee || tx('입장료 정보 확인 필요', 'Admission fee info not available yet')}</Text>
      </View>
    </View>)}</View>}
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] }, logo: { width: 96, height: 28 }, spacer: { width: 44 },
  heading: { gap: spacing[2], marginTop: spacing[4], marginBottom: spacing[6] },
  filterCard: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card }, filterCardWide: { flexDirection: 'row', alignItems: 'flex-end', flexWrap: 'wrap' },
  dateField: { flex: 1, minWidth: 180, gap: spacing[1] }, input: { minHeight: 48, paddingHorizontal: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, color: color.text.heading, backgroundColor: color.brand.ivory }, inputError: { borderColor: color.state.danger }, searchButton: { minWidth: 180, width: undefined },
  primaryAction: { backgroundColor: color.brand.navy },
  sortRow: { flexDirection: 'row', gap: spacing[2], marginVertical: spacing[4] }, sortButton: { minHeight: 40, justifyContent: 'center', paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, backgroundColor: color.surface.card }, sortSelected: { borderColor: color.brand.orange, backgroundColor: color.brand.orange },
  stateCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  sampleNotice: { marginBottom: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[4] }, card: { width: '100%', overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card }, cardWide: { width: '48%' }, image: { width: '100%', height: 180 }, imageFallback: { height: 180, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.tint }, cardBody: { gap: spacing[2], padding: spacing[4] },
  cardTopRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sampleBadge: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.tint },
});
