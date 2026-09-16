import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { usePathname } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { subscribeApiAvailability } from '@/api/client';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { Text } from '@/components/Text';

/**
 * 🔴 배너를 띄우기 전에 기다리는 시간 (S15P21E201-1107).
 *
 * S15P21E201-1081 에서 5xx 를 "서버가 잠깐 못 받는다" 로 세기 시작하면서, 배포 중 502 한 번이나
 * 백그라운드 요청 하나가 실패해도 이 배너가 **즉시** 떴다. 몇 초 뒤 저절로 사라지는데, 그 사이
 * 화면 위쪽을 덮어서 사용자는 "자꾸 뜬다 · 뭔가 고장났다" 로 읽는다.
 *
 * 그래서 **끊긴 상태가 이 시간만큼 이어질 때만** 말한다. 잠깐 끊긴 것은 말할 가치가 없다 —
 * 그 사이에 이미 다시 붙는다. 반대로 정말 안 되는 상태는 이 시간이 지나도 그대로라 반드시 뜬다.
 *
 * 사라질 때는 기다리지 않는다. 다시 붙었으면 그 즉시 치우는 것이 맞다.
 */
export const SHOW_AFTER_MS = 4000;

/**
 * 끊긴 상태가 {@link SHOW_AFTER_MS} 만큼 이어질 때만 참을 알린다. 다시 붙으면 즉시 거짓을 알린다.
 *
 * 🔴 화면 밖으로 뺀 이유는 **이 규칙이 시험 대상이기 때문**이다. "잠깐 끊긴 것은 안 띄운다" 는
 * 눈으로 확인할 수 없다 — 안 뜨는 것을 봐야 하는데, 안 뜨는 화면은 아무것도 안 보인다.
 */
export function watchApiUnavailable(
  subscribe: (listener: (unavailable: boolean) => void) => () => void,
  onChange: (visible: boolean) => void,
  delayMs: number = SHOW_AFTER_MS,
): () => void {
  let timer: ReturnType<typeof setTimeout> | null = null;
  const clear = () => { if (timer) { clearTimeout(timer); timer = null; } };
  const stop = subscribe((next) => {
    clear();
    // 붙었으면 즉시 치운다. 끊겼으면 잠시 지켜본다.
    if (!next) { onChange(false); return; }
    timer = setTimeout(() => onChange(true), delayMs);
  });
  return () => { clear(); stop(); };
}

export function ApiAvailabilityBanner() {
  const [unavailable, setUnavailable] = useState(false);
  const insets = useSafeAreaInsets();
  const { tx } = useI18n();
  const pathname = usePathname();

  useEffect(() => watchApiUnavailable(subscribeApiAvailability, setUnavailable), []);

  // 앱을 소개하거나 로그인하는 단계에는 서버 데이터가 아직 필요 없다. 이 화면들에서
  // 전역 배너를 띄우면 로고·건너뛰기·입력 제목을 가려 첫인상만 망친다.
  if (!unavailable || shouldHideApiBanner(pathname)) return null;

  return (
    <View
      accessibilityRole="alert"
      accessibilityLiveRegion="assertive"
      style={[styles.position, { top: insets.top + spacing[2] }]}
      pointerEvents="none"
    >
      <View style={styles.banner}>
        <View style={styles.dot} />
        <View style={styles.copy}>
          <Text weight="bold" color={color.text.heading}>
            {tx('서버 연결을 확인하고 있어요', 'Checking the server connection')}
          </Text>
          <Text variant="caption" color={color.text.body}>
            {tx(
              '보고 있던 화면은 그대로 유지돼요. 연결되면 자동으로 사라집니다.',
              'This screen will stay available and the notice will disappear automatically.',
            )}
          </Text>
        </View>
      </View>
    </View>
  );
}

export function shouldHideApiBanner(pathname: string) {
  return ['/', '/app-intro', '/age-gate', '/sign-in', '/sign-up', '/permissions'].includes(pathname);
}

const styles = StyleSheet.create({
  position: {
    position: 'absolute',
    left: spacing[4],
    right: spacing[4],
    zIndex: 1000,
    alignItems: 'center',
  },
  banner: {
    width: '100%',
    maxWidth: 520,
    minHeight: 64,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: color.brand.orange,
    backgroundColor: color.brand.ivory,
    shadowColor: color.brand.navy,
    shadowOpacity: 0.12,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 4,
  },
  dot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: color.brand.orange,
  },
  copy: { flex: 1, gap: spacing[1] },
});
