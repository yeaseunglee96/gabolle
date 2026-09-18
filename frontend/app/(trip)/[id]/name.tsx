// 이 여행에 이름 붙이기. 시안 `design_handoff_trip_name`.

import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useQuery } from '@tanstack/react-query';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { Skeleton } from '@/components/Skeleton';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import {
  checkTripTitle,
  isModelNamed,
  loadTripNameSuggestions,
  markTripNameAsked,
  planNameStep,
  updateTripTitle,
  type TripNameSource,
} from '@/trip/tripNaming';
import { loadTripItineraries, loadTrips, type TripSummaryDto } from '@/trip/trips';

type Status = 'loading' | 'suggestions' | 'empty' | 'custom' | 'saving' | 'saved';

/** 시안이 정한 상한. 서버는 60자까지 받지만 화면에서는 40자로 끊는다. */
const DRAFT_MAX_LENGTH = 40;

function shortDay(value: string | null | undefined) {
  if (!value) return null;
  const [, month, day] = value.split('-');
  return month && day ? `${month}.${day}` : value;
}

/** 헤더에 보여줄 날짜 한 줄. 모르면 null — 「날짜 미정」 같은 말을 지어내지 않는다. */
function headerRange(trip: TripSummaryDto | null) {
  const start = shortDay(trip?.startDate);
  if (!start) return null;
  const end = shortDay(trip?.endDate);
  return end && end !== start ? `${start} ~ ${end}` : start;
}

function fullRange(trip: TripSummaryDto | null) {
  if (!trip?.startDate) return null;
  return trip.endDate && trip.endDate !== trip.startDate
    ? `${trip.startDate} – ${trip.endDate}`
    : trip.startDate;
}

export default function TripName() {
  const router = useRouter();
  const { id, next } = useLocalSearchParams<{ id?: string; next?: string }>();
  const tripId = id ?? '';
  const { tx } = useI18n();
  const { accessToken, user } = useAuth();

  const [status, setStatus] = useState<Status>('loading');
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [source, setSource] = useState<TripNameSource>('TEMPLATE');
  const [discardedCount, setDiscardedCount] = useState(0);
  const [draft, setDraft] = useState('');
  const [chosen, setChosen] = useState<string | null>(null);
  const [feedback, setFeedback] = useState('');
  // 「직접 쓸게요」에서 뒤로 갔을 때 돌아갈 자리. 후보가 없었으면 빈 화면으로 돌아간다.
  const [beforeCustom, setBeforeCustom] = useState<Status>('empty');

  // 헤더에 날짜를 보여주려면 이 여행이 필요하다. 목록에서 찾는다 — 이 화면만을 위한
  // 단건 조회를 새로 만들지 않는다.
  const tripsQuery = useQuery({
    queryKey: ['trips', user?.userId ?? 'anonymous'],
    queryFn: () => loadTrips(accessToken as string),
    enabled: Boolean(accessToken),
  });
  const trip = tripsQuery.data?.state === 'success'
    ? tripsQuery.data.trips.find((item) => item.tripId === tripId) ?? null
    : null;

  useEffect(() => {
    let alive = true;
    void (async () => {
      const result = await loadTripNameSuggestions(tripId, accessToken);
      if (!alive) return;
      // 실패해도 오류 화면으로 가지 않는다. 이름을 못 지은 것은 사람을 다치게 하지
      // 않으므로, 직접 쓰거나 건너뛸 수 있는 자리로 보낸다. 거짓 후보를 만들지 않는다.
      const next = planNameStep(result);
      setSuggestions(next.suggestions);
      setSource(next.source);
      setDiscardedCount(next.discardedCount);
      setStatus(next.step);
    })();
    return () => { alive = false; };
  }, [tripId, accessToken]);

  // 여기서 남긴다 — 저장했든 건너뛰었든 "한 번 물어봤다" 는 같다. 저장할 때만 남기면
  // 건너뛴 사람에게 일정을 열 때마다 다시 묻게 되고, 그건 건너뛸 수 있다고 말해 놓고
  // 안 놓아주는 것이다.
  useEffect(() => { void markTripNameAsked(tripId); }, [tripId]);

  /** 이름을 안 붙이고 넘어간다. PUT 을 안 보낸다 — 이름 없음이 이미 기본이다. */
  const goOn = async () => {
    // 부른 쪽이 갈 곳을 정해 줬으면 그리로 간다 — 추천 화면에서 끼어든 경우다.
    if (next) { router.replace(next as never); return; }
    const outcome = await loadTripItineraries(tripId, accessToken);
    if (outcome.state === 'success' && outcome.itineraries.length === 1) {
      router.replace(`/trips/${outcome.itineraries[0].itineraryId}/itinerary`);
      return;
    }
    // 일정이 없거나 여럿이면 목록으로 보낸다. 어느 것이 최신인지 서버가 정해 주지 않으므로
    // 여기서 고르지 않는다 — 고르면 사용자가 안 고른 것을 고른 셈이 된다.
    router.replace('/trips');
  };

  const save = async (title: string, back: Status) => {
    setChosen(title);
    setFeedback('');
    setStatus('saving');
    const outcome = await updateTripTitle(tripId, title, accessToken);
    if (outcome.state === 'success') { setChosen(outcome.title); setStatus('saved'); return; }
    setStatus(back);
    setFeedback(outcome.message);
  };

  const checked = checkTripTitle(draft);
  const draftLength = [...draft].length;
  const blankOnly = draft.length > 0 && draft.trim().length === 0;
  const range = headerRange(trip);
  const cardDate = fullRange(trip);

  // ── 공통 조각 ──────────────────────────────────────────────────────────────

  const feedbackBar = feedback ? (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={tx('안내 닫기', 'Dismiss notice')}
      accessibilityLiveRegion="polite"
      onPress={() => setFeedback('')}
      style={styles.feedback}
    >
      <Text variant="caption" weight="bold" color={color.text.onAction}>{feedback}</Text>
      <Text variant="caption" color={color.text.onAction}>{tx('닫기', 'Close')}</Text>
    </Pressable>
  ) : null;

  const header = (
    <View style={styles.header}>
      <Eyebrow>{tx('여행 이름', 'Trip name')}</Eyebrow>
      <Text variant="display" weight="bold">{tx('이 여행에 이름을 붙일까요?', 'Want to name this trip?')}</Text>
      {/* 날짜를 모르면 이 줄을 통째로 안 그린다. 「날짜 미정」은 정보가 아니라 잡음이다. */}
      {range ? (
        <Text color={color.text.body}>
          {tx('붙이지 않으면 여행 카드에는 날짜 ', 'Without a name, the trip card shows ')}
          <Text weight="bold" color={color.text.heading}>{range}</Text>
          {tx('가 보여요.', '.')}
        </Text>
      ) : null}
    </View>
  );

  /** 여행 카드 미리보기 — 목록·홈에서 보게 될 모습 그대로. */
  const previewCard = (title: string | null) => (
    <View style={styles.previewCard}>
      <Text variant="title" weight="bold">{title ?? cardDate ?? tx('날짜 미확인', 'Date unknown')}</Text>
      {title && cardDate ? <Text variant="caption" color={color.text.muted}>{cardDate}</Text> : null}
    </View>
  );

  const skipButton = (
    <Button
      label={tx('건너뛰기 · 날짜로 둘게요', 'Skip — keep the dates')}
      variant="ghost"
      onPress={() => void goOn()}
      containerStyle={styles.bottomButton}
    />
  );

  const writeButton = (variant: 'primary' | 'ghost') => (
    <Button
      label={tx('직접 쓸게요', "I'll write my own")}
      variant={variant}
      onPress={() => { setBeforeCustom(status === 'custom' ? beforeCustom : status); setStatus('custom'); }}
      containerStyle={styles.bottomButton}
    />
  );

  // ── 직접 쓰기 ──────────────────────────────────────────────────────────────

  if (status === 'custom') {
    return <Screen scroll style={styles.canvas}>
      <View style={styles.header}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={tx('뒤로 가기', 'Go back')}
          onPress={() => setStatus(beforeCustom)}
          style={styles.back}
        >
          <Text variant="title">‹</Text>
        </Pressable>
        <Eyebrow>{tx('여행 이름', 'Trip name')}</Eyebrow>
        <Text variant="display" weight="bold">{tx('직접 써 볼까요?', 'Write your own')}</Text>
      </View>

      {feedbackBar}

      <TextInput
        accessibilityLabel={tx('여행 이름', 'Trip name')}
        value={draft}
        onChangeText={setDraft}
        maxLength={DRAFT_MAX_LENGTH}
        placeholder={tx('예: 광안리 노을 보러 간 이틀', 'e.g. Two days chasing the Gwangalli sunset')}
        placeholderTextColor={color.text.muted}
        style={[styles.input, blankOnly && styles.inputDanger]}
      />
      <View style={styles.hintRow}>
        {/* 공백만 넣은 것을 조용히 넘기지 않는다. 그대로 저장하면 제목 칸이 빈 채로
            그려져 카드가 「이름을 잃은 것」처럼 보이는데, 그건 「이름이 없다」와 다른 말이다.
        */}
        <Text variant="caption" color={blankOnly ? color.state.danger : color.text.muted} style={styles.hint}>
          {blankOnly
            ? tx('공백만 있으면 이름이 없는 것과 같아요', 'Spaces only counts as no name')
            : draft.length === 0
              ? tx('비워 두면 이름 없이 날짜가 보여요', 'Leave it empty to keep the dates')
              : tx('앞뒤 공백은 저장할 때 지워져요', 'Leading and trailing spaces are trimmed')}
        </Text>
        <Text variant="caption" color={color.text.muted}>{`${draftLength}/${DRAFT_MAX_LENGTH}`}</Text>
      </View>

      <View style={styles.preview}>
        <Eyebrow>{tx('여행 카드에 이렇게 보여요', 'How the trip card will look')}</Eyebrow>
        {previewCard(checked.ok ? checked.title : null)}
        {checked.ok && checked.title === null ? (
          <Text variant="caption" color={color.text.muted}>{tx('이름이 없어서 날짜가 보여요', 'No name, so the dates show')}</Text>
        ) : null}
      </View>

      <View style={styles.bottom}>
        {checked.ok && checked.title !== null ? (
          <Button
            label={tx('이 이름으로 저장', 'Save this name')}
            onPress={() => void save(checked.title as string, 'custom')}
            containerStyle={styles.bottomButton}
          />
        ) : (
          <Button
            label={tx('이름 없이 · 날짜로 둘게요', 'No name — keep the dates')}
            variant="ghost"
            onPress={() => void goOn()}
            containerStyle={styles.bottomButton}
          />
        )}
      </View>
    </Screen>;
  }

  // ── 저장됨 ────────────────────────────────────────────────────────────────

  if (status === 'saved') {
    return <Screen scroll style={styles.canvas}>
      <View style={styles.header}>
        <Text variant="caption" weight="bold" color={color.state.success}>{tx('저장했어요', 'Saved')}</Text>
        <Text variant="display" weight="bold">{tx('이름을 붙였어요', 'Your trip has a name')}</Text>
        <Text color={color.text.body}>{tx('여행 카드와 홈의 「내 여행」에 이 이름이 보여요.', 'It shows on the trip card and under My trip at home.')}</Text>
      </View>
      {previewCard(chosen)}
      <View style={styles.successNote}>
        <Text variant="caption" weight="bold" color={color.state.success}>
          {tx('이름은 여행 카드에서 언제든 바꾸거나 지울 수 있어요.', 'You can rename or clear it any time from the trip card.')}
        </Text>
      </View>
      <View style={styles.bottom}>
        <Button label={tx('일정 보기', 'View itinerary')} onPress={() => void goOn()} containerStyle={styles.bottomButton} />
      </View>
    </Screen>;
  }

  // ── 받는 중 · 후보 있음 · 후보 없음 · 저장 중 ─────────────────────────────

  const saving = status === 'saving';

  return <Screen scroll style={styles.canvas}>
    {header}
    {feedbackBar}

    <View style={[styles.body, saving && styles.bodySaving]} pointerEvents={saving ? 'none' : 'auto'}>
      {status === 'loading' ? (
        <View accessibilityLiveRegion="polite">
          <Text variant="caption" color={color.text.muted} style={styles.loadingCopy}>
            {tx('일정의 장소를 보고 이름 후보를 짓는 중이에요', 'Reading the places in your itinerary to suggest names')}
          </Text>
          {[0, 1, 2].map((index) => (
            <Skeleton key={index} height={48} radius={radius.md} style={styles.skeleton} />
          ))}
        </View>
      ) : null}

      {status === 'empty' ? (
        <View style={styles.emptyCard}>
          <GabolleMascot state="thinking" style={styles.mascot} />
          <Text variant="title" weight="bold">{tx('이번엔 이름 후보를 못 만들었어요', "We couldn't come up with names this time")}</Text>
          <Text color={color.text.body} style={styles.center}>{tx('직접 써도 되고, 그냥 날짜로 두어도 괜찮아요.', 'Write your own, or just keep the dates.')}</Text>
        </View>
      ) : null}

      {status === 'suggestions' || saving ? (
        <View>
          {/* 여기가 이 화면에서 제일 중요한 갈림이다. 틀로 만든 이름을 「일정을 보고
              지었다」고 말하면, 아무도 안 읽은 것을 읽었다고 하는 것이 된다.
          */}
          {isModelNamed(source) ? (
            <Text variant="eyebrow" weight="bold">{tx('일정의 장소를 보고 지은 이름이에요', 'Named from the places in your itinerary')}</Text>
          ) : (
            <View style={styles.softCard}>
              <Text variant="caption" weight="bold" color={color.text.muted}>{tx('이번엔 이름을 짓지 못했어요', "We couldn't name it this time")}</Text>
              <Text variant="caption" color={color.text.body}>
                {tx('아래는 장소 이름을 정해진 틀에 넣어 만든 후보예요. 마음에 안 들면 직접 써도 돼요.', 'These came from a fixed template using place names. Write your own if none fit.')}
              </Text>
            </View>
          )}

          {/* 지어낸 장소가 섞여 버려진 후보가 있으면 숨기지 않는다. 막혔다는 뜻이다. */}
          {discardedCount > 0 ? (
            <View style={styles.tintCard}>
              <Text variant="caption" color={color.text.body}>
                {tx('이 일정에 없는 장소가 섞인 후보 ', 'We hid ')}
                <Text weight="bold" color={color.text.heading}>{tx(`${discardedCount}개`, `${discardedCount}`)}</Text>
                {tx('는 보여드리지 않았어요.', ' suggestion(s) that mentioned places not in this itinerary.')}
              </Text>
            </View>
          ) : null}

          <View style={styles.list}>
            {suggestions.map((name) => (
              <Pressable
                key={name}
                accessibilityRole="button"
                accessibilityLabel={tx(`${name} 로 정하기`, `Use the name ${name}`)}
                disabled={saving}
                onPress={() => void save(name, 'suggestions')}
                style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
              >
                <Text weight="bold" color={color.text.heading} style={styles.rowName}>{name}</Text>
                <Text variant="caption" weight="bold" color={color.brand.navy} style={styles.rowGo}>{tx('이 이름으로 →', 'Use this →')}</Text>
              </Pressable>
            ))}
          </View>

          <Text variant="caption" color={color.text.muted}>
            {tx('고르면 바로 저장돼요. 나중에 여행 카드에서 바꿀 수 있어요.', 'Tapping saves it right away. You can change it later from the trip card.')}
          </Text>
        </View>
      ) : null}
    </View>

    {saving ? (
      <View accessibilityLiveRegion="polite" style={styles.savingBar}>
        <Text variant="caption" weight="bold" color={color.text.onAction}>
          {tx(`「${chosen ?? ''}」으로 저장하는 중이에요`, `Saving "${chosen ?? ''}"`)}
        </Text>
        <ActivityIndicator color={color.text.onAction} size={16} />
      </View>
    ) : (
      <View style={styles.bottom}>
        {writeButton(status === 'empty' ? 'primary' : 'ghost')}
        {skipButton}
      </View>
    )}
  </Screen>;
}

const styles = StyleSheet.create({
  canvas: { backgroundColor: color.canvas },
  header: { gap: spacing[2], marginBottom: spacing[8] },
  back: {
    width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card,
    alignItems: 'center', justifyContent: 'center', marginBottom: spacing[2],
  },
  body: { gap: spacing[3] },
  bodySaving: { opacity: 0.5 },
  loadingCopy: { marginBottom: spacing[3] },
  skeleton: { marginBottom: spacing[2] },
  emptyCard: {
    backgroundColor: color.surface.card, borderRadius: radius.lg, borderWidth: 1,
    borderColor: color.surface.border, padding: spacing[6], alignItems: 'center', gap: spacing[3],
  },
  mascot: { width: 64, height: 64 },
  center: { textAlign: 'center' },
  softCard: { backgroundColor: color.surface.soft, borderRadius: radius.md, padding: spacing[3], gap: spacing[1] },
  tintCard: { backgroundColor: color.surface.tint, borderRadius: radius.md, padding: spacing[3], marginTop: spacing[3] },
  list: { gap: spacing[2], marginTop: spacing[3], marginBottom: spacing[3] },
  row: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    minHeight: 48, paddingVertical: spacing[3], paddingHorizontal: spacing[4],
    borderRadius: radius.md, backgroundColor: color.surface.card,
    borderWidth: 1, borderColor: color.surface.border,
  },
  rowPressed: { backgroundColor: color.surface.soft },
  rowName: { flexShrink: 1, marginRight: spacing[2] },
  rowGo: { flexShrink: 0 },
  input: {
    minHeight: 48, paddingVertical: spacing[3], paddingHorizontal: spacing[4],
    borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field,
    backgroundColor: color.surface.card, color: color.text.heading,
  },
  inputDanger: { borderColor: color.state.danger },
  hintRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], marginTop: spacing[2] },
  hint: { flexShrink: 1 },
  preview: { marginTop: spacing[6], gap: spacing[2] },
  previewCard: {
    backgroundColor: color.surface.card, borderRadius: radius.md, borderWidth: 1,
    borderColor: color.surface.border, padding: spacing[4], gap: spacing[1],
  },
  successNote: { backgroundColor: color.state.successBg, borderRadius: radius.md, padding: spacing[3], marginTop: spacing[4] },
  bottom: { gap: spacing[2], marginTop: spacing[8] },
  bottomButton: { width: '100%' },
  savingBar: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    backgroundColor: color.brand.navy, borderRadius: radius.md,
    paddingVertical: spacing[3], paddingHorizontal: spacing[4], marginTop: spacing[8],
  },
  feedback: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    backgroundColor: color.brand.navy, borderRadius: radius.md,
    paddingVertical: spacing[3], paddingHorizontal: spacing[4], marginBottom: spacing[4],
  },
});
