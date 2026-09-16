import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { usePathname } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { subscribeApiAvailability } from '@/api/client';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { Text } from '@/components/Text';
import { TAB_BAR_HEIGHT } from '@/components/TabBar';

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
      style={[styles.position, { bottom: bannerBottomOffset(insets.bottom) }]}
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

/**
 * 배너를 화면 아래 어디에 띄우나 (S15P21E201-1139).
 *
 * 🔴 **위에 띄우면 모든 화면의 제목을 덮는다.** 배너는 absolute 라 자리를 안 차지하고,
 * 그 자리가 곧 모든 화면이 제목을 그리는 자리다. 390 폭 실측(/field/bus)에서 「주변 버스」가
 * DOM 에는 있는데 화면에는 한 글자도 안 보였다. 사용자 보고 2번(「상단 마진이 너무 넓어서
 * 비워진 것 같아」)이 이것이다 — 제목이 지워지니 화면이 빈 것처럼 보인다.
 *
 * 🔴 **흐름 요소로 바꿔 내용을 밀어내는 방법은 못 쓴다.** 그러면 상단 안전영역이 두 번
 * 들어간다 — 배너가 한 번, 아래의 `Screen` 이 `SafeAreaView edges={['top']}` 로 또 한 번.
 * 고치려던 "위가 너무 비었다" 를 오히려 키운다.
 *
 * 🔴 **탭바와 AI 도우미 버튼 위로 띄운다.** 둘 다 화면 오른쪽 아래를 쓰고, AI 도우미
 * 버튼이 더 위에 있다(홈의 `assistantButton` — bottom 100 · 높이 68). 그 둘을 다 비켜야
 * 아무것도 안 가린다.
 *
 * 🔴 **화면 목록으로 나누지 않는다.** 탭바가 있는 화면(지금 9곳)·도우미 버튼이 있는
 * 화면(지금 1곳)을 여기에 적어 두면, 화면이 하나 늘 때마다 이 목록이 조용히 낡는다.
 * 없는 화면에서 배너가 조금 더 떠 있는 것은 아무 해가 없고, 가리는 것은 해가 있다.
 * 한쪽만 틀릴 수 있다면 **해가 없는 쪽으로 틀린다.**
 */
/** 홈의 AI 도우미 버튼이 차지하는 높이 (bottom 100 + 높이 68). 이 위로 띄운다. */
const ASSISTANT_BUTTON_TOP = 168;

export function bannerBottomOffset(bottomInset: number) {
  return bottomInset + Math.max(TAB_BAR_HEIGHT, ASSISTANT_BUTTON_TOP) + spacing[2];
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
