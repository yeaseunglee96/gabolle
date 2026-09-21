// 조건 화면 왼쪽 기둥 — **넓은 화면에만** 있다 (시안 ①).
//
// 🔴 질문에 답하는 동안 「내가 무엇을 근거로 답하고 있는지」를 옆에 세워 둔다.
//    홈에서 이미 받은 날짜·출발지·인원과, 계정에 기억된 취향이 그것이다.
//    이것이 없으면 사람은 같은 것을 또 묻는 줄 알고 되돌아간다.
import { Image, Pressable, StyleSheet, View } from 'react-native';

import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

const COVER = require('../../assets/home/haeundae.png');

export type ContextCard = { label: string; value: string };

export function PlanContextColumn({
  headerChips,
  dateLine,
  originLine,
  tastes,
  onEditGiven,
  onOpenTastes,
  tx,
}: {
  headerChips: string[];
  /** 「10월 3일 – 5일 · 3일」. 홈에서 받은 것이라 여기서는 못 고친다. */
  dateLine: string;
  /** 「부산역 출발 · 성인 2」. */
  originLine: string;
  /** 계정에 기억된 취향 — 없으면 카드를 안 그린다. */
  tastes: string[];
  onEditGiven: () => void;
  onOpenTastes: () => void;
  tx: (ko: string, en: string) => string;
}) {
  return (
    <View style={styles.column}>
      <Eyebrow>{tx('여행 만들기', 'Plan a trip')}</Eyebrow>
      <Text variant="hero" weight="bold" color={color.text.heading}>
        {tx('이번 부산 여행,\n어떻게 다닐까요?', 'How do you want to\ntravel Busan?')}
      </Text>
      <Text color={color.text.muted}>
        {tx('홈에서 받은 출발지·날짜·인원은 그대로 써요. 여기서는 취향과 조건만 열 가지 물어보고, 답한 만큼 일정이 정확해져요.',
          'We keep the origin, dates and party size from the home screen. These ten questions are about taste and constraints.')}
      </Text>

      {/* 🔴 홈에서 받은 것 — 사진 위에 얹는다. 사진이 밝을 수 있어 어둡게 덮고 올린다. */}
      <View style={styles.given}>
        {/* 🔴 사진을 네이비 **위에 28% 로 얹는다**(시안). 사진을 깔고 어둡게 덮는 것과는
            결과가 다르다 — 사진마다 밝기가 달라도 카드 색이 흔들리지 않는다. */}
        <Image source={COVER} resizeMode="cover" accessibilityLabel="" style={styles.givenPhoto} />
        <View style={styles.givenBody}>
          <View style={styles.givenTop}>
            <View style={styles.givenBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('홈에서 받은 정보', 'From the home screen')}</Text></View>
            <Pressable accessibilityRole="button" onPress={onEditGiven} style={({ pressed }) => [styles.givenEdit, pressed && styles.pressed]}>
              <Text variant="caption" weight="bold" color={color.brand.navy}>{tx('수정', 'Edit')}</Text>
            </Pressable>
          </View>
          <Text variant="title" weight="bold" color={color.text.onAction} numberOfLines={1}>{dateLine}</Text>
          <Text variant="caption" color={color.text.onDarkMuted} numberOfLines={1}>{originLine}</Text>
          <View style={styles.givenChips}>
            {headerChips.map((chip) => (
              <View key={chip} style={styles.givenChip}><Text variant="caption" weight="bold" color={color.text.onAction} numberOfLines={1}>{chip}</Text></View>
            ))}
          </View>
        </View>
      </View>

      {/* 🔴 취향이 하나도 없으면 카드를 안 그린다. 빈 카드는 「고장났다」로 읽힌다. */}
      {tastes.length ? (
        <View style={styles.taste}>
          <View style={styles.tasteTop}>
            <Text variant="caption" weight="bold" color={color.text.heading}>{tx('계정에 기억된 취향', 'Saved on your account')}</Text>
            <Pressable accessibilityRole="button" onPress={onOpenTastes}>
              <Text variant="caption" weight="bold" color={color.text.muted}>{tx('마이페이지 › 여행 취향', 'My page › Preferences')}</Text>
            </Pressable>
          </View>
          <View style={styles.tasteChips}>
            {tastes.map((taste) => (
              <View key={taste} style={styles.tasteChip}><Text variant="caption" weight="bold">{taste}</Text></View>
            ))}
          </View>
          <Text variant="caption" color={color.text.muted}>
            {tx('이미 아는 건 다시 묻지 않고 기본값으로 채워요. 이번 여행에만 다르게 하고 싶으면 질문에서 바꾸면 돼요.',
              'We prefill what we already know. Change it in the questions if this trip is different.')}
          </Text>
        </View>
      ) : null}

      <View style={styles.mini}>
        {([
          ['걸리는 시간', 'Takes about', '약 2분', 'about 2 min'],
          ['추천 코스', 'Courses', '3안 비교', 'compare 3'],
          ['나중에', 'Later', '언제든 수정', 'edit anytime'],
        ] as const).map(([labelKo, labelEn, valueKo, valueEn]) => (
          <View key={labelKo} style={styles.miniCard}>
            <Text variant="caption" color={color.text.muted} numberOfLines={1}>{tx(labelKo, labelEn)}</Text>
            <Text weight="bold" numberOfLines={1}>{tx(valueKo, valueEn)}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  column: { width: 440, gap: spacing[4] },

  given: { height: 132, borderRadius: radius.lg, overflow: 'hidden', backgroundColor: color.brand.navy },
  givenPhoto: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, opacity: 0.28 },
  givenBody: { flex: 1, gap: spacing[1], padding: spacing[4], justifyContent: 'center' },
  givenTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] },
  givenBadge: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.16)' },
  givenEdit: { minHeight: 28, justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.card },
  givenChips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[1], marginTop: spacing[1] },
  givenChip: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.16)' },
  pressed: { opacity: 0.72 },

  taste: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  tasteTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] },
  tasteChips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  tasteChip: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },

  mini: { flexDirection: 'row', gap: spacing[2] },
  miniCard: { flex: 1, gap: 2, padding: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
});
