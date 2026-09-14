// S15P21E201-222 — 택시 목적지 카드. 사용자가 아니라 택시 기사가 보는 화면이라
// 평소 화면 규칙과 다르게 만든다: 어두운 차 안에서 팔 길이만큼 떨어져 읽어야 하므로
// 화면 전체를 흰 배경 + 최소 28px(hero 토큰, 34px) 글자로 채운다.
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import * as Clipboard from 'expo-clipboard';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { getTaxiCard, type TaxiCard } from '@/discovery/taxiCard';
import { useI18n } from '@/i18n';

type State =
  | { status: 'loading' }
  | { status: 'loaded'; card: TaxiCard }
  | { status: 'not-found' }
  | { status: 'error' };

export default function TaxiCardScreen() {
  const router = useRouter();
  const { tx } = useI18n();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const [state, setState] = useState<State>({ status: 'loading' });
  const [retryCount, setRetryCount] = useState(0);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!id) return;
    let active = true;
    const controller = new AbortController();
    setState({ status: 'loading' });
    getTaxiCard(id, controller.signal)
      .then((card) => { if (active) setState({ status: 'loaded', card }); })
      .catch((cause) => {
        if (!active) return;
        setState(cause instanceof ApiClientError && cause.status === 404 ? { status: 'not-found' } : { status: 'error' });
      });
    return () => { active = false; controller.abort(); };
  }, [id, retryCount]);

  async function copyAddress(address: string) {
    await Clipboard.setStringAsync(address);
    setCopied(true);
  }

  return (
    <Screen style={styles.screen}>
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹</Text>
        </Pressable>
      </View>

      {state.status === 'loading' ? (
        <View style={styles.notice} accessibilityLiveRegion="polite">
          <ActivityIndicator color={color.brand.orange} />
          <Text color={color.text.body}>{tx('택시 카드를 준비하고 있어요', 'Preparing the taxi card')}</Text>
        </View>
      ) : null}

      {state.status === 'not-found' ? (
        <View style={styles.notice} accessibilityRole="alert">
          <Text variant="title" weight="bold">{tx('장소를 찾을 수 없어요', 'Place not found')}</Text>
          <Button label={tx('돌아가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} />
        </View>
      ) : null}

      {state.status === 'error' ? (
        <View style={styles.notice} accessibilityRole="alert">
          <Text variant="title" weight="bold">{tx('택시 카드를 불러오지 못했어요', "We couldn't load the taxi card")}</Text>
          <Button label={tx('다시 시도', 'Try again')} onPress={() => setRetryCount((count) => count + 1)} />
        </View>
      ) : null}

      {state.status === 'loaded' ? (
        <View style={styles.card}>
          <Text variant="caption" weight="bold" color={color.text.accent}>
            {tx('기사님께 보여주세요', 'Show this to the driver')}
          </Text>
          <Text variant="hero" weight="bold" color={color.text.heading} style={styles.address}>
            {state.card.addressKo}
          </Text>
          {state.card.addressEn ? (
            <Text variant="title" color={color.text.body}>{state.card.addressEn}</Text>
          ) : null}
          <Text variant="title" weight="medium" color={color.text.heading} style={styles.sentence}>
            {state.card.driverSentence}
          </Text>
          <Pressable accessibilityRole="button" accessibilityLabel={tx('주소 복사', 'Copy address')} onPress={() => void copyAddress(state.card.addressKo)} style={styles.copyButton}>
            <Text variant="body" weight="bold" color={color.text.accent}>
              {copied ? tx('복사됨 ✓', 'Copied ✓') : tx('주소 복사', 'Copy address')}
            </Text>
          </Pressable>
          <Text variant="caption" color={color.text.muted} style={styles.brightnessHint}>
            {tx('※ 화면 밝기를 최대로 올려주세요', '※ Turn your screen brightness all the way up')}
          </Text>
        </View>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.surface.card },
  topBar: { minHeight: 52, justifyContent: 'center' },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  notice: { flex: 1, gap: spacing[3], alignItems: 'center', justifyContent: 'center' },
  card: { flex: 1, justifyContent: 'center', gap: spacing[3] },
  address: { marginTop: spacing[2] },
  sentence: { marginTop: spacing[4] },
  copyButton: { marginTop: spacing[6], alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center' },
  brightnessHint: { marginTop: spacing[2] },
});
