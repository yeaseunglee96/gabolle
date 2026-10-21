// — 택시 목적지 카드. 사용자가 아니라 택시 기사가 보는 화면이라
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
import { markScreenGuideUsed } from '@/onboarding/firstRun';
import { useI18n } from '@/i18n';
import { addressForLanguage } from '@/discovery/localAddress';

/** 카카오 결과로 연 카드의 자리표시 id — speak.tsx 의 taxiCardHref 가 쓴다. */
const EXTERNAL_ID = 'external';

type State =
  | { status: 'loading' }
  | { status: 'loaded'; card: TaxiCard }
  | { status: 'not-found' }
  | { status: 'error' };

export default function TaxiCardScreen() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { id, name, address } = useLocalSearchParams<{ id?: string; name?: string; address?: string }>();
  const [state, setState] = useState<State>({ status: 'loading' });
  const [retryCount, setRetryCount] = useState(0);
  const [copied, setCopied] = useState(false);
  // 택시 카드를 한 번 연 사람에게는 장소 정보의 택시 안내를 다시 띄우지 않는다(UI 캔버스 ㉔-5, S15P21E201-1885).
  useEffect(() => { void markScreenGuideUsed('taxi'); }, []);

  useEffect(() => {
    if (!id) return;
    // 카카오에서 고른 곳(S15P21E201-1742) — 우리 DB 에 없는 장소라 서버에 물을 것이 없다. 받은 이름·주소로 그린다.
    // 🔴 문장 형식은 서버(TaxiCardService#driverSentence)와 같게 둔다.
    if (id === EXTERNAL_ID) {
      const placeName = (name ?? '').trim();
      const placeAddress = (address ?? '').trim();
      setState(placeName
        ? { status: 'loaded', card: { placeId: EXTERNAL_ID, nameKo: placeName, addressKo: placeAddress || undefined, resolvedLanguage: 'ko', driverSentence: '이 주소로 가주세요, ' + (placeAddress || placeName) } }
        : { status: 'not-found' });
      return;
    }
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
  }, [id, name, address, retryCount]);

  async function copyAddress(address: string) {
    await Clipboard.setStringAsync(address);
    setCopied(true);
  }

  const travelerAddress = state.status === 'loaded'
    ? (language === 'ko' ? state.card.addressEn ?? null : addressForLanguage({ address: state.card.addressKo, addressEn: state.card.addressEn }, language) || null)
    : null;
  const shownTravelerAddress = travelerAddress && travelerAddress !== (state.status === 'loaded' ? state.card.addressKo : null) ? travelerAddress : null;

  return (
    <Screen style={styles.screen}>
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹</Text>
        </Pressable>
      </View>

      {state.status === 'loading' ? (
        <View style={styles.notice} accessibilityLiveRegion="polite">
          <ActivityIndicator color={color.action.primary} />
          <Text color={color.text.body}>{tx('택시 카드를 준비하고 있어요', 'Preparing the taxi card')}</Text>
        </View>
      ) : null}

      {state.status === 'not-found' ? (
        <View style={styles.notice} accessibilityRole="alert">
          <Text variant="title" weight="bold">{tx('장소를 찾을 수 없어요', 'Place not found')}</Text>
          <Button compact label={tx('돌아가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} />
        </View>
      ) : null}

      {state.status === 'error' ? (
        <View style={styles.notice} accessibilityRole="alert">
          <Text variant="title" weight="bold">{tx('택시 카드를 불러오지 못했어요', "We couldn't load the taxi card")}</Text>
          <Button compact label={tx('다시 시도', 'Try again')} onPress={() => setRetryCount((count) => count + 1)} />
        </View>
      ) : null}

      {state.status === 'loaded' ? (
        <View style={styles.card}>
          <Text variant="caption" weight="bold" color={color.text.accent}>
            {tx('기사님께 보여주세요', 'Show this to the driver')}
          </Text>
          {/* 🔴 이름을 가장 크게 — 기사는 주소보다 내비에 장소 이름을 넣는 일이 더 많다(S15P21E201-1742). */}
          <Text testID="taxi-card-name" variant="hero" weight="bold" color={color.text.heading} style={styles.address}>
            {state.card.nameKo}
          </Text>
          {state.card.addressKo ? (
            <Text testID="taxi-card-address" variant="title" weight="bold" color={color.text.heading}>{state.card.addressKo}</Text>
          ) : null}
          {/* 여행자가 읽는 줄 — 한국어 화면은 영문 주소, 그 밖은 화면 언어(S15P21E201-1877). 기사가 읽는 한국어 줄은 위에 그대로다. */}
          {shownTravelerAddress ? (
            <Text testID="taxi-card-traveler-address" variant="title" color={color.text.body}>{shownTravelerAddress}</Text>
          ) : null}
          <Text variant="title" weight="medium" color={color.text.heading} style={styles.sentence}>
            {state.card.driverSentence}
          </Text>
          {state.card.addressKo ? (
            <Pressable accessibilityRole="button" accessibilityLabel={tx('주소 복사', 'Copy address')} onPress={() => void copyAddress(state.card.addressKo!)} style={styles.copyButton}>
              <Text variant="body" weight="bold" color={color.text.accent}>
                {copied ? tx('복사됨 ✓', 'Copied ✓') : tx('주소 복사', 'Copy address')}
              </Text>
            </Pressable>
          ) : null}
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
