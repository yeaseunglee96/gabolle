// 여행 이름 바꾸기 · 붙이기 · 지우기 (S15P21E201-1036). 시안 `design_handoff_trip_name_flow`.
//
// 🔴 이 화면이 생긴 이유가 「앱이 못 지키던 약속」이다. 이름을 저장하면 마지막 화면이
// 「이름은 여행 카드에서 언제든 바꾸거나 지울 수 있어요」라고 말하는데, 그 자리가 없었다.
// 서버에는 있었다(빈 값을 보내면 지워진다). 화면만 없었다.
//
// 🔴 지우기를 숨기지 않는다. 이름은 붙이는 것만큼 **지우는 것도 정상 동작**이다 —
// 지우면 카드가 날짜로 돌아가고, 그건 서버와의 계약이다.
//
// phone 은 하단 시트, 넓은 화면은 가운데 모달이다. 내용은 같다.

import { useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, StyleSheet, TextInput, View } from 'react-native';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';
import {
  checkTripTitle,
  isModelNamed,
  loadTripNameSuggestions,
  planNameStep,
  updateTripTitle,
  type TripNameSource,
} from '@/trip/tripNaming';

/** 시안이 정한 상한. 서버는 60자까지 받지만 화면은 40자로 끊는다. */
const DRAFT_MAX_LENGTH = 40;

export type TripNameSheetMode = 'edit' | 'add';

export type TripNameSheetProps = {
  tripId: string;
  /** 지금 붙어 있는 이름. 없으면 null — 「붙이기」로 연다. */
  currentTitle: string | null;
  /** 이름이 없을 때 카드에 보이는 날짜. 모르면 null 이고, 그러면 그 줄을 안 그린다. */
  dateLabel: string | null;
  accessToken: string | null;
  onClose: () => void;
  /** 저장·삭제가 끝났을 때. 서버가 돌려준 이름을 그대로 넘긴다(지웠으면 null). */
  onSaved: (title: string | null) => void;
};

export function TripNameSheet({ tripId, currentTitle, dateLabel, accessToken, onClose, onSaved }: TripNameSheetProps) {
  const { tx } = useI18n();
  const { kind } = useLayout();
  const mode: TripNameSheetMode = (currentTitle ?? '').trim() ? 'edit' : 'add';

  const [draft, setDraft] = useState(currentTitle ?? '');
  const [confirmClear, setConfirmClear] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [source, setSource] = useState<TripNameSource>('TEMPLATE');
  const [discardedCount, setDiscardedCount] = useState(0);

  // 이름이 없을 때만 후보를 받아 온다. 이미 이름이 있는 사람에게 「이런 이름은 어때요」를
  // 들이미는 것은 붙인 이름을 없는 것처럼 대하는 것이다.
  useEffect(() => {
    if (mode !== 'add') return;
    let alive = true;
    void (async () => {
      const result = await loadTripNameSuggestions(tripId, accessToken);
      if (!alive) return;
      const next = planNameStep(result);
      setSuggestions(next.suggestions);
      setSource(next.source);
      setDiscardedCount(next.discardedCount);
    })();
    return () => { alive = false; };
  }, [mode, tripId, accessToken]);

  const checked = checkTripTitle(draft);
  const draftLength = [...draft].length;
  const blankOnly = draft.length > 0 && draft.trim().length === 0;
  const willClear = checked.ok && checked.title === null;

  const commit = async (title: string) => {
    setBusy(true);
    setError('');
    const outcome = await updateTripTitle(tripId, title, accessToken);
    setBusy(false);
    if (outcome.state === 'success') { onSaved(outcome.title); return; }
    setError(outcome.message);
  };

  const hint = blankOnly
    ? tx('공백만 있으면 이름이 없는 것과 같아요', 'Spaces only counts as no name')
    : draft.length === 0
      ? tx('비워 두면 이름이 지워져요', 'Leave it empty to clear the name')
      : tx('앞뒤 공백은 저장할 때 지워져요', 'Leading and trailing spaces are trimmed');

  const body = confirmClear ? (
    // ── 지우기 확인 ────────────────────────────────────────────────────────
    <>
      <Text variant="title" weight="bold">{tx('이름을 지울까요?', 'Clear the name?')}</Text>
      <Text color={color.text.body}>
        {tx(`「${currentTitle ?? ''}」이(가) 지워지고, 카드에는 다시 날짜가 보여요.`, `"${currentTitle ?? ''}" will be removed and the card will show the dates again.`)}
      </Text>
      {/* 🔴 지운 뒤 카드를 미리 보여준다. 「날짜가 보여요」는 말이고, 이건 그 말의 증거다. */}
      <View style={styles.previewCard}>
        <Text variant="caption" color={color.text.muted}>{tx('지운 뒤 카드', 'After clearing')}</Text>
        <Text variant="title" weight="bold">{dateLabel ?? tx('날짜 미확인', 'Date unknown')}</Text>
      </View>
      {error ? <Text variant="caption" color={color.state.danger}>{error}</Text> : null}
      <View style={styles.buttonRow}>
        <Button label={tx('취소', 'Cancel')} variant="ghost" disabled={busy} onPress={() => setConfirmClear(false)} containerStyle={styles.rowButton} />
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ busy, disabled: busy }}
          disabled={busy}
          onPress={() => void commit('')}
          style={[styles.dangerButton, busy && styles.disabled]}
        >
          {busy ? <ActivityIndicator color={color.text.onAction} size={16} /> : <Text weight="bold" color={color.text.onAction}>{tx('지우기', 'Clear')}</Text>}
        </Pressable>
      </View>
    </>
  ) : (
    <>
      <Text variant="title" weight="bold">
        {mode === 'edit' ? tx('이름 바꾸기', 'Rename trip') : tx('이 여행에 이름 붙이기', 'Name this trip')}
      </Text>
      {mode === 'edit' ? (
        <Text color={color.text.body}>{tx('비워 두고 저장하면 이름이 지워지고 카드에 날짜가 보여요.', 'Save it empty to clear the name and show the dates.')}</Text>
      ) : dateLabel ? (
        <Text color={color.text.body}>
          {tx('지금은 카드에 날짜 ', 'Right now the card shows ')}
          <Text weight="bold" color={color.text.heading}>{dateLabel}</Text>
          {tx('가 보여요.', '.')}
        </Text>
      ) : null}

      {/* 이름이 없을 때만 후보를 보여준다. 규칙은 이름 짓기 화면과 같다. */}
      {mode === 'add' && suggestions.length > 0 ? (
        <View style={styles.suggestions}>
          {isModelNamed(source) ? (
            <Text variant="eyebrow" weight="bold">{tx('일정의 장소를 보고 지은 이름이에요', 'Named from the places in your itinerary')}</Text>
          ) : (
            <View style={styles.softCard}>
              <Text variant="caption" weight="bold" color={color.text.muted}>{tx('이번엔 이름을 짓지 못했어요', "We couldn't name it this time")}</Text>
              <Text variant="caption" color={color.text.body}>{tx('아래는 장소 이름을 정해진 틀에 넣어 만든 후보예요.', 'These came from a fixed template using place names.')}</Text>
            </View>
          )}
          {discardedCount > 0 ? (
            <View style={styles.tintCard}>
              <Text variant="caption" color={color.text.body}>
                {tx('이 일정에 없는 장소가 섞인 후보 ', 'We hid ')}
                <Text weight="bold" color={color.text.heading}>{tx(`${discardedCount}개`, `${discardedCount}`)}</Text>
                {tx('는 보여드리지 않았어요.', ' suggestion(s) that mentioned places not in this itinerary.')}
              </Text>
            </View>
          ) : null}
          {suggestions.map((name) => (
            <Pressable
              key={name}
              accessibilityRole="button"
              accessibilityLabel={tx(`${name} 로 정하기`, `Use the name ${name}`)}
              disabled={busy}
              onPress={() => void commit(name)}
              style={({ pressed }) => [styles.suggestionRow, pressed && styles.suggestionPressed]}
            >
              <Text weight="bold" color={color.text.heading} style={styles.suggestionName}>{name}</Text>
              <Text variant="caption" weight="bold" color={color.brand.navy} numberOfLines={1}>{tx('이 이름으로 →', 'Use this →')}</Text>
            </Pressable>
          ))}
        </View>
      ) : null}

      <TextInput
        accessibilityLabel={tx('여행 이름', 'Trip name')}
        value={draft}
        onChangeText={setDraft}
        maxLength={DRAFT_MAX_LENGTH}
        editable={!busy}
        placeholder={mode === 'add' ? tx('직접 쓰기 · 예: 광안리 노을 보러 간 이틀', 'Write your own — e.g. Two days chasing the Gwangalli sunset') : undefined}
        placeholderTextColor={color.text.muted}
        style={[styles.input, blankOnly && styles.inputDanger]}
      />
      <View style={styles.hintRow}>
        <Text variant="caption" color={blankOnly ? color.state.danger : color.text.muted} style={styles.hint}>{hint}</Text>
        <Text variant="caption" color={color.text.muted}>{`${draftLength}/${DRAFT_MAX_LENGTH}`}</Text>
      </View>

      {/* 저장하면 카드가 어떻게 보이는지 미리 보여준다. */}
      <View style={styles.softCard}>
        <Text variant="caption" color={color.text.muted}>{tx('카드에 이렇게', 'On the card')}</Text>
        <Text weight="bold" color={color.text.heading}>
          {checked.ok && checked.title ? checked.title : dateLabel ?? tx('날짜 미확인', 'Date unknown')}
        </Text>
      </View>

      {error ? <Text variant="caption" color={color.state.danger}>{error}</Text> : null}

      <View style={styles.buttonRow}>
        <Button label={tx('취소', 'Cancel')} variant="ghost" disabled={busy} onPress={onClose} containerStyle={styles.rowButton} />
        <Button
          label={willClear
            ? (mode === 'edit' ? tx('지우고 저장', 'Clear and save') : tx('날짜로 둘게요', 'Keep the dates'))
            : tx('저장', 'Save')}
          disabled={busy || !checked.ok}
          onPress={() => (willClear && mode === 'add' ? onClose() : void commit(draft))}
          containerStyle={styles.rowButton}
        />
      </View>

      {/* 🔴 이름이 있을 때만 나온다. 없는 이름을 지우라고 권하지 않는다. */}
      {mode === 'edit' ? (
        <Button
          label={tx('이름 지우기', 'Clear the name')}
          variant="ghost"
          disabled={busy}
          onPress={() => { setError(''); setConfirmClear(true); }}
          containerStyle={styles.clearButton}
        />
      ) : null}
    </>
  );

  return (
    <Modal transparent visible animationType={kind === 'phone' ? 'slide' : 'fade'} onRequestClose={onClose}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={tx('닫기', 'Close')}
        onPress={onClose}
        style={[styles.backdrop, kind === 'phone' ? styles.backdropPhone : styles.backdropWide]}
      >
        {/* 안쪽을 눌렀을 때 닫히지 않게 누름을 여기서 멈춘다. */}
        <Pressable onPress={() => {}} style={kind === 'phone' ? styles.sheet : styles.card}>
          {kind === 'phone' ? <View style={styles.handle} /> : null}
          {body}
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(11,29,58,0.62)' },
  backdropPhone: { justifyContent: 'flex-end' },
  backdropWide: { alignItems: 'center', justifyContent: 'center', padding: spacing[4] },
  sheet: {
    backgroundColor: color.brand.ivory, borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg,
    paddingHorizontal: spacing[6], paddingTop: spacing[6], paddingBottom: spacing[8], gap: spacing[3],
  },
  card: {
    backgroundColor: color.brand.ivory, borderRadius: radius.lg, padding: spacing[6],
    gap: spacing[3], width: '100%', maxWidth: 480,
  },
  handle: { width: 40, height: 4, borderRadius: radius.full, backgroundColor: color.surface.field, alignSelf: 'center', marginBottom: spacing[2] },
  suggestions: { gap: spacing[2] },
  softCard: { backgroundColor: color.surface.soft, borderRadius: radius.md, paddingVertical: spacing[3], paddingHorizontal: spacing[4], gap: spacing[1] },
  tintCard: { backgroundColor: color.surface.tint, borderRadius: radius.md, padding: spacing[3] },
  suggestionRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2],
    minHeight: 48, paddingVertical: spacing[3], paddingHorizontal: spacing[4], borderRadius: radius.md,
    backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border,
  },
  suggestionPressed: { backgroundColor: color.surface.soft },
  suggestionName: { flexShrink: 1 },
  input: {
    minHeight: 48, paddingVertical: spacing[3], paddingHorizontal: spacing[4], borderRadius: radius.md,
    borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, color: color.text.heading,
  },
  inputDanger: { borderColor: color.state.danger },
  hintRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] },
  hint: { flexShrink: 1 },
  previewCard: {
    backgroundColor: color.surface.card, borderRadius: radius.lg, borderWidth: 1,
    borderColor: color.surface.border, padding: spacing[4], gap: spacing[1],
  },
  buttonRow: { flexDirection: 'row', gap: spacing[2] },
  rowButton: { flex: 1 },
  clearButton: { width: '100%' },
  dangerButton: {
    flex: 1, minHeight: 48, borderRadius: radius.md, backgroundColor: color.state.danger,
    alignItems: 'center', justifyContent: 'center',
  },
  disabled: { opacity: 0.4 },
});
