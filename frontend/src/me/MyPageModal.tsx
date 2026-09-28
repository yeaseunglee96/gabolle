// 데스크톱에서 하위 메뉴를 겹쳐 여는 모달 (시안 03·05).
//
// 🔴 화면을 바꾸지 않는다. 마이페이지가 뒤에 그대로 남아 있어야, 닫았을 때 보던 자리로
//    바로 돌아온다 — 화면을 바꾸면 스크롤 위치도 열었던 카드도 잃는다.
import { useEffect, useRef } from 'react';
import { Animated, Easing, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

export function MyPageModal({
  open,
  title,
  description,
  onClose,
  children,
  tx,
}: {
  open: boolean;
  title: string;
  description: string;
  onClose: () => void;
  children: React.ReactNode;
  tx: (ko: string, en: string) => string;
}) {
  const enter = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(enter, {
      toValue: open ? 1 : 0,
      duration: 320,
      easing: Easing.bezier(0.34, 1.3, 0.64, 1),
      useNativeDriver: false,
    }).start();
  }, [open, enter]);

  // 🔴 닫혔을 때는 아예 안 그린다. 투명도만 0으로 두면 화면 읽기 프로그램이 안 보이는
  //    내용을 읽고, 누르는 자리도 남아 뒤 화면을 가린다.
  if (!open) return null;

  return (
    <View style={styles.layer}>
      {/* 바깥을 누르면 닫힌다. 어둠막이 카드보다 먼저 와야 카드가 위에 온다. */}
      <Animated.View style={[StyleSheet.absoluteFill, styles.backdrop, { opacity: enter }]}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={tx('닫기', 'Close')}
          onPress={onClose}
          style={StyleSheet.absoluteFill}
        />
      </Animated.View>

      <Animated.View
        accessibilityViewIsModal
        style={[
          styles.card,
          {
            opacity: enter,
            transform: [
              { translateY: enter.interpolate({ inputRange: [0, 1], outputRange: [24, 0] }) },
              { scale: enter.interpolate({ inputRange: [0, 1], outputRange: [0.96, 1] }) },
            ],
          },
        ]}
      >
        <View style={styles.head}>
          <View style={styles.headCopy}>
            <Eyebrow>{tx('내 계정', 'Account')}</Eyebrow>
            <Text variant="display" weight="bold">{title}</Text>
            {description ? <Text color={color.text.body}>{description}</Text> : null}
          </View>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={tx('닫기', 'Close')}
            onPress={onClose}
            style={({ pressed }) => [styles.close, pressed && styles.pressed]}
          >
            <Text variant="title" weight="bold">✕</Text>
          </Pressable>
        </View>
        <ScrollView style={styles.scroll} contentContainerStyle={styles.body}>{children}</ScrollView>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  // 화면 전체를 덮는다. 웹에서는 스크롤해도 따라와야 해서 fixed 다 — 탭바가 같은 이유로
  // 그렇게 되어 있다.
  layer: {
    position: Platform.OS === 'web' ? ('fixed' as 'absolute') : 'absolute',
    top: 0, left: 0, right: 0, bottom: 0,
    alignItems: 'center', justifyContent: 'center',
    padding: spacing[4],
    zIndex: 40,
  },
  backdrop: { backgroundColor: 'rgba(25,25,25,0.62)' },
  card: {
    width: '100%', maxWidth: 640, maxHeight: '100%',
    borderRadius: radius.lg, overflow: 'hidden',
    backgroundColor: color.brand.ivory,
    shadowColor: color.brand.navy, shadowOpacity: 0.3, shadowRadius: 64, shadowOffset: { width: 0, height: 24 }, elevation: 24,
  },
  head: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3], padding: spacing[6], paddingBottom: spacing[3] },
  headCopy: { flex: 1, gap: spacing[2], minWidth: 0 },
  close: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.soft },
  pressed: { opacity: 0.72 },
  // 🔴 본문은 폰 창(MyPageSheet)처럼 연회색 + 머리 아래 선이다(S15P21E201-1659). 창 전체가 아이보리일 때는 흰 카드가
  //    창 바탕에 묻혀 선 하나로만 갈렸다(저장한 기록·내 댓글).
  scroll: { backgroundColor: color.canvas, borderTopWidth: 1, borderTopColor: color.surface.border },
  body: { paddingHorizontal: spacing[6], paddingTop: spacing[4], paddingBottom: spacing[6] },
});
