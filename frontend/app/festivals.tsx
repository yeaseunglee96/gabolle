import { useCallback, useEffect, useMemo, useState } from 'react';
import { Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { getFestivals, type Festival } from '@/discovery/festivals';
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
      setErrorMessage(cause instanceof ApiClientError ? cause.message : '축제 정보를 불러오지 못했어요.');
      setState('error');
    }
    return () => controller.abort();
  }, [dateValid, from, to]);

  useEffect(() => { void load(); }, []); // 첫 화면의 기본 기간만 자동 조회한다.

  const sorted = useMemo(() => [...festivals].sort((a, b) => sort === 'soon'
    ? a.startDate.localeCompare(b.startDate)
    : a.title.localeCompare(b.title, 'ko')), [festivals, sort]);

  return <Screen scroll wide style={styles.screen}>
    <View style={styles.topBar}>
      <Pressable accessibilityRole="button" accessibilityLabel="이전 화면으로 이동" onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}><Text variant="title" weight="bold">‹</Text></Pressable>
      <BrandLogoLink href="/home" imageStyle={styles.logo} />
      <View style={styles.spacer} />
    </View>
    <View style={styles.heading}><Text variant="eyebrow" weight="bold">BUSAN FESTIVAL</Text><Text variant="display" weight="bold">여행 날짜에 열리는 축제</Text><Text color={color.text.body}>선택한 기간에 실제로 열리는 축제만 보여드려요.</Text></View>

    <View style={[styles.filterCard, width >= 760 && styles.filterCardWide]}>
      <View style={styles.dateField}><Text variant="caption" weight="bold">시작일</Text><TextInput accessibilityLabel="축제 조회 시작일" value={from} onChangeText={setFrom} placeholder="YYYY-MM-DD" maxLength={10} style={[styles.input, !dateValid && styles.inputError]} /></View>
      <View style={styles.dateField}><Text variant="caption" weight="bold">종료일</Text><TextInput accessibilityLabel="축제 조회 종료일" value={to} onChangeText={setTo} placeholder="YYYY-MM-DD" maxLength={10} style={[styles.input, !dateValid && styles.inputError]} /></View>
      <Button label="이 기간으로 조회" disabled={!dateValid || state === 'loading'} onPress={() => void load()} containerStyle={[styles.searchButton, styles.primaryAction]} />
      {!dateValid && <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>YYYY-MM-DD 형식으로 시작일이 종료일보다 빠르게 입력해 주세요.</Text>}
    </View>

    <View style={styles.sortRow} accessibilityRole="radiogroup">
      {([{ key: 'soon', label: '곧 시작순' }, { key: 'name', label: '이름순' }] as const).map((item) => <Pressable key={item.key} accessibilityRole="radio" accessibilityState={{ selected: sort === item.key }} onPress={() => setSort(item.key)} style={[styles.sortButton, sort === item.key && styles.sortSelected]}><Text variant="caption" weight="bold" color={sort === item.key ? color.text.onAction : color.text.heading}>{item.label}</Text></Pressable>)}
    </View>

    {state === 'loading' && <View accessibilityLiveRegion="polite" style={styles.stateCard}><Text variant="title" weight="bold">축제를 확인하고 있어요</Text><Text color={color.text.body}>선택한 기간과 부산 지역을 기준으로 조회합니다.</Text></View>}
    {state === 'error' && <View accessibilityRole="alert" style={styles.stateCard}><Text variant="title" weight="bold">불러오지 못했습니다</Text><Text color={color.text.body}>{errorMessage}</Text><Button label="다시 시도" variant="ghost" onPress={() => void load()} /></View>}
    {state === 'ready' && sorted.length === 0 && <View style={styles.stateCard}><Text variant="title" weight="bold">이 기간에 열리는 축제가 없습니다</Text><Text color={color.text.body}>날짜를 바꿔 다시 조회해 보세요. 기간과 무관한 축제는 대신 보여드리지 않아요.</Text></View>}
    {state === 'ready' && sorted.length > 0 && <View style={styles.grid}>{sorted.map((festival) => <View key={festival.placeId} style={[styles.card, width >= 760 && styles.cardWide]}>
      {festival.imageUrl ? <Image source={{ uri: festival.imageUrl }} resizeMode="cover" style={styles.image} /> : <View style={styles.imageFallback}><Text weight="bold" color={color.brand.orange}>GABOLLE</Text></View>}
      <View style={styles.cardBody}><Text variant="caption" weight="bold" color={color.brand.orange}>{festival.startDate} — {festival.endDate}</Text><Text variant="title" weight="bold">{festival.title}</Text><Text color={color.text.body}>{festival.address}</Text><Text variant="caption" color={color.text.muted}>{festival.admissionFee || '입장료 정보 확인 필요'}</Text></View>
    </View>)}</View>}
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  topBar: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] }, logo: { width: 96, height: 28 }, spacer: { width: 44 },
  heading: { gap: spacing[2], marginTop: spacing[4], marginBottom: spacing[6] },
  filterCard: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card }, filterCardWide: { flexDirection: 'row', alignItems: 'flex-end', flexWrap: 'wrap' },
  dateField: { flex: 1, minWidth: 180, gap: spacing[1] }, input: { minHeight: 48, paddingHorizontal: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, color: color.text.heading, backgroundColor: color.brand.ivory }, inputError: { borderColor: color.state.danger }, searchButton: { minWidth: 180, width: undefined },
  primaryAction: { backgroundColor: color.brand.navy },
  sortRow: { flexDirection: 'row', gap: spacing[2], marginVertical: spacing[4] }, sortButton: { minHeight: 40, justifyContent: 'center', paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, backgroundColor: color.surface.card }, sortSelected: { borderColor: color.brand.orange, backgroundColor: color.brand.orange },
  stateCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[4] }, card: { width: '100%', overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card }, cardWide: { width: '48%' }, image: { width: '100%', height: 180 }, imageFallback: { height: 180, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.tint }, cardBody: { gap: spacing[2], padding: spacing[4] },
});
