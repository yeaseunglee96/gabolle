// 07 취향 선택 — Figma 07_취향 선택 실측 그대로.
//
// 🔴 이 화면은 지금 어디서도 안 들어온다. 06 기본 조건 설정이 이미 08 제약·접근성으로
// 바로 넘어가게 커밋돼 있어서다(이 작업 범위 밖이라 그 파일은 건드리지 않는다).
// 04 로그인·09 최종 확인도 같은 상태다 — 화면부터 채우고 전체 흐름을 잇는 건 다음 작업이다.
// 그래서 이 화면의 CTA 는 Figma 번호 순서상 다음인 08(제약·접근성)로 보낸다.
import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { Button } from '@/components/Button';
import { LanguageBadge } from '@/components/LanguageBadge';
import { PlanStepHeader } from '@/plan/PlanStepHeader';
import { usePlan } from '@/plan/PlanProvider';

type PreferenceKey = 'sea' | 'alley' | 'food' | 'nature' | 'night' | 'photo';

type Preference = {
  key: PreferenceKey;
  title: string;
  subtitle: string;
};

const PREFERENCES: Preference[] = [
  { key: 'sea', title: '바다', subtitle: '파도·해안 산책' },
  { key: 'alley', title: '골목 & 로컬', subtitle: '현지인 동네' },
  { key: 'food', title: '미식', subtitle: '시장·로컬 맛집' },
  { key: 'nature', title: '자연 & 힐링', subtitle: '공원·숲길' },
  { key: 'night', title: '야경', subtitle: '빛나는 부산의 밤' },
  { key: 'photo', title: '사진 명소', subtitle: '인생 사진 스팟' },
];

const MAX_PREFERENCES = 3;

type FoodKey = 'milmyeon' | 'porkSoup' | 'seedHotteok';

const FOODS: { key: FoodKey; label: string }[] = [
  { key: 'milmyeon', label: '밀면' },
  { key: 'porkSoup', label: '돼지국밥' },
  { key: 'seedHotteok', label: '씨앗호떡' },
];

export default function Taste() {
  const router = useRouter();
  const { draft, update, completeStep } = usePlan();
  const selected = new Set(draft.preferences as PreferenceKey[]);
  const foods = new Set(draft.foods as FoodKey[]);

  function togglePreference(key: PreferenceKey) {
    {
      const next = new Set(selected);
      if (next.has(key)) {
        next.delete(key);
      } else if (next.size < MAX_PREFERENCES) {
        // 최대 3개 — 이미 3개면 새로 누른 항목은 조용히 무시한다(먼저 하나를 빼야 한다).
        next.add(key);
      }
      update({ preferences: [...next] });
    }
  }

  function toggleFood(key: FoodKey) {
    {
      const next = new Set(foods);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      update({ foods: [...next] });
    }
  }

  return (
    <Screen scroll>
      <LanguageBadge />
      <View style={styles.headerRow}>
        <Eyebrow>07 · 여행 만들기</Eyebrow>
        <Text variant="eyebrow" weight="bold">
          2/4
        </Text>
      </View>
      <Text variant="display" weight="bold" style={styles.title}>
        좋아하는 부산을 골라주세요
      </Text>
      <Text variant="caption" style={styles.subtitle}>
        최대 3개 · 선택에 따라 일정 분위기가 달라져요
      </Text>

      <PlanStepHeader current={2} />

      <View style={styles.grid}>
        {PREFERENCES.map((item) => {
          const isSelected = selected.has(item.key);
          return (
            <Pressable key={item.key} onPress={() => togglePreference(item.key)} style={styles.card}>
              <View style={styles.cardImage}>
                {isSelected && (
                  <View style={styles.cardCheck}>
                    <Text variant="body" weight="bold" color={color.text.onAction}>
                      ✓
                    </Text>
                  </View>
                )}
              </View>
              <View style={styles.cardBody}>
                <Text variant="body" weight="bold">
                  {item.title}
                </Text>
                <Text variant="caption">{item.subtitle}</Text>
              </View>
            </Pressable>
          );
        })}
      </View>

      <View style={styles.foodBox}>
        <Text variant="caption" weight="bold" color={color.text.heading}>
          꼭 먹고 싶은 부산 음식
        </Text>
        <View style={styles.foodRow}>
          {FOODS.map((food) => {
            const isSelected = foods.has(food.key);
            return (
              <Pressable
                key={food.key}
                onPress={() => toggleFood(food.key)}
                style={[styles.foodChip, isSelected && styles.foodChipSelected]}
              >
                <Text
                  variant="caption"
                  weight="medium"
                  color={isSelected ? color.text.onAction : color.text.body}
                >
                  {isSelected ? `✓ ${food.label}` : food.label}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </View>

      <Button label="선택 완료 · 다음" disabled={selected.size === 0} containerStyle={styles.cta} onPress={() => { completeStep(2); router.push('/plan/conditions'); }} />
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
    marginTop: spacing[1],
    marginBottom: spacing[4],
  },
  grid: {
    marginTop: spacing[6],
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing[3],
  },
  card: {
    width: '47%',
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    overflow: 'hidden',
  },
  cardImage: {
    height: 88,
    backgroundColor: color.surface.soft,
    alignItems: 'flex-end',
    padding: spacing[2],
  },
  cardCheck: {
    width: 28,
    height: 28,
    borderRadius: radius.full,
    backgroundColor: color.action.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardBody: {
    padding: spacing[3],
    gap: spacing[1],
  },
  foodBox: {
    marginTop: spacing[6],
    backgroundColor: color.surface.soft,
    borderRadius: radius.md,
    padding: spacing[4],
    gap: spacing[3],
  },
  foodRow: {
    flexDirection: 'row',
    gap: spacing[2],
  },
  foodChip: {
    backgroundColor: color.surface.card,
    borderRadius: radius.full,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
  },
  foodChipSelected: {
    backgroundColor: color.action.brand,
  },
  cta: {
    marginTop: spacing[8],
  },
});
