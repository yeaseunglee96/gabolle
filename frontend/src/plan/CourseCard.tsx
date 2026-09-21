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

type Tx = (ko: string, en: string) => string;

export function courseFacts(course: TripCourse, tx: Tx): string {
  const { places, moveMin, walkKm } = course.summary;
  return [
    places === null ? null : tx(`장소 ${places}곳`, `${places} places`),
    moveMin === null ? null : tx(`이동 ${moveMin}분`, `${moveMin} min travel`),
    walkKm === null ? null : tx(`${walkKm}km`, `${walkKm} km`),
  ].filter(Boolean).join(' · ');
}

export function courseCost(course: TripCourse, tx: Tx): string | null {
  const cost = course.summary.costKrw;
  if (cost === null) return null;
  return tx(`${(cost / 10000).toFixed(1)}만원`, `₩${cost.toLocaleString()}`);
}

/**
 * 표지 콜라주 — 시안 1절 (S15P21E201-1454).
 *
 * 🔴 **사진 수에 따라 배치가 달라진다.** 3장이면 왼쪽 큰 칸 + 오른쪽 위아래, 2장이면
 *    오른쪽이 두 줄을 차지, 1장이면 한 칸 전체. 0장이면 **표지 칸을 아예 안 그린다** —
 *    회색 띠를 남기지 않는다.
 *
 * 🔴 장소 사진은 354곳이 끝이라 0장인 카드가 흔하다. 그래서 0장이 예외가 아니라 기본이다.
 */
function CourseCover({ photos, extra, tx }: { photos: Array<{ url: string; name: string }>; extra: number; tx: Tx }) {
  if (photos.length === 0) return null;
  const [first, ...rest] = photos;
  return (
    <View style={styles.cover}>
      {/* 큰 칸 — 1장일 때는 전체, 2·3장일 때는 왼쪽 */}
      <View style={photos.length === 1 ? styles.coverFull : styles.coverMain}>
        <CoverPhoto photo={first} tx={tx} />
      </View>
      {rest.length > 0 ? (
        <View style={styles.coverSide}>
          {rest.map((photo, index) => (
            <View key={photo.url} style={styles.coverCell}>
              <CoverPhoto
                photo={photo}
                tx={tx}
                // +N 은 마지막 작은 칸 우하단에만. 2장일 때도 오른쪽 칸이다 —
                // 우상단은 ☆ 자리라 겹친다.
                extra={index === rest.length - 1 ? extra : 0}
              />
            </View>
          ))}
        </View>
      ) : null}
    </View>
  );
}

function CoverPhoto({ photo, extra = 0, tx }: { photo: { url: string; name: string }; extra?: number; tx: Tx }) {
  return (
    <>
      <Image source={{ uri: photo.url }} resizeMode="cover" accessibilityLabel="" style={styles.coverPhoto} />
      {/* 좌하단 이름 꼬리표. +N 이 있는 칸은 그만큼 좁힌다 — 겹치면 둘 다 못 읽는다. */}
      <View style={[styles.coverTag, extra > 0 && styles.coverTagNarrow]}>
        <Text variant="micro" weight="bold" numberOfLines={1} color={color.text.onAction}>{photo.name}</Text>
      </View>
      {extra > 0 ? (
        <View style={styles.coverMore}>
          <Text variant="micro" weight="bold">{txf(tx, '+%s', '+%s', extra)}</Text>
        </View>
      ) : null}
    </>
  );
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
  // 🔴 하루가 아니라 **여행 전체**에서 앞에서부터 최대 3장. 1일차에 사진이 없고
  //    2일차에 있는 코스가 흔하다 — 장소 사진이 354곳뿐이라 듬성듬성하다.
  const photos = course.days
    .flatMap((day) => day.stops)
    .filter((stop) => stop.photoUrl)
    .slice(0, 3)
    .map((stop) => ({ url: stop.photoUrl as string, name: stop.name }));
  // +N — 표지에 못 담은 나머지. 총 장소 수를 모르면 안 그린다(0 으로 짓지 않는다).
  const extra = typeof course.summary.places === 'number'
    ? Math.max(0, course.summary.places - photos.length)
    : 0;
  const cost = courseCost(course, tx);

  /**
   * 🔴 표지에 «얹을» 것인가.
   *
   * <p>사진이 있을 때만 참이다. 사진이 없으면 표지 칸이 96px 로 좁아지는데, 얹은 것들은
   * 그 폭에 맞춰 줄지 않는다 — 배지가 별표 밑을 지나고 본문 제목까지 덮는다.
   */
  const overlayOnCover = photos.length > 0;

  const badgeAndSave = (
    <>
      {/* 🔴 미선택 카드에는 배지가 없다 (시안 1절). 예전에는 「코스 A/B/C」를 붙였는데,
          그 글자는 **자리 이름일 뿐 내용이 아니다** — 제목이 이미 「바다 따라 걷는 코스」라고
          말하고 있고, 그 옆의 「코스 A」는 읽을 것을 하나 늘릴 뿐이다. */}
      {selected ? (
        <View style={[styles.badge, overlayOnCover ? styles.badgeOverlay : null, styles.badgeOn]}>
          <Text variant="caption" weight="bold" color={color.text.onAction} numberOfLines={1}>
            {tx('✓ 내 일정으로', '✓ My itinerary')}
          </Text>
        </View>
      ) : null}
      {/* 🔴 ☆ 저장은 고르기와 **다른 일**이다(인계 §7③) — 보관함에 담아 두는 것이지 이
          여행으로 정하는 것이 아니다. 사진이 있으면 모서리에, 없으면 배지 옆에 둔다. */}
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ selected: saved }}
        accessibilityLabel={saved ? tx('저장 취소', 'Unsave') : tx('이 코스 저장해 두기', 'Save this course')}
        onPress={onToggleSave}
        style={({ pressed }) => [styles.save, overlayOnCover ? styles.saveOverlay : styles.saveInline, pressed && styles.pressed]}
      >
        <Text variant="caption" weight="bold" color={saved ? color.action.secondary : color.text.muted}>{saved ? '★' : '☆'}</Text>
      </Pressable>
    </>
  );

  return (
    <View style={[styles.card, selected && styles.cardOn]}>
      <Pressable
        accessibilityRole="radio"
        accessibilityState={{ selected }}
        accessibilityLabel={txf(tx, '코스 %s %s', 'Course %s %s', courseLetter(index), course.title)}
        onPress={onSelect}
        style={styles.stack}
      >
        {/* 🔴 사진이 없는 카드가 기본이다. 장소 사진이 채워진 비율이 아주 낮아서, 사진 자리를
            늘 잡아 두면 회색 띠만 남는다. 있을 때만 얹는다. */}
        {/* 🔴 사진이 없으면 표지 칸을 좁힌다. 200px 짜리 빈 판이 카드 절반을 먹으면
            정작 읽어야 할 일차별 동선이 밀린다 — 없는 사진의 자리를 지켜 줄 이유가 없다. */}
        <View style={photos.length ? styles.coverWrap : styles.coverNone}>
          <CourseCover photos={photos} extra={extra} tx={tx} />
          {/* 🔴 사진 위에 얹는 것은 **사진이 있을 때만**이다 — S15P21E201-1351.
              사진이 없으면 이 칸은 96px 로 좁아지는데(coverEmpty), 얹은 것들은 그 폭을 모르고
              그대로 그려져 서로 겹쳤다. 「✓ My itinerary」 배지가 ☆ 밑을 지나 본문까지 넘어가
              제목을 덮었다(2026-09-20 실기, versionCode 23, 영어). 좁을 때는 본문 줄에 세운다. */}
          {overlayOnCover ? badgeAndSave : null}
        </View>

        <View style={styles.body}>
          {/* 사진이 없어 얹지 못한 것을 여기서 한 줄로 세운다. 겹칠 자리가 없다. */}
          {overlayOnCover ? null : <View style={styles.markRow}>{badgeAndSave}</View>}
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
  // 🔴 고른 카드의 테두리가 **동백**이다 (시안 1절). 이것은 tokens.ts 의 「선택에 빨강을
  //    쓰지 않는다」와 부딪히지만, 여기서는 채움이 아니라 **선**이고 action.outline 이
  //    「큰 면적이 부담스러울 때 채움 대신 쓰는 붉은 선」으로 정의돼 있어 그 쓰임에 맞다.
  cardOn: {
    borderColor: color.action.outline,
    shadowColor: color.action.outline, shadowOpacity: 0.16, shadowRadius: 16, shadowOffset: { width: 0, height: 6 }, elevation: 6,
  },
  // 위 표지 → 아래 본문. 예전에는 가로였다.
  stack: { flexDirection: 'column' },

  // ── 표지 콜라주 (시안 1절) ───────────────────────────────────────────────
  coverWrap: { height: 200 },
  coverNone: { height: 0 },
  cover: { flex: 1, flexDirection: 'row', gap: 3, backgroundColor: color.surface.card },
  coverFull: { flex: 1, position: 'relative' },
  coverMain: { flex: 2, position: 'relative' },
  coverSide: { flex: 1, gap: 3 },
  coverCell: { flex: 1, position: 'relative' },
  coverPhoto: { width: '100%', height: '100%' },
  // 사진마다 좌하단 이름 꼬리표 — 사진만으로는 어디인지 모른다.
  coverTag: {
    position: 'absolute', left: spacing[2], bottom: spacing[2], maxWidth: '86%',
    paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full,
    backgroundColor: 'rgba(25,25,25,0.72)',
  },
  coverTagNarrow: { maxWidth: '62%' },
  coverMore: {
    position: 'absolute', right: spacing[2], bottom: spacing[2],
    paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full,
    backgroundColor: 'rgba(255,255,255,0.92)',
  },
  // 세워 둘 때의 배지 — 흐름 안에 있으므로 겹칠 자리가 없다.
  badge: {
    flexShrink: 1, minWidth: 0,
    paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full,
    backgroundColor: color.surface.soft,
  },
  // 사진 위에 얹을 때만 절대 위치가 된다.
  badgeOverlay: { position: 'absolute', top: spacing[3], left: spacing[3], backgroundColor: 'rgba(25,25,25,0.78)' },
  badgeOn: { backgroundColor: color.brand.navy },
  markRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },

  body: { flex: 1, minWidth: 0, gap: spacing[2], padding: spacing[4] },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  title: { flex: 1, minWidth: 0 },
  // 🔴 안 줄어들게 한다. 줄어들면 「확인됨」이 「확」으로 잘리고, 잘린 글자는 정보가 아니다.
  status: { flexShrink: 0, paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full },
  statusOk: { backgroundColor: color.state.successBg },
  statusEst: { backgroundColor: color.state.warningBg },

  dayRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  dayPill: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, backgroundColor: color.surface.soft },
  dayLine: { flex: 1, minWidth: 0 },

  // 🔴 flexWrap 이 없으면 긴 글자(「Build this itinerary →」)가 카드 밖으로 밀려 잘린다.
  //    카드가 overflow: 'hidden' 이라 조용히 잘린다 — 실제로 오른쪽 끝이 잘려 있었다.
  bottom: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], marginTop: spacing[1] },
  costBlock: { flexDirection: 'row', flexShrink: 1, minWidth: 0, alignItems: 'baseline', gap: spacing[1] },
  build: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.brand.navy },
  pick: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.tint },
  pressed: { opacity: 0.82 },

  save: {
    flexShrink: 0,
    width: 32, height: 32, alignItems: 'center', justifyContent: 'center',
    borderRadius: radius.full, backgroundColor: color.surface.card,
  },
  saveOverlay: { position: 'absolute', top: spacing[2], right: spacing[2] },
  // 🔴 사진이 없어 본문 줄에 세울 때도 ☆ 는 **오른쪽 끝**이다. 사진이 있는 카드에서는
  //    모서리에 있는데 없는 카드만 왼쪽에 서면, 같은 목록을 훑는 눈이 매번 그것을 다시
  //    찾는다. `marginLeft: 'auto'` 라 배지가 있든 없든 한쪽 끝에 붙는다.
  saveInline: { marginLeft: 'auto' },
});
