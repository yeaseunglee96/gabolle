// 저장한 장소 — home.tsx 캐러셀의 하트·장소 상세의 "내 여행 후보에 저장"이 쓰는 것과
// 같은 AsyncStorage 키(gabolle.saved-home-places, place/[id].tsx 에서 내보냄)를 읽어
// 실제 목록을 보여준다. 전에는 이 키를 아예 안 읽고 항상 빈 상태만 보여주고 있었다.
// 언어 다섯을 다 받는다. 이 함수가 'ko' | 'en' 만 받으면 부르는 쪽
// 열다섯 곳이 각자 떨어뜨려야 하고, 한 곳만 빠뜨리면 일본어 사용자가 한국어 이름을 본다.
// 떨어뜨리는 일은 여기 한 자리에서 한다.
import { resolveTextLanguage, type LanguageCode } from '@/i18n/languages';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useFocusEffect, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { DEMO_PLACES, loadSavedPlaceIds, setSavedPlace } from '@/discovery/savedPlaces';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { color, radius, spacing } from '@/design/tokens';
import { getPlace, hasLocalityScore, needsFoodSafetyCheck } from '@/discovery/places';
import { placeNameForLanguage } from '@/discovery/romanize';
import { useI18n } from '@/i18n';

type SavedCard = { placeId: string; title: string; subtitle: string; image: number | { uri: string } | null; hasLocalityScore: boolean; needsFoodSafetyCheck: boolean };

// — 카카오 평점처럼 근거 없는 값을 지어내 보여주지 않는다. 저장 목록은
// place/[id].tsx와 같은 place/features 데이터를 쓰므로, 상세 화면에 이미 있던 두 배지
// (로컬 점수 유무·알레르기 확인 필요)를 목록 카드에도 그대로 옮긴다 — 데모 장소는
// features 자체가 없어 둘 다 자연히 꺼진 채로 남는다(지어내지 않는다).
async function resolveSavedPlace(placeId: string, tx: (ko: string, en: string) => string, language: LanguageCode): Promise<SavedCard | null> {
  if (placeId in DEMO_PLACES) {
    const demo = DEMO_PLACES[placeId as keyof typeof DEMO_PLACES];
    return { placeId, title: tx(demo.titleKo, demo.titleEn), subtitle: tx(demo.subtitleKo, demo.subtitleEn), image: demo.image, hasLocalityScore: false, needsFoodSafetyCheck: false };
  }
  try {
    const place = await getPlace(placeId);
    return { placeId, title: placeNameForLanguage(place.nameKo, place.nameEn, language), subtitle: tx(place.address, place.addressEn ?? place.address), image: place.photoUrl ? { uri: place.photoUrl } : null, hasLocalityScore: hasLocalityScore(place), needsFoodSafetyCheck: needsFoodSafetyCheck(place) };
  } catch (error) {
    // 삭제됐거나(404) 서버가 잠깐 안 되는 장소는 목록에서 조용히 뺀다 — 저장한 것 자체는
    // 기기에 그대로 남아 있으니 다음에 다시 시도하면 보일 수 있다.
    if (error instanceof ApiClientError) return null;
    return null;
  }
}

export default function Saved() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { accessToken } = useAuth();
  const [state, setState] = useState<'loading' | 'ready'>('loading');
  const [cards, setCards] = useState<SavedCard[]>([]);
  // 저장 해제가 서버까지 못 갔을 때만 채워진다.
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setState('loading');
    const ids = await loadSavedPlaceIds(accessToken);
    const resolved = await Promise.all(ids.map((id) => resolveSavedPlace(id, tx, language)));
    setCards(resolved.filter((card): card is SavedCard => card !== null));
    setState('ready');
  }, [tx]);

  // 다른 화면(홈 캐러셀·장소 상세)에서 저장·해제하고 이 탭으로 돌아왔을 때 바로 반영되도록
  // 마운트 시 한 번이 아니라 포커스를 받을 때마다 다시 읽는다.
  useFocusEffect(useCallback(() => { void load(); }, [load]));

  async function unsave(placeId: string) {
    setNotice(null);
    const { sync } = await setSavedPlace(placeId, false, accessToken);
    setCards((current) => current.filter((card) => card.placeId !== placeId));
    // — 서버가 못 받았으면 그 사실을 알린다. 안 알리면 다음에 이 탭을
    // 다시 열었을 때 카드가 되살아나는데(load 가 서버 목록과 합치므로) 사용자는
    // "지웠는데 왜 또 있지" 만 겪고 이유를 모른다.
    if (sync === 'failed') setNotice(tx('서버에 아직 반영하지 못했어요. 다시 열면 이 장소가 남아 있을 수 있어요.', 'Not synced to the server yet — this place may reappear next time you open this tab.'));
  }

  return <View style={styles.shell}><Screen scroll withTabBar style={styles.screen}>
    <View style={styles.heading}><Eyebrow>{tx('저장 목록', 'Saved')}</Eyebrow><Text variant="display" weight="bold">{tx('저장한 장소', 'Saved places')}</Text></View>

    {notice ? <View style={styles.notice}><Text color={color.text.body}>{notice}</Text></View> : null}

    {state === 'loading' && <ActivityIndicator style={styles.spinner} color={color.action.primary} />}

    {state === 'ready' && cards.length === 0 && (
      <View accessibilityLiveRegion="polite" style={styles.empty}>
        <View style={styles.mark}><Text variant="display" color={color.action.primary}>⌁</Text></View>
        <Text variant="title" weight="bold">{tx('아직 저장한 장소가 없어요', 'No saved places yet')}</Text>
        <Text color={color.text.body} style={styles.description}>{tx('마음에 드는 장소와 추천 코스를 저장하면 이곳에서 다시 볼 수 있어요.', 'Save places and recommended routes you like to find them here again.')}</Text>
        <Pressable accessibilityRole="button" onPress={() => router.push('/home')} style={styles.action}><Text weight="bold" color={color.text.onAction}>{tx('부산 둘러보기', 'Explore Busan')}</Text></Pressable>
      </View>
    )}

    {state === 'ready' && cards.length > 0 && (
      <View style={styles.list}>
        {cards.map((card) => (
          <Pressable key={card.placeId} accessibilityRole="button" onPress={() => router.push(`/place/${card.placeId}`)} style={styles.card}>
            {card.image ? <Image source={card.image} resizeMode="cover" style={styles.cardImage} /> : <View style={[styles.cardImage, styles.cardImageFallback]}><Text weight="bold" color={color.text.heading}>GABOLLE</Text></View>}
            <View style={styles.cardBody}>
              <Text variant="title" weight="bold">{card.title}</Text>
              <Text variant="caption" color={color.text.muted}>{card.subtitle}</Text>
              {card.hasLocalityScore && <View style={styles.scoreBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('로컬 점수 있음', 'Has locality score')}</Text></View>}
              {card.needsFoodSafetyCheck && <Text variant="caption" weight="bold" color={color.state.danger}>{tx('알레르기·식단 확인 필요', 'Check allergy/dietary info')}</Text>}
            </View>
            <Pressable accessibilityRole="button" accessibilityLabel={tx(`${card.title} 저장 취소`, `Unsave ${card.title}`)} onPress={(event) => { event.stopPropagation(); void unsave(card.placeId); }} style={styles.unsaveButton}>
              <Text variant="caption" weight="bold" color={color.text.muted}>{tx('저장 취소', 'Unsave')}</Text>
            </Pressable>
          </Pressable>
        ))}
      </View>
    )}
  </Screen><TabBar active="saved" /></View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.canvas },
  screen: { flex: 1, backgroundColor: color.canvas },
  heading: { gap: spacing[2], marginBottom: spacing[6] },
  notice: { padding: spacing[4], marginBottom: spacing[6], borderRadius: radius.md, backgroundColor: color.surface.tint },
  spinner: { marginTop: spacing[8] },
  empty: { flex: 1, minHeight: 360, alignItems: 'center', justifyContent: 'center', gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  mark: { width: 72, height: 72, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.tint },
  description: { maxWidth: 420, textAlign: 'center' },
  action: { minHeight: 48, minWidth: 220, marginTop: spacing[3], alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.navy },
  list: { gap: spacing[3] },
  card: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card },
  cardImage: { width: 64, height: 64, borderRadius: radius.md },
  cardImageFallback: { alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.tint },
  cardBody: { flex: 1, gap: spacing[1] },
  scoreBadge: { alignSelf: 'flex-start', borderRadius: radius.full, paddingHorizontal: spacing[3], paddingVertical: spacing[1], backgroundColor: color.brand.navy },
  unsaveButton: { minHeight: 44, paddingHorizontal: spacing[2], alignItems: 'center', justifyContent: 'center' },
});
