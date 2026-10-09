// 일정에 장소 더하기 — 넣을 자리를 고르고, 이름으로 찾거나 「저장한 후보」에서 고른다 (S15P21E201-2017).
//
// 전에는 축제 화면에서만 더할 수 있었고 늘 그날 맨 끝에 붙었다(시각은 다시 계산이 정했다). 이제는 일정 화면에서 바로,
// 고른 자리에 넣고 서버가 구간·시각을 그 자리에서 매긴다.
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { otherNameFor } from '@/discovery/localNames';
import { searchPlacesByName } from '@/discovery/places';
import { placeNameForLanguage } from '@/discovery/romanize';
import { resolveSavedPlace } from '@/discovery/savedPlaceCards';
import { isDemoPlaceId, loadSavedPlaceIds } from '@/discovery/savedPlaces';
import { useI18n } from '@/i18n';
import { insertPositionOptions } from '@/plan/itineraryAdjust';
import { SuggestPlaceLink } from '@/discovery/SuggestPlaceLink';
import { ReferenceSightsList } from '@/plan/ReferenceSightsList';

// 「참고 명소」(S15P21E201-2019) — 갈래별 부산 대표 관광지를 한 번에 넣는다.
type Source = 'search' | 'sights' | 'saved';
type Candidate = { placeId: string; title: string; subtitle: string };

const MIN_QUERY = 2;

export function AddPlaceSheet({ visible, stops, accessToken, busy = false, error, onSubmit, onClose, dayPlaceIds = [], startDate, endDate }: {
  visible: boolean;
  /** 그날 곳들의 이름(순서대로) — 「○○ 다음」 자리를 만든다. */
  stops: { title: string }[];
  accessToken: string | null;
  busy?: boolean;
  /** 더하기에 실패한 이유. 창은 닫지 않는다 — 다른 자리·장소로 다시 고를 수 있게. */
  error?: string | null;
  onSubmit: (placeId: string, position: number) => void;
  onClose: () => void;
  /** 그날 이미 있는 장소 번호 — 참고 명소에서 「넣음」으로 그린다. */
  dayPlaceIds?: string[];
  /** 여행 첫날·마지막 날 — 참고 명소의 축제를 그 기간으로 찾는다. */
  startDate?: string | null;
  endDate?: string | null;
}) {
  const { tx, language } = useI18n();
  const positions = insertPositionOptions(stops, tx);
  const [position, setPosition] = useState(positions.length);
  const [source, setSource] = useState<Source>('search');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Candidate[]>([]);
  const [searching, setSearching] = useState(false);
  const [saved, setSaved] = useState<Candidate[] | null>(null);
  const requestRef = useRef(0);

  // 열 때마다 자리를 「맨 끝」으로 — 지난번에 고른 자리가 그사이 사라졌을 수 있다.
  useEffect(() => {
    if (visible) { setPosition(stops.length + 1); setQuery(''); setResults([]); }
  }, [visible, stops.length]);

  useEffect(() => {
    const trimmed = query.trim();
    if (!visible || trimmed.length < MIN_QUERY) { setResults([]); setSearching(false); return; }
    const request = ++requestRef.current;
    const controller = new AbortController();
    setSearching(true);
    const timer = setTimeout(() => {
      searchPlacesByName(trimmed, controller.signal)
        .then((items) => {
          if (request !== requestRef.current) return;
          setResults(items.map((item) => ({ placeId: item.placeId, title: placeNameForLanguage(item.nameKo, otherNameFor(item.nameEn, item.localNames, language), language), subtitle: item.address })));
        })
        .catch(() => { if (request === requestRef.current) setResults([]); })
        .finally(() => { if (request === requestRef.current) setSearching(false); });
    }, 300);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [query, visible, language]);

  useEffect(() => {
    if (!visible || source !== 'saved' || saved !== null) return;
    let alive = true;
    void (async () => {
      const ids = (await loadSavedPlaceIds(accessToken)).filter((id) => !isDemoPlaceId(id)).slice(0, 20);
      const cards = await Promise.all(ids.map((id) => resolveSavedPlace(id, tx, language)));
      if (alive) setSaved(cards.filter((card): card is NonNullable<typeof card> => card !== null).map((card) => ({ placeId: card.placeId, title: card.title, subtitle: card.subtitle })));
    })();
    return () => { alive = false; };
    // tx 는 그릴 때마다 새로 만들어질 수 있어 넣지 않는다 — 넣으면 받는 동안 다시 받기를 되풀이한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, source, saved, accessToken, language]);

  const list = source === 'search' ? results : saved ?? [];
  const loading = source === 'search' ? searching : source === 'saved' ? saved === null : false;

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card} testID="add-place-sheet">
          <Text variant="title" weight="bold">{tx('장소 더하기', 'Add a place')}</Text>

          <Text variant="caption" weight="bold" color={color.text.muted}>{tx('넣을 자리', 'Where')}</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
            {positions.map((option) => {
              const selected = option.position === position;
              return (
                <Pressable key={option.position} testID={`add-place-position-${option.position}`} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => setPosition(option.position)} style={[styles.chip, selected && styles.chipSelected]}>
                  <Text variant="caption" weight="bold" numberOfLines={1} color={selected ? color.text.onAction : color.text.heading}>{option.label}</Text>
                </Pressable>
              );
            })}
          </ScrollView>

          <View style={styles.tabs} accessibilityRole="tablist">
            {([['search', tx('검색', 'Search')], ['sights', tx('참고 명소', 'Top sights')], ['saved', tx('저장한 후보', 'Saved places')]] as const).map(([key, label]) => (
              <Pressable key={key} testID={`add-place-tab-${key}`} accessibilityRole="tab" accessibilityState={{ selected: source === key }} onPress={() => setSource(key)} style={[styles.tab, source === key && styles.tabSelected]}>
                <Text variant="caption" weight="bold" color={source === key ? color.text.onAction : color.text.body}>{label}</Text>
              </Pressable>
            ))}
          </View>

          {source === 'search' ? (
            <View style={styles.field}>
              <Text color={color.text.muted}>⌕</Text>
              <TextInput testID="add-place-query" accessibilityLabel={tx('장소 검색', 'Search places')} value={query} onChangeText={setQuery} autoFocus placeholder={tx('장소 이름 — 예: 광안리', 'Place name — e.g. Gwangalli')} placeholderTextColor={color.text.muted} style={styles.input} />
            </View>
          ) : null}

          {source === 'sights' ? (
            <ScrollView style={styles.list} contentContainerStyle={styles.listContent}>
              <ReferenceSightsList compact startDate={startDate} endDate={endDate} addedPlaceIds={new Set(dayPlaceIds)} busyPlaceId={busy ? '' : null} onAdd={(placeId) => onSubmit(placeId, position)} />
            </ScrollView>
          ) : (
          <ScrollView style={styles.list} contentContainerStyle={styles.listContent}>
            {loading ? <ActivityIndicator color={color.brand.navy} /> : null}
            {!loading && list.length === 0 ? (
              <Text variant="caption" color={color.text.muted}>
                {source === 'saved'
                  ? tx('저장한 후보가 없어요. 장소 화면에서 ♡ 를 눌러 저장해 두세요.', 'No saved places yet. Tap ♡ on a place to save it.')
                  : query.trim().length < MIN_QUERY ? tx('두 글자 이상 입력해 주세요.', 'Type at least two characters.') : tx('찾는 장소가 없어요.', 'No places found.')}
              </Text>
            ) : null}
            {/* 검색해도 없으면 현지인이 직접 제안할 수 있다(S15P21E201-2031). 친 이름을 들고 간다. */}
            {!loading && list.length === 0 && source !== 'saved' && query.trim().length >= MIN_QUERY ? <SuggestPlaceLink tx={tx} name={query} onNavigate={onClose} /> : null}
            {list.map((candidate) => (
              <Pressable key={candidate.placeId} testID={`add-place-pick-${candidate.placeId}`} accessibilityRole="button" disabled={busy} onPress={() => onSubmit(candidate.placeId, position)} style={({ pressed }) => [styles.row, (pressed || busy) && styles.dim]}>
                <View style={styles.rowCopy}>
                  <Text weight="bold" numberOfLines={1}>{candidate.title}</Text>
                  {candidate.subtitle ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{candidate.subtitle}</Text> : null}
                </View>
                <Text variant="caption" weight="bold" color={color.brand.navy}>{busy ? '…' : tx('넣기', 'Add')}</Text>
              </Pressable>
            ))}
          </ScrollView>
          )}

          {error ? <Text variant="caption" weight="bold" color={color.state.danger}>{error}</Text> : null}

          <Pressable testID="add-place-close" accessibilityRole="button" onPress={onClose} style={({ pressed }) => [styles.close, pressed && styles.dim]}>
            <Text weight="bold" color={color.brand.navy}>{tx('닫기', 'Close')}</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  card: { width: '100%', maxWidth: 480, maxHeight: '90%', gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  chips: { gap: spacing[2], paddingVertical: spacing[1] },
  chip: { minHeight: 36, maxWidth: 200, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, justifyContent: 'center' },
  chipSelected: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  tabs: { flexDirection: 'row', gap: spacing[1], padding: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
  tab: { flex: 1, minHeight: 36, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' },
  tabSelected: { backgroundColor: color.action.secondary },
  field: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingHorizontal: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  input: { flex: 1, minWidth: 0, minHeight: 48, color: color.text.heading },
  list: { maxHeight: 320 },
  listContent: { gap: spacing[2] },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 52, paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  rowCopy: { flex: 1, minWidth: 0 },
  close: { alignSelf: 'flex-end', minHeight: 44, paddingHorizontal: spacing[3], justifyContent: 'center' },
  dim: { opacity: 0.6 },
});
