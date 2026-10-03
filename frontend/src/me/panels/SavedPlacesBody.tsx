// 저장한 장소(내 여행 후보) 본문 — 저장 탭 화면(app/(tabs)/saved.tsx)과 마이페이지 패널이 같은 것을 쓴다.
//
// 둘러보기·홈 캐러셀의 하트·장소 상세의 "내 여행 후보에 저장"이 쓰는 것과 같은 목록(savedPlaces.ts)을
// 읽는다. 전에는 이 목록을 보는 화면이 저장 탭 하나뿐이었는데 그 탭으로 가는 단추가 앱 어디에도 없어서,
// 저장은 되는데 다시 볼 길이 없었다(S15P21E201-1969). 제목과 설명은 껍데기가 그린다.
import { useCallback, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, View } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { loadSavedPlaceIds, setSavedPlace } from '@/discovery/savedPlaces';
import { resolveSavedPlace, type SavedPlaceCard } from '@/discovery/savedPlaceCards';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';

export function SavedPlacesBody() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { accessToken } = useAuth();
  const [state, setState] = useState<'loading' | 'ready'>('loading');
  const [cards, setCards] = useState<SavedPlaceCard[]>([]);
  // 빼기가 서버까지 못 갔을 때만 채워진다.
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setState('loading');
    const ids = await loadSavedPlaceIds(accessToken);
    const resolved = await Promise.all(ids.map((id) => resolveSavedPlace(id, tx, language)));
    setCards(resolved.filter((card): card is SavedPlaceCard => card !== null));
    setState('ready');
  }, [accessToken, tx, language]);

  // 다른 화면(둘러보기·장소 상세)에서 저장·해제하고 돌아왔을 때 바로 반영되도록 포커스마다 다시 읽는다.
  useFocusEffect(useCallback(() => { void load(); }, [load]));

  async function unsave(placeId: string) {
    setNotice(null);
    const { sync } = await setSavedPlace(placeId, false, accessToken);
    setCards((current) => current.filter((card) => card.placeId !== placeId));
    // 서버가 못 받았으면 그 사실을 알린다. 안 알리면 다음에 열었을 때 카드가 되살아나는데
    // (load 가 서버 목록과 합치므로) 사용자는 「지웠는데 왜 또 있지」만 겪고 이유를 모른다.
    if (sync === 'failed') setNotice(tx('서버에 아직 반영하지 못했어요. 다시 열면 이 장소가 남아 있을 수 있어요.', 'Not synced to the server yet — this place may reappear next time you open this tab.'));
  }

  return <>
    {notice ? <View style={styles.notice}><Text color={color.text.body}>{notice}</Text></View> : null}

    {state === 'loading' && <ActivityIndicator style={styles.spinner} color={color.action.primary} />}

    {state === 'ready' && cards.length === 0 && (
      <View accessibilityLiveRegion="polite" style={styles.empty}>
        <View style={styles.mark}><Text variant="display" color={color.action.primary}>⌁</Text></View>
        <Text variant="title" weight="bold">{tx('아직 저장한 장소가 없어요', 'No saved places yet')}</Text>
        <Text color={color.text.body} style={styles.description}>{tx('둘러보기에서 마음에 드는 곳을 「내 여행 후보에 저장」해 보세요. 여행을 만들 때 꼭 가고 싶은 곳으로 고를 수 있어요.', 'Save places you like from Explore with "Save". You can pick them as must-visit places when you plan a trip.')}</Text>
        <Pressable accessibilityRole="button" onPress={() => router.push('/explore')} style={styles.action}><Text weight="bold" color={color.text.onAction}>{tx('부산 둘러보기', 'Explore Busan')}</Text></Pressable>
      </View>
    )}

    {state === 'ready' && cards.length > 0 && (
      <View style={styles.list}>
        {cards.map((card) => (
          // 카드는 틀이다 — 사진·글만 누르는 곳이고 「후보에서 빼기」는 그 옆 형제다. 카드 전체를 누르는 곳으로 두면
          // 빼기가 단추 안의 단추가 되고, 웹은 그것을 허용하지 않는다(S15P21E201-1710).
          <View key={card.placeId} style={styles.card}>
            <Pressable accessibilityRole="button" onPress={() => router.push(`/place/${card.placeId}`)} style={styles.cardMain}>
              {card.image ? <Image source={card.image} resizeMode="cover" style={styles.cardImage} /> : <View style={[styles.cardImage, styles.cardImageFallback]}><Text weight="bold" color={color.text.heading}>GABOLLE</Text></View>}
              <View style={styles.cardBody}>
                <Text variant="title" weight="bold" numberOfLines={2}>{card.title}</Text>
                <Text variant="caption" color={color.text.muted} numberOfLines={2}>{card.subtitle}</Text>
                {card.needsFoodSafetyCheck && <Text variant="caption" weight="bold" color={color.state.danger}>{tx('알레르기·식단 확인 필요', 'Check allergy/dietary info')}</Text>}
              </View>
            </Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 후보에서 빼기', 'Remove %s from saved', card.title)} onPress={() => void unsave(card.placeId)} style={styles.unsaveButton}>
              <Text variant="caption" weight="bold" color={color.text.muted}>{tx('후보에서 빼기', 'Unsave')}</Text>
            </Pressable>
          </View>
        ))}
      </View>
    )}
  </>;
}

const styles = StyleSheet.create({
  notice: { padding: spacing[4], marginBottom: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.tint },
  spinner: { marginTop: spacing[8] },
  empty: { minHeight: 320, alignItems: 'center', justifyContent: 'center', gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  mark: { width: 72, height: 72, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.tint },
  description: { maxWidth: 420, textAlign: 'center' },
  action: { minHeight: 48, minWidth: 220, marginTop: spacing[3], alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.navy },
  list: { gap: spacing[3] },
  card: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card },
  cardMain: { flex: 1, minWidth: 0, flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  cardImage: { width: 64, height: 64, borderRadius: radius.md },
  cardImageFallback: { alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.tint },
  cardBody: { flex: 1, minWidth: 0, gap: spacing[1] },
  unsaveButton: { minHeight: 44, paddingHorizontal: spacing[2], alignItems: 'center', justifyContent: 'center' },
});
