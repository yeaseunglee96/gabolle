// AI 도우미 드롭업 — 마스코트를 누르면 위로 뜨는 메뉴.
//
// 전에는 마스코트를 누르면 /chat 한 곳으로만 갔다. 그런데 라벨은 「일정 · 통역 · 여행 도움」
// 이라고 적혀 있었다 — 셋을 약속하고 하나만 줬다. 메뉴판 번역과 통역은 이미 만들어져 있는
// 화면인데 홈에서 가는 길이 없었다.
//
// 데스크톱과 폰이 같은 부품을 쓴다. 폭과 행 높이만 다르다 — 두 벌로 만들면 항목이 늘 때
// 한쪽만 늘어나고, 그 차이는 두 화면을 나란히 눌러 봐야만 보인다.
import { useEffect, useRef } from 'react';
import { Animated, Easing, Image, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { GabolleMascot } from '@/components/DongbaekMascot';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

const cameraIcon = require('../../assets/icons/common/camera.png');
const speakerIcon = require('../../assets/icons/common/speaker.png');

type Item = {
  key: string;
  ko: string;
  en: string;
  subKo: string;
  subEn: string;
  path: string;
  icon?: number;
};

const ITEMS: Item[] = [
  { key: 'menu', ko: '메뉴판 번역', en: 'Menu translation', subKo: '메뉴판을 찍으면 읽어 드려요', subEn: 'Snap a menu and we read it', path: '/field/menu-scan', icon: cameraIcon },
  { key: 'speak', ko: '통역', en: 'Interpreter', subKo: '현장에서 말하고 들려주기', subEn: 'Speak and play it out loud', path: '/field/speak', icon: speakerIcon },
  // 챗봇만 마스코트를 쓴다 — 셋 중 「우리가 하는 것」이 어느 것인지 그림으로 구분된다.
  { key: 'chat', ko: 'AI 챗봇', en: 'AI chat', subKo: '일정 · 통역 · 여행 도움', subEn: 'Plans · phrases · travel help', path: '/chat' },
];

/**
 * 도우미가 실제로 데려다주는 곳.
 *
 * 시험이 이 목록을 읽어 **그 화면이 정말 있는지** 확인한다. 주소를 잘못 적으면 눌렀을 때
 * 빈 화면이 뜨는데, 타입도 시험도 안 잡는다 — 그냥 문자열이기 때문이다.
 */
export const ASSISTANT_PATHS = ITEMS.map((item) => item.path);

/** 라벨 부제 — 메뉴에 있는 것을 그대로 적는다. 없는 것을 약속하지 않는다. */
export function assistantSubtitle(tx: (ko: string, en: string) => string) {
  return tx('메뉴판 번역 · 통역 · AI 챗봇', 'Menu · interpreter · AI chat');
}

export function AssistantMenu({
  open,
  onClose,
  compact = false,
}: {
  open: boolean;
  onClose: () => void;
  /** 폰. 폭과 행 높이가 줄고 부제를 안 그린다. */
  compact?: boolean;
}) {
  const router = useRouter();
  const { tx } = useI18n();
  const progress = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(progress, {
      toValue: open ? 1 : 0,
      duration: 320,
      // 시안의 cubic-bezier(.34,1.4,.64,1) — 살짝 튀어 올랐다 자리를 잡는다.
      easing: Easing.bezier(0.34, 1.4, 0.64, 1),
      useNativeDriver: true,
    }).start();
  }, [open, progress]);

  const go = (path: string) => {
    onClose();
    router.push(path as never);
  };

  // 🔴 닫혔을 때는 아예 안 그린다. 투명도만 0으로 두면 화면 읽기 프로그램이 항목을 읽고,
  // 사용자는 안 보이는 메뉴 안을 걷게 된다. 누르는 자리도 남아 아래 내용을 가린다.
  if (!open) return null;

  return (
    <Animated.View
        accessibilityRole="menu"
        style={[
          styles.card,
          compact ? styles.cardCompact : styles.cardWide,
          {
            opacity: progress,
            transform: [
              { translateY: progress.interpolate({ inputRange: [0, 1], outputRange: [16, 0] }) },
              { scale: progress.interpolate({ inputRange: [0, 1], outputRange: [0.92, 1] }) },
            ],
          },
        ]}
      >
        {ITEMS.map((item) => (
          <Pressable
            key={item.key}
            accessibilityRole="menuitem"
            accessibilityLabel={tx(item.ko, item.en)}
            onPress={() => go(item.path)}
            style={({ pressed }) => [styles.item, compact ? styles.itemCompact : styles.itemWide, pressed && styles.itemPressed]}
          >
            <View style={styles.iconCircle}>
              {item.icon
                ? <Image source={item.icon} resizeMode="contain" accessibilityIgnoresInvertColors style={styles.icon} />
                : <GabolleMascot state="idle" style={styles.itemMascot} />}
            </View>
            <View style={styles.itemText}>
              <Text variant="body" weight="bold" color={color.text.heading} numberOfLines={1}>{tx(item.ko, item.en)}</Text>
              {compact ? null : (
                <Text variant="caption" color={color.text.muted} numberOfLines={1}>{tx(item.subKo, item.subEn)}</Text>
              )}
            </View>
          </Pressable>
        ))}
    </Animated.View>
  );
}

/**
 * 바깥을 누르면 닫히는 판. **화면이 직접 놓는다.**
 *
 * 🔴 메뉴 안에 두면 안 된다. 메뉴는 오른쪽 아래 구석에 붙어 있는 작은 상자라, 그 안의
 * 「화면 전체」는 그 상자만큼이다. 그리고 안드로이드는 **부모 바깥의 터치를 아예 안 준다** —
 * 웹에서는 되는데 폰에서만 안 닫히는, 화면을 봐야만 아는 종류의 결함이 된다.
 */
export function AssistantBackdrop({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { tx } = useI18n();
  if (!open) return null;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={tx('도우미 메뉴 닫기', 'Close assistant menu')}
      onPress={onClose}
      style={StyleSheet.absoluteFill}
    />
  );
}

const styles = StyleSheet.create({
  card: {
    position: 'absolute',
    bottom: '100%',
    right: 0,
    marginBottom: spacing[3],
    paddingVertical: spacing[2],
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: color.surface.field,
    backgroundColor: color.surface.card,
    shadowColor: color.brand.navy,
    shadowOpacity: 0.16,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: 8 },
    elevation: 8,
  },
  cardWide: { width: 260 },
  cardCompact: { width: 232 },

  item: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], paddingHorizontal: spacing[3] },
  itemWide: { height: 52 },
  itemCompact: { height: 48 },
  itemPressed: { backgroundColor: color.surface.soft },

  iconCircle: { width: 36, height: 36, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.tint },
  icon: { width: 18, height: 18 },
  itemMascot: { width: 30, height: 30 },
  itemText: { flex: 1, minWidth: 0 },
});
