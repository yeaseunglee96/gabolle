import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { type RecommendationJobSnapshot, unavailableJob, unavailableRecommendationJobAdapter } from '@/plan/recommendationJob';

const COPY: Record<RecommendationJobSnapshot['state'], { title: string; body: string }> = {
  idle: { title: '생성 요청 전이에요', body: '조건을 확인한 뒤 일정 생성을 시작해 주세요.' },
  submitting: { title: '요청을 보내고 있어요', body: '입력한 조건은 화면을 벗어나도 유지됩니다.' },
  accepted: { title: '요청을 접수했어요', body: '서버에서 작업을 시작할 때까지 기다리고 있어요.' },
  polling: { title: '일정을 만들고 있어요', body: '진행 상태를 확인하고 있습니다.' },
  completed: { title: '일정 생성이 끝났어요', body: '완성된 추천을 확인할 수 있어요.' },
  conflict: { title: '조건 충돌을 확인해 주세요', body: '조건을 자동으로 완화하지 않았어요. 충돌한 조건을 직접 수정해 주세요.' },
  failed: { title: '일정을 만들지 못했어요', body: '입력은 유지되어 있어요. 잠시 후 다시 시도할 수 있습니다.' },
  cancelled: { title: '생성을 취소했어요', body: '입력한 조건은 그대로 남아 있습니다.' },
  unavailable: { title: '일정 생성 기능을 준비하고 있어요', body: '입력한 조건은 그대로 유지됩니다. 조건을 다시 확인할 수 있어요.' },
};

export default function Generating() {
  const router = useRouter(); const { jobId } = useLocalSearchParams<{ jobId?: string }>();
  const [job, setJob] = useState<RecommendationJobSnapshot>(() => jobId ? unavailableJob('생성 요청 상태를 확인하는 연결을 준비하고 있어요. 입력한 조건은 유지됩니다.') : unavailableJob());
  const copy = COPY[job.state];
  async function retry() { if (!job.jobId) { setJob(unavailableJob()); return; } setJob({ ...job, state: 'polling', errorMessage: null }); setJob(await unavailableRecommendationJobAdapter.poll(job.jobId)); }
  async function cancel() { if (!job.jobId || !job.canCancel || !unavailableRecommendationJobAdapter.cancel) return; setJob(await unavailableRecommendationJobAdapter.cancel(job.jobId)); }
  return <Screen style={styles.canvas}>
    <Text variant="caption" weight="bold" color={color.text.eyebrow}>일정 생성</Text><Text variant="display" weight="bold" style={styles.title}>{copy.title}</Text><Text style={styles.body} color={color.text.body}>{job.errorMessage ?? copy.body}</Text>
    <View accessibilityLiveRegion="polite" style={styles.status}><Text weight="bold">현재 상태</Text><Text color={color.text.body}>{job.stage ?? copy.title}</Text>{job.progress !== null && <Text accessibilityRole="progressbar" accessibilityValue={{ min: 0, max: 100, now: job.progress }}>{job.progress}%</Text>}</View>
    <View style={styles.steps}><Text weight="bold">진행 단계</Text>{['요청 접수', '추천 후보 수집', '제약 조건 확인', '동선 최적화', '결과 준비'].map((step) => <View key={step} style={styles.step}><View style={styles.stepDot} /><Text color={color.text.body}>{step}</Text></View>)}</View>
    {(job.state === 'failed' || job.state === 'conflict' || job.state === 'cancelled' || job.state === 'unavailable') && <Button accessibilityRole="button" label="조건 다시 확인" onPress={() => router.replace('/plan/confirm')} />}
    {(job.state === 'failed' || job.state === 'accepted') && <Button accessibilityRole="button" label="다시 시도" variant="ghost" onPress={retry} />}
    {job.canCancel && <Button accessibilityRole="button" label="생성 취소" variant="ghost" onPress={cancel} />}
    <Text variant="caption" color={color.text.muted} style={styles.note}>확인되지 않은 예상 진행률은 표시하지 않아요. 생성 연결이 완료되면 실제 진행 단계만 안내합니다.</Text>
  </Screen>;
}
const styles = StyleSheet.create({ canvas: { backgroundColor: color.brand.ivory, gap: spacing[3] }, title: { marginTop: spacing[2] }, body: { marginBottom: spacing[3] }, status: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: '#e8e4dd' }, steps: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: '#ede9e0' }, step: { minHeight: 32, flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, stepDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: color.text.muted }, note: { marginTop: 'auto' } });
