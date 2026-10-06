// 현장 도구 화면의 뒤로 단추 — S15P21E201-1993.
// 통역·환율·사투리·버스 화면에 나갈 길이 없어서(탭 막대도 없다) 앱을 껐다 켜야 했다.
// 돌아갈 화면이 없으면(링크로 바로 열었으면) 현장 도구 모음으로 간다.
import { useEffect } from 'react';
import { BackHandler, Platform, Pressable, StyleSheet } from 'react-native';
import { useRouter, type Href } from 'expo-router';

import { color, radius } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { Text } from './Text';

export function FieldBackButton({ fallback = '/field/translate' }: { fallback?: Href }) {
  const router = useRouter();
  const { tx } = useI18n();
  // 안드로이드 뒤로 버튼·제스처 — 돌아갈 화면이 없으면 앱이 닫혔다. 그때는 fallback 으로 보낸다(S15P21E201-1998).
  useEffect(() => {
    if (Platform.OS !== 'android') return undefined;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (router.canGoBack()) return false;
      router.replace(fallback);
      return true;
    });
    return () => sub.remove();
  }, [router, fallback]);
  return (
    <Pressable
      testID="field-back"
      accessibilityRole="button"
      accessibilityLabel={tx('뒤로 가기', 'Go back')}
      hitSlop={8}
      onPress={() => (router.canGoBack() ? router.back() : router.replace(fallback))}
      style={({ pressed }) => [styles.back, pressed && styles.pressed]}
    >
      <Text variant="title" weight="bold">‹</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card, marginBottom: 8 },
  pressed: { opacity: 0.7 },
});
