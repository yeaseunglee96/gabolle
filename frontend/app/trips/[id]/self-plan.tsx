// 직접 짜기 — AI 추천 없이 날마다 곳을 골라 넣는다 (S15P21E201-2018) · 참고 명소(S15P21E201-2019).
//
// 들어오는 길: 여행 만들기 확인 표의 「직접 짜기」 → 여행 + 빈 일정(POST /trips/{id}/itineraries/blank) → 여기(id = 일정 번호).
// 곳을 넣으면 서버가 그날 구간·시각을 매긴다(「장소 더하기」와 같은 경로, 자리 = 그날 맨 끝). 빼기·순서 바꾸기도 같은 편집 경로다.
// 「다 짰어요」를 누르면 여느 여행처럼 여행 화면(지도·공유 등)으로 간다 — 이 일정은 다른 일정과 다르지 않다.
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { otherNameFor } from '@/discovery/localNames';
import { searchPlacesByName } from '@/discovery/places';
import { placeNameForLanguage, stopNameForLanguage } from '@/discovery/romanize';
import { useI18n } from '@/i18n';
import { formatDayHeading } from '@/i18n/datetime';
import { localizeMessage } from '@/i18n/messages';
import { useLayout } from '@/layout/useLayout';
import { addItineraryItem, loadItinerary, pollItineraryJob, removeItineraryItem, reorderItineraryDay, stopClock, type ItineraryDto, type ItineraryItemDto } from '@/plan/itinerary';
import { describeAdjustWarnings, formatStay, stayMinutesOf } from '@/plan/itineraryAdjust';
import { ReferenceSightsList } from '@/plan/ReferenceSightsList';

type Found = { placeId: string; title: string; subtitle: string };

export default function SelfPlanScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const itineraryId = id ?? '';
  const { accessToken } = useAuth();
  const { tx, language, locale } = useI18n();
  const { kind } = useLayout();
  const wide = kind !== 'phone';

  const [itinerary, setItinerary] = useState<ItineraryDto | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [dayIndex, setDayIndex] = useState(0);
  const [busyPlaceId, setBusyPlaceId] = useState<string | null>(null);
  const [busyItemId, setBusyItemId] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [found, setFound] = useState<Found[]>([]);
  const [searching, setSearching] = useState(false);
  const requestRef = useRef(0);

  const reload = useCallback(async () => {
    if (!itineraryId) return;
    const next = await loadItinerary(itineraryId, accessToken);
    if (next.state === 'success') { setItinerary(next.itinerary); setLoadError(null); }
    else setLoadError(localizeMessage(tx, next.message));
    // tx 는 넣지 않는다 — 언어가 바뀌어도 다시 받을 까닭이 없다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [itineraryId, accessToken]);
  useEffect(() => { void reload(); }, [reload]);

  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length < 2) { setFound([]); setSearching(false); return; }
    const request = ++requestRef.current;
    const controller = new AbortController();
    setSearching(true);
    const timer = setTimeout(() => {
      searchPlacesByName(trimmed, controller.signal)
        .then((items) => { if (request === requestRef.current) setFound(items.map((item) => ({ placeId: item.placeId, title: placeNameForLanguage(item.nameKo, otherNameFor(item.nameEn, item.localNames, language), language), subtitle: item.address }))); })
        .catch(() => { if (request === requestRef.current) setFound([]); })
        .finally(() => { if (request === requestRef.current) setSearching(false); });
    }, 300);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [query, language]);

  const day = itinerary?.days[dayIndex];
  const items = day?.items ?? [];
  const dayPlaceIds = new Set(items.map((item) => item.placeId));

  const add = async (placeId: string) => {
    if (!itinerary || busyPlaceId) return;
    setBusyPlaceId(placeId); setMessage(null);
    const outcome = await addItineraryItem({ itineraryId: itinerary.id, placeId, dayIndex, baseVersion: itinerary.version, position: items.length + 1, accessToken });
    setBusyPlaceId(null);
    if (outcome.state === 'success') {
      setItinerary({ ...outcome.itinerary, tripId: outcome.itinerary.tripId ?? itinerary.tripId, title: outcome.itinerary.title ?? itinerary.title });
      const warnings = describeAdjustWarnings(outcome.itinerary, outcome.warnings, tx);
      setMessage(warnings[0] ?? tx('넣었어요. 이동 시간과 시각을 맞췄어요.', 'Added. Travel and times are updated.'));
    } else if (outcome.state === 'conflict') { setMessage(localizeMessage(tx, outcome.message)); void reload(); }
    else setMessage(localizeMessage(tx, outcome.message));
  };

  const remove = async (item: ItineraryItemDto) => {
    if (!itinerary || busyItemId) return;
    setBusyItemId(item.id); setMessage(null);
    const accepted = await removeItineraryItem({ itineraryId: itinerary.id, itemId: item.id, baseVersion: itinerary.version, accessToken });
    if (accepted.state === 'accepted') {
      for (;;) {
        const poll = await pollItineraryJob(accepted.jobId, accessToken);
        if (poll.state === 'pending') { await new Promise((resolve) => setTimeout(resolve, 1500)); continue; }
        if (poll.state !== 'succeeded') setMessage(localizeMessage(tx, poll.message));
        break;
      }
      await reload();
    } else setMessage(localizeMessage(tx, accepted.message));
    setBusyItemId(null);
  };

  const move = async (index: number, direction: -1 | 1) => {
    if (!itinerary || busyItemId) return;
    const target = index + direction;
    if (target < 0 || target >= items.length) return;
    const keys = items.map((item) => item.id);
    [keys[index], keys[target]] = [keys[target], keys[index]];
    setBusyItemId(items[index].id); setMessage(null);
    const outcome = await reorderItineraryDay({ itineraryId: itinerary.id, dayIndex, itemKeys: keys, baseVersion: itinerary.version, accessToken });
    setBusyItemId(null);
    if (outcome.state === 'success') setItinerary({ ...outcome.itinerary, tripId: outcome.itinerary.tripId ?? itinerary.tripId, title: outcome.itinerary.title ?? itinerary.title });
    else { setMessage(localizeMessage(tx, outcome.message)); void reload(); }
  };

  if (!itinerary) {
    return (
      <Screen scroll>
        {loadError ? <Text color={color.state.danger}>{loadError}</Text> : <ActivityIndicator color={color.brand.navy} />}
      </Screen>
    );
  }

  const total = itinerary.days.reduce((sum, entry) => sum + entry.items.length, 0);

  const dayPanel = (
    <View style={styles.panel} testID="self-plan-day">
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.days}>
        {itinerary.days.map((entry, index) => {
          const selected = index === dayIndex;
          return (
            <Pressable key={entry.date} testID={`self-plan-day-${index + 1}`} accessibilityRole="tab" accessibilityState={{ selected }} onPress={() => setDayIndex(index)} style={[styles.dayChip, selected && styles.dayChipSelected]}>
              <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.heading}>{tx(`${index + 1}일차`, `Day ${index + 1}`)}</Text>
              <Text variant="caption" color={selected ? color.text.onAction : color.text.muted}>{formatDayHeading(entry.date, locale) ?? entry.date}</Text>
            </Pressable>
          );
        })}
      </ScrollView>

      {items.length === 0 ? (
        <View style={styles.empty}>
          <Text weight="bold">{tx('이 날은 아직 비어 있어요', 'This day is still empty')}</Text>
          <Text variant="caption" color={color.text.body}>{tx('참고 명소에서 고르거나 이름으로 찾아 넣어 보세요. 넣는 순서대로 이동 시간과 시각을 맞춰 드려요.', 'Pick from top sights or search by name. We fit travel time and times in the order you add.')}</Text>
        </View>
      ) : items.map((item, index) => {
        const clock = stopClock(item.startsAt);
        const stay = stayMinutesOf(item);
        const busy = busyItemId === item.id;
        return (
          <View key={item.id} style={styles.stop} testID={`self-plan-stop-${index + 1}`}>
            <Text weight="bold" style={styles.clock}>{clock ?? '--:--'}</Text>
            <View style={styles.stopCopy}>
              <Text weight="bold" numberOfLines={2}>{stopNameForLanguage(item.title, item.nameEn, language)}</Text>
              <Text variant="caption" color={color.text.muted} numberOfLines={1}>
                {[stay != null ? formatStay(stay, tx) : null, item.travelDurationMin != null && index > 0 ? tx(`이동 ${item.travelDurationMin}분`, `Travel ${item.travelDurationMin} min`) : null].filter(Boolean).join(' · ')}
              </Text>
            </View>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('위로', 'Move up')} disabled={index === 0 || busyItemId !== null} onPress={() => void move(index, -1)} style={[styles.icon, (index === 0 || busyItemId !== null) && styles.dim]}><Text weight="bold">▲</Text></Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('아래로', 'Move down')} disabled={index === items.length - 1 || busyItemId !== null} onPress={() => void move(index, 1)} style={[styles.icon, (index === items.length - 1 || busyItemId !== null) && styles.dim]}><Text weight="bold">▼</Text></Pressable>
            <Pressable testID={`self-plan-remove-${index + 1}`} accessibilityRole="button" disabled={busyItemId !== null} onPress={() => void remove(item)} style={[styles.remove, busyItemId !== null && styles.dim]}>
              <Text variant="caption" weight="bold" color={color.state.danger}>{busy ? '…' : tx('빼기', 'Remove')}</Text>
            </Pressable>
          </View>
        );
      })}
      {message ? <Text accessibilityRole="alert" variant="caption" weight="bold" color={color.text.body}>{message}</Text> : null}
    </View>
  );

  const pickPanel = (
    <View style={styles.panel}>
      <Text variant="title" weight="bold">{tx('장소 찾기', 'Find a place')}</Text>
      <View style={styles.field}>
        <Text color={color.text.muted}>⌕</Text>
        <TextInput testID="self-plan-query" accessibilityLabel={tx('장소 검색', 'Search places')} value={query} onChangeText={setQuery} placeholder={tx('장소 이름 — 예: 광안리', 'Place name — e.g. Gwangalli')} placeholderTextColor={color.text.muted} style={styles.input} />
      </View>
      {searching ? <ActivityIndicator color={color.brand.navy} /> : null}
      {!searching && query.trim().length >= 2 && found.length === 0 ? <Text variant="caption" color={color.text.muted}>{tx('찾는 장소가 없어요.', 'No places found.')}</Text> : null}
      {found.map((candidate) => {
        const added = dayPlaceIds.has(candidate.placeId);
        return (
          <View key={candidate.placeId} style={styles.found}>
            <View style={styles.stopCopy}>
              <Text weight="bold" numberOfLines={1}>{candidate.title}</Text>
              {candidate.subtitle ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{candidate.subtitle}</Text> : null}
            </View>
            <Pressable testID={`self-plan-pick-${candidate.placeId}`} accessibilityRole="button" disabled={added || busyPlaceId !== null} onPress={() => void add(candidate.placeId)} style={[styles.addSmall, (added || busyPlaceId !== null) && styles.dim]}>
              <Text variant="caption" weight="bold" color={color.text.onAction}>{added ? tx('넣음', 'Added') : busyPlaceId === candidate.placeId ? '…' : tx('넣기', 'Add')}</Text>
            </Pressable>
          </View>
        );
      })}

      <Text variant="title" weight="bold" style={styles.sightsTitle}>{tx('참고 명소', 'Top sights')}</Text>
      <Text variant="caption" color={color.text.body}>{tx(`「넣기」를 누르면 ${dayIndex + 1}일차 맨 끝에 들어가요.`, `Tap Add to put it at the end of day ${dayIndex + 1}.`)}</Text>
      <ReferenceSightsList startDate={itinerary.days[0]?.date} endDate={itinerary.days[itinerary.days.length - 1]?.date} addedPlaceIds={dayPlaceIds} busyPlaceId={busyPlaceId} onAdd={(placeId) => void add(placeId)} />
    </View>
  );

  return (
    <Screen scroll wide={wide}>
      <View style={styles.head}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => (router.canGoBack() ? router.back() : router.replace('/home'))} style={styles.back}><Text variant="title">‹</Text></Pressable>
        <View style={styles.headCopy}>
          <Text variant="caption" weight="bold" color={color.text.muted}>{tx('직접 짜기', 'Plan it yourself')}</Text>
          <Text variant="title" weight="bold" numberOfLines={1}>{itinerary.title || tx('내 일정', 'My itinerary')}</Text>
        </View>
      </View>
      <View style={[styles.body, wide && styles.bodyWide]}>
        <View style={wide ? styles.colLeft : undefined}>{dayPanel}</View>
        <View style={wide ? styles.colRight : undefined}>{pickPanel}</View>
      </View>
      <View style={styles.done}>
        <Button testID="self-plan-done" label={total === 0 ? tx('나중에 채우기 · 여행 보기', 'Fill in later · view trip') : tx('다 짰어요 · 여행 보기', 'Done · view trip')} onPress={() => router.replace(`/trips/${encodeURIComponent(itineraryId)}/itinerary`)} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  head: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginTop: spacing[4], marginBottom: spacing[4] },
  back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  headCopy: { flex: 1, minWidth: 0 },
  body: { gap: spacing[4] },
  bodyWide: { flexDirection: 'row', alignItems: 'flex-start' },
  colLeft: { flex: 1, minWidth: 0 },
  colRight: { flex: 1, minWidth: 0 },
  panel: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  days: { gap: spacing[2], paddingVertical: spacing[1] },
  dayChip: { minHeight: 48, paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, justifyContent: 'center' },
  dayChipSelected: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  empty: { gap: spacing[1], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  stop: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 56, paddingHorizontal: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.tint },
  clock: { width: 48 },
  stopCopy: { flex: 1, minWidth: 0 },
  icon: { width: 36, height: 36, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  remove: { minHeight: 36, paddingHorizontal: spacing[2], justifyContent: 'center' },
  field: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingHorizontal: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  input: { flex: 1, minWidth: 0, minHeight: 48, color: color.text.heading },
  found: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 52, paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  addSmall: { minHeight: 36, minWidth: 56, paddingHorizontal: spacing[3], borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.secondary },
  sightsTitle: { marginTop: spacing[3] },
  done: { marginTop: spacing[6], marginBottom: spacing[8] },
  dim: { opacity: 0.5 },
});
