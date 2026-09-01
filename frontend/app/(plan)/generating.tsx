// 10 AI 일정 생성 — Figma 10_AI 일정 생성 실측 그대로.
import { StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { ProgressBar } from '@/components/ProgressBar';
import { Button } from '@/components/Button';

type StepStatus = 'done' | 'active' | 'waiting';

type Step = {
  label: string;
  status: StepStatus;
};

const STEPS: Step[] = [
  { label: '여행 조건 확인', status: 'done' },
  { label: '현지인 추천 장소 탐색', status: 'done' },
  { label: '혼잡도와 이동 시간 계산', status: 'active' },
  { label: '무장애·편의 정보 검증', status: 'waiting' },
  { label: '최적 경로 구성', status: 'waiting' },
];

const STATUS_LABEL: Record<StepStatus, string> = {
  done: '완료',
  active: '진행 중',
  waiting: '대기',
};

const STATUS_MARK: Record<StepStatus, string> = {
  done: '✓',
  active: '●',
  waiting: '○',
};

const STATUS_COLOR: Record<StepStatus, string> = {
  done: color.state.success,
  active: color.text.eyebrow,
  waiting: color.text.body,
};

export default function Generating() {
  const router = useRouter();

  return (
    <Screen>
      <Eyebrow>10 · AI 일정 생성</Eyebrow>
      <Text variant="display" weight="bold" style={styles.title}>
        AI가 일정을 만들고 있어요
      </Text>
      <Text variant="body" style={styles.subtitle}>
        미리님의 조건에 맞는 부산 여행을 설계 중이에요.
      </Text>

      <View style={styles.character}>
        {/* TODO: 동백이 AI생성 캐릭터 PNG. 자산이 오기 전까지 원형 브랜드 색 면으로 대체한다. */}
      </View>

      <Text variant="body" weight="medium" color={color.text.eyebrow} style={styles.hint}>
        잠시만요, 딱 맞는 동선을 찾고 있어요!
      </Text>

      <View style={styles.steps}>
        {STEPS.map((step) => (
          <View key={step.label} style={styles.step}>
            <Text
              variant="body"
              weight={step.status === 'waiting' ? 'medium' : 'bold'}
              color={STATUS_COLOR[step.status]}
              style={styles.stepLabel}
            >
              {STATUS_MARK[step.status]}  {step.label}
            </Text>
            <Text variant="caption">{STATUS_LABEL[step.status]}</Text>
          </View>
        ))}
      </View>

      <View style={styles.footer}>
        <ProgressBar progress={0.6} thickness={7} />
        <Button
          label="백그라운드에서 계속"
          variant="ghost"
          containerStyle={styles.cta}
          // (trip)/[id]/result 는 동적 경로라 id 가 있어야 매칭된다. 실제 여행 생성 API 가
          // 아직 없어서 데모용 id 를 그대로 쓴다.
          onPress={() => router.push('/demo-trip/result')}
        />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: {
    marginTop: spacing[1],
  },
  subtitle: {
    marginTop: spacing[1],
    color: color.text.body,
  },
  character: {
    alignSelf: 'center',
    width: 166,
    height: 166,
    borderRadius: radius.full,
    backgroundColor: color.action.primary,
    marginTop: spacing[6],
  },
  hint: {
    textAlign: 'center',
    marginTop: spacing[4],
  },
  steps: {
    marginTop: spacing[8],
    gap: spacing[4],
  },
  step: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  stepLabel: {
    flex: 1,
  },
  footer: {
    marginTop: 'auto',
    gap: spacing[4],
  },
  cta: {
    marginTop: 0,
  },
});
