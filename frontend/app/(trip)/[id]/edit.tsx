// 21 일정 편집·부분 재계산 — Figma 21_일정 편집·부분 재계산 실측 그대로.
//
// 🔴 11 여행 결과 화면(result.tsx)의 "일정 수정" 버튼은 아직 이 화면으로 연결돼 있지 않다
// (result.tsx 는 이번 작업 범위 밖이라 건드리지 않는다 — 07 취향 화면과 같은 상태).
// 지금은 URL 로 직접 들어와야 보인다.
//
// 여러 날짜 데이터 모델이 아직 없어 "전체 보기 / 하루 보기" 는 선택 상태만 바뀌고
// 목록 내용은 그대로다(11 화면의 DAY 2 자리표시자와 같은 이유).
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Button } from '@/components/Button';

type ScheduleItem = {
  key: string;
  time: string;
  title: string;
  note: string;
  /** 고정·교체·순서는 이번 범위에서 실제 동작이 없다(대체 후보·재정렬 UI 미정). 제외만 로컬로 토글된다. */
  action: '고정' | '교체' | '제외' | '순서';
};

const INITIAL_SCHEDULE: ScheduleItem[] = [
  { key: 'songdo', time: '10:00', title: '송도 해상 케이블카', note: '고정됨 · 운영 09:00–21:00', action: '고정' },
  { key: 'nampo', time: '12:30', title: '남포동 로컬 맛집', note: '대체 장소 3곳 있음', action: '교체' },
  { key: 'huinnyeoul', time: '15:00', title: '흰여울문화마을', note: '도보 12분 · 체류 90분', action: '제외' },
  { key: 'gwangalli', time: '18:30', title: '광안리 야경', note: '지연 예상 18분', action: '순서' },
];

type ViewMode = '전체' | '하루';

export default function Edit() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const tripId = id ?? 'demo-trip';
  const [viewMode, setViewMode] = useState<ViewMode>('전체');
  const [schedule, setSchedule] = useState(INITIAL_SCHEDULE);
  const [excluded, setExcluded] = useState<Set<string>>(new Set());

  function toggleExclude(key: string) {
    setExcluded((prev) => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  function reset() {
    setSchedule(INITIAL_SCHEDULE);
    setExcluded(new Set());
  }

  return (
    <Screen scroll>
      <Text variant="display" weight="bold">
        일정 편집
      </Text>
      <Text variant="caption" style={styles.subtitle}>
        DAY 1 · 변경할 장소만 골라 수정해요
      </Text>

      <View style={styles.chipsRow}>
        {(['전체', '하루'] as ViewMode[]).map((mode) => {
          const label = mode === '전체' ? '전체 보기' : '하루 보기';
          const active = mode === viewMode;
          return (
            <Pressable
              key={mode}
              onPress={() => setViewMode(mode)}
              style={[styles.chip, active ? styles.chipActive : styles.chipInactive]}
            >
              <Text variant="caption" weight="bold" color={active ? color.text.onAction : color.text.heading}>
                {label}
              </Text>
            </Pressable>
          );
        })}
        {/* TODO: 실행 취소 스택 미정 — 지금은 "제외" 로 뺀 항목만 되돌린다. */}
        <Pressable onPress={reset} style={[styles.chip, styles.chipInactive]}>
          <Text variant="caption" weight="bold" color={color.text.heading}>
            ↶ 되돌리기
          </Text>
        </Pressable>
      </View>

      <View style={styles.list}>
        {schedule.map((item) => {
          const isExcluded = excluded.has(item.key);
          const canToggle = item.action === '제외';
          return (
            <View key={item.key} style={[styles.card, isExcluded && styles.cardExcluded]}>
              <Text variant="caption" weight="bold" color={color.action.secondary}>
                {item.time}
              </Text>
              <View style={styles.cardBody}>
                <Text variant="body" weight="bold">
                  {item.title}
                </Text>
                <Text variant="caption" style={styles.cardNote}>
                  {item.note}
                </Text>
              </View>
              <Pressable
                disabled={!canToggle}
                onPress={() => toggleExclude(item.key)}
                style={styles.cardAction}
              >
                <Text
                  variant="caption"
                  weight="bold"
                  color={item.action === '순서' ? color.state.danger : color.action.secondary}
                >
                  {canToggle && isExcluded ? '포함' : item.action}
                </Text>
              </Pressable>
            </View>
          );
        })}
      </View>

      <View style={styles.delayCard}>
        <Text variant="caption" weight="bold" style={styles.delayText}>
          ⏱ 현재 속도라면 광안리 도착이 18분 늦어요{'\n'}이 날만 다시 계산하면 다른 날짜는 유지돼요.
        </Text>
      </View>

      <Button
        label="선택한 날짜만 다시 계산"
        variant="secondary"
        containerStyle={styles.cta}
        onPress={() => router.push(`/${tripId}/result`)}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  subtitle: {
    marginTop: spacing[1],
    marginBottom: spacing[4],
  },
  chipsRow: {
    flexDirection: 'row',
    gap: spacing[2],
  },
  chip: {
    borderRadius: radius.full,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[2],
  },
  chipActive: {
    backgroundColor: color.action.secondary,
  },
  chipInactive: {
    backgroundColor: color.surface.tint,
  },
  list: {
    marginTop: spacing[4],
    gap: spacing[3],
  },
  card: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing[3],
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    padding: spacing[4],
  },
  cardExcluded: {
    opacity: 0.5,
  },
  cardBody: {
    flex: 1,
    gap: spacing[1],
  },
  cardNote: {
    color: color.text.body,
  },
  cardAction: {
    paddingVertical: spacing[1],
    paddingHorizontal: spacing[1],
  },
  delayCard: {
    marginTop: spacing[4],
    backgroundColor: color.state.warningBg,
    borderRadius: radius.md,
    padding: spacing[4],
  },
  delayText: {
    color: color.text.heading,
  },
  cta: {
    marginTop: spacing[6],
  },
});
