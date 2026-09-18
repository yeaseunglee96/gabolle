// 여행 조건 모달 — 알레르기 · 식단 · 이동 환경 (S15P21E201-1233).
// 시안: docs/design_handoff_plan_flow/PlanFlow.dc.html 의 conditions-modal / conditions-sheet
//
// 🔴 이 모달이 없어서 일정이 아예 안 만들어졌다 (2026-09-18).
//
//    옛 제약조건 화면을 「조건 한 페이지로 합쳤다」며 넘겨보냈는데, 그 한 페이지는
//    알레르기·식단·이동 환경을 **안 묻는다** — 시안이 그 셋을 이 모달로 옮겼기 때문이다.
//    그런데 모달을 안 만들었다. 그래서 확인 화면이 「미확인 필수 조건이 있어요」로 막고,
//    그 링크를 눌러도 물어볼 자리가 없어 **영영 못 만드는 상태**가 됐다.
//
// 🔴 **휠체어·유아차·큰 짐은 여기 없다.** 여행마다 달라서 조건 페이지 질문으로 갔다.
//
// 🔴 「나중에」와 「다시 묻지 않기」는 **다른 답**이다. 닫기(×)는 또 다르다 —
//    실수로 닫은 사람이 영영 못 적게 되면 안 된다.
import { useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';
import { usePlan, type ConstraintSelectionStatus, type PlanDraft } from '@/plan/PlanProvider';

const ALLERGIES = [
  ['PEANUT', '땅콩', 'Peanuts'], ['TREE_NUT', '견과류', 'Tree nuts'], ['SHELLFISH_CRUSTACEAN', '갑각류', 'Shellfish'],
  ['FISH', '생선', 'Fish'], ['EGG', '달걀', 'Egg'], ['MILK_DAIRY', '우유·유제품', 'Milk · dairy'],
  ['WHEAT', '밀', 'Wheat'], ['SOY', '대두', 'Soy'],
] as const;

const DIETS = [
  ['VEGETARIAN', '채식', 'Vegetarian'], ['VEGAN', '비건', 'Vegan'], ['HALAL', '할랄', 'Halal'],
  ['GLUTEN_FREE', '글루텐 프리', 'Gluten-free'], ['PESCATARIAN', '페스코', 'Pescatarian'],
] as const;

const WALK_LIMITS = [500, 1000, 2000, 0] as const;

/** 사람이 이 모달을 어떻게 닫았나. 「나중에」와 「다시 묻지 않기」는 다른 답이다. */
export type ConditionsOutcome = 'SAVED' | 'LATER' | 'NEVER' | 'DISMISSED';

function Chip({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: selected }} onPress={onPress} style={[styles.chip, selected && styles.chipOn]}>
      <Text weight="bold" color={selected ? color.text.onAction : color.text.heading}>{label}</Text>
    </Pressable>
  );
}

export type ConditionsPromptModalProps = {
  visible: boolean;
  /** 「일정 물어보기」에서 다시 열린 것인가 — 인트로 문구와 왼쪽 단추가 바뀐다. */
  reprompt?: boolean;
  onClose: (outcome: ConditionsOutcome) => void;
};

export function ConditionsPromptModal({ visible, reprompt = false, onClose }: ConditionsPromptModalProps) {
  const { tx } = useI18n();
  const { kind } = useLayout();
  const { draft, update } = usePlan();
  const phone = kind === 'phone';
  const [never, setNever] = useState(false);

  const setStatus = (field: 'allergyStatus' | 'dietStatus', answered: 'allergyAnswered' | 'dietAnswered', values: 'allergies' | 'dietTypes', next: ConstraintSelectionStatus) => {
    // 🔴 「해당 없음」을 고르면 고른 항목을 비운다. 안 비우면 「해당 없음인데 땅콩 선택됨」이
    //    남아, 서버가 둘 중 어느 것을 믿어야 할지 모른다.
    update({ [field]: next, [answered]: true, ...(next === 'VALUES' ? {} : { [values]: [] }) } as Partial<PlanDraft>);
  };

  const toggle = (field: 'allergies' | 'dietTypes', code: string) => {
    const list = draft[field];
    update({ [field]: list.includes(code) ? list.filter((item) => item !== code) : [...list, code] } as Partial<PlanDraft>);
  };

  // 🔴 저장하려면 알레르기·식단 둘 다 답해야 한다. 그 둘은 「모르면 안전하다고 치지 않는」
  //    자리라, 비운 채로 저장하면 확인 화면이 다시 막는다 — 지금 사용자가 겪은 그것이다.
  const savable = draft.allergyAnswered && draft.dietAnswered
    && (draft.allergyStatus !== 'VALUES' || draft.allergies.length > 0)
    && (draft.dietStatus !== 'VALUES' || draft.dietTypes.length > 0);

  const statusRow = (
    label: string,
    field: 'allergyStatus' | 'dietStatus',
    answered: 'allergyAnswered' | 'dietAnswered',
    values: 'allergies' | 'dietTypes',
  ) => (
    <View style={styles.chips}>
      <Chip label={tx('해당 없음', 'None')} selected={draft[field] === 'NONE'} onPress={() => setStatus(field, answered, values, 'NONE')} />
      <Chip label={tx('조건 선택', 'Choose items')} selected={draft[field] === 'VALUES'} onPress={() => setStatus(field, answered, values, 'VALUES')} />
    </View>
  );

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={() => onClose('DISMISSED')}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={() => onClose('DISMISSED')} style={[styles.backdrop, phone && styles.backdropPhone]}>
        {/* 안쪽 누름이 바깥으로 안 새게 한다 — 고르다가 모달이 닫히면 답이 통째로 날아간다. */}
        <Pressable style={[styles.sheet, phone ? styles.sheetPhone : styles.sheetWide]} onPress={() => {}}>
          <View style={styles.header}>
            <Text variant="title" weight="bold">{tx('여행 조건 미리 알려주기', 'Tell us your travel conditions')}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={() => onClose('DISMISSED')} style={styles.close}>
              <Text variant="title" weight="bold">✕</Text>
            </Pressable>
          </View>

          <ScrollView style={styles.body} keyboardShouldPersistTaps="handled">
            <Text color={color.text.body} style={styles.intro}>
              {reprompt
                ? tx('일정을 만들기 전에 여행 조건을 알려주실래요? 건너뛰면 다음 「일정 물어보기」 때 다시 물어요.', 'Shall we take your travel conditions before building the itinerary? If you skip, we will ask again next time.')
                : tx('알레르기와 식단은 안전에 걸리는 것이라, 모르면 안전하다고 치지 않아요. 한 번만 알려주시면 다음부터 안 물어봐요.', 'Allergies and diet affect safety — we never assume a place is safe when we do not know. Tell us once and we will not ask again.')}
            </Text>

            <View style={styles.block}>
              <Text weight="bold">{tx('알레르기', 'Allergies')} <Text color={color.brand.orange}>*</Text></Text>
              {statusRow(tx('알레르기', 'Allergies'), 'allergyStatus', 'allergyAnswered', 'allergies')}
              {draft.allergyStatus === 'VALUES' ? (
                <View style={styles.chips}>{ALLERGIES.map(([code, ko, en]) => (
                  <Chip key={code} label={tx(ko, en)} selected={draft.allergies.includes(code)} onPress={() => toggle('allergies', code)} />
                ))}</View>
              ) : null}
            </View>

            <View style={styles.block}>
              <Text weight="bold">{tx('식단', 'Diet')} <Text color={color.brand.orange}>*</Text></Text>
              {statusRow(tx('식단', 'Diet'), 'dietStatus', 'dietAnswered', 'dietTypes')}
              {draft.dietStatus === 'VALUES' ? (
                <View style={styles.chips}>{DIETS.map(([code, ko, en]) => (
                  <Chip key={code} label={tx(ko, en)} selected={draft.dietTypes.includes(code)} onPress={() => toggle('dietTypes', code)} />
                ))}</View>
              ) : null}
            </View>

            <View style={styles.block}>
              <Text weight="bold">{tx('이동 환경', 'Getting around')}</Text>
              <Text variant="caption" color={color.text.muted}>{tx('한 번에 걷는 최대 거리', 'Longest walk at once')}</Text>
              <View style={styles.chips}>{WALK_LIMITS.map((meters) => (
                <Chip
                  key={meters}
                  label={meters === 0 ? tx('제한 없음', 'No limit') : meters >= 1000 ? `${meters / 1000}km` : `${meters}m`}
                  selected={draft.maxWalkingDistanceM === (meters === 0 ? null : meters) && (meters !== 0 || draft.maxWalkingDistanceM === null)}
                  onPress={() => update({ maxWalkingDistanceM: meters === 0 ? null : meters })}
                />
              ))}</View>

              {([
                ['slopeConstraint', '가파른 경사 피하기', 'Avoid steep slopes', 'AVOID', 'ALLOW'],
                ['stairsConstraint', '계단 피하기', 'Avoid stairs', 'AVOID', 'ALLOW'],
                ['shadePreference', '그늘길 우선', 'Prefer shaded routes', 'PREFER', 'NO_PREFERENCE'],
              ] as const).map(([field, ko, en, yes, no]) => (
                <View key={field} style={styles.binaryRow}>
                  <Text style={styles.binaryLabel}>{tx(ko, en)}</Text>
                  <View style={styles.chips}>
                    <Chip label={tx('예', 'Yes')} selected={draft[field] === yes} onPress={() => update({ [field]: yes } as Partial<PlanDraft>)} />
                    <Chip label={tx('아니요', 'No')} selected={draft[field] === no} onPress={() => update({ [field]: no } as Partial<PlanDraft>)} />
                  </View>
                </View>
              ))}
            </View>

            <Text variant="caption" color={color.text.muted}>
              {tx('휠체어·유아차·큰 짐은 여행마다 달라서 여행 조건 화면에서 물어봐요.', 'Wheelchair, stroller and large luggage change trip to trip, so we ask those on the conditions page.')}
            </Text>
          </ScrollView>

          <View style={styles.footer}>
            <View style={styles.footerLeft}>
              <Pressable accessibilityRole="button" onPress={() => onClose('LATER')} style={styles.later}>
                <Text variant="caption" weight="bold" color={color.text.body} style={styles.underline}>
                  {reprompt ? tx('이번엔 건너뛰기', 'Skip this time') : tx('나중에', 'Later')}
                </Text>
              </Pressable>
              <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: never }} onPress={() => setNever((on) => !on)} style={styles.never}>
                <Text variant="caption" color={never ? color.brand.orange : color.text.muted}>
                  {never ? '☑ ' : '☐ '}{tx('다시 묻지 않기', 'Do not ask again')}
                </Text>
              </Pressable>
            </View>
            <Button
              label={tx('저장하고 시작', 'Save and start')}
              disabled={!savable}
              onPress={() => onClose(never ? 'NEVER' : 'SAVED')}
              containerStyle={styles.save}
            />
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(11,29,58,0.45)', alignItems: 'center', justifyContent: 'center', padding: spacing[4] },
  backdropPhone: { justifyContent: 'flex-end', padding: 0 },
  sheet: { backgroundColor: color.brand.ivory, overflow: 'hidden' },
  sheetWide: { width: '100%', maxWidth: 640, maxHeight: '88%', borderRadius: radius.lg },
  sheetPhone: { width: '100%', maxHeight: '92%', borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg },
  header: { minHeight: 64, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4], borderBottomWidth: 1, borderColor: color.surface.border },
  close: { position: 'absolute', right: spacing[2], width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  body: { paddingHorizontal: spacing[6], paddingTop: spacing[4] },
  intro: { marginBottom: spacing[4] },
  block: { gap: spacing[2], marginBottom: spacing[6] },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 44, paddingHorizontal: spacing[4], justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.ivory, borderWidth: 1, borderColor: color.surface.border },
  chipOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  binaryRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], marginTop: spacing[2] },
  binaryLabel: { flex: 1 },
  footer: { gap: spacing[3], padding: spacing[6], paddingBottom: spacing[8], borderTopWidth: 1, borderColor: color.surface.border },
  footerLeft: { gap: spacing[1] },
  later: { minHeight: 32, justifyContent: 'center' },
  underline: { textDecorationLine: 'underline' },
  never: { minHeight: 32, justifyContent: 'center' },
  save: { minHeight: 48 },
});
