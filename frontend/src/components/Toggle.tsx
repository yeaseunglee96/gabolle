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
          backgroundColor: value ? color.brand.orange : '#ddd7cf',
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
