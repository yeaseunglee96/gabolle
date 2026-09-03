// 08 제약·접근성 — 이 앱의 차별점. Figma 08_제약·접근성 실측 그대로.
//
// 지금은 각 카드를 통째로 탭하면 선택/해제만 되게 한다(값이 실제로 어디 저장되진 않는다).
// 카드별 세부 옵션 편집기는 범위 밖이라 단순화했다 — 접근성 카드의 tint 배경은 선택 상태가
// 아니라 Figma 가 이미 고정해 둔 강조라서, 선택 표시(체크)는 tint 와 별도로 얹는다.
import { useEffect } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { Button } from '@/components/Button';
import { LanguageBadge } from '@/components/LanguageBadge';
import { PlanStepHeader } from '@/plan/PlanStepHeader';
import { usePlan } from '@/plan/PlanProvider';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';

type ConstraintKey = 'budget' | 'walking' | 'diet' | 'accessibility' | 'companions';

type ConstraintItem = {
  key: ConstraintKey;
  title: string;
  description: string;
  /** 08 화면에서 접근성 카드만 배경이 강조돼 있다 — 선택 여부와 무관한 고정값. */
  tinted?: boolean;
};

const ITEMS: ConstraintItem[] = [
  { key: 'budget', title: '예산 범위', description: '1인 50,000원  ─────●──  200,000원' },
  { key: 'walking', title: '걷기 강도', description: '여유롭게     보통     활동적' },
  { key: 'diet', title: '식단·알레르기', description: '해산물 제외 · 채식 우선' },
  { key: 'accessibility', title: '접근성', description: '휠체어 접근 · 유아차 이동', tinted: true },
  { key: 'companions', title: '동반 유형', description: '혼자 · 커플 · 가족 · 친구' },
];

export default function Constraints() {
  const router = useRouter();
  const { mobility } = useOnboardingPreferences();
  const { draft, update, completeStep } = usePlan();
  const selected = new Set<ConstraintKey>([
    ...(draft.dietTypes.length || draft.allergies.length ? ['diet' as const] : []),
    ...(draft.accessibilityNeeds.length ? ['accessibility' as const] : []),
  ]);

  useEffect(() => {
    if (mobility !== 'none' && draft.accessibilityNeeds.length === 0) update({ accessibilityNeeds: [mobility.toUpperCase()] });
  }, [draft.accessibilityNeeds.length, mobility, update]);

  function toggle(key: ConstraintKey) {
    if (key === 'accessibility') update({ accessibilityNeeds: draft.accessibilityNeeds.length ? [] : ['WHEELCHAIR_ROUTE'] });
    if (key === 'diet') update({ dietTypes: draft.dietTypes.length ? [] : ['VEGETARIAN_PREFERRED'] });
  }

  return (
    <Screen scroll>
      <LanguageBadge />
      <View style={styles.headerRow}>
        <Eyebrow>08 · 접근성</Eyebrow>
        <Text variant="eyebrow" weight="bold">
          선택 설정
        </Text>
      </View>
      <Text variant="display" weight="bold" style={styles.title}>
        제약 조건을 알려주세요
      </Text>

      <PlanStepHeader current={3} />

      <Text variant="caption" style={styles.subtitle}>
        더 편안한 일정을 위해 필요한 항목만 선택해요.
      </Text>

      {mobility !== 'none' && (
        <View style={styles.welcomePreference}>
          <Text variant="caption" weight="bold" color={color.text.eyebrow}>
            첫 화면에서 선택한 이동 조건
          </Text>
          <Text variant="body" weight="bold">
            {mobility === 'wheelchair' ? '휠체어' : mobility === 'stroller' ? '유아차' : '천천히 걷기'}
          </Text>
        </View>
      )}

      <View style={styles.cards}>
        {ITEMS.map((item) => {
          const isSelected = selected.has(item.key);
          return (
            <Pressable
              key={item.key}
              onPress={() => toggle(item.key)}
              style={[
                styles.card,
                { backgroundColor: item.tinted ? color.surface.tint : color.surface.card },
                isSelected && styles.cardSelected,
              ]}
            >
              <View style={styles.cardBody}>
                <Text variant="title" weight="bold">
                  {item.title}
                </Text>
                <Text
                  variant="caption"
                  weight="medium"
                  color={item.tinted ? color.text.eyebrow : color.text.body}
                  style={styles.cardDescription}
                >
                  {item.description}
                </Text>
              </View>
              {isSelected && (
                <View style={styles.check}>
                  <Text variant="caption" weight="bold" color={color.text.onAction}>
                    ✓
                  </Text>
                </View>
              )}
            </Pressable>
          );
        })}
      </View>

      <View style={styles.allergyBox}>
        <Text variant="title" weight="bold">알레르기 하드 제약</Text>
        <Text variant="caption">알레르기는 취향 점수와 섞지 않고 추천 후보에서 제외합니다. 없으면 비워두세요.</Text>
        <TextInput accessibilityLabel="알레르기" value={draft.allergies.join(', ')} onChangeText={(value) => update({ allergies: value.split(',').map((item) => item.trim()).filter(Boolean) })} placeholder="예: 땅콩, 갑각류" style={styles.input} />
      </View>

      <Button label="다음" containerStyle={styles.cta} onPress={() => { completeStep(3); router.push('/plan/confirm'); }} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: spacing[2],
  },
  title: {
    marginTop: spacing[1],
  },
  subtitle: {
    marginTop: spacing[3],
    color: color.text.body,
  },
  cards: {
    marginTop: spacing[6],
    gap: spacing[3],
  },
  welcomePreference: {
    marginTop: spacing[4],
    backgroundColor: color.surface.soft,
    borderRadius: radius.md,
    padding: spacing[4],
    gap: spacing[1],
  },
  card: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    borderRadius: radius.md,
    padding: spacing[4],
    borderWidth: 1.5,
    borderColor: 'transparent',
  },
  cardSelected: {
    borderColor: color.action.primary,
  },
  cardBody: {
    flex: 1,
    gap: spacing[1],
  },
  cardDescription: {
    marginTop: spacing[1],
  },
  check: {
    width: 22,
    height: 22,
    borderRadius: radius.full,
    backgroundColor: color.action.primary,
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: spacing[2],
  },
  allergyBox: { marginTop: spacing[4], gap: spacing[2], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.card },
  input: { minHeight: 50, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, paddingHorizontal: spacing[4], color: color.text.heading },
  cta: {
    marginTop: spacing[8],
  },
});
