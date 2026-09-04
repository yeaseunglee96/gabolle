import { useEffect, useState } from 'react';
import { ImageBackground, Linking, Pressable, StyleSheet, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

const PLACES = {
  haeundae: { title: '해운대 해수욕장', subtitle: '푸른 바다와 도시가 만나는 곳', image: require('../../assets/home/haeundae.png') },
  gwangalli: { title: '광안리 해수욕장', subtitle: '야경과 함께하는 해변 산책', image: require('../../assets/home/gwangalli.png') },
  gamcheon: { title: '감천문화마을', subtitle: '형형색색 감성 골목 여행', image: require('../../assets/home/gamcheon.png') },
} as const;

const SAVED_PLACES_KEY = 'gabolle.saved-home-places';

export default function Place() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const place = id && id in PLACES ? PLACES[id as keyof typeof PLACES] : null;
  const [isSaved, setIsSaved] = useState(false);
  const [feedback, setFeedback] = useState('');

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
    setFeedback(nextSaved ? '내 여행 후보에 저장했어요.' : '저장을 해제했어요.');
  };

  const openMap = async () => {
    if (!place) return;
    await Linking.openURL(`https://map.kakao.com/link/search/${encodeURIComponent(place.title)}`);
  };

  return (
    <Screen scroll wide style={styles.screen}>
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel="이전 화면으로 이동" onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹</Text>
        </Pressable>
        <BrandLogoLink href="/home" imageStyle={styles.logo} />
        <View style={styles.spacer} />
      </View>

      {place ? <>
        <ImageBackground source={place.image} resizeMode="cover" style={styles.hero} imageStyle={styles.heroImage}>
          <View style={styles.shade} />
          <View style={styles.heroCopy}>
            <Text variant="display" weight="bold" color={color.text.onAction}>{place.title}</Text>
            <Text color={color.text.onAction}>{place.subtitle}</Text>
          </View>
        </ImageBackground>
        <View style={styles.notice} accessibilityLiveRegion="polite">
          <Text variant="title" weight="bold">상세 정보를 준비하고 있어요</Text>
          <Text color={color.text.body} style={styles.noticeCopy}>운영시간·접근성·혼잡도·리뷰는 실제 장소 조회 API가 연결된 뒤 표시합니다. 확인되지 않은 정보는 임의로 보여드리지 않아요.</Text>
        </View>
        <View style={styles.actions}>
          <Button label={isSaved ? '내 여행 후보에서 빼기' : '내 여행 후보에 저장'} variant="ghost" onPress={() => void toggleSaved()} />
          <Button label="카카오맵에서 위치 확인" onPress={() => void openMap()} containerStyle={styles.primaryAction} />
          {feedback ? <Text accessibilityLiveRegion="polite" color={color.text.body} style={styles.feedback}>{feedback}</Text> : null}
        </View>
      </> : <View style={styles.notice} accessibilityRole="alert">
        <Text variant="title" weight="bold">장소를 찾을 수 없어요</Text>
        <Text color={color.text.body}>목록으로 돌아가 다른 장소를 선택해 주세요.</Text>
        <Button label="홈으로 돌아가기" onPress={() => router.replace('/home')} containerStyle={styles.recoveryButton} />
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
  hero: { height: 360, justifyContent: 'flex-end', overflow: 'hidden', borderRadius: radius.lg },
  heroImage: { borderRadius: radius.lg },
  shade: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(8, 27, 53, 0.25)' },
  heroCopy: { gap: spacing[1], padding: spacing[4] },
  notice: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: '#eee5da', borderRadius: radius.lg, backgroundColor: color.surface.card },
  noticeCopy: { lineHeight: 22 },
  actions: { gap: spacing[3], marginTop: spacing[4] },
  feedback: { textAlign: 'center' },
  primaryAction: { backgroundColor: color.brand.navy },
  recoveryButton: { marginTop: spacing[2] },
});
