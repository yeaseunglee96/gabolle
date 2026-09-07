import { useEffect, useState } from 'react';
import { ImageBackground, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { listAvailableMapApps, type AvailableMapProvider } from '@/utils/externalMaps';

const PLACES = {
  haeundae: { titleKo: '해운대 해수욕장', titleEn: 'Haeundae Beach', subtitleKo: '푸른 바다와 도시가 만나는 곳', subtitleEn: 'Where the blue sea meets the city', image: require('../../assets/home/haeundae.png') },
  gwangalli: { titleKo: '광안리 해수욕장', titleEn: 'Gwangalli Beach', subtitleKo: '야경과 함께하는 해변 산책', subtitleEn: 'A beach walk under the night view', image: require('../../assets/home/gwangalli.png') },
  gamcheon: { titleKo: '감천문화마을', titleEn: 'Gamcheon Culture Village', subtitleKo: '형형색색 감성 골목 여행', subtitleEn: 'A colorful walk through winding alleys', image: require('../../assets/home/gamcheon.png') },
} as const;

const SAVED_PLACES_KEY = 'gabolle.saved-home-places';

export default function Place() {
  const router = useRouter();
  const { tx } = useI18n();
  const { width } = useWindowDimensions();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const place = id && id in PLACES ? PLACES[id as keyof typeof PLACES] : null;
  const [isSaved, setIsSaved] = useState(false);
  const [feedback, setFeedback] = useState('');
  const [mapApps, setMapApps] = useState<AvailableMapProvider[]>([]);

  useEffect(() => {
    if (!place) return;
    let active = true;
    void listAvailableMapApps({ name: tx(place.titleKo, place.titleEn) }).then((apps) => { if (active) setMapApps(apps); });
    return () => { active = false; };
  }, [place, tx]);

  useEffect(() => {
    if (!id || !place) return;
    void AsyncStorage.getItem(SAVED_PLACES_KEY).then((raw) => {
      try {
        const savedIds = raw ? JSON.parse(raw) : [];
        setIsSaved(Array.isArray(savedIds) && savedIds.includes(id));
      } catch {
        void AsyncStorage.removeItem(SAVED_PLACES_KEY);
      }
    });
  }, [id, place]);

  const toggleSaved = async () => {
    if (!id || !place) return;
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

  return (
    <Screen scroll wide style={styles.screen}>
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹</Text>
        </Pressable>
        <BrandLogoLink href="/home" imageStyle={styles.logo} />
        <View style={styles.spacer} />
      </View>

      {place ? <>
        <ImageBackground source={place.image} resizeMode="cover" style={[styles.hero, width >= 760 && styles.heroWide]} imageStyle={styles.heroImage}>
          <View style={styles.shade} />
          <View style={styles.heroCopy}>
            <Text variant="display" weight="bold" color={color.text.onAction}>{tx(place.titleKo, place.titleEn)}</Text>
            <Text color={color.text.onAction}>{tx(place.subtitleKo, place.subtitleEn)}</Text>
          </View>
        </ImageBackground>
        <View style={styles.notice} accessibilityLiveRegion="polite">
          <Text variant="title" weight="bold">{tx('상세 정보를 준비하고 있어요', 'Details are on the way')}</Text>
          <Text color={color.text.body} style={styles.noticeCopy}>{tx('운영시간·접근성·혼잡도·리뷰는 실제 장소 조회 API가 연결된 뒤 표시합니다. 확인되지 않은 정보는 임의로 보여드리지 않아요.', 'Hours, accessibility, crowd levels, and reviews will show once the real place lookup API is connected. We never show unverified information.')}</Text>
        </View>
        <View style={styles.actions}>
          <Button label={isSaved ? tx('내 여행 후보에서 빼기', 'Remove from candidates') : tx('내 여행 후보에 저장', 'Save to candidates')} variant="ghost" onPress={() => void toggleSaved()} />
          <View style={styles.mapRow}>{mapApps.map((app) => <Button key={app.key} label={tx(`${app.labelKo}으로 이동`, `Open in ${app.labelEn}`)} onPress={() => void app.open()} containerStyle={styles.mapAction} />)}</View>
          {feedback ? <Text accessibilityLiveRegion="polite" color={color.text.body} style={styles.feedback}>{feedback}</Text> : null}
        </View>
      </> : <View style={styles.notice} accessibilityRole="alert">
        <Text variant="title" weight="bold">{tx('장소를 찾을 수 없어요', 'Place not found')}</Text>
        <Text color={color.text.body}>{tx('목록으로 돌아가 다른 장소를 선택해 주세요.', 'Go back to the list and choose another place.')}</Text>
        <Button label={tx('홈으로 돌아가기', 'Back to home')} onPress={() => router.replace('/home')} containerStyle={styles.recoveryButton} />
      </View>}
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
  shade: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(8, 27, 53, 0.25)' },
  heroCopy: { gap: spacing[1], padding: spacing[4] },
  notice: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: '#eee5da', borderRadius: radius.lg, backgroundColor: color.surface.card },
  noticeCopy: { lineHeight: 22 },
  actions: { gap: spacing[3], marginTop: spacing[4] },
  feedback: { textAlign: 'center' },
  mapRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  mapAction: { flex: 1, minWidth: 160, backgroundColor: color.brand.navy },
  recoveryButton: { marginTop: spacing[2] },
});
