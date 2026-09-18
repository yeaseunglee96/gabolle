// 여행 조건 모달 — 알레르기 · 식단 · 이동 환경.
// 시안: docs/design_handoff_plan_flow/PlanFlow.dc.html 의 conditions-modal / conditions-sheet
import { useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';
import { usePlan, type ConstraintSelectionStatus, type PlanDraft } from '@/plan/PlanProvider';
import { conditionsFromDraft, saveTravelConditions } from '@/plan/travelConditions';

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
  const { user, accessToken } = useAuth();
  const phone = kind === 'phone';
  const [never, setNever] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveFailed, setSaveFailed] = useState(false);

  // 저장은 이 모달이 한다. 전에는 아무도 안 했다 — 화면 상태만
  // 바꾸고 닫았고, 그 상태는 새로고침 한 번에 사라졌다. 부르는 화면이 넷이라, 그중
  // 하나만 빠뜨려도 같은 사고가 다시 난다. 그래서 여기 한 곳에 둔다.
  const finish = async (outcome: ConditionsOutcome) => {
    if (outcome === 'DISMISSED') { onClose(outcome); return; }
    setSaving(true);
    const { synced } = await saveTravelConditions({
      userId: user?.userId ?? null,
      accessToken,
      status: outcome,
      conditions: outcome === 'SAVED' ? conditionsFromDraft(draft) : null,
    });
    setSaving(false);
    // 로그인 안 한 사람은 서버에 갈 자리가 없다 — 실패가 아니라 기기 저장이 정상이다.
    if (accessToken && !synced && outcome === 'SAVED' && !saveFailed) { setSaveFailed(true); return; }
    onClose(outcome);
  };

  const setStatus = (field: 'allergyStatus' | 'dietStatus', answered: 'allergyAnswered' | 'dietAnswered', values: 'allergies' | 'dietTypes', next: ConstraintSelectionStatus) => {
    // 「해당 없음」을 고르면 고른 항목을 비운다. 안 비우면 「해당 없음인데 땅콩 선택됨」이
    // 남아, 서버가 둘 중 어느 것을 믿어야 할지 모른다.
    update({ [field]: next, [answered]: true, ...(next === 'VALUES' ? {} : { [values]: [] }) } as Partial<PlanDraft>);
  };

  const toggle = (field: 'allergies' | 'dietTypes', code: string) => {
    const list = draft[field];
    update({ [field]: list.includes(code) ? list.filter((item) => item !== code) : [...list, code] } as Partial<PlanDraft>);
  };

  // 저장하려면 알레르기·식단 둘 다 답해야 한다. 그 둘은 「모르면 안전하다고 치지 않는」
  // 자리라, 비운 채로 저장하면 확인 화면이 다시 막는다 — 지금 사용자가 겪은 그것이다.
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
      {/* 바깥을 눌러 닫되 단추 역할을 주지 않는다. 단추 안에 단추가 들어가면
          웹에서 잘못된 마크업이 된다. 읽어 주는 이름은 안쪽 ✕ 가 갖는다.
      */}
      <Pressable onPress={() => onClose('DISMISSED')} style={[styles.backdrop, phone && styles.backdropPhone]}>
        {/* 안쪽 누름이 바깥으로 안 새게 한다 — 고르다가 모달이 닫히면 답이 통째로 날아간다. */}
        <View onStartShouldSetResponder={() => true} style={[styles.sheet, phone ? styles.sheetPhone : styles.sheetWide]}>
          <View style={styles.header}>
            <Text variant="title" weight="bold">{tx('여행 조건 미리 알려주기', 'Tell us your travel conditions')}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={() => onClose('DISMISSED')} style={styles.close}>
              <Text variant="title" weight="bold">✕</Text>
            </Pressable>
          </View>

          <ScrollView style={styles.bodyScroll} contentContainerStyle={styles.body} keyboardShouldPersistTaps="handled">
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
              {/* 「제한 없음」은 0 이다. null 이 아니다.
                  null 은 「아직 안 정했다」라서, 확인 화면이 그것을 「보행거리 미확인」으로
                  그린다 — 사용자가 제한 없음을 고르고도 미확인을 보고 있었다.
                  요청을 만들 때도 0 은 제약을 안 붙인다(`tripApi.ts`), 그래서 뜻이 맞는다.
              */}
              <View style={styles.chips}>{WALK_LIMITS.map((meters) => (
                <Chip
                  key={meters}
                  label={meters === 0 ? tx('제한 없음', 'No limit') : meters >= 1000 ? `${meters / 1000}km` : `${meters}m`}
                  selected={draft.maxWalkingDistanceM === meters}
                  onPress={() => update({ maxWalkingDistanceM: meters })}
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

          {saveFailed ? <Text accessibilityRole="alert" variant="caption" weight="bold" color={color.state.danger} style={styles.saveFailed}>
            {tx('서버에 저장하지 못했어요. 이 기기에는 적어 뒀어요 — 한 번 더 눌러 보시고, 그래도 안 되면 그대로 진행해도 괜찮아요.', 'We could not save to the server. It is stored on this device — try once more, or go ahead anyway.')}
          </Text> : null}

          <View style={styles.footer}>
            <View style={styles.footerLeft}>
              <Pressable accessibilityRole="button" onPress={() => void finish(never ? 'NEVER' : 'LATER')} style={styles.later}>
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
              label={saving ? tx('저장 중…', 'Saving…') : saveFailed ? tx('다시 저장', 'Save again') : tx('저장하고 시작', 'Save and start')}
              disabled={!savable || saving}
              /**
               * 여기서는 「다시 묻지 않기」를 보지 않는다. NEVER 로 보내면
               * 서버가 적은 값을 버린다 — 조건을 다 적고 체크까지 한 사람이 그 답을
               * 통째로 잃는다. SAVED 도 다시 묻지 않으므로 체크의 뜻은 이미 지켜진다.
               * 체크는 아래 「나중에」에만 붙는다.
               */
              onPress={() => void finish('SAVED')}
              containerStyle={styles.save}
            />
          </View>
        </View>
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
  bodyScroll: { flexShrink: 1 },
  body: { paddingHorizontal: spacing[6], paddingTop: spacing[4], paddingBottom: spacing[4] },
  intro: { marginBottom: spacing[4] },
  block: { gap: spacing[2], marginBottom: spacing[6] },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 44, paddingHorizontal: spacing[4], justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.ivory, borderWidth: 1, borderColor: color.surface.border },
  chipOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  binaryRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], marginTop: spacing[2] },
  binaryLabel: { flex: 1 },
  saveFailed: { paddingHorizontal: spacing[6], paddingTop: spacing[3] },
  footer: { gap: spacing[3], padding: spacing[6], paddingBottom: spacing[8], borderTopWidth: 1, borderColor: color.surface.border },
  footerLeft: { gap: spacing[1] },
  later: { minHeight: 32, justifyContent: 'center' },
  underline: { textDecorationLine: 'underline' },
  never: { minHeight: 32, justifyContent: 'center' },
  save: { minHeight: 48 },
});
