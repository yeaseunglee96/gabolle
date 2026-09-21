// 여행 취향 본문 — 화면과 마이페이지 패널이 같은 것을 쓴다.
//
// 제목과 설명은 껍데기가 그린다(myPanels 의 panelTitle). 여기서 또 그리면 두 번 나온다.
import { txf } from '@/i18n/format';
import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ActivityIndicator, Image, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
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

/** 펼친 줄 하나. 세 질문과 취향이 같은 목록에 섞여 있어 어느 쪽인지 함께 들고 다닌다. */
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
      <Text variant="caption" weight="bold" color={color.action.secondary}>
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
          accessibilityLabel={txf(tx, '%s %s단계', '%s level %s', label, point)}
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

export function PreferencesBody() {
  const { tx } = useI18n();
  const { accessToken } = useAuth();
  const { width } = useWindowDimensions();
  // 1024 이상 — breakpoints.ts 의 표에서 사이드바가 들어가는 폭이다.
  const wide = isAtLeast(width, 'lg');
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
    // 고른 것이 바로 보이게 먼저 화면을 바꾼다. 실패하면 서버에서 다시 읽어 되돌린다
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
    // 세 질문은 한 꾸러미라 지울 때도 남은 것 전부를 다시 보낸다. 지운 것만 보내면
    // 나머지 둘이 같이 사라진다.
    void write({ ...saved, spend }, () => putSpendProfile(spend, accessToken),
      txf(tx, '%s 답을 지웠어요. 다음 여행에서 다시 물어요.', 'Cleared %s. We will ask again on your next trip.', label));
  };

  const setTaste = (key: TasteKey, value: TasteValue) => {
    const taste = { ...saved.taste, [key]: value };
    void write({ ...saved, taste }, () => putTasteProfile({ [key]: value }, accessToken), savedMessage);
  };

  const clearTaste = (key: TasteKey, label: string) => {
    const taste = { ...saved.taste };
    delete taste[key];
    // 지우기는 null 로 명시해서 보낸다. 키를 빼고 보내면 서버는 "안 보냈다" 로 읽어
    // 그대로 둔다 — 지운 것처럼 보이지만 안 지워진다.
    void write({ ...saved, taste }, () => putTasteProfile({ [key]: null }, accessToken),
      txf(tx, '%s 답을 지웠어요. 다음 여행에서 다시 물어요.', 'Cleared %s. We will ask again on your next trip.', label));
  };

  const clearAll = () => {
    setClearConfirm(false);
    void write({ spend: {}, taste: {} }, async () => {
      await putSpendProfile({}, accessToken);
      await putTasteProfile(Object.fromEntries(TASTE_KEYS.map((key) => [key, null])), accessToken);
    }, tx('기억된 취향을 모두 지웠어요.', 'Cleared every saved preference.'));
  };

  const toggle = (row: OpenRow) => setOpen((current) => (sameRow(current, row) ? null : row));

  // isPending 이 아니라 isLoading 이다. 읽기를 아예 안 켜는 자리(로그인 전 · 화면
  // 미리보기)에서 isPending 은 영원히 참이라 돌아가는 동그라미에 갇힌다. isLoading 은
  // "지금 실제로 받아오는 중" 만 참이다.
  if (query.isLoading) {
    return <ActivityIndicator color={color.action.primary} />;
  }

  // 머리(뒤로 버튼·눈썹·제목·설명)는 MyPageShell 이 그린다 — 마이페이지 탭 다섯이 같은
  // 머리를 쓰고, 넓은 화면에서는 그 자리에 왼쪽 메뉴가 함께 붙는다.
  return <>

    {/* 넓은 화면에서는 가로로 눕는다 — 세로로 쌓으면 마스코트 하나가 화면 절반을 먹는다. */}
    {answeredCount === 0 && <View style={[styles.emptyCard, wide && styles.emptyCardWide]}>
      <Image source={require('../../../assets/mascot/dongbaek-thinking.png')} style={[styles.emptyMascot, wide && styles.emptyMascotWide]} resizeMode="contain" />
      <View style={[styles.emptyCopy, wide && styles.emptyCopyWide]}>
        <Text variant="title" weight="bold">{tx('아직 기억된 취향이 없어요', 'No preferences saved yet')}</Text>
        <Text variant="caption" color={color.text.body} style={wide ? undefined : styles.emptyBody}>
          {tx('처음에 건너뛰셨어요. 지금 답하면 여행을 만들 때 미리 채워 드려요.',
            'You skipped these at the start. Answer now and we will fill them in when you plan.')}
        </Text>
        <Button
          label={tx('8개 답하기 · 약 1분', 'Answer 8 questions · about a minute')}
          containerStyle={[styles.emptyCta, wide && styles.emptyCtaWide]}
          onPress={() => setOpen({ group: 'spend', key: SPEND_QUESTIONS[0].key })}
        />
      </View>
    </View>}

    {/* 넓은 화면에서는 두 그룹을 나란히. alignItems 를 'flex-start' 로 둬야 한 쪽 줄을
        펼쳤을 때 반대쪽 카드가 같이 늘어나지 않는다 — 늘어나면 빈 흰 바탕이 생긴다.
    */}
    <View style={wide ? styles.groupsWide : undefined}>
    <View style={wide ? styles.groupColumn : undefined}>
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
              // 「그날그날 달라요」는 건너뛰기가 아니라 답이다. 저장되고, 계산에서만
              // 빠진다(spendProfile.ts 의 VARIES 주석). 목록에서 빼면 그 사람은 셋 중
              // 아무것도 고를 수 없게 된다.
              ...(question.key === 'meal' ? [{ code: MEAL_VARIES_CODE, label: tx(question.skipLabelKo, question.skipLabelEn) }] : []),
            ]}
          />
          <RowActions canClear={code !== undefined} onClear={() => clearSpend(question.key, shortLabel)} onClose={() => setOpen(null)} />
        </Row>;
      })}
    </View>
    </View>

    <View style={wide ? styles.groupColumn : undefined}>
    {/* 🔴 「다섯」이라고 적고 넷만 그렸다 — 1423 이 관광지 문항을 뺐는데 이 줄이 남았다.
        없는 다섯째를 찾느라 사람이 화면을 다시 훑었다(팀원 실기 지적). */}
    <Eyebrow>{tx('취향', 'Travel tastes')}</Eyebrow>
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
    </View>
    </View>

    {/* 넓은 화면에서는 설명과 버튼이 한 줄에 눕는다. */}
    {answeredCount > 0 && <View style={[styles.dangerCard, wide && styles.dangerCardWide]}>
      <View style={styles.dangerCopy}>
        <Text variant="caption" weight="bold" color={color.state.danger}>{tx('기억 지우기', 'Forget everything')}</Text>
        <Text variant="caption" color={color.text.body}>
          {tx('여덟 답을 모두 지워요. 다음 여행부터는 빈칸으로 시작해요.',
            'Clears all eight answers. Your next trip starts blank.')}
        </Text>
      </View>
      {clearConfirm
        ? <View style={[styles.confirmRow, wide && styles.confirmRowWide]}>
            <Button label={tx('취소', 'Cancel')} variant="tertiary" containerStyle={styles.confirmButton} onPress={() => setClearConfirm(false)} />
            <Pressable accessibilityRole="button" onPress={clearAll} style={styles.dangerConfirm}>
              <Text weight="bold" color={color.state.danger}>{tx('모두 지우기', 'Clear all')}</Text>
            </Pressable>
          </View>
        : <Pressable accessibilityRole="button" onPress={() => setClearConfirm(true)} style={[styles.dangerButton, wide && styles.dangerButtonWide]}>
            <Text weight="bold" color={color.state.danger}>{tx('기억된 취향 모두 지우기 ›', 'Clear all saved preferences ›')}</Text>
          </Pressable>}
    </View>}

    {toast && <View style={styles.toast}>
      <Text variant="caption" weight="bold" color={color.state.success}>{toast}</Text>
    </View>}
  </>;
}

// 목록의 줄 이름. 문항 제목은 한 문장이라 줄에 안 맞는다.
const SPEND_ROW_LABEL: Record<SpendKey, [string, string]> = {
  transport: ['오는 교통', 'Getting there'],
  stay: ['숙소 (1인 1박)', 'Stay (per person, per night)'],
  meal: ['저녁 한 끼', 'Dinner'],
};

const styles = StyleSheet.create({
  screen: { backgroundColor: color.canvas },
  centerScreen: { alignItems: 'center', justifyContent: 'center' },
  header: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginTop: spacing[2] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  headerCopy: { gap: spacing[1] },
  intro: { marginTop: spacing[3], marginBottom: spacing[6] },
  emptyCard: { alignItems: 'center', gap: spacing[2], padding: spacing[4], marginBottom: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  emptyCardWide: { flexDirection: 'row', gap: spacing[6], padding: spacing[6] },
  emptyMascot: { width: 88, height: 88 },
  emptyMascotWide: { width: 104, height: 104 },
  emptyCopy: { alignItems: 'center', gap: spacing[2], alignSelf: 'stretch' },
  emptyCopyWide: { flex: 1, alignItems: 'flex-start', justifyContent: 'center' },
  emptyBody: { textAlign: 'center' },
  emptyCta: { minHeight: 46, alignSelf: 'stretch', marginTop: spacing[2] },
  // width: 'auto' 가 있어야 줄어든다. Button 의 기본 스타일에 width: '100%' 가 박혀
  // 있어서 alignSelf 만으로는 아무 일도 안 일어난다 — 조용히 안 먹는 자리다.
  emptyCtaWide: { alignSelf: 'flex-start', width: 'auto', paddingHorizontal: spacing[6] },
  // alignItems: 'flex-start' — 한 쪽 줄을 펼쳤을 때 반대쪽 카드가 같이 늘어나지 않게.
  // 늘어나면 그만큼 빈 흰 바탕이 생기고, 그게 "여기 뭔가 빠졌나" 로 읽힌다.
  groupsWide: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[6] },
  groupColumn: { flex: 1 },
  group: { marginTop: spacing[2], marginBottom: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, overflow: 'hidden' },
  row: { borderTopWidth: 1, borderTopColor: color.surface.border },
  rowOpen: { backgroundColor: color.surface.tint },
  rowHead: { minHeight: 62, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingVertical: spacing[3], paddingHorizontal: spacing[4] },
  rowCopy: { flex: 1, gap: spacing[1] },
  rowBody: { gap: spacing[3], paddingHorizontal: spacing[4], paddingBottom: spacing[4] },
  openTitle: { marginBottom: spacing[1] },
  rowActions: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: spacing[1] },
  clearLink: { minHeight: 44, justifyContent: 'center' },
  closeButton: { minHeight: 40, paddingHorizontal: spacing[4], borderRadius: radius.sm, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.secondary },
  scaleEnds: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: spacing[2] },
  scaleTrack: { flexDirection: 'row', justifyContent: 'space-between', padding: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.card },
  scalePoint: { width: 48, height: 48, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' },
  scalePointSelected: { backgroundColor: color.action.secondary },
  cardChoices: { gap: spacing[2] },
  cardChoice: { minHeight: 44, justifyContent: 'center', gap: spacing[1], padding: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  cardChoiceSelected: { borderColor: color.action.secondary, backgroundColor: color.surface.tint },
  cardChoiceDesc: { lineHeight: 18 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 44, justifyContent: 'center', paddingHorizontal: 14, borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  chipSelected: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  dangerCard: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.dangerBg },
  dangerCardWide: { flexDirection: 'row', alignItems: 'center', gap: spacing[6], padding: spacing[6] },
  dangerCopy: { flex: 1, gap: spacing[1] },
  dangerButton: { minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, backgroundColor: color.surface.card, marginTop: spacing[1] },
  dangerButtonWide: { marginTop: 0, paddingHorizontal: spacing[6] },
  confirmRow: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[1] },
  confirmRowWide: { marginTop: 0, minWidth: 280 },
  confirmButton: { flex: 1, minHeight: 44 },
  dangerConfirm: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, backgroundColor: color.state.dangerBg, borderWidth: 1, borderColor: color.state.danger },
  toast: { marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.successBg },
});
