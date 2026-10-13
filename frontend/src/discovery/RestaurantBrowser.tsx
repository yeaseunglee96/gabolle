// 로컬 탐색 「맛집」 갈래의 본문 — 구 칩 한 줄 → 구를 고르면 동 칩 한 줄 → 동을 고르면 기존 장소 카드 격자 (S15P21E201-2051).
//
// 맛집에서는 「내 근처 / 부산 전체」가 아니라 **구·동이 범위를 대신한다.** 그래서 이 갈래를 고르면 explore 화면이 범위 토글을 감춘다.
// 카드 격자는 explore 의 PlaceRows 를 그대로 쓴다(사진·출처 표시·이름 읽는 법까지 한 벌) — 부르는 쪽이 renderRows 로 넘긴다.
import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, gutter, radius, spacing } from '@/design/tokens';
import {
  areaLabel,
  getRestaurantAreas,
  getRestaurants,
  sourceBadgeLabels,
  type RestaurantGu,
  type RestaurantItem,
} from '@/discovery/localRestaurants';
import { useI18n } from '@/i18n';
import { localizeMessage } from '@/i18n/messages';

type Chip = { key: string; label: string; count: number };

/** 구 안의 모든 맛집을 고르는 칸의 열쇠 — 동 이름과 겹치지 않게 서버가 안 쓰는 글자를 쓴다. */
const ALL_DONGS = '*';

/** 칩 한 줄 — 폰에서는 옆으로 밀리는 레일, 넓은 화면에서는 줄바꿈. 갈래 칩(FacetPicker)과 같은 모양이다. */
function ChipRow({ title, chips, selectedKey, onSelect, rail }: {
  title: string;
  chips: Chip[];
  selectedKey: string | null;
  onSelect: (key: string) => void;
  rail: boolean;
}) {
  const nodes = chips.map((chip) => {
    const selected = chip.key === selectedKey;
    return (
      <Pressable
        key={chip.key}
        accessibilityRole="button"
        accessibilityState={{ selected }}
        onPress={() => onSelect(chip.key)}
        style={[styles.chip, selected && styles.chipSelected]}
      >
        <Text weight="bold" numberOfLines={1} color={selected ? color.text.onAction : color.text.heading}>{selected ? '✓ ' : ''}{chip.label}</Text>
        <Text variant="caption" weight="bold" numberOfLines={1} color={selected ? color.text.onAction : color.text.muted} style={styles.chipCount}>{chip.count}</Text>
      </Pressable>
    );
  });
  return (
    <View style={styles.row} accessibilityLabel={title}>
      <Text variant="caption" weight="bold" color={color.text.muted}>{title}</Text>
      {rail
        ? <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.railBleed} contentContainerStyle={styles.rail}>{nodes}</ScrollView>
        : <View style={styles.wrap}>{nodes}</View>}
    </View>
  );
}

/** 카드에 작게 다는 출처 배지 — 어느 공공 목록이 인증한 식당인지. 모르는 낱말뿐이면 아무것도 안 그린다. */
export function RestaurantSourceBadges({ sources }: { sources: readonly string[] | undefined }) {
  const { tx } = useI18n();
  const labels = sourceBadgeLabels(sources ?? [], tx);
  if (!labels.length) return null;
  return (
    <View style={styles.badges}>
      {labels.map((label) => (
        <View key={label} style={styles.badge}>
          <Text variant="caption" weight="bold" color={color.text.body} numberOfLines={1}>{label}</Text>
        </View>
      ))}
    </View>
  );
}

type Loaded<T> = { state: 'loading' } | { state: 'error'; message: string } | { state: 'ready'; value: T };
/** 목록은 «어느 요청의 것인가»를 같이 든다 — 구·동을 바꾼 첫 그림에서 옛 목록이 새 제목 밑에 한 번 비치지 않게. */
type ListState = Loaded<RestaurantItem[]> & { requestKey: string };

export function RestaurantBrowser({ wide, renderRows }: {
  /** 넓은 화면이면 칩이 줄바꿈되고, 아니면 옆으로 밀린다. */
  wide: boolean;
  /** 고른 동의 맛집을 그리는 카드 격자 — explore 의 PlaceRows. */
  renderRows: (items: RestaurantItem[]) => ReactNode;
}) {
  const { tx, language } = useI18n();
  const [areas, setAreas] = useState<Loaded<RestaurantGu[]>>({ state: 'loading' });
  const [gu, setGu] = useState<string | null>(null);
  const [dong, setDong] = useState<string | null>(null);
  const [list, setList] = useState<ListState>({ state: 'loading', requestKey: '' });
  const [reloadKey, setReloadKey] = useState(0);

  const loadAreas = useCallback(async (signal?: AbortSignal) => {
    setAreas({ state: 'loading' });
    try {
      const value = await getRestaurantAreas(signal);
      if (!signal?.aborted) setAreas({ state: 'ready', value });
    } catch (error) {
      if (!signal?.aborted) setAreas({ state: 'error', message: error instanceof Error ? localizeMessage(tx, error.message) : tx('구·동 목록을 불러오지 못했어요.', 'Could not load the district list.') });
    }
  }, [tx]);

  useEffect(() => {
    const controller = new AbortController();
    void loadAreas(controller.signal);
    return () => controller.abort();
  }, [loadAreas]);

  const selectedGu = areas.state === 'ready' ? areas.value.find((entry) => entry.gu === gu) ?? null : null;

  // 구·동을 고르면 그 곳의 맛집을 부른다. 구를 바꾸면 동은 비워서 목록이 옛 구의 것으로 남지 않게 한다.
  const requestKey = selectedGu && dong ? `${selectedGu.gu}/${dong}/${reloadKey}` : '';
  useEffect(() => {
    if (!selectedGu || !dong) return undefined;
    const controller = new AbortController();
    setList({ state: 'loading', requestKey });
    getRestaurants({ gu: selectedGu.gu, dong: dong === ALL_DONGS ? null : dong }, controller.signal)
      .then((page) => { if (!controller.signal.aborted) setList({ state: 'ready', value: page.items, requestKey }); })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setList({ state: 'error', message: error instanceof Error ? localizeMessage(tx, error.message) : tx('맛집을 불러오지 못했어요.', 'Could not load restaurants.'), requestKey });
      });
    return () => controller.abort();
  }, [requestKey, tx]);
  // 다른 요청의 결과는 아직 «불러오는 중»이다.
  const current: Loaded<RestaurantItem[]> = list.requestKey === requestKey ? list : { state: 'loading' };

  if (areas.state === 'loading') {
    return <View accessibilityLiveRegion="polite" style={styles.state}><ActivityIndicator color={color.action.primary} /><Text color={color.text.body}>{tx('구·동을 불러오고 있어요', 'Loading districts')}</Text></View>;
  }
  if (areas.state === 'error') {
    return (
      <View accessibilityRole="alert" style={styles.state}>
        <Text color={color.text.body}>{areas.message}</Text>
        <Button label={tx('다시 시도', 'Try again')} variant="tertiary" compact onPress={() => void loadAreas()} />
      </View>
    );
  }
  if (areas.value.length === 0) {
    return <View style={styles.state}><Text color={color.text.body}>{tx('이곳에는 인증 맛집이 없어요.', 'No certified restaurants here.')}</Text></View>;
  }

  const guChips: Chip[] = areas.value.map((entry) => ({ key: entry.gu, label: areaLabel(entry.gu, language), count: entry.placeCount }));
  const dongChips: Chip[] = selectedGu
    ? [
      { key: ALL_DONGS, label: tx('전체', 'All'), count: selectedGu.placeCount },
      ...selectedGu.dongs.map((entry) => ({ key: entry.dong, label: areaLabel(entry.dong, language), count: entry.placeCount })),
    ]
    : [];

  return (
    <View style={styles.body}>
      <ChipRow
        title={tx('구 고르기', 'Pick a district')}
        chips={guChips}
        selectedKey={gu}
        onSelect={(next) => { setGu(next); setDong(null); }}
        rail={!wide}
      />
      {selectedGu ? (
        <ChipRow title={tx('동 고르기', 'Pick a neighborhood')} chips={dongChips} selectedKey={dong} onSelect={setDong} rail={!wide} />
      ) : null}

      {!selectedGu ? <Text color={color.text.body}>{tx('먼저 구를 골라 주세요.', 'Pick a district first.')}</Text> : null}
      {selectedGu && !dong ? <Text color={color.text.body}>{tx('동을 골라 주세요. 「전체」를 누르면 이 구의 맛집이 모두 나와요.', 'Pick a neighborhood. “All” shows every restaurant in this district.')}</Text> : null}

      {selectedGu && dong ? (
        <View style={styles.listBody}>
          {current.state === 'loading' ? (
            <View accessibilityLiveRegion="polite" style={styles.listState}><ActivityIndicator color={color.action.primary} /><Text color={color.text.body}>{tx('맛집을 불러오고 있어요', 'Loading restaurants')}</Text></View>
          ) : null}
          {current.state === 'error' ? (
            <View accessibilityRole="alert" style={styles.listState}>
              <Text color={color.text.body}>{current.message}</Text>
              <Button label={tx('다시 시도', 'Try again')} variant="tertiary" compact onPress={() => setReloadKey((key) => key + 1)} />
            </View>
          ) : null}
          {current.state === 'ready' && current.value.length === 0 ? <Text color={color.text.body}>{tx('이곳에는 인증 맛집이 없어요.', 'No certified restaurants here.')}</Text> : null}
          {current.state === 'ready' && current.value.length > 0 ? (
            <>
              {/* 어느 곳의 몇 곳인지 — 칩만으로는 목록이 어디 것인지 한 번 더 확인할 데가 없다. */}
              <Text variant="caption" color={color.text.muted}>
                {`${areaLabel(selectedGu.gu, language)} · ${dong === ALL_DONGS ? tx('전체', 'All') : areaLabel(dong, language)} · ${tx(`${current.value.length}곳`, `${current.value.length} places`)}`}
              </Text>
              {renderRows(current.value)}
            </>
          ) : null}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  body: { gap: spacing[3] },
  row: { gap: spacing[2] },
  // 폰 — 칩 줄이 화면 끝까지 흐른다(갈래 칩과 같다). 여백 안에서 잘리면 끝 칩이 반쯤 걸려 「고장」처럼 보인다.
  railBleed: { marginHorizontal: -gutter },
  rail: { gap: spacing[2], paddingHorizontal: gutter },
  // 넓으면 줄바꿈 — 숨는 칩이 없어야 한다.
  wrap: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 44, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  chipSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  chipCount: { opacity: 0.75 },
  state: { gap: spacing[3], marginTop: spacing[2], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  listBody: { gap: spacing[2] },
  listState: { gap: spacing[3], padding: spacing[4], alignItems: 'center' },
  badges: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[1], paddingTop: spacing[1] },
  badge: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, backgroundColor: color.surface.soft },
});
