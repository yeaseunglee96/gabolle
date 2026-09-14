import { useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Animated, ImageBackground, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { sendAppEvent } from '@/analytics/appEvents';
import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { bilingualPlaceName, formatFeatureSlot, getPlace, hasFoodSafetyConfirmed, hasLocalityScore, needsFoodSafetyCheck, type Place as ApiPlace } from '@/discovery/places';
import { DEMO_PLACES, SAVED_PLACES_KEY } from '@/discovery/savedPlaces';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { PlacePhraseModal } from '@/components/PlacePhraseModal';

// 데모 3곳·저장 키는 src/discovery/savedPlaces.ts 로 옮겼다 — (tabs)/saved.tsx 도 같은 값을 쓴다.
const PLACES = DEMO_PLACES;

type RemoteState =
  | { status: 'loading' }
  | { status: 'loaded'; place: ApiPlace }
  | { status: 'not-found' }
  | { status: 'error' };

export default function Place() {
  const router = useRouter();
  const { tx } = useI18n();
  const { accessToken } = useAuth();
  const { width } = useWindowDimensions();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const demoPlace = id && id in PLACES ? PLACES[id as keyof typeof PLACES] : null;
  const [isSaved, setIsSaved] = useState(false);
  const [feedback, setFeedback] = useState('');
  const [remote, setRemote] = useState<RemoteState>({ status: 'loading' });
  const [retryCount, setRetryCount] = useState(0);
  const [phraseModalOpen, setPhraseModalOpen] = useState(false);
  const [heroLoaded, setHeroLoaded] = useState(false);
  const heroReveal = useRef(new Animated.Value(0)).current;

  // 데모 3곳은 로컬 값을, 그 밖의 id 는 방금 받아온 API 응답을 같은 모양으로 맞춘다.
  // useMemo 로 묶는다 — 안 묶으면 매 렌더 새 객체가 생겨 resolved 를 의존성으로 삼는
  // 아래 effect 들이 재실행 루프에 빠질 수 있다.
  const resolved = useMemo(() => (
    demoPlace
      ? { title: tx(demoPlace.titleKo, demoPlace.titleEn), subtitle: tx(demoPlace.subtitleKo, demoPlace.subtitleEn), apiPlace: null as ApiPlace | null }
      : remote.status === 'loaded'
        ? { title: bilingualPlaceName(remote.place.nameKo, remote.place.nameEn), subtitle: tx(remote.place.address, remote.place.addressEn ?? remote.place.address), apiPlace: remote.place }
        : null
  ), [demoPlace, remote, tx]);
  const photoUrl = resolved?.apiPlace?.photoUrl ?? null;
  const taxiPlaceId = resolved?.apiPlace?.placeId ?? null;

  useEffect(() => {
    setHeroLoaded(false);
    heroReveal.setValue(0);
  }, [photoUrl, heroReveal]);

  useEffect(() => {
    if (!heroLoaded) return;
    Animated.timing(heroReveal, { toValue: 1, duration: 320, useNativeDriver: true }).start();
  }, [heroLoaded, heroReveal]);

  useEffect(() => {
    if (!id || demoPlace) return;
    let active = true;
    const controller = new AbortController();
    setRemote({ status: 'loading' });
    getPlace(id, controller.signal)
      .then((place) => { if (active) setRemote({ status: 'loaded', place }); })
      .catch((cause) => {
        if (!active) return;
        setRemote(cause instanceof ApiClientError && cause.status === 404 ? { status: 'not-found' } : { status: 'error' });
      });
    return () => { active = false; controller.abort(); };
  }, [id, demoPlace, retryCount]);

  useEffect(() => {
    if (!id || !resolved) return;
    void AsyncStorage.getItem(SAVED_PLACES_KEY).then((raw) => {
      try {
        const savedIds = raw ? JSON.parse(raw) : [];
        setIsSaved(Array.isArray(savedIds) && savedIds.includes(id));
      } catch {
        void AsyncStorage.removeItem(SAVED_PLACES_KEY);
      }
    });
  }, [id, resolved]);

  const toggleSaved = async () => {
    if (!id || !resolved) return;
    const raw = await AsyncStorage.getItem(SAVED_PLACES_KEY);
    let savedIds: string[] = [];
    try {
      const parsed = raw ? JSON.parse(raw) : [];
      savedIds = Array.isArray(parsed) ? parsed : [];
    } catch {
      // 손상된 로컬 값은 현재 선택을 기준으로 안전하게 다시 만든다.
    }
    const nextSaved = !isSaved;
    const nextIds = nextSaved ? [...new Set([...savedIds, id])] : savedIds.filter((savedId) => savedId !== id);
    await AsyncStorage.setItem(SAVED_PLACES_KEY, JSON.stringify(nextIds));
    setIsSaved(nextSaved);
    setFeedback(nextSaved ? tx('내 여행 후보에 저장했어요.', 'Saved to your trip candidates.') : tx('저장을 해제했어요.', 'Removed from saved.'));
    // 저장할 때만 보낸다. 해제는 "싫다" 가 아니라 "취소" 다.
    //
    // 🔴 목업 장소면 보내지 않는다 (2026-09-10). 이 화면은 id 가 DEMO_PLACES 에 있으면
    //    그 고정 데이터를 보여준다(위 demoPlace). 그때의 id 는 서버 장소 번호가 아니라
    //    화면용 이름표라, 보내면 **없는 장소에 붙은 place_like** 가 서버에 쌓인다.
    //    서버는 장소 번호의 실재를 검사하지 않으므로 조용히 들어가고, 나중에 못 골라낸다.
    //    목업이 걷히면 demoPlace 가 언제나 null 이 되어 이 조건은 저절로 사라진다.
    if (nextSaved && !demoPlace) sendAppEvent({ type: 'place_like', accessToken, payload: { place_id: id, surface: 'place_detail' } });
  };

  const notFound = !demoPlace && remote.status === 'not-found';
  const loadFailed = !demoPlace && remote.status === 'error';
  const loading = !demoPlace && remote.status === 'loading';

  return (
    <Screen scroll wide style={styles.screen}>
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹</Text>
        </Pressable>
        <BrandLogoLink href="/home" imageStyle={styles.logo} />
        <View style={styles.spacer} />
      </View>

      {loading ? <View style={styles.notice} accessibilityLiveRegion="polite"><ActivityIndicator color={color.brand.orange} /><Text color={color.text.body} style={styles.noticeCopy}>{tx('장소 정보를 불러오고 있어요', 'Loading place details')}</Text></View> : null}

      {resolved ? <>
        {demoPlace ? (
          <ImageBackground source={demoPlace.image} resizeMode="cover" style={[styles.hero, isAtLeast(width, 'md') && styles.heroWide]} imageStyle={styles.heroImage}>
            <View style={styles.shade} />
            <View style={styles.heroCopy}>
              <Text variant="display" weight="bold" color={color.text.onAction}>{resolved.title}</Text>
              <Text color={color.text.onAction}>{resolved.subtitle}</Text>
            </View>
          </ImageBackground>
        ) : resolved.apiPlace?.photoUrl ? (
          <View style={[styles.hero, isAtLeast(width, 'md') && styles.heroWide]}>
            <View style={[StyleSheet.absoluteFill, styles.heroPlaceholder]} />
            <Animated.View style={[StyleSheet.absoluteFill, { opacity: heroReveal, transform: [{ scale: heroReveal.interpolate({ inputRange: [0, 1], outputRange: [1.04, 1] }) }] }]}>
              <ImageBackground source={{ uri: resolved.apiPlace.photoUrl }} resizeMode="cover" style={styles.heroFill} imageStyle={styles.heroImage} onLoad={() => setHeroLoaded(true)} onError={() => setHeroLoaded(true)}>
                <View style={styles.shade} />
                <View style={styles.heroCopy}>
                  <Text variant="display" weight="bold" color={color.text.onAction}>{resolved.title}</Text>
                  <Text color={color.text.onAction}>{resolved.subtitle}</Text>
                  {hasLocalityScore(resolved.apiPlace) ? (
                    <View style={styles.scoreBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('로컬 점수 있음', 'Has locality score')}</Text></View>
                  ) : null}
                  {resolved.apiPlace.photoSource ? (
                    <Text variant="caption" color={color.text.onAction} style={styles.photoCredit}>{tx(`사진 제공: ${resolved.apiPlace.photoSource}`, `Photo: ${resolved.apiPlace.photoSource}`)}</Text>
                  ) : null}
                </View>
              </ImageBackground>
            </Animated.View>
          </View>
        ) : (
          <View style={[styles.hero, styles.heroPlain, isAtLeast(width, 'md') && styles.heroWide]}>
            <View style={styles.heroCopy}>
              <Text variant="display" weight="bold" color={color.text.onAction}>{resolved.title}</Text>
              <Text color={color.text.onAction}>{resolved.subtitle}</Text>
              {resolved.apiPlace && hasLocalityScore(resolved.apiPlace) ? (
                <View style={styles.scoreBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('로컬 점수 있음', 'Has locality score')}</Text></View>
              ) : null}
            </View>
          </View>
        )}
        {resolved.apiPlace && (formatFeatureSlot(resolved.apiPlace.openingHours, tx) || formatFeatureSlot(resolved.apiPlace.priceLevel, tx)) ? (
          <View style={styles.infoRows}>
            {formatFeatureSlot(resolved.apiPlace.openingHours, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('영업시간', 'Hours')}</Text><Text variant="body">{formatFeatureSlot(resolved.apiPlace.openingHours, tx)}</Text></View> : null}
            {formatFeatureSlot(resolved.apiPlace.priceLevel, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('가격대', 'Price level')}</Text><Text variant="body">{formatFeatureSlot(resolved.apiPlace.priceLevel, tx)}</Text></View> : null}
          </View>
        ) : null}
        {resolved.apiPlace && needsFoodSafetyCheck(resolved.apiPlace) ? (
          <View style={styles.safetyNotice} accessibilityRole="alert">
            <Text variant="caption" weight="bold" color={color.state.danger}>{tx('확인 필요', 'Needs confirmation')}</Text>
            <Text color={color.text.body}>{tx('알레르기·식단 정보가 없어 주문 전 확인이 필요합니다.', 'Allergy and dietary information is not available for this place — please check before ordering.')}</Text>
          </View>
        ) : null}
        {/* S15P21E201-325: "확인 못 함"(위 배너)과 "확인했고 문제 없음"을 다른 표시로 보여준다 —
            아무것도 안 보이는 빈 자리를 사용자가 "안전 확인됨"으로 착각하지 않게 한다. */}
        {resolved.apiPlace && hasFoodSafetyConfirmed(resolved.apiPlace) ? (
          <View style={styles.safetyConfirmed} accessibilityRole="text">
            <Text variant="caption" weight="bold" color={color.state.success}>{tx('확인됨', 'Confirmed')}</Text>
            <Text color={color.text.body}>{tx('알레르기·식단 정보를 확인했고, 등록된 유발 성분이 없습니다.', "Allergy and dietary info has been checked — no listed allergens or restrictions.")}</Text>
          </View>
        ) : null}
        {demoPlace ? (
          // 데모 3곳은 실제로 있는 해운대·광안리·감천문화마을이다(savedPlaces.ts) — 장소 자체는
          // 진짜다. 다만 place 표 적재 전(-547)이라 영업시간·가격대·접근성·혼잡도 같은 상세
          // 정보만 아직 없다. 그래서 "장소가 가짜"가 아니라 "상세 정보가 아직" 이라고만 말한다.
          // 예전엔 이 칸이 모든 장소(데모든 API든)에 무조건 떴는데, 그러면 이미 상세 정보가
          // 있는 실제 API 장소에도 "아직 없다"는 틀린 안내가 나갔다.
          <View style={styles.notice} accessibilityLiveRegion="polite">
            <Text variant="title" weight="bold">{tx('상세 정보를 준비하고 있어요', 'Details are on the way')}</Text>
            <Text color={color.text.body} style={styles.noticeCopy}>{tx('영업시간·가격대 같은 상세 정보는 곧 추가돼요. 확인되지 않은 정보는 임의로 보여드리지 않아요.', "Details like hours and price level are coming soon. We never show unverified information.")}</Text>
          </View>
        ) : null}
        <View style={styles.actions}>
          <Button label={isSaved ? tx('내 여행 후보에서 빼기', 'Remove from candidates') : tx('내 여행 후보에 저장', 'Save to candidates')} variant="ghost" onPress={() => void toggleSaved()} />
          <Button label={tx('한국어로 말하기', 'Speak Korean')} onPress={() => setPhraseModalOpen(true)} containerStyle={styles.speakAction} />
          {taxiPlaceId ? <Button label={tx('택시 기사에게 보여주기', 'Show to a taxi driver')} onPress={() => router.push(`/taxi-card/${taxiPlaceId}`)} containerStyle={styles.speakAction} /> : null}
          {feedback ? <Text accessibilityLiveRegion="polite" color={color.text.body} style={styles.feedback}>{feedback}</Text> : null}
        </View>
      </> : null}

      <PlacePhraseModal visible={phraseModalOpen} onClose={() => setPhraseModalOpen(false)} category={resolved?.apiPlace?.category} />

      {notFound ? <View style={styles.notice} accessibilityRole="alert">
        <Text variant="title" weight="bold">{tx('장소를 찾을 수 없어요', 'Place not found')}</Text>
        <Text color={color.text.body}>{tx('목록으로 돌아가 다른 장소를 선택해 주세요.', 'Go back to the list and choose another place.')}</Text>
        <Button label={tx('홈으로 돌아가기', 'Back to home')} onPress={() => router.replace('/home')} containerStyle={styles.recoveryButton} />
      </View> : null}

      {loadFailed ? <View style={styles.notice} accessibilityRole="alert">
        <Text variant="title" weight="bold">{tx('장소 정보를 불러오지 못했어요', "We couldn't load this place")}</Text>
        <Text color={color.text.body}>{tx('연결 상태를 확인하고 다시 시도해 주세요.', 'Check your connection and try again.')}</Text>
        <Button label={tx('다시 시도', 'Try again')} onPress={() => setRetryCount((count) => count + 1)} containerStyle={styles.recoveryButton} />
      </View> : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[3] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  logo: { width: 96, height: 28 },
  spacer: { width: 44 },
  hero: { height: 240, justifyContent: 'flex-end', overflow: 'hidden', borderRadius: radius.lg },
  heroWide: { height: 360 },
  heroFill: { flex: 1, justifyContent: 'flex-end' },
  heroImage: { borderRadius: radius.lg },
  heroPlaceholder: { backgroundColor: color.surface.soft },
  heroPlain: { backgroundColor: color.brand.navy, padding: spacing[4] },
  shade: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(8, 27, 53, 0.25)' },
  heroCopy: { gap: spacing[1], padding: spacing[4] },
  scoreBadge: { alignSelf: 'flex-start', marginTop: spacing[2], borderRadius: radius.full, paddingHorizontal: spacing[3], paddingVertical: spacing[1], backgroundColor: 'rgba(255,255,255,0.18)' },
  photoCredit: { marginTop: spacing[1], opacity: 0.8 },
  infoRows: { marginTop: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, overflow: 'hidden' },
  infoRow: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border },
  safetyNotice: { gap: spacing[1], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.dangerBg },
  safetyConfirmed: { gap: spacing[1], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.successBg },
  notice: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card },
  noticeCopy: { lineHeight: 22 },
  actions: { gap: spacing[3], marginTop: spacing[4] },
  feedback: { textAlign: 'center' },
  speakAction: { backgroundColor: color.brand.navy },
  recoveryButton: { marginTop: spacing[2] },
});
