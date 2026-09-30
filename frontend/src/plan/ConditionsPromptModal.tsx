// 여행 조건 모달 — 식단 · 이동 환경.
//
// 🔴 알레르기는 «일부러» 안 묻는다 (S15P21E201-1497, 결정은 -1468 의 ㄱ).
//    묻고 나서 그 답 때문에 여행을 «못 만들게» 하고 있었다. 운영 place_feature 에
//    ALLERGEN_TAG 가 0행이라, 채점기가 표식 없는 후보를 「확인 못 함」으로 남기고
//    unknown-exclusion-threshold=REQUIRED 가 그것을 전부 뺀다 — 후보가 0건이 된다.
//    실측(2026-09-22): 알레르기·식단을 «고른» 작업 8건 중 성공 0건. 한 명도 못 만들었다.
//
//    「확인 못 했어요」 경고를 달아 내보내는 길(-1468 의 ㄴ)은 이동 제약에서 실제로
//    통했지만(표식 1.5%였는데 경고로 바꾼 뒤 55건 성공), 알레르기에는 쓰지 않는다.
//    접근성 추정이 틀리면 «불편»하고 알레르기 추정이 틀리면 «사람이 다친다».
//
//    🔴 여기서 안 묻는 것은 «여행을 만들 때 거는 조건» 하나다. 메뉴판 읽기
//    (field/menuScan.ts)와 장소 상세의 안전 표시는 그대로다 — 그건 사용자가 그 자리에서
//    직접 확인하는 것이라 성격이 다르다. 이미 저장된 값도 지우지 않는다.
//
//    다시 여는 조건: 운영 place_feature 에 ALLERGEN_TAG 가 VERIFIED 로 쌓였을 때.
//    ck_place_feature_safety_never_estimated 가 ESTIMATED 저장을 막으므로 추정으로는 못 채운다.
// 🔴 **판정할 장소 자료가 한 곳도 없는 문항에는 그 사실을 적는다** (S15P21E201-1044).
//    알레르기와 같은 병인데 처방이 다르다 — 알레르기는 틀린 답이 «사람을 다치게» 해서
//    질문을 지웠고(-1497), 여기는 고르는 것을 그대로 두고 **무슨 일이 일어나는지만** 적는다.
//    목록은 앱에 없다. 서버가 센다 (S15P21E201-1508 · `conditionCoverage.ts`) — 박아 두면
//    자료가 들어온 날 거짓말이 된다.
// 시안: docs/design_handoff_plan_flow/PlanFlow.dc.html 의 conditions-modal / conditions-sheet
import { useRef, useState } from 'react';
import { Animated, Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { shouldDismiss, useSheetDrag } from '@/components/sheetDrag';
import { useSheetBottomPadding } from '@/components/sheetBottomInset';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';
import { usePlan, type ConstraintSelectionStatus, type PlanDraft } from '@/plan/PlanProvider';
import { COVERAGE_FEATURE, hasNoPlaceData, useConditionCoverage } from '@/plan/conditionCoverage';
import { conditionsFromDraft, DIETS, saveTravelConditions } from '@/plan/travelConditions';
import { tasteDietsApplied } from '@/plan/tasteDiets';
import { txf } from '@/i18n/format';


const WALK_LIMITS = [500, 1000, 2000, 0] as const;

/** 사람이 이 모달을 어떻게 닫았나. 「나중에」와 「다시 묻지 않기」는 다른 답이다. */
export type ConditionsOutcome = 'SAVED' | 'LATER' | 'NEVER' | 'DISMISSED';

/**
 * 「이 조건을 판정할 장소 자료가 지금 한 곳도 없다」 — S15P21E201-1044.
 *
 * 🔴 **자료가 없을 때만 그린다.** 서버에 못 물어봤으면(끝점이 아직 없는 배포·네트워크 실패)
 *    `hasNoPlaceData` 가 `false` 를 내므로 아무것도 안 나온다 — 모르는 것을 「없다」로
 *    적으면 화면이 지어내는 것이 된다. 판단은 `conditionCoverage.ts` 한 곳에 있다.
 *
 * 🔴 **문항을 지우거나 못 고르게 하지 않는다.** 알레르기는 지웠지만(-1497) 그건 틀린 답이
 *    사람을 다치게 하는 자리였고, 여기는 아니다. 고르는 것은 그대로 두고 **무슨 일이
 *    일어나는지만 사실대로 적는다.**
 */
function NoPlaceData({ label }: { label: string }) {
  // 🔴 `alert` 역할을 주지 않는다. 화면을 여는 순간 이미 셋이 붙어 있어서, 알림으로
  //    읽히면 **한꺼번에 세 번** 끼어든다. 바로 앞 줄에 딸린 설명이라 읽는 차례대로
  //    나오는 편이 맞다.
  return (
    <Text variant="caption" color={color.text.muted} style={styles.noData}>
      {label}
    </Text>
  );
}

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
  // 자료가 한 곳도 없는 문항에 그 사실을 적는다 (S15P21E201-1044). 못 받아오면 null 이고,
  // 그때는 아무 문항에도 안 붙는다.
  const coverage = useConditionCoverage();
  const noData = tx(
    '지금은 이 조건을 확인할 장소 자료가 없어요 — 골라도 지금은 가려낼 수 없어요.',
    'We have no place data to check this yet — picking it cannot filter anything right now.',
  );
  const phone = kind === 'phone';
  // 🔴 안드로이드 탐색 막대 밑으로 버튼이 들어가지 않게(S15P21E201-1765).
  const bottomPad = useSheetBottomPadding(spacing[8]);
  const [never, setNever] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveFailed, setSaveFailed] = useState(false);

  // 휴대폰에서는 손잡이·머리를 아래로 쓸어내려 닫는다 (S15P21E201-1798).
  // 🔴 시트 전체가 아니라 손잡이·머리에만 붙인다 — 안쪽 ScrollView 의 스크롤을 뺏지 않게.
  // 닫는 길은 바깥 누름과 같은 onClose('DISMISSED') 다. 이 모달은 어떤 상태에서도
  // 바깥 누름으로 닫히므로, 쓸어내리기만 막을 상태는 없다.
  // 문턱값·판정은 다른 시트와 한 곳(`sheetDrag.ts`)을 쓴다 — 창마다 손맛이 다르면 고장으로 보인다.
  const dragY = useRef(new Animated.Value(0)).current;
  const dragHandlers = useSheetDrag({
    onMove: (dy) => dragY.setValue(Math.max(0, dy)),
    onEnd: (dy, vy) => {
      if (shouldDismiss(dy, vy)) {
        Animated.timing(dragY, { toValue: 800, duration: 180, useNativeDriver: false }).start(() => {
          dragY.setValue(0);
          onClose('DISMISSED');
        });
      } else {
        Animated.spring(dragY, { toValue: 0, useNativeDriver: false }).start();
      }
    },
  });

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

  const setStatus = (field: 'dietStatus', answered: 'dietAnswered', values: 'dietTypes', next: ConstraintSelectionStatus) => {
    // 「해당 없음」을 고르면 고른 항목을 비운다. 안 비우면 「해당 없음인데 땅콩 선택됨」이
    // 남아, 서버가 둘 중 어느 것을 믿어야 할지 모른다.
    update({ [field]: next, [answered]: true, ...(next === 'VALUES' ? {} : { [values]: [] }) } as Partial<PlanDraft>);
  };

  const toggle = (field: 'dietTypes', code: string) => {
    const list = draft[field];
    update({ [field]: list.includes(code) ? list.filter((item) => item !== code) : [...list, code] } as Partial<PlanDraft>);
  };

  // 저장하려면 식단에 답해야 한다. 「모르면 안전하다고 치지 않는」 자리라, 비운 채로
  // 저장하면 확인 화면이 다시 막는다.
  //
  // 🔴 알레르기는 이 조건에서 «빠져야» 한다. 질문을 지웠으므로 allergyAnswered 가 영영
  //    false 인 사람이 생기고, 남겨 두면 그런 사람은 저장 단추를 영영 못 누른다.
  const savable = draft.dietAnswered
    && (draft.dietStatus !== 'VALUES' || draft.dietTypes.length > 0);

  // 음식 취향에서 와서 이번 여행에 더해지는 식단 — 서버 규칙과 같은 셈(tasteDiets.ts).
  const tasteDiets = tasteDietsApplied(draft).map((code) => { const found = DIETS.find(([c]) => c === code); return found ? tx(found[1], found[2]) : code; });

  const statusRow = (
    label: string,
    field: 'dietStatus',
    answered: 'dietAnswered',
    values: 'dietTypes',
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
        <Animated.View onStartShouldSetResponder={() => true} style={[styles.sheet, phone ? styles.sheetPhone : styles.sheetWide, phone && { transform: [{ translateY: dragY }] }]}>
          <View testID="conditions-sheet-grab" {...(phone ? dragHandlers : {})}>
          {phone ? (
            <View testID="conditions-sheet-handle" style={styles.handleWrap} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
              <View style={styles.handle} />
            </View>
          ) : null}
          <View style={[styles.header, phone && styles.headerPhone]}>
            <Text variant="title" weight="bold">{tx('여행 조건 미리 알려주기', 'Tell us your travel conditions')}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={() => onClose('DISMISSED')} style={styles.close}>
              <Text variant="title" weight="bold">✕</Text>
            </Pressable>
          </View>
          </View>

          <ScrollView style={styles.bodyScroll} contentContainerStyle={styles.body} keyboardShouldPersistTaps="handled">
            <Text color={color.text.body} style={styles.intro}>
              {reprompt
                ? tx('일정을 만들기 전에 여행 조건을 알려주실래요? 건너뛰면 다음 「일정 물어보기」 때 다시 물어요.', 'Shall we take your travel conditions before building the itinerary? If you skip, we will ask again next time.')
                : tx('식단은 안전에 걸리는 것이라, 모르면 안전하다고 치지 않아요. 한 번만 알려주시면 다음부터 안 물어봐요.', 'Diet affects safety — we never assume a place is safe when we do not know. Tell us once and we will not ask again.')}
            </Text>

            <View style={styles.block}>
              <Text weight="bold">{tx('식단', 'Diet')} <Text color={color.state.danger}>*</Text></Text>
              {statusRow(tx('식단', 'Diet'), 'dietStatus', 'dietAnswered', 'dietTypes')}
              {hasNoPlaceData(coverage, COVERAGE_FEATURE.diet) ? <NoPlaceData label={noData} /> : null}
              {draft.dietStatus === 'VALUES' ? (
                <View style={styles.chips}>{DIETS.map(([code, ko, en]) => (
                  <Chip key={code} label={tx(ko, en)} selected={draft.dietTypes.includes(code)} onPress={() => toggle('dietTypes', code)} />
                ))}</View>
              ) : null}
              {/* 🔴 음식 취향의 채식·할랄은 서버가 식단 조건처럼 반드시 지킨다(S15P21E201-1873) — 여기서 안 말하면
                  「식단: 해당 없음」 칸만 보고 왜 고기집이 빠졌는지 모른다. 이번만 빼는 길(해당 없음)도 같이 말한다(S15P21E201-1878). */}
              {tasteDiets.length ? (
                <Text testID="taste-diet-note" variant="caption" color={color.text.body}>
                  {txf(tx, '음식 취향에서 고른 「%s」도 이번 여행에 반드시 지켜요. 이번만 빼려면 「해당 없음」을 고르세요.', 'The %s you chose in your food preferences also applies to this trip. To leave it out this time, choose “None”.', tasteDiets.join('·'))}
                </Text>
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

              {/* 🔴 마지막 칸은 이 줄이 «실제로 보는» 장소 표식이다 (S15P21E201-1044).
                  「이동 조건」으로 뭉뚱그리지 않는다 — 접근성은 자료가 있고 계단은 0곳이라,
                  뭉치면 있는 쪽이 없는 쪽을 덮어 계단 줄이 계속 못 지키는 약속으로 남는다. */}
              {/* 🔴 그늘은 「그늘 많은 곳 우선」이다 — 「그늘길 우선」이라고 적었더니 길을 골라 주는 것처럼 읽혔는데,
                  이 답은 장소 점수에만 들어가고 걷는 길은 안 바꾼다. 저장하는 값(PREFER)은 그대로다. */}
              {([
                ['slopeConstraint', '가파른 경사 피하기', 'Avoid steep slopes', 'AVOID', 'ALLOW', COVERAGE_FEATURE.slope],
                ['stairsConstraint', '계단 피하기', 'Avoid stairs', 'AVOID', 'ALLOW', COVERAGE_FEATURE.stairs],
                ['shadePreference', '그늘 많은 곳 우선', 'Prefer shadier places', 'PREFER', 'NO_PREFERENCE', COVERAGE_FEATURE.shade],
              ] as const).map(([field, ko, en, yes, no, featureType]) => (
                <View key={field}>
                  <View style={styles.binaryRow}>
                    <Text style={styles.binaryLabel}>{tx(ko, en)}</Text>
                    <View style={styles.chips}>
                      <Chip label={tx('예', 'Yes')} selected={draft[field] === yes} onPress={() => update({ [field]: yes } as Partial<PlanDraft>)} />
                      <Chip label={tx('아니요', 'No')} selected={draft[field] === no} onPress={() => update({ [field]: no } as Partial<PlanDraft>)} />
                    </View>
                  </View>
                  {hasNoPlaceData(coverage, featureType) ? <NoPlaceData label={noData} /> : null}
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

          <View style={[styles.footer, phone && { paddingBottom: bottomPad }]}>
            <View style={styles.footerLeft}>
              <Pressable accessibilityRole="button" onPress={() => void finish(never ? 'NEVER' : 'LATER')} style={styles.later}>
                <Text variant="caption" weight="bold" color={color.text.body} style={styles.underline}>
                  {reprompt ? tx('이번엔 건너뛰기', 'Skip this time') : tx('나중에', 'Later')}
                </Text>
              </Pressable>
              <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: never }} onPress={() => setNever((on) => !on)} style={styles.never}>
                <Text variant="caption" color={never ? color.action.secondary : color.text.muted}>
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
        </Animated.View>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(25,25,25,0.45)', alignItems: 'center', justifyContent: 'center', padding: spacing[4] },
  backdropPhone: { justifyContent: 'flex-end', padding: 0 },
  sheet: { backgroundColor: color.brand.ivory, overflow: 'hidden' },
  sheetWide: { width: '100%', maxWidth: 640, maxHeight: '88%', borderRadius: radius.lg },
  sheetPhone: { width: '100%', maxHeight: '92%', borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg },
  header: { minHeight: 64, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4], borderBottomWidth: 1, borderColor: color.surface.border },
  handleWrap: { alignItems: 'center', paddingTop: spacing[2], paddingBottom: spacing[1] },
  handle: { width: 36, height: 4, borderRadius: radius.full, backgroundColor: color.surface.field },
  headerPhone: { minHeight: 52 },
  close: { position: 'absolute', right: spacing[2], width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  bodyScroll: { flexShrink: 1 },
  body: { paddingHorizontal: spacing[6], paddingTop: spacing[4], paddingBottom: spacing[4] },
  intro: { marginBottom: spacing[4] },
  block: { gap: spacing[2], marginBottom: spacing[6] },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 44, paddingHorizontal: spacing[4], justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.ivory, borderWidth: 1, borderColor: color.surface.border },
  chipOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  binaryRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], marginTop: spacing[2] },
  // 앞 줄에 딸린 말이라 줄 바로 밑에 붙인다. 위쪽 여백을 주면 다음 줄의 것으로 읽힌다.
  noData: { marginTop: spacing[1] },
  binaryLabel: { flex: 1 },
  saveFailed: { paddingHorizontal: spacing[6], paddingTop: spacing[3] },
  footer: { gap: spacing[3], padding: spacing[6], paddingBottom: spacing[8], borderTopWidth: 1, borderColor: color.surface.border },
  footerLeft: { gap: spacing[1] },
  later: { minHeight: 32, justifyContent: 'center' },
  underline: { textDecorationLine: 'underline' },
  never: { minHeight: 32, justifyContent: 'center' },
  save: { minHeight: 48 },
});
