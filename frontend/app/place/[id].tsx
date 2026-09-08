import { useEffect, useState } from 'react';
import { ActivityIndicator, ImageBackground, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { getPlace, hasLocalityScore, type Place as ApiPlace } from '@/discovery/places';
import { useI18n } from '@/i18n';
import { PlacePhraseModal } from '@/components/PlacePhraseModal';
import { listAvailableMapApps, type AvailableMapProvider } from '@/utils/externalMaps';

// 홈 화면의 3개 데모 카드는 지금도 이 로컬 값을 그대로 쓴다 — place 표가 비어 있어(-547 적재 전)
// 실제 API 로는 이 셋이 아직 안 나온다. 그 밖의 id(실 장소 id)는 아래에서 진짜 API 로 조회한다.
const PLACES = {
  haeundae: { titleKo: '해운대 해수욕장', titleEn: 'Haeundae Beach', subtitleKo: '푸른 바다와 도시가 만나는 곳', subtitleEn: 'Where the blue sea meets the city', image: require('../../assets/home/haeundae.png') },
  gwangalli: { titleKo: '광안리 해수욕장', titleEn: 'Gwangalli Beach', subtitleKo: '야경과 함께하는 해변 산책', subtitleEn: 'A beach walk under the night view', image: require('../../assets/home/gwangalli.png') },
  gamcheon: { titleKo: '감천문화마을', titleEn: 'Gamcheon Culture Village', subtitleKo: '형형색색 감성 골목 여행', subtitleEn: 'A colorful walk through winding alleys', image: require('../../assets/home/gamcheon.png') },
} as const;

const SAVED_PLACES_KEY = 'gabolle.saved-home-places';

type RemoteState =
  | { status: 'loading' }
  | { status: 'loaded'; place: ApiPlace }
  | { status: 'not-found' }
  | { status: 'error' };

export default function Place() {
  const router = useRouter();
  const { tx } = useI18n();
  const { width } = useWindowDimensions();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const demoPlace = id && id in PLACES ? PLACES[id as keyof typeof PLACES] : null;
  const [isSaved, setIsSaved] = useState(false);
  const [feedback, setFeedback] = useState('');
  const [mapApps, setMapApps] = useState<AvailableMapProvider[]>([]);
  const [remote, setRemote] = useState<RemoteState>({ status: 'loading' });
  const [retryCount, setRetryCount] = useState(0);
  const [phraseModalOpen, setPhraseModalOpen] = useState(false);

  // 데모 3곳은 로컬 값을, 그 밖의 id 는 방금 받아온 API 응답을 같은 모양으로 맞춘다.
  const resolved = demoPlace
    ? { title: tx(demoPlace.titleKo, demoPlace.titleEn), subtitle: tx(demoPlace.subtitleKo, demoPlace.subtitleEn), apiPlace: null as ApiPlace | null }
    : remote.status === 'loaded'
      ? { title: tx(remote.place.nameKo, remote.place.nameEn ?? remote.place.nameKo), subtitle: tx(remote.place.address, remote.place.addressEn ?? remote.place.address), apiPlace: remote.place }
      : null;

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
    if (!resolved) return;
    let active = true;
    void listAvailableMapApps({ name: resolved.title }).then((apps) => { if (active) setMapApps(apps); });
    return () => { active = false; };
  }, [resolved]);

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
          <ImageBackground source={demoPlace.image} resizeMode="cover" style={[styles.hero, width >= 760 && styles.heroWide]} imageStyle={styles.heroImage}>
            <View style={styles.shade} />
            <View style={styles.heroCopy}>
              <Text variant="display" weight="bold" color={color.text.onAction}>{resolved.title}</Text>
              <Text color={color.text.onAction}>{resolved.subtitle}</Text>
            </View>
          </ImageBackground>
        ) : (
          <View style={[styles.hero, styles.heroPlain, width >= 760 && styles.heroWide]}>
            <View style={styles.heroCopy}>
              <Text variant="display" weight="bold" color={color.text.onAction}>{resolved.title}</Text>
              <Text color={color.text.onAction}>{resolved.subtitle}</Text>
              {resolved.apiPlace && hasLocalityScore(resolved.apiPlace) ? (
                <View style={styles.scoreBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('로컬 점수 있음', 'Has locality score')}</Text></View>
              ) : null}
            </View>
          </View>
        )}
        <View style={styles.notice} accessibilityLiveRegion="polite">
          <Text variant="title" weight="bold">{tx('상세 정보를 준비하고 있어요', 'Details are on the way')}</Text>
          <Text color={color.text.body} style={styles.noticeCopy}>{tx('운영시간·접근성·혼잡도·리뷰는 실제 장소 조회 API가 연결된 뒤 표시합니다. 확인되지 않은 정보는 임의로 보여드리지 않아요.', 'Hours, accessibility, crowd levels, and reviews will show once the real place lookup API is connected. We never show unverified information.')}</Text>
        </View>
        <View style={styles.actions}>
          <Button label={isSaved ? tx('내 여행 후보에서 빼기', 'Remove from candidates') : tx('내 여행 후보에 저장', 'Save to candidates')} variant="ghost" onPress={() => void toggleSaved()} />
          <Button label={tx('한국어로 말하기', 'Speak Korean')} onPress={() => setPhraseModalOpen(true)} containerStyle={styles.speakAction} />
          <View style={styles.mapRow}>{mapApps.map((app) => <Button key={app.key} label={tx(`${app.labelKo}으로 이동`, `Open in ${app.labelEn}`)} onPress={() => void app.open()} containerStyle={styles.mapAction} />)}</View>
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
  topBar: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[3] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  logo: { width: 96, height: 28 },
  spacer: { width: 44 },
  hero: { height: 240, justifyContent: 'flex-end', overflow: 'hidden', borderRadius: radius.lg },
  heroWide: { height: 360 },
  heroImage: { borderRadius: radius.lg },
  heroPlain: { backgroundColor: color.brand.navy, padding: spacing[4] },
  shade: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(8, 27, 53, 0.25)' },
  heroCopy: { gap: spacing[1], padding: spacing[4] },
  scoreBadge: { alignSelf: 'flex-start', marginTop: spacing[2], borderRadius: radius.full, paddingHorizontal: spacing[3], paddingVertical: spacing[1], backgroundColor: 'rgba(255,255,255,0.18)' },
  notice: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: '#eee5da', borderRadius: radius.lg, backgroundColor: color.surface.card },
  noticeCopy: { lineHeight: 22 },
  actions: { gap: spacing[3], marginTop: spacing[4] },
  feedback: { textAlign: 'center' },
  speakAction: { backgroundColor: color.brand.navy },
  mapRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  mapAction: { flex: 1, minWidth: 160, backgroundColor: color.brand.navy },
  recoveryButton: { marginTop: spacing[2] },
});
