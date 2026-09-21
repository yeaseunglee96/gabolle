// 03 온보딩·권한 안내(권한별 on/off) · 15 내 정보(개인화 추천 끄기)에서 반복되는 스위치.
// 실제 값은 어디에도 저장되지 않는다 — 화면 안 로컬 상태만 바뀐다(권한 API·서버 저장 전부 미착수).
import { Pressable, StyleSheet, View } from 'react-native';

import { color, radius } from '@/design/tokens';

type ToggleProps = {
  value: boolean;
  onValueChange: (next: boolean) => void;
  disabled?: boolean;
};

export function Toggle({ value, onValueChange, disabled }: ToggleProps) {
  return (
    <Pressable
      accessibilityRole="switch"
      accessibilityState={{ checked: value, disabled }}
      disabled={disabled}
      onPress={() => onValueChange(!value)}
      style={({ pressed }) => [
        styles.track,
        {
          // 🔴 꺼짐 색을 하드코딩('#ddd7cf')하고 있었다 — 토큰을 갈아 끼워도 이 스위치만
          //    옛 배색으로 남았다 (S15P21E201-1343).
          backgroundColor: value ? color.action.primary : color.surface.field,
          justifyContent: value ? 'flex-end' : 'flex-start',
        },
        disabled && styles.disabled,
        pressed && !disabled && styles.pressed,
      ]}
    >
      <View style={styles.knob} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  track: {
    width: 44,
    height: 26,
    borderRadius: radius.full,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 3,
  },
  knob: {
    width: 20,
    height: 20,
    borderRadius: radius.full,
    backgroundColor: color.canvas,
  },
  disabled: {
    opacity: 0.7,
  },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
});
