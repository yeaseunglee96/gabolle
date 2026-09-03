// 09 최종 확인 — Figma 09_최종 확인 실측 그대로.
//
// 🔴 이 화면도 taste.tsx 와 같은 이유로 지금은 어디서도 안 들어온다(08 제약·접근성이
// 이미 10 AI 일정 생성으로 바로 넘어가게 커밋돼 있다 — 이 작업 범위 밖이라 안 건드린다).
// 각 항목의 "수정" 링크는 result.tsx 의 기존 "일정 수정" 버튼과 같은 이유로 아직 연결하지
// 않는다 — 눌러서 이동할 화면(날짜/인원 수정 등)이 명세에 없어 임의로 잇지 않는다.
import { useEffect } from 'react';
import { View, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { Card } from '@/components/Card';
import { Button } from '@/components/Button';
import { LanguageBadge } from '@/components/LanguageBadge';
import { PlanStepHeader } from '@/plan/PlanStepHeader';
import { usePlan } from '@/plan/PlanProvider';

type SummaryItem = {
  label: string;
  value: string;
};

function SummaryRow({ label, value, onPress }: SummaryItem & { onPress: () => void }) {
  return (
    <Card style={styles.summaryCard}>
      <View style={styles.summaryText}>
        <Text variant="caption" weight="medium" color={color.text.body}>
          {label}
        </Text>
        <Text variant="body" weight="bold" style={styles.summaryValue}>
          {value}
        </Text>
      </View>
      <Button label="수정" variant="ghost" onPress={onPress} />
    </Card>
  );
}

export default function Confirm() {
  const router = useRouter();
  const { draft, basicComplete } = usePlan();
  useEffect(() => {
    if (!basicComplete) router.replace('/plan/basic');
  }, [basicComplete, router]);
  const summary: (SummaryItem & { path: '/plan/basic' | '/plan/taste' | '/plan/conditions' })[] = [
    { label: '여행 일정', value: `${draft.startDate || '미입력'} ~ ${draft.endDate || '미입력'}`, path: '/plan/basic' },
    { label: '인원·출발', value: `${draft.travelers}명 · ${draft.origin || '미입력'}`, path: '/plan/basic' },
    { label: '이동 수단', value: ({ TRANSIT: '대중교통', WALK: '도보 위주', CAR: '렌터카' } as const)[draft.transport], path: '/plan/basic' },
    { label: '선택 취향', value: draft.preferences.map((value) => ({ sea: '바다', alley: '골목·로컬', food: '미식', nature: '자연·힐링', night: '야경', photo: '사진 명소' }[value] ?? value)).join(' · ') || '미선택', path: '/plan/taste' },
    { label: '먹고 싶은 음식', value: draft.foods.join(' · ') || '없음', path: '/plan/taste' },
    { label: '1인 예산', value: `${draft.budgetPerPerson.toLocaleString()}원`, path: '/plan/conditions' },
    { label: '걷기 강도', value: ({ LOW: '여유롭게', MEDIUM: '보통', HIGH: '활동적' } as const)[draft.walkingLevel], path: '/plan/conditions' },
    { label: '동반 유형', value: ({ SOLO: '혼자', COUPLE: '커플', FRIENDS: '친구', FAMILY: '가족' } as const)[draft.companionType], path: '/plan/conditions' },
    { label: '접근성', value: draft.accessibilityNeeds.join(' · ') || '없음', path: '/plan/conditions' },
    { label: '알레르기', value: draft.allergies.join(' · ') || '해당 없음', path: '/plan/conditions' },
  ];

  return (
    <Screen scroll>
      <LanguageBadge />
      <View style={styles.headerRow}>
        <Eyebrow>09 · 최종 확인</Eyebrow>
        <Text variant="eyebrow" weight="bold">
          4/4
        </Text>
      </View>
      <Text variant="display" weight="bold" style={styles.title}>
        입력한 정보를 확인해 주세요
      </Text>

      <PlanStepHeader current={4} />

      <View style={styles.summaryList}>
        {summary.map((item) => (
          <SummaryRow key={item.label} {...item} onPress={() => router.push(item.path)} />
        ))}
      </View>

      <Card tinted style={styles.notice}>
        <Text variant="caption" weight="bold" color={color.text.eyebrow}>
          AI가 현지 정보·혼잡도·이동 부담을 함께 고려해요.
        </Text>
      </Card>

      <Button
        label="이 조건으로 일정 만들기"
        containerStyle={styles.cta}
        disabled={!basicComplete || draft.preferences.length === 0}
        onPress={() => router.push('/generating')}
      />
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
    marginBottom: spacing[4],
  },
  summaryList: {
    marginTop: spacing[6],
    gap: spacing[3],
  },
  summaryCard: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    borderRadius: radius.lg,
  },
  summaryText: {
    flex: 1,
    gap: spacing[1],
  },
  summaryValue: {
    marginTop: spacing[1],
  },
  notice: {
    marginTop: spacing[6],
  },
  cta: {
    marginTop: spacing[8],
  },
});
