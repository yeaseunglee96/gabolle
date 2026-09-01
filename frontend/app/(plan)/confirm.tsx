// 09 최종 확인 — Figma 09_최종 확인 실측 그대로.
//
// 🔴 이 화면도 taste.tsx 와 같은 이유로 지금은 어디서도 안 들어온다(08 제약·접근성이
// 이미 10 AI 일정 생성으로 바로 넘어가게 커밋돼 있다 — 이 작업 범위 밖이라 안 건드린다).
// 각 항목의 "수정" 링크는 result.tsx 의 기존 "일정 수정" 버튼과 같은 이유로 아직 연결하지
// 않는다 — 눌러서 이동할 화면(날짜/인원 수정 등)이 명세에 없어 임의로 잇지 않는다.
import { View, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { Card } from '@/components/Card';
import { ProgressBar } from '@/components/ProgressBar';
import { Button } from '@/components/Button';
import { LanguageBadge } from '@/components/LanguageBadge';

type SummaryItem = {
  label: string;
  value: string;
};

const SUMMARY: SummaryItem[] = [
  { label: '여행 일정', value: '8.24 ~ 8.25 · 1박 2일' },
  { label: '인원·출발', value: '2명 · 부산역' },
  { label: '선택 취향', value: '바다 · 골목·로컬' },
  { label: '예산', value: '1인 5만 ~ 20만원' },
  { label: '편의 조건', value: '휠체어 접근 우선' },
];

function SummaryRow({ label, value }: SummaryItem) {
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
      <Text variant="caption" weight="bold" color={color.text.eyebrow}>
        수정
      </Text>
    </Card>
  );
}

export default function Confirm() {
  const router = useRouter();

  return (
    <Screen scroll>
      <LanguageBadge />
      <View style={styles.headerRow}>
        <Eyebrow>09 · 최종 확인</Eyebrow>
        <Text variant="eyebrow" weight="bold">
          3/3
        </Text>
      </View>
      <Text variant="display" weight="bold" style={styles.title}>
        입력한 정보를 확인해 주세요
      </Text>

      <ProgressBar progress={1} />

      <View style={styles.summaryList}>
        {SUMMARY.map((item) => (
          <SummaryRow key={item.label} {...item} />
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
