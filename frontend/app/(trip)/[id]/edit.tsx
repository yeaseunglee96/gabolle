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
import { Card } from '@/components/Card';
import { Split } from '@/layout/Split';
import { useI18n } from '@/i18n';

type ScheduleItem = {
  key: string;
  time: string;
  titleKo: string; titleEn: string;
  noteKo: string; noteEn: string;
  /** 고정·교체·순서는 이번 범위에서 실제 동작이 없다(대체 후보·재정렬 UI 미정). 제외만 로컬로 토글된다. */
  action: '고정' | '교체' | '제외' | '순서';
};

const ACTION_LABEL: Record<ScheduleItem['action'], [string, string]> = {
  고정: ['고정', 'Lock'],
  교체: ['교체', 'Replace'],
  제외: ['제외', 'Exclude'],
  순서: ['순서', 'Reorder'],
};
const INCLUDE_LABEL: [string, string] = ['포함', 'Include'];

const INITIAL_SCHEDULE: ScheduleItem[] = [
  { key: 'songdo', time: '10:00', titleKo: '송도 해상 케이블카', titleEn: 'Songdo Marine Cable Car', noteKo: '고정됨 · 운영 09:00–21:00', noteEn: 'Locked · Open 09:00–21:00', action: '고정' },
  { key: 'nampo', time: '12:30', titleKo: '남포동 로컬 맛집', titleEn: 'Nampo-dong local eatery', noteKo: '대체 장소 3곳 있음', noteEn: '3 alternative places available', action: '교체' },
  { key: 'huinnyeoul', time: '15:00', titleKo: '흰여울문화마을', titleEn: 'Huinnyeoul Culture Village', noteKo: '도보 12분 · 체류 90분', noteEn: '12 min walk · 90 min stay', action: '제외' },
  { key: 'gwangalli', time: '18:30', titleKo: '광안리 야경', titleEn: 'Gwangalli night view', noteKo: '지연 예상 18분', noteEn: '18 min delay expected', action: '순서' },
];

type ViewMode = '전체' | '하루';

export default function Edit() {
  const router = useRouter();
  const { tx } = useI18n();
  const { id } = useLocalSearchParams<{ id: string }>();
  const tripId = id ?? 'demo-trip';
  const [viewMode, setViewMode] = useState<ViewMode>('전체');
  const [schedule, setSchedule] = useState(INITIAL_SCHEDULE);
  const [excluded, setExcluded] = useState<Set<string>>(new Set());
  // 태블릿 detail 패널("선택 항목 편집")에 띄울 항목. 기본값을 첫 항목으로 둔 이유는
  // result.tsx 와 같다 — 펼쳤을 때 detail 패널이 빈 채로 시작하지 않게 하기 위해서다.
  const [selectedKey, setSelectedKey] = useState(INITIAL_SCHEDULE[0].key);
  const selected = schedule.find((item) => item.key === selectedKey) ?? schedule[0];

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
    <Screen scroll wide>
      <Text variant="display" weight="bold">
        {tx('일정 편집', 'Edit itinerary')}
      </Text>
      <Text variant="caption" style={styles.subtitle}>
        {tx('DAY 1 · 변경할 장소만 골라 수정해요', 'DAY 1 · Pick only the places you want to change')}
      </Text>

      <View style={styles.chipsRow}>
        {(['전체', '하루'] as ViewMode[]).map((mode) => {
          const label = tx(mode === '전체' ? '전체 보기' : '하루 보기', mode === '전체' ? 'View all' : 'View one day');
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
            {tx('↶ 되돌리기', '↶ Undo')}
          </Text>
        </Pressable>
      </View>

      <Split
        master={
          <View style={styles.list}>
            {schedule.map((item) => {
              const isExcluded = excluded.has(item.key);
              const canToggle = item.action === '제외';
              const isSelected = item.key === selectedKey;
              return (
                <Pressable
                  key={item.key}
                  onPress={() => setSelectedKey(item.key)}
                  style={[styles.card, isExcluded && styles.cardExcluded, isSelected && styles.cardSelected]}
                >
                  <Text variant="caption" weight="bold" color={color.action.secondary}>
                    {item.time}
                  </Text>
                  <View style={styles.cardBody}>
                    <Text variant="body" weight="bold">
                      {tx(item.titleKo, item.titleEn)}
                    </Text>
                    <Text variant="caption" style={styles.cardNote}>
                      {tx(item.noteKo, item.noteEn)}
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
                      {tx(...(canToggle && isExcluded ? INCLUDE_LABEL : ACTION_LABEL[item.action]))}
                    </Text>
                  </Pressable>
                </Pressable>
              );
            })}
          </View>
        }
        detail={
          // 🔴 "선택 항목 편집" 도 Figma·명세에 없는 새 화면이다. 시간·제목을 바꾸는 입력
          // 폼은 이번 레이아웃 작업 범위 밖이라 만들지 않았고, 지금 있는 유일한 실제 동작
          // (제외 토글)을 오른쪽 패널에서 더 크게 다시 보여주는 선에서 멈췄다. 사람 검토 필요.
          <Card tinted style={styles.detailCard}>
            <Text variant="caption" weight="bold" color={color.text.eyebrow}>
              {tx('선택 항목 편집', 'Edit selected item')}
            </Text>
            <Text variant="caption" weight="bold" color={color.action.secondary}>
              {selected.time}
            </Text>
            <Text variant="title" weight="bold">
              {tx(selected.titleKo, selected.titleEn)}
            </Text>
            <Text variant="body" style={styles.cardNote}>
              {tx(selected.noteKo, selected.noteEn)}
            </Text>
            <Pressable
              disabled={selected.action !== '제외'}
              onPress={() => toggleExclude(selected.key)}
              style={styles.detailAction}
            >
              <Text
                variant="body"
                weight="bold"
                color={selected.action === '순서' ? color.state.danger : color.action.secondary}
              >
                {tx(...(selected.action === '제외' && excluded.has(selected.key) ? INCLUDE_LABEL : ACTION_LABEL[selected.action]))}
              </Text>
            </Pressable>
          </Card>
        }
      />

      <View style={styles.delayCard}>
        <Text variant="caption" weight="bold" style={styles.delayText}>
          {tx('⏱ 현재 속도라면 광안리 도착이 18분 늦어요\n이 날만 다시 계산하면 다른 날짜는 유지돼요.', "⏱ At this pace you'll arrive 18 min late to Gwangalli\nRecalculating just this day keeps the other days as they are.")}
        </Text>
      </View>

      <Button
        label={tx('선택한 날짜만 다시 계산', 'Recalculate only the selected day')}
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
  cardSelected: {
    backgroundColor: color.surface.tint,
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
  detailCard: {
    gap: spacing[1],
  },
  detailAction: {
    marginTop: spacing[2],
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
