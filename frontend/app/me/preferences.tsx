// 마이페이지 › 여행 취향 — 계정에 기억된 여덟 답을 보고·고치고·지운다 (S15P21E201-960).
//
// 🔴 이 화면이 없어서 사용자는 첫 답에 갇혀 있었다. 여행에서 고른 취향은 계정으로 따라
// 올라오는데(백엔드 carryOver) 그것을 고치는 자리가 없었다 — 다리를 다쳤을 때 "경사
// 피하기" 로 답하면 다 나은 뒤에도 평생 언덕을 피해 다니는 추천을 받고, 되돌릴 방법이
// 없었다. 백엔드 PreferenceDefaultsService 주석이 그 상황을 직접 적어 두고 있었다.
//
// 🔴 답 여덟이 두 곳에 나뉘어 산다. 저장 경로도 둘이다.
//
//   세 질문   한 차원(SPEND_PROFILE) 안에 세 값이 든 꾸러미 → 한 줄을 고쳐도 셋을 다 보낸다
//   취향 다섯 차원 다섯 → 고친 한 줄만 보낸다
//
// 그래서 저장 함수를 하나로 합치지 않았다. 합치면 "왜 이쪽만 전부 보내지" 가 사라지고,
// 그 순간 세 질문 중 둘이 조용히 지워지는 길이 열린다.
import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ActivityIndicator, Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import {
  getSpendProfile,
  MEAL_VARIES_CODE,
  putSpendProfile,
  SPEND_QUESTIONS,
  type SpendAnswers,
  type SpendKey,
} from '@/onboarding/spendProfile';
import { FOODS } from '@/plan/foodConflicts';
import {
  countAnswered,
  EMPTY_PREFERENCES,
  loadAccountPreferences,
  PREFERENCES_KEY,
  type AccountPreferences,
} from '@/preferences/accountPreferences';
import {
  describeTasteAnswer,
  putTasteProfile,
  TASTE_KEYS,
  TASTE_LABELS,
  TASTE_QUESTIONS,
  type TasteKey,
  type TasteValue,
} from '@/preferences/tasteProfile';

const TOAST_MS = 3000;

type Saved = AccountPreferences;

/** 펼친 줄 하나. 세 질문과 취향 다섯이 같은 목록에 섞여 있어 어느 쪽인지 함께 들고 다닌다. */
type OpenRow = { group: 'spend'; key: SpendKey } | { group: 'taste'; key: TasteKey };

function sameRow(a: OpenRow | null, b: OpenRow) {
  return a !== null && a.group === b.group && a.key === b.key;
}

// ── 줄 하나 ─────────────────────────────────────────────────────────────────

function Row({ label, current, open, onPress, children }: {
  label: string;
  current: string | null;
  open: boolean;
  onPress: () => void;
  children: React.ReactNode;
}) {
  const { tx } = useI18n();
  return <View style={[styles.row, open && styles.rowOpen]}>
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ expanded: open }}
      onPress={onPress}
      style={styles.rowHead}
    >
      <View style={styles.rowCopy}>
        <Text weight="bold">{label}</Text>
        <Text variant="caption" color={current ? color.text.body : color.text.muted}>
          {current ?? tx('답 안 함', 'Not answered')}
        </Text>
      </View>
      <Text variant="caption" weight="bold" color={color.brand.orange}>
        {current ? tx('수정', 'Edit') : tx('답하기', 'Answer')}
      </Text>
    </Pressable>
    {open && <View style={styles.rowBody}>{children}</View>}
  </View>;
}

function RowActions({ canClear, onClear, onClose }: { canClear: boolean; onClear: () => void; onClose: () => void }) {
  const { tx } = useI18n();
  return <View style={styles.rowActions}>
    {canClear
      ? <Pressable accessibilityRole="button" onPress={onClear} style={styles.clearLink}>
          <Text variant="caption" color={color.text.muted}>{tx('답 지우기', 'Clear answer')}</Text>
        </Pressable>
      : <View />}
    <Pressable accessibilityRole="button" onPress={onClose} style={styles.closeButton}>
      <Text variant="caption" weight="bold" color={color.text.onAction}>{tx('닫기', 'Close')}</Text>
    </Pressable>
  </View>;
}

// ── 컨트롤 ──────────────────────────────────────────────────────────────────
//
// 온보딩(taste-profile.tsx)과 같은 모양이지만 크기가 다르다 — 거기는 한 화면에 문항이
// 하나뿐이라 크게 쓰고, 여기는 줄 안에서 펼쳐지므로 작다. 그래서 공용으로 빼지 않았다.

function Scale({ label, value, low, high, onChange }: { label: string; value: number | undefined; low: string; high: string; onChange: (value: number) => void }) {
  const { tx } = useI18n();
  return <View accessibilityRole="radiogroup" accessibilityLabel={label}>
    <View style={styles.scaleEnds}>
      <Text variant="caption" color={color.text.muted}>{low}</Text>
      <Text variant="caption" color={color.text.muted}>{high}</Text>
    </View>
    <View style={styles.scaleTrack}>
      {[1, 2, 3, 4, 5].map((point) => (
        <Pressable
          key={point}
          accessibilityRole="radio"
          accessibilityLabel={tx(`${label} ${point}단계`, `${label} level ${point}`)}
          accessibilityState={{ selected: value === point }}
          onPress={() => onChange(point)}
          style={[styles.scalePoint, value === point && styles.scalePointSelected]}
        >
          <Text weight="bold" color={value === point ? color.text.onAction : color.text.body}>{point}</Text>
        </Pressable>
      ))}
    </View>
  </View>;
}

function CardChoice({ options, value, onChange }: {
  options: { code: string; label: string; desc?: string }[];
  value: string | undefined;
  onChange: (code: string) => void;
}) {
  return <View style={styles.cardChoices}>
    {options.map((option) => (
      <Pressable
        key={option.code}
        accessibilityRole="radio"
        accessibilityLabel={option.label}
        accessibilityState={{ selected: value === option.code }}
        onPress={() => onChange(option.code)}
        style={[styles.cardChoice, value === option.code && styles.cardChoiceSelected]}
      >
        <Text weight="bold">{option.label}</Text>
        {option.desc ? <Text variant="caption" color={color.text.body} style={styles.cardChoiceDesc}>{option.desc}</Text> : null}
      </Pressable>
    ))}
  </View>;
}

function Chips({ options, values, onChange }: { options: { code: string; label: string }[]; values: string[]; onChange: (values: string[]) => void }) {
  return <View style={styles.chips}>
    {options.map((option) => {
      const selected = values.includes(option.code);
      return <Pressable
        key={option.code}
        accessibilityRole="checkbox"
        accessibilityState={{ checked: selected }}
        onPress={() => onChange(selected ? values.filter((code) => code !== option.code) : [...values, option.code])}
        style={[styles.chip, selected && styles.chipSelected]}
      >
        <Text weight="bold" color={selected ? color.text.onAction : color.text.heading}>{option.label}</Text>
      </Pressable>;
    })}
  </View>;
}

// ── 화면 ────────────────────────────────────────────────────────────────────

export default function MePreferences() {
  const router = useRouter();
  const { tx } = useI18n();
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState<OpenRow | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [clearConfirm, setClearConfirm] = useState(false);
  const [saving, setSaving] = useState(false);

  const query = useQuery({
    queryKey: PREFERENCES_KEY,
    enabled: Boolean(accessToken),
    queryFn: () => loadAccountPreferences(accessToken),
  });

  const saved: Saved = query.data ?? EMPTY_PREFERENCES;
  const answeredCount = countAnswered(saved);

  const showToast = (message: string) => {
    setToast(message);
    setTimeout(() => setToast((current) => (current === message ? null : current)), TOAST_MS);
  };

  const write = async (next: Saved, work: () => Promise<unknown>, message: string) => {
    if (saving) return;
    setSaving(true);
    // 고른 것이 바로 보이게 먼저 화면을 바꾼다. 실패하면 서버에서 다시 읽어 되돌린다 —
    // 눌렀는데 아무 일도 안 일어나는 것이 가장 나쁘다.
    queryClient.setQueryData<Saved>(PREFERENCES_KEY, next);
    try {
      await work();
      showToast(message);
    } catch {
      await queryClient.invalidateQueries({ queryKey: PREFERENCES_KEY });
      showToast(tx('저장하지 못했어요. 잠시 뒤 다시 해주세요.', 'Could not save. Please try again in a moment.'));
    } finally {
      setSaving(false);
    }
  };

  const savedMessage = tx('저장했어요. 다음 여행부터 이 답으로 시작해요.', 'Saved. Your next trip starts with this.');

  const setSpend = (key: SpendKey, code: string) => {
    const spend = { ...saved.spend, [key]: code };
    void write({ ...saved, spend }, () => putSpendProfile(spend, accessToken), savedMessage);
  };

  const clearSpend = (key: SpendKey, label: string) => {
    const spend = { ...saved.spend };
    delete spend[key];
    // 🔴 세 질문은 한 꾸러미라 지울 때도 **남은 것 전부**를 다시 보낸다. 지운 것만 보내면
    //    나머지 둘이 같이 사라진다.
    void write({ ...saved, spend }, () => putSpendProfile(spend, accessToken),
      tx(`${label} 답을 지웠어요. 다음 여행에서 다시 물어요.`, `Cleared ${label}. We will ask again on your next trip.`));
  };

  const setTaste = (key: TasteKey, value: TasteValue) => {
    const taste = { ...saved.taste, [key]: value };
    void write({ ...saved, taste }, () => putTasteProfile({ [key]: value }, accessToken), savedMessage);
  };

  const clearTaste = (key: TasteKey, label: string) => {
    const taste = { ...saved.taste };
    delete taste[key];
    // 🔴 지우기는 null 로 **명시해서** 보낸다. 키를 빼고 보내면 서버는 "안 보냈다" 로 읽어
    //    그대로 둔다 — 지운 것처럼 보이지만 안 지워진다.
    void write({ ...saved, taste }, () => putTasteProfile({ [key]: null }, accessToken),
      tx(`${label} 답을 지웠어요. 다음 여행에서 다시 물어요.`, `Cleared ${label}. We will ask again on your next trip.`));
  };

  const clearAll = () => {
    setClearConfirm(false);
    void write({ spend: {}, taste: {} }, async () => {
      await putSpendProfile({}, accessToken);
      await putTasteProfile(Object.fromEntries(TASTE_KEYS.map((key) => [key, null])), accessToken);
    }, tx('기억된 취향을 모두 지웠어요.', 'Cleared every saved preference.'));
  };

  const toggle = (row: OpenRow) => setOpen((current) => (sameRow(current, row) ? null : row));

  if (query.isPending) {
    return <Screen style={styles.centerScreen}><ActivityIndicator color={color.brand.orange} /></Screen>;
  }

  return <Screen scroll style={styles.screen}>
    <View style={styles.header}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로', 'Back')} onPress={() => router.back()} style={styles.back}>
        <Text weight="bold">‹</Text>
      </Pressable>
      <View style={styles.headerCopy}>
        <Eyebrow>{tx('내 계정', 'My account')}</Eyebrow>
        <Text variant="display" weight="bold">{tx('여행 취향', 'Travel preferences')}</Text>
      </View>
    </View>

    <Text color={color.text.body} style={styles.intro}>
      {tx('여행을 만들 때 이 답이 미리 채워져요. 여기서 고치면 다음 여행부터 바뀌어요.',
        'These are filled in when you plan a trip. Changes here apply from your next trip.')}
    </Text>

    {answeredCount === 0 && <View style={styles.emptyCard}>
      <Image source={require('../../assets/mascot/dongbaek-thinking.png')} style={styles.emptyMascot} resizeMode="contain" />
      <Text variant="title" weight="bold">{tx('아직 기억된 취향이 없어요', 'No preferences saved yet')}</Text>
      <Text variant="caption" color={color.text.body} style={styles.emptyBody}>
        {tx('처음에 건너뛰셨어요. 지금 답하면 여행을 만들 때 미리 채워 드려요.',
          'You skipped these at the start. Answer now and we will fill them in when you plan.')}
      </Text>
      <Button
        label={tx('8개 답하기 · 약 1분', 'Answer 8 questions · about a minute')}
        containerStyle={styles.emptyCta}
        onPress={() => setOpen({ group: 'spend', key: SPEND_QUESTIONS[0].key })}
      />
    </View>}

    <Eyebrow>{tx('여행 스타일 세 질문', 'Three questions about your style')}</Eyebrow>
    <View style={styles.group}>
      {SPEND_QUESTIONS.map((question) => {
        const row: OpenRow = { group: 'spend', key: question.key };
        const code = saved.spend[question.key];
        const chosen = question.options.find((option) => option.code === code);
        const label = tx(question.titleKo('USER'), question.titleEn('USER'));
        const shortLabel = tx(...SPEND_ROW_LABEL[question.key]);
        const current = code === MEAL_VARIES_CODE
          ? tx(question.skipLabelKo, question.skipLabelEn)
          : chosen ? tx(chosen.labelKo, chosen.labelEn) : null;
        return <Row key={question.key} label={shortLabel} current={current} open={sameRow(open, row)} onPress={() => toggle(row)}>
          <Text weight="bold" style={styles.openTitle}>{label}</Text>
          <CardChoice
            value={code}
            onChange={(next) => setSpend(question.key, next)}
            options={[
              ...question.options.map((option) => ({
                code: option.code,
                label: tx(option.labelKo, option.labelEn),
                desc: option.descKo ? tx(option.descKo, option.descEn ?? '') : undefined,
              })),
              // 🔴 「그날그날 달라요」는 건너뛰기가 아니라 **답**이다. 저장되고, 계산에서만
              //    빠진다(spendProfile.ts 의 VARIES 주석). 목록에서 빼면 그 사람은 셋 중
              //    아무것도 고를 수 없게 된다.
              ...(question.key === 'meal' ? [{ code: MEAL_VARIES_CODE, label: tx(question.skipLabelKo, question.skipLabelEn) }] : []),
            ]}
          />
          <RowActions canClear={code !== undefined} onClear={() => clearSpend(question.key, shortLabel)} onClose={() => setOpen(null)} />
        </Row>;
      })}
    </View>

    <Eyebrow>{tx('취향 다섯', 'Five travel tastes')}</Eyebrow>
    <View style={styles.group}>
      {TASTE_QUESTIONS.map((question) => {
        const row: OpenRow = { group: 'taste', key: question.key };
        const described = describeTasteAnswer(question.key, saved.taste);
        const shortLabel = tx(TASTE_LABELS[question.key].ko, TASTE_LABELS[question.key].en);
        return <Row
          key={question.key}
          label={shortLabel}
          current={described ? tx(described.ko, described.en) : null}
          open={sameRow(open, row)}
          onPress={() => toggle(row)}
        >
          <Text weight="bold" style={styles.openTitle}>{tx(question.title.ko, question.title.en)}</Text>

          {question.kind === 'scale' && <Scale
            label={shortLabel}
            value={saved.taste[question.key]}
            low={tx(question.low.ko, question.low.en)}
            high={tx(question.high.ko, question.high.en)}
            onChange={(value) => setTaste(question.key, value)}
          />}

          {question.kind === 'multi' && <Chips
            values={saved.taste.foods ?? []}
            options={FOODS.map(([code, labelKo, labelEn]) => ({ code, label: tx(labelKo, labelEn) }))}
            onChange={(values) => {
              // 마지막 하나까지 빼면 그건 "답 없음" 이다. 빈 배열을 저장해 두면 다음에
              // 「답 안 함」이 아니라 「(아무것도 아님)」이 보인다.
              if (values.length === 0) clearTaste('foods', shortLabel);
              else setTaste('foods', values);
            }}
          />}

          {question.kind === 'choice' && <CardChoice
            value={saved.taste.slope}
            onChange={(next) => setTaste('slope', next as TasteValue)}
            options={question.options.map((option) => ({
              code: option.value,
              label: tx(option.label.ko, option.label.en),
              desc: option.desc ? tx(option.desc.ko, option.desc.en) : undefined,
            }))}
          />}

          <RowActions
            canClear={described !== null}
            onClear={() => clearTaste(question.key, shortLabel)}
            onClose={() => setOpen(null)}
          />
        </Row>;
      })}
    </View>

    {answeredCount > 0 && <View style={styles.dangerCard}>
      <Text variant="caption" weight="bold" color={color.state.danger}>{tx('기억 지우기', 'Forget everything')}</Text>
      <Text variant="caption" color={color.text.body}>
        {tx('여덟 답을 모두 지워요. 다음 여행부터는 빈칸으로 시작해요.',
          'Clears all eight answers. Your next trip starts blank.')}
      </Text>
      {clearConfirm
        ? <View style={styles.confirmRow}>
            <Button label={tx('취소', 'Cancel')} variant="ghost" containerStyle={styles.confirmButton} onPress={() => setClearConfirm(false)} />
            <Pressable accessibilityRole="button" onPress={clearAll} style={styles.dangerConfirm}>
              <Text weight="bold" color={color.text.onAction}>{tx('모두 지우기', 'Clear all')}</Text>
            </Pressable>
          </View>
        : <Pressable accessibilityRole="button" onPress={() => setClearConfirm(true)} style={styles.dangerButton}>
            <Text weight="bold" color={color.state.danger}>{tx('기억된 취향 모두 지우기 ›', 'Clear all saved preferences ›')}</Text>
          </Pressable>}
    </View>}

    {toast && <View style={styles.toast}>
      <Text variant="caption" weight="bold" color={color.state.success}>{toast}</Text>
    </View>}
  </Screen>;
}

// 목록의 줄 이름. 문항 제목은 한 문장이라 줄에 안 맞는다.
const SPEND_ROW_LABEL: Record<SpendKey, [string, string]> = {
  transport: ['오는 교통', 'Getting there'],
  stay: ['숙소 (1인 1박)', 'Stay (per person, per night)'],
  meal: ['저녁 한 끼', 'Dinner'],
};

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  centerScreen: { alignItems: 'center', justifyContent: 'center' },
  header: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginTop: spacing[2] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  headerCopy: { gap: spacing[1] },
  intro: { marginTop: spacing[3], marginBottom: spacing[6] },
  emptyCard: { alignItems: 'center', gap: spacing[2], padding: spacing[4], marginBottom: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  emptyMascot: { width: 88, height: 88 },
  emptyBody: { textAlign: 'center' },
  emptyCta: { minHeight: 46, alignSelf: 'stretch', marginTop: spacing[2] },
  group: { marginTop: spacing[2], marginBottom: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, overflow: 'hidden' },
  row: { borderTopWidth: 1, borderTopColor: color.surface.border },
  rowOpen: { backgroundColor: color.surface.tint },
  rowHead: { minHeight: 62, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingVertical: spacing[3], paddingHorizontal: spacing[4] },
  rowCopy: { flex: 1, gap: spacing[1] },
  rowBody: { gap: spacing[3], paddingHorizontal: spacing[4], paddingBottom: spacing[4] },
  openTitle: { marginBottom: spacing[1] },
  rowActions: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: spacing[1] },
  clearLink: { minHeight: 44, justifyContent: 'center' },
  closeButton: { minHeight: 40, paddingHorizontal: spacing[4], borderRadius: radius.sm, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },
  scaleEnds: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: spacing[2] },
  scaleTrack: { flexDirection: 'row', justifyContent: 'space-between', padding: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.card },
  scalePoint: { width: 48, height: 48, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' },
  scalePointSelected: { backgroundColor: color.brand.navy },
  cardChoices: { gap: spacing[2] },
  cardChoice: { minHeight: 44, justifyContent: 'center', gap: spacing[1], padding: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  cardChoiceSelected: { borderColor: color.brand.orange, backgroundColor: color.surface.warm },
  cardChoiceDesc: { lineHeight: 18 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 44, justifyContent: 'center', paddingHorizontal: 14, borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  chipSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  dangerCard: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.dangerBg },
  dangerButton: { minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, backgroundColor: color.surface.card, marginTop: spacing[1] },
  confirmRow: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[1] },
  confirmButton: { flex: 1, minHeight: 44 },
  dangerConfirm: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, backgroundColor: color.state.danger },
  toast: { marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.successBg },
});
