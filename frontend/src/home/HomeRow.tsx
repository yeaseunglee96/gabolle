// 홈의 가로 줄 하나 — 제목 + 화살표 원 + 옆으로 흐르는 카드들.
//
// 시안 design_handoff_home_airbnb_rows 의 「줄 공통」. 기록·축제·전통시장 셋이 이 부품을
// 같이 쓴다. 줄마다 따로 만들면 간격이 조금씩 어긋나고, 그 어긋남은 나란히 놓고 봐야만
// 보인다 — 한 줄씩 고칠 때는 아무도 못 잡는다.
import { useRef } from 'react';
import { Image, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, desktopGutter, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

const arrowLeft = require('../../assets/icons/home/arrow-left.png');
const arrowRight = require('../../assets/icons/home/arrow-right.png');

/** 한 줄에 일곱 장이 보이는 카드 폭. 시안 1440 에서 184 가 나오는 식이다. */
export function homeCardWidth(width: number) {
  return Math.max(120, Math.floor((width - desktopGutter * 2 - 6 * spacing[3]) / 7));
}

/** 32짜리 원형 단추 — 줄 제목 옆의 「전체 보기」와 좌우 이동에 같이 쓴다. */
function CircleButton({
  icon,
  label,
  onPress,
  filled,
}: {
  icon: number;
  label: string;
  onPress: () => void;
  filled?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      style={({ pressed }) => [styles.circle, filled && styles.circleFilled, pressed && styles.pressed]}
    >
      <Image source={icon} resizeMode="contain" accessibilityIgnoresInvertColors style={styles.circleIcon} />
    </Pressable>
  );
}

export function HomeRow({
  eyebrow,
  title,
  subtitle,
  onOpen,
  openLabel,
  width,
  gutter = desktopGutter,
  /**
   * 좌우 이동 단추. 기본은 웹에서만 — 터치는 손가락이 더 빠르고, 단추를 두면 카드 위에
   * 겹쳐 눌리는 자리가 생긴다(시안 「자주 틀리는 것」 4).
   *
   * 🔴 폰 폭의 웹에서도 `Platform.OS` 는 'web' 이라 그대로 두면 폰에 단추가 뜬다.
   * 그래서 부르는 쪽이 끌 수 있게 열어 둔다.
   */
  arrows = Platform.OS === 'web',
  children,
}: {
  eyebrow?: string;
  title: string;
  subtitle?: string;
  onOpen: () => void;
  openLabel: string;
  width: number;
  gutter?: number;
  arrows?: boolean;
  children: React.ReactNode;
}) {
  const { tx } = useI18n();
  const scroller = useRef<ScrollView>(null);
  const offset = useRef(0);

  // 🔴 2026-09-21 — 카드 «한 장»씩 민다 (시안 design_handoff_home_startbar 3절).
  //
  // 전에는 보이는 폭의 80%씩 밀었다. 그때 적어 둔 이유는 *"한 장씩 밀면 일곱 번
  // 눌러야 하고, 한 화면을 통째로 밀면 방금 본 카드가 사라져 어디까지 봤는지 잃는다"*
  // 였다. 앞의 걱정은 맞았지만 뒤의 걱정이 더 컸다 — 80%도 여섯 장이 한꺼번에
  // 지나가서, 화살표를 누른 사람이 «어디로 갔는지»를 눈으로 따라가지 못했다.
  // 한 장씩이면 누른 만큼만 움직여서 눈이 따라간다. 여러 장을 보려면 여러 번 누른다.
  const step = homeCardWidth(width) + spacing[3];
  const slide = (direction: 1 | -1) => {
    const next = Math.max(0, offset.current + direction * step);
    scroller.current?.scrollTo({ x: next, animated: true });
  };

  return (
    <View style={[styles.row, { paddingTop: gutter, paddingHorizontal: gutter }]}>
      <View style={styles.head}>
        <View style={styles.headLeft}>
          {eyebrow ? <Text variant="caption" weight="bold" color={color.text.eyebrow}>{eyebrow}</Text> : null}
          <View style={styles.titleLine}>
            <Text variant="display" weight="bold" color={color.text.heading}>{title}</Text>
            <CircleButton icon={arrowRight} label={openLabel} onPress={onOpen} filled />
          </View>
          {subtitle ? <Text variant="body" color={color.text.body}>{subtitle}</Text> : null}
        </View>

        {/* 좌우 단추는 웹에서만 그린다. 터치 기기는 손가락으로 미는 것이 더 빠르고,
            거기에 단추를 두면 카드 위에 겹쳐 눌리는 자리가 생긴다 (시안 「자주 틀리는 것」 4). */}
        {arrows ? (
          <View style={styles.headRight}>
            <CircleButton icon={arrowLeft} label={tx('이전', 'Previous')} onPress={() => slide(-1)} />
            <CircleButton icon={arrowRight} label={tx('다음', 'Next')} onPress={() => slide(1)} />
          </View>
        ) : null}
      </View>

      <ScrollView
        ref={scroller}
        horizontal
        showsHorizontalScrollIndicator={false}
        decelerationRate="fast"
        onScroll={(event) => { offset.current = event.nativeEvent.contentOffset.x; }}
        scrollEventThrottle={16}
        // 줄은 화면 좌우 끝까지 흐른다. 바깥 여백을 음수로 빼고 안쪽에서 다시 주면,
        // 첫 카드는 글자와 줄이 맞으면서 마지막 카드는 가장자리까지 나간다.
        style={{ marginHorizontal: -gutter }}
        contentContainerStyle={[styles.railContent, { paddingHorizontal: gutter }]}
      >
        {children}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  pressed: { opacity: 0.78 },

  row: { gap: spacing[4] },
  head: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: spacing[4] },
  headLeft: { flex: 1, minWidth: 0, gap: spacing[1] },
  titleLine: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  headRight: { flexDirection: 'row', gap: spacing[2] },

  circle: {
    width: 32,
    height: 32,
    borderRadius: radius.full,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: color.surface.border,
    backgroundColor: color.surface.card,
  },
  circleFilled: { borderColor: 'transparent', backgroundColor: color.surface.soft },
  circleIcon: { width: 16, height: 16 },

  railContent: { gap: spacing[3] },
});
