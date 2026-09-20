// 코스 카드 한 장 — 시안 ③.
//
// 🔴 **자리를 바꾸지 않는다.** 고른 카드를 맨 위로 올리거나 「선택한 코스」 칸을 따로
//    만들지 않는다(인계 §10-3). 눌렀는데 목록이 움직이면 사람은 자기가 무엇을 눌렀는지
//    다시 찾아야 하고, 옆의 안과 견주던 흐름이 끊긴다. 고르는 화면에서 그건 치명적이다.
import { txf } from '@/i18n/format';
import { Image, Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import type { TripCourse } from '@/plan/tripCourses';

/** 「코스 A」 · 「코스 B」 … 자리로 부르는 이름. 서버가 제목을 안 줘도 부를 말이 있어야 한다. */
export function courseLetter(index: number) {
  return String.fromCharCode('A'.charCodeAt(0) + index);
}

export function courseFacts(course: TripCourse, ko: boolean): string {
  const { places, moveMin, walkKm } = course.summary;
  return [
    places === null ? null : ko ? `장소 ${places}곳` : `${places} places`,
    moveMin === null ? null : ko ? `이동 ${moveMin}분` : `${moveMin} min travel`,
    walkKm === null ? null : ko ? `${walkKm}km` : `${walkKm} km`,
  ].filter(Boolean).join(' · ');
}

export function courseCost(course: TripCourse, ko: boolean): string | null {
  const cost = course.summary.costKrw;
  if (cost === null) return null;
  return ko ? `${(cost / 10000).toFixed(1)}만원` : `₩${cost.toLocaleString()}`;
}

export function CourseCard({
  course, index, selected, saved, onSelect, onToggleSave, onBuild, tx, ko,
}: {
  course: TripCourse;
  index: number;
  selected: boolean;
  saved: boolean;
  onSelect: () => void;
  onToggleSave: () => void;
  /** 고른 안으로 일정을 만든다. 고른 카드에서만 쓴다. */
  onBuild: () => void;
  tx: (koText: string, enText: string) => string;
  ko: boolean;
}) {
  const cover = course.days[0]?.stops.find((stop) => stop.photoUrl)?.photoUrl ?? null;
  const cost = courseCost(course, ko);

  return (
    <View style={[styles.card, selected && styles.cardOn]}>
      <Pressable
        accessibilityRole="radio"
        accessibilityState={{ selected }}
        accessibilityLabel={txf(tx, '코스 %s %s', 'Course %s %s', courseLetter(index), course.title)}
        onPress={onSelect}
        style={styles.row}
      >
        {/* 🔴 사진이 없는 카드가 기본이다. 장소 사진이 채워진 비율이 아주 낮아서, 사진 자리를
            늘 잡아 두면 회색 띠만 남는다. 있을 때만 얹는다. */}
        {/* 🔴 사진이 없으면 표지 칸을 좁힌다. 200px 짜리 빈 판이 카드 절반을 먹으면
            정작 읽어야 할 일차별 동선이 밀린다 — 없는 사진의 자리를 지켜 줄 이유가 없다. */}
        <View style={[styles.cover, cover ? null : styles.coverEmpty]}>
          {cover ? <Image source={{ uri: cover }} resizeMode="cover" accessibilityLabel="" style={styles.coverPhoto} /> : null}
          <View style={[styles.badge, selected && styles.badgeOn]}>
            <Text variant="caption" weight="bold" color={color.text.onAction} numberOfLines={1}>
              {selected ? tx('✓ 내 일정으로', '✓ My itinerary') : txf(tx, '코스 %s', 'Course %s', courseLetter(index))}
            </Text>
          </View>
          {/* 🔴 ☆ 저장은 고르기와 **다른 일**이다(인계 §7③) — 보관함에 담아 두는 것이지 이
              여행으로 정하는 것이 아니다. 표지 모서리에 둔다.
              본문 쪽에 두면 「확인됨」 배지를 덮어 글자가 반쪽만 보인다 — 실제로 그랬다. */}
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ selected: saved }}
            accessibilityLabel={saved ? tx('저장 취소', 'Unsave') : tx('이 코스 저장해 두기', 'Save this course')}
            onPress={onToggleSave}
            style={({ pressed }) => [styles.save, pressed && styles.pressed]}
          >
            <Text variant="caption" weight="bold" color={saved ? color.action.secondary : color.text.muted}>{saved ? '★' : '☆'}</Text>
          </Pressable>
        </View>

        <View style={styles.body}>
          <View style={styles.titleRow}>
            <Text variant="title" weight="bold" numberOfLines={1} style={styles.title}>
              {course.title || txf(tx, '코스 %s', 'Course %s', courseLetter(index))}
            </Text>
            <View style={[styles.status, course.status === 'CONFIRMED' ? styles.statusOk : styles.statusEst]}>
              <Text variant="caption" weight="bold" numberOfLines={1}>
                {course.status === 'CONFIRMED' ? tx('확인됨', 'Verified') : tx('추정', 'Estimated')}
              </Text>
            </View>
          </View>
          {course.tagline ? <Text variant="caption" color={color.text.muted} numberOfLines={2}>{course.tagline}</Text> : null}

          {/* 일차마다 한 줄 — 「1일차  A → B → C」. 접어 두면 세 안을 견줄 수가 없다. */}
          {course.days.map((day) => (
            <View key={day.day} style={styles.dayRow}>
              <View style={styles.dayPill}>
                <Text variant="caption" weight="bold">{tx(`${day.day}일차`, `Day ${day.day}`)}</Text>
              </View>
              <Text variant="caption" color={color.text.body} numberOfLines={1} style={styles.dayLine}>
                {day.stops.map((stop) => stop.name).join(' → ')}
              </Text>
            </View>
          ))}

          <View style={styles.bottom}>
            <View style={styles.costBlock}>
              {cost ? (
                <>
                  <Text variant="title" weight="bold">{cost}</Text>
                  <Text variant="caption" color={color.text.muted}>{tx('예상', 'est.')}</Text>
                </>
              ) : (
                // 🔴 모르는 값을 0원으로 적지 않는다. 0원은 「무료」라는 뜻이다.
                <Text variant="caption" color={color.text.muted}>{tx('비용 미정', 'Cost unknown')}</Text>
              )}
            </View>
            {selected ? (
              <Pressable accessibilityRole="button" onPress={onBuild} style={({ pressed }) => [styles.build, pressed && styles.pressed]}>
                <Text weight="bold" color={color.text.onAction}>{tx('이 코스로 일정 만들기 →', 'Build this itinerary →')}</Text>
              </Pressable>
            ) : (
              <Pressable accessibilityRole="button" onPress={onSelect} style={({ pressed }) => [styles.pick, pressed && styles.pressed]}>
                <Text weight="bold" color={color.action.secondary}>{tx('이 코스 선택', 'Pick this')}</Text>
              </Pressable>
            )}
          </View>
        </View>
      </Pressable>

    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    position: 'relative', borderRadius: radius.lg, borderWidth: 2, borderColor: color.surface.border,
    backgroundColor: color.surface.card, overflow: 'hidden',
  },
  cardOn: {
    borderColor: color.brand.navy,
    shadowColor: color.brand.navy, shadowOpacity: 0.16, shadowRadius: 16, shadowOffset: { width: 0, height: 6 }, elevation: 6,
  },
  row: { flexDirection: 'row', alignItems: 'stretch', gap: spacing[4] },

  cover: { width: 200, minHeight: 200, backgroundColor: color.surface.soft },
  coverEmpty: { width: 96, minHeight: 0 },
  coverPhoto: { width: '100%', height: '100%' },
  badge: {
    position: 'absolute', top: spacing[3], left: spacing[3],
    paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full,
    backgroundColor: 'rgba(25,25,25,0.78)',
  },
  badgeOn: { backgroundColor: color.brand.navy },

  body: { flex: 1, minWidth: 0, gap: spacing[2], paddingVertical: spacing[4], paddingRight: spacing[4] },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  title: { flex: 1, minWidth: 0 },
  // 🔴 안 줄어들게 한다. 줄어들면 「확인됨」이 「확」으로 잘리고, 잘린 글자는 정보가 아니다.
  status: { flexShrink: 0, paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full },
  statusOk: { backgroundColor: color.state.successBg },
  statusEst: { backgroundColor: color.state.warningBg },

  dayRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  dayPill: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, backgroundColor: color.surface.soft },
  dayLine: { flex: 1, minWidth: 0 },

  bottom: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], marginTop: spacing[1] },
  costBlock: { flexDirection: 'row', alignItems: 'baseline', gap: spacing[1] },
  build: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.brand.navy },
  pick: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.tint },
  pressed: { opacity: 0.82 },

  save: {
    position: 'absolute', top: spacing[2], right: spacing[2],
    width: 32, height: 32, alignItems: 'center', justifyContent: 'center',
    borderRadius: radius.full, backgroundColor: color.surface.card,
  },
});
