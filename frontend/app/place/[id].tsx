import { ImageBackground, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

const PLACES = {
  haeundae: { title: '해운대 해수욕장', subtitle: '푸른 바다와 도시가 만나는 곳', image: require('../../assets/home/haeundae.png') },
  gwangalli: { title: '광안리 해수욕장', subtitle: '야경과 함께하는 해변 산책', image: require('../../assets/home/gwangalli.png') },
  gamcheon: { title: '감천문화마을', subtitle: '형형색색 감성 골목 여행', image: require('../../assets/home/gamcheon.png') },
} as const;

export default function Place() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const place = id && id in PLACES ? PLACES[id as keyof typeof PLACES] : null;

  return (
    <Screen scroll wide style={styles.screen}>
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel="이전 화면으로 이동" onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹</Text>
        </Pressable>
        <Text variant="caption" weight="bold" color={color.brand.orange}>PLACE</Text>
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
      </> : <View style={styles.notice} accessibilityRole="alert">
        <Text variant="title" weight="bold">장소를 찾을 수 없어요</Text>
        <Text color={color.text.body}>목록으로 돌아가 다른 장소를 선택해 주세요.</Text>
      </View>}
    </Screen>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  topBar: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[3] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  spacer: { width: 44 },
  hero: { height: 360, justifyContent: 'flex-end', overflow: 'hidden', borderRadius: radius.lg },
  heroImage: { borderRadius: radius.lg },
  shade: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(8, 27, 53, 0.25)' },
  heroCopy: { gap: spacing[1], padding: spacing[4] },
  notice: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: '#eee5da', borderRadius: radius.lg, backgroundColor: color.surface.card },
  noticeCopy: { lineHeight: 22 },
});
