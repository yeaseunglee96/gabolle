// 여행 돈 — 쓴 돈·남은 돈·동행 정산(S15P21E201-1935, UI 캔버스 ㉕). 여행 화면 도구 줄의 「여행 돈」에서 들어온다.
//
// 전에는 코스 카드의 「6.0만원 예상」뿐이라 실제로 얼마를 썼는지, 누가 누구에게 얼마를 줘야 하는지 적을 곳이 없었다.
// 🔴 금액은 원으로만 적는다 — 서버 합계가 원 정수다. 외화로 냈으면 환율 도구로 바꿔 적는다(안내 한 줄).
// 🔴 정산은 화면이 계산한다(src/trip/expenses.ts settle) — 구성원이 들고 나면 바뀌는 값이라 서버에 저장하지 않는다.
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Modal, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Toggle } from '@/components/Toggle';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';
import { formatWon } from '@/place/detailExtras';
import { SelectTripFirst } from '@/trip/SelectTripFirst';
import { listTripMembers, type TripMember } from '@/trip/collaboration';
import { addExpense, EXPENSE_CATEGORIES, loadLedger, removeExpense, saveBudget, settle, totalsByCategory, type Expense, type ExpenseCategory, type Ledger } from '@/trip/expenses';

type Tx = (ko: string, en: string) => string;

export const CATEGORY_LABEL: Record<ExpenseCategory, [string, string]> = {
  FOOD: ['식비', 'Food'],
  CAFE: ['카페', 'Cafe'],
  TRANSPORT: ['교통', 'Transport'],
  ADMISSION: ['입장료', 'Admission'],
  SHOPPING: ['쇼핑', 'Shopping'],
  LODGING: ['숙소', 'Lodging'],
  OTHER: ['기타', 'Other'],
};

export default function TripMoney() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return id ? <TripMoneyForTrip tripId={id} /> : <SelectTripFirst />;
}

function TripMoneyForTrip({ tripId }: { tripId: string }) {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { accessToken } = useAuth();
  const [ledger, setLedger] = useState<Ledger | null>(null);
  const [members, setMembers] = useState<TripMember[]>([]);
  const [canEdit, setCanEdit] = useState(false);
  const [myRole, setMyRole] = useState<string>('VIEWER');
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [editingBudget, setEditingBudget] = useState(false);

  const reload = useCallback(async () => {
    const [result, people] = await Promise.all([loadLedger(tripId, accessToken), listTripMembers(tripId, accessToken)]);
    if (result.state === 'success') { setLedger(result.ledger); setError(null); } else setError(result.message);
    if (people.state === 'success') { setMembers(people.members); setCanEdit(people.canEdit); setMyRole(people.myRole); }
  }, [tripId, accessToken]);
  useEffect(() => { void reload(); }, [reload]);

  const me = members.find((member) => member.isMe);
  const nameOf = useCallback((userId: string) => {
    const member = members.find((each) => each.userId === userId);
    if (!member) return tx('나간 동행', 'Former companion');
    if (member.isMe) return txf(tx, '%s (나)', '%s (me)', member.displayName ?? tx('나', 'Me'));
    return member.displayName ?? tx('동행', 'Companion');
  }, [members, tx]);
  const won = (amount: number) => formatWon(amount, language);

  const transfers = useMemo(() => (ledger ? settle(ledger.items, members.map((member) => member.userId)) : []), [ledger, members]);
  const categories = useMemo(() => (ledger ? totalsByCategory(ledger.items) : []), [ledger]);
  const days = useMemo(() => groupByDay(ledger?.items ?? []), [ledger]);

  const apply = (result: Awaited<ReturnType<typeof loadLedger>>) => {
    if (result.state === 'success') { setLedger(result.ledger); setError(null); return true; }
    setError(result.message);
    return false;
  };

  const total = ledger?.totalKrw ?? 0;
  const budget = ledger?.budgetKrw ?? null;
  const ratio = budget ? Math.min(1, total / budget) : 0;
  const perPerson = members.length > 1 ? Math.round(total / members.length) : null;

  return <View style={styles.shell}>
    <Screen scroll>
      <View style={styles.top}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => (router.canGoBack() ? router.back() : router.replace('/trips'))} style={styles.back}><Text variant="title">‹</Text></Pressable>
        <Text variant="title" weight="bold">{tx('여행 돈', 'Trip money')}</Text>
      </View>

      {error ? <View accessibilityRole="alert" style={styles.errorBox}><Text color={color.state.danger}>{localizeMessage(tx, error)}</Text></View> : null}

      {/* 요약 — 쓴 돈이 먼저, 예산은 옆에 작게 */}
      <View style={styles.card}>
        <View style={styles.rowBetween}>
          <Text variant="caption" weight="bold" color={color.text.muted}>{tx('쓴 돈', 'Spent')}</Text>
          {canEdit ? (
            <Pressable accessibilityRole="button" onPress={() => setEditingBudget(true)} style={styles.linkButton}>
              <Text variant="caption" weight="bold" color={color.text.body}>{budget ? txf(tx, '예산 %s · 바꾸기 ›', 'Budget %s · change ›', won(budget)) : tx('예산 정하기 ›', 'Set a budget ›')}</Text>
            </Pressable>
          ) : budget ? <Text variant="caption" color={color.text.muted}>{txf(tx, '예산 %s', 'Budget %s', won(budget))}</Text> : null}
        </View>
        {/* 🔴 색을 적는다 — hero 의 기본색은 흰색이라(사진 위에 얹던 시절) 흰 카드에서는 금액이 통째로 안 보인다 */}
        <Text variant="hero" weight="bold" color={color.text.heading} testID="money-total">{ledger ? won(total) : '…'}</Text>
        {budget ? (
          <>
            <View style={styles.bar}><View style={[styles.barFill, { width: `${Math.round(ratio * 100)}%` }, total > budget && styles.barOver]} /></View>
            <Text variant="caption" color={color.text.body}>{total > budget ? txf(tx, '예산보다 %s 더 썼어요', '%s over budget', won(total - budget)) : txf(tx, '%s 남았어요', '%s left', won(budget - total))}</Text>
          </>
        ) : null}
        {perPerson !== null ? <Text variant="caption" color={color.text.muted}>{txf(tx, '%s명이면 1인 %s', '%s people · %s each', members.length, won(perPerson))}</Text> : null}
      </View>

      {categories.length ? (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.categories}>
          {categories.map((entry) => (
            <View key={entry.category} style={styles.categoryChip}>
              <Text variant="micro" weight="bold" color={color.text.muted}>{tx(...CATEGORY_LABEL[entry.category])}</Text>
              <Text weight="bold">{won(entry.amountKrw)}</Text>
            </View>
          ))}
        </ScrollView>
      ) : null}

      {transfers.length ? (
        <View style={styles.settle} testID="money-settle">
          <Text variant="caption" color={color.text.onDarkMuted}>{tx('반씩 나누면', 'Split evenly')}</Text>
          {transfers.map((transfer) => (
            <Text key={`${transfer.from}-${transfer.to}`} weight="bold" color={color.text.onAction}>{txf(tx, '%s → %s · %s', '%s → %s · %s', nameOf(transfer.from), nameOf(transfer.to), won(transfer.amountKrw))}</Text>
          ))}
        </View>
      ) : null}

      {ledger && ledger.items.length === 0 ? (
        <View style={styles.card}>
          <Text weight="bold">{tx('아직 적은 돈이 없어요', 'Nothing written down yet')}</Text>
          <Text variant="caption" color={color.text.body}>{tx('밥값·교통비·입장료를 적으면\n남은 돈과 동행 정산을 계산해 드려요.', 'Write down meals, rides and tickets\nand we work out what is left and who owes whom.')}</Text>
        </View>
      ) : null}

      {days.map((day) => (
        <View key={day.key} style={styles.day}>
          <View style={styles.rowBetween}>
            <Text weight="bold">{day.label(language)}</Text>
            <Text variant="caption" color={color.text.muted}>{won(day.total)}</Text>
          </View>
          {day.items.map((item) => (
            <ExpenseRow key={item.expenseId} item={item} payer={item.paidBy === me?.userId ? null : nameOf(item.paidBy)} won={won} tx={tx}
              canDelete={item.createdBy === me?.userId || myRole === 'OWNER'}
              onDelete={async () => { apply(await removeExpense(tripId, item.expenseId, accessToken)); }} />
          ))}
        </View>
      ))}

      <Text variant="caption" color={color.text.muted} style={styles.foot}>{tx('원화로 적어요. 외화로 냈으면 환율 도구로 바꿔 적어 주세요.\n동행에게도 같은 내역이 보여요.', 'Amounts are in won. If you paid in another currency, convert it with the exchange tool first.\nYour companions see the same list.')}</Text>
    </Screen>

    <View style={styles.dock}>
      <Button label={tx('쓴 돈 적기', 'Add an expense')} onPress={() => setAdding(true)} testID="money-add" />
    </View>

    <AddExpenseSheet visible={adding} members={members} meId={me?.userId ?? null} nameOf={nameOf} tx={tx}
      onClose={() => setAdding(false)}
      onSubmit={async (expense) => { const ok = apply(await addExpense(tripId, expense, accessToken)); if (ok) setAdding(false); return ok; }} />
    <BudgetSheet visible={editingBudget} initial={budget} tx={tx} onClose={() => setEditingBudget(false)}
      onSubmit={async (amount) => { const ok = apply(await saveBudget(tripId, amount, accessToken)); if (ok) setEditingBudget(false); }} />
  </View>;
}

function ExpenseRow({ item, payer, won, tx, canDelete, onDelete }: { item: Expense; payer: string | null; won: (n: number) => string; tx: Tx; canDelete: boolean; onDelete: () => void }) {
  const [confirming, setConfirming] = useState(false);
  const time = new Date(item.spentAt);
  const hhmm = Number.isNaN(time.getTime()) ? '' : `${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}`;
  return (
    <View style={styles.row}>
      <View style={styles.badge}><Text variant="micro" weight="bold">{tx(...CATEGORY_LABEL[item.category])}</Text></View>
      <View style={styles.grow}>
        <Text weight="bold" numberOfLines={1}>{item.placeName ?? item.note ?? tx(...CATEGORY_LABEL[item.category])}</Text>
        <Text variant="caption" color={color.text.muted} numberOfLines={1}>{[hhmm, payer === null ? tx('내가 냄', 'I paid') : txf(tx, '%s 냄', 'paid by %s', payer), item.splitEven ? null : tx('혼자 쓴 돈', 'not split')].filter(Boolean).join(' · ')}</Text>
      </View>
      <Text weight="bold">{won(item.amountKrw)}</Text>
      {canDelete ? (
        <Pressable accessibilityRole="button" accessibilityLabel={confirming ? tx('정말 지우기', 'Really delete') : tx('이 줄 지우기', 'Delete this line')} onPress={() => (confirming ? onDelete() : setConfirming(true))} style={[styles.delete, confirming && styles.deleteConfirm]}>
          <Text variant="micro" weight="bold" color={color.state.danger}>{confirming ? tx('지우기', 'Delete') : '✕'}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

function AddExpenseSheet({ visible, members, meId, nameOf, tx, onClose, onSubmit }: {
  visible: boolean; members: TripMember[]; meId: string | null; nameOf: (id: string) => string; tx: Tx;
  onClose: () => void; onSubmit: (expense: { amountKrw: number; category: ExpenseCategory; paidBy?: string; placeName?: string | null; splitEven: boolean }) => Promise<boolean>;
}) {
  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState<ExpenseCategory>('FOOD');
  const [place, setPlace] = useState('');
  const [payer, setPayer] = useState<string | null>(null);
  const [split, setSplit] = useState(true);
  const [saving, setSaving] = useState(false);
  useEffect(() => { if (visible) { setAmount(''); setPlace(''); setPayer(meId); setSplit(members.length > 1); } }, [visible, meId, members.length]);
  const value = Number(amount.replace(/[^0-9]/g, ''));
  const ready = value > 0 && !saving;
  const submit = async () => {
    if (!ready) return;
    setSaving(true);
    const ok = await onSubmit({ amountKrw: value, category, paidBy: payer ?? undefined, placeName: place.trim() || null, splitEven: split });
    setSaving(false);
    if (ok) setAmount('');
  };
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <Pressable style={StyleSheet.absoluteFill} accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={onClose} />
        <View style={styles.sheet}>
          <View style={styles.rowBetween}>
            <Text variant="title" weight="bold">{tx('쓴 돈 적기', 'Add an expense')}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={onClose} style={styles.close}><Text variant="title">✕</Text></Pressable>
          </View>
          <Text variant="caption" weight="bold" color={color.text.muted}>{tx('얼마 (원)', 'How much (won)')}</Text>
          <TextInput testID="money-amount" accessibilityLabel={tx('얼마 (원)', 'How much (won)')} value={amount ? Number(amount.replace(/[^0-9]/g, '') || 0).toLocaleString('en-US') : ''} onChangeText={setAmount} keyboardType="number-pad" placeholder="18,000" placeholderTextColor={color.text.muted} style={styles.amountInput} />
          <Text variant="caption" weight="bold" color={color.text.muted}>{tx('무엇에', 'For')}</Text>
          <View style={styles.wrap}>
            {EXPENSE_CATEGORIES.map((each) => (
              <Pressable key={each} accessibilityRole="radio" accessibilityState={{ selected: each === category }} onPress={() => setCategory(each)} style={[styles.choice, each === category && styles.choiceOn]}>
                <Text variant="util" weight="bold" color={each === category ? color.text.onAction : color.text.heading}>{tx(...CATEGORY_LABEL[each])}</Text>
              </Pressable>
            ))}
          </View>
          <Text variant="caption" weight="bold" color={color.text.muted}>{tx('어디서 (선택)', 'Where (optional)')}</Text>
          <TextInput accessibilityLabel={tx('어디서', 'Where')} value={place} onChangeText={setPlace} maxLength={120} placeholder={tx('광안리 밀면집', 'Gwangalli noodle shop')} placeholderTextColor={color.text.muted} style={styles.textInput} />
          {members.length > 1 ? (
            <>
              <Text variant="caption" weight="bold" color={color.text.muted}>{tx('누가 냈어요', 'Who paid')}</Text>
              <View style={styles.wrap}>
                {members.map((member) => (
                  <Pressable key={member.userId} accessibilityRole="radio" accessibilityState={{ selected: payer === member.userId }} onPress={() => setPayer(member.userId)} style={[styles.choice, payer === member.userId && styles.choiceOn]}>
                    <Text variant="util" weight="bold" color={payer === member.userId ? color.text.onAction : color.text.heading}>{nameOf(member.userId)}</Text>
                  </Pressable>
                ))}
              </View>
              <View style={styles.rowBetween}>
                <Text weight="bold">{txf(tx, '%s명이 똑같이 나누기', 'Split evenly among %s', members.length)}</Text>
                <Toggle value={split} onValueChange={setSplit} accessibilityLabel={tx('똑같이 나누기', 'Split evenly')} />
              </View>
            </>
          ) : null}
          <Button variant="secondary" label={tx('적기', 'Save')} onPress={() => void submit()} disabled={!ready} testID="money-save" />
        </View>
      </View>
    </Modal>
  );
}

function BudgetSheet({ visible, initial, tx, onClose, onSubmit }: { visible: boolean; initial: number | null; tx: Tx; onClose: () => void; onSubmit: (amount: number | null) => void }) {
  const [amount, setAmount] = useState('');
  useEffect(() => { if (visible) setAmount(initial ? String(initial) : ''); }, [visible, initial]);
  const value = Number(amount.replace(/[^0-9]/g, ''));
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.backdropCenter}>
        <Pressable style={StyleSheet.absoluteFill} accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={onClose} />
        <View style={styles.dialog}>
          <Text variant="title" weight="bold">{tx('여행 예산', 'Trip budget')}</Text>
          <TextInput accessibilityLabel={tx('예산 (원)', 'Budget (won)')} value={amount ? value.toLocaleString('en-US') : ''} onChangeText={setAmount} keyboardType="number-pad" placeholder="300,000" placeholderTextColor={color.text.muted} style={styles.amountInput} />
          <Button variant="secondary" label={tx('저장', 'Save')} onPress={() => onSubmit(value > 0 ? value : null)} />
          {initial ? <Button variant="tertiary" label={tx('예산 없애기', 'Remove budget')} onPress={() => onSubmit(null)} /> : null}
        </View>
      </View>
    </Modal>
  );
}

type DayGroup = { key: string; label: (language: string) => string; total: number; items: Expense[] };

/** 쓴 날(기기 시간대)별로 묶는다 — 서버가 최근 것부터 주므로 묶음도 최근 날이 위 */
export function groupByDay(items: Expense[]): DayGroup[] {
  const groups = new Map<string, DayGroup>();
  for (const item of items) {
    const date = new Date(item.spentAt);
    const key = Number.isNaN(date.getTime()) ? 'unknown' : `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`;
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        label: (language) => (Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString(language === 'ko' ? 'ko-KR' : language, { month: 'long', day: 'numeric', weekday: 'short' })),
        total: 0,
        items: [],
      });
    }
    const group = groups.get(key)!;
    group.total += item.amountKrw;
    group.items.push(item);
  }
  return [...groups.values()];
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.canvas },
  top: { minHeight: 52, flexDirection: 'row', alignItems: 'center', gap: spacing[2], marginBottom: spacing[4] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  errorBox: { padding: spacing[3], marginBottom: spacing[3], borderRadius: radius.md, backgroundColor: color.state.dangerBg },
  card: { gap: spacing[2], padding: spacing[4], marginBottom: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card },
  rowBetween: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3] },
  linkButton: { minHeight: 44, justifyContent: 'center' },
  bar: { height: 10, borderRadius: radius.full, backgroundColor: color.surface.tint, overflow: 'hidden' },
  barFill: { height: '100%', borderRadius: radius.full, backgroundColor: color.action.secondary },
  barOver: { backgroundColor: color.state.dot },
  categories: { gap: spacing[2], paddingBottom: spacing[3] },
  categoryChip: { minWidth: 84, gap: 2, paddingVertical: spacing[2], paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card },
  settle: { gap: spacing[1], padding: spacing[4], marginBottom: spacing[3], borderRadius: radius.lg, backgroundColor: color.action.secondary },
  day: { gap: spacing[2], marginBottom: spacing[4] },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card },
  badge: { minWidth: 44, height: 40, paddingHorizontal: spacing[1], borderRadius: radius.md, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.tint },
  grow: { flex: 1, minWidth: 0, gap: 2 },
  delete: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md },
  deleteConfirm: { backgroundColor: color.state.dangerBg },
  // 아래 「쓴 돈 적기」 단추(56 + 바닥 16)에 마지막 안내가 깔리지 않게 — 일본어는 세 줄이 된다
  foot: { marginTop: spacing[2], marginBottom: spacing[8] * 3, lineHeight: 18 },
  dock: { position: 'absolute', left: spacing[4], right: spacing[4], bottom: spacing[4] },
  backdrop: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(25,25,25,0.45)' },
  backdropCenter: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: spacing[6], backgroundColor: 'rgba(25,25,25,0.45)' },
  sheet: { width: '100%', maxWidth: 640, alignSelf: 'center', gap: spacing[3], padding: spacing[6], borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg, backgroundColor: color.surface.card },
  dialog: { width: '100%', maxWidth: 420, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  close: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  // 웹은 브라우저 테두리 대신 밑줄로 — 밑줄이 이미 「여기에 쓴다」를 말한다
  amountInput: { minHeight: 56, fontSize: 30, fontWeight: '700', color: color.text.heading, borderBottomWidth: 2, borderBottomColor: color.text.heading, ...(Platform.OS === 'web' ? ({ outlineStyle: 'none' } as object) : null) },
  textInput: { minHeight: 48, paddingHorizontal: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, color: color.text.heading, fontSize: 16 },
  wrap: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  choice: { minHeight: 40, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.tint },
  choiceOn: { backgroundColor: color.action.secondary },
});
