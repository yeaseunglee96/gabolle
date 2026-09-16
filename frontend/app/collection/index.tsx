// 부슐랭 홈 — 내가 만든 리스트와 최근 담은 장소를 한눈에 본다.
// SNS(피드·코스)와 무관한 개인 아카이브다.
//
// 🔴 정정 (2026-09-16, S15P21E201-1071) — "전부 기기에만 저장된다" 는 이제 사실이 아니다.
// 로그인하면 계정에 저장된다. 안 했으면 여전히 기기에만 남는다. 아래 안내 문구가 그 둘을
// 갈라서 말한다 — 화면이 저장되는 곳을 틀리게 말하면 사용자가 잃을 것을 잃는다.
import { useState } from 'react';
import { Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Screen } from '@/components/Screen';
import { Skeleton } from '@/components/Skeleton';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useCollection } from '@/collection/CollectionProvider';
import { useLayout } from '@/layout/useLayout';

export default function CollectionHome() {
  const router = useRouter();
  const { tx } = useI18n();
  const { ready, syncedToServer, syncState, retrySync, lists, places, totalPlaceCount, recentPlaces, createList } = useCollection();
  // 🔴 넓은 화면은 별도 화면이 아니라 같은 화면이 넓어지는 것이다. 격자 칸 수만 달라진다.
  const { kind } = useLayout();
  const [creating, setCreating] = useState(false);
  const [newName, setNewName] = useState('');

  const submitCreate = () => {
    const name = newName.trim();
    if (!name) return;
    const list = createList(name);
    setNewName('');
    setCreating(false);
    router.push(`/collection/${list.id}`);
  };

  return <View style={styles.shell}><Screen scroll withTabBar>
    <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/trips')} style={styles.back}><Text variant="title">‹ {tx('뒤로', 'Back')}</Text></Pressable>

    <View style={styles.hero}>
      <Text variant="caption" weight="bold" color={color.text.onAction}>{tx('나만의 부산 컬렉션', 'My Busan collection')}</Text>
      <Text variant="title" weight="bold" color={color.text.onAction} style={styles.heroTitle}>{tx('내가 직접 가보고, 좋아한 장소들을 모아보세요.', 'Gather the places you visited and loved.')}</Text>
      <View style={styles.heroStats}>
        <Text variant="body" weight="bold" color={color.text.onAction}>{tx(`📍 ${totalPlaceCount}곳`, `📍 ${totalPlaceCount} places`)}</Text>
        <Text variant="body" weight="bold" color={color.text.onAction}>{tx(`📋 ${lists.length}개 리스트`, `📋 ${lists.length} lists`)}</Text>
      </View>
      {syncState === 'loading' ? (
        <Text variant="caption" color={color.text.onDarkMuted} style={styles.deviceOnlyNotice}>{tx('계정에 저장된 것을 불러오고 있어요.', 'Loading what is saved to your account…')}</Text>
      ) : syncedToServer ? (
        <Text variant="caption" color={color.text.onDarkMuted} style={styles.deviceOnlyNotice}>{tx('내 계정에 저장돼요. 다른 기기에서도 보여요.', "Saved to your account — you'll see it on your other devices.")}</Text>
      ) : (
        <View style={styles.noticeRow}>
          <Text variant="caption" color={color.text.onDarkMuted} style={styles.noticeCopy}>{tx('이 기기에만 저장돼요. 로그인하면 계정에 저장돼요.', 'Saved only on this device — sign in to keep it on your account.')}</Text>
          {/* 🔴 문구가 곧 행동이 되게 한다. 다만 강요하지 않는다 — 로그인 안 해도 부슐랭은 그대로 쓴다. */}
          <Pressable accessibilityRole="button" accessibilityLabel={tx('로그인', 'Sign in')} onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/collection' } })} style={({ pressed }) => [styles.signInPill, pressed && styles.pressed]}>
            <Text variant="caption" weight="bold" color={color.brand.navy}>{tx('로그인', 'Sign in')}</Text>
          </Pressable>
        </View>
      )}
    </View>

    <View style={styles.sectionHeading}><Text variant="title" weight="bold">{tx('내 리스트', 'My lists')}</Text><Button label={tx('+ 새 리스트', '+ New list')} variant="ghost" onPress={() => setCreating(true)} containerStyle={styles.newListButton} /></View>

    {creating ? <Card style={styles.createCard}>
      <Text variant="caption" weight="bold" color={color.text.muted}>{tx('리스트 이름', 'List name')}</Text>
      <TextInput accessibilityLabel={tx('리스트 이름', 'List name')} value={newName} onChangeText={setNewName} placeholder={tx('예: 다시 가고 싶은 카페', 'e.g. Cafés to revisit')} placeholderTextColor={color.text.muted} autoFocus style={styles.input} />
      <View style={styles.createActions}>
        <Button label={tx('취소', 'Cancel')} variant="ghost" onPress={() => { setCreating(false); setNewName(''); }} containerStyle={styles.createActionButton} />
        <Button label={tx('만들기', 'Create')} disabled={!newName.trim()} onPress={submitCreate} containerStyle={styles.createActionButton} />
      </View>
    </Card> : null}

    {ready && lists.length === 0 && !creating ? <View style={styles.empty}><Text variant="body" weight="bold">{tx('아직 리스트가 없어요.', 'No lists yet.')}</Text><Text variant="caption" color={color.text.muted}>{tx('원하는 이름으로 첫 리스트를 만들어 보세요.', 'Create your first list with any name you like.')}</Text></View> : null}

    {syncState === 'unreachable' ? (
      <View style={styles.syncWarn}>
        {/* 🔴 기기 것을 그대로 그린다. 「못 불러왔어요」로 화면을 덮지 않는다 —
            덮으면 리스트가 사라진 것처럼 보이는데 그건 사실이 아니다. */}
        <Text variant="caption" color={color.text.body} style={styles.noticeCopy}>{tx('지금은 계정과 못 맞췄어요. 이 기기에 있는 것을 보여드려요.', "We couldn't sync with your account — showing what is on this device.")}</Text>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('다시 시도', 'Try again')} onPress={retrySync} style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}>
          <Text variant="caption" weight="bold" color={color.brand.navy}>{tx('다시 시도', 'Try again')}</Text>
        </Pressable>
      </View>
    ) : null}

    <View style={styles.grid}>
      {lists.map((list) => {
        const listPlaces = list.placeIds.map((id) => places[id]).filter((entry): entry is NonNullable<typeof entry> => Boolean(entry));
        const previewPhotos = listPlaces.map((place) => place.photoUri).filter((uri): uri is string => Boolean(uri)).slice(0, 4);
        return <Pressable key={list.id} accessibilityRole="button" onPress={() => router.push(`/collection/${list.id}`)} style={({ pressed }) => [styles.listCard, kind === 'tablet' && styles.listCardWide, pressed && styles.pressed]}>
          <View style={styles.listPreview}>
            {previewPhotos.length ? previewPhotos.map((uri, index) => <Image key={index} source={{ uri }} resizeMode="cover" style={styles.previewImage} />) : <View style={styles.previewPlaceholder}><Text variant="caption" color={color.text.muted}>{tx('사진 없음', 'No photos yet')}</Text></View>}
          </View>
          <Text variant="body" weight="bold">{list.name}</Text>
          <Text variant="caption" color={color.text.muted}>{tx(`${listPlaces.length}곳`, `${listPlaces.length} places`)}</Text>
        </Pressable>;
      })}
    </View>

    <Text variant="title" weight="bold" style={styles.sectionTitle}>{tx('최근 추가한 장소', 'Recently added')}</Text>
    {recentPlaces.length === 0 ? <Text variant="caption" color={color.text.muted}>{tx('아직 담은 장소가 없어요.', 'No places added yet.')}</Text> : <View style={styles.recentList}>{recentPlaces.map((place) => <View key={place.id} style={styles.recentRow}>
      {place.photoUri ? <Image source={{ uri: place.photoUri }} resizeMode="cover" style={styles.recentPhoto} /> : <View style={[styles.recentPhoto, styles.recentPhotoPlaceholder]} />}
      <View style={styles.grow}><Text variant="body" weight="bold">{place.name}</Text><View style={styles.recentMeta}>{place.category ? <View style={styles.chip}><Text variant="caption" weight="bold" color={color.text.body}>{place.category}</Text></View> : null}{place.locality ? <Text variant="caption" color={color.text.muted}>{place.locality}</Text> : null}</View></View>
    </View>)}</View>}
  </Screen><TabBar active="map" /></View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.brand.ivory },
  back: { minHeight: 44, alignSelf: 'flex-start', justifyContent: 'center', marginBottom: spacing[3] },
  hero: { gap: spacing[2], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.navy },
  heroTitle: { marginTop: spacing[1] },
  heroStats: { flexDirection: 'row', gap: spacing[4], marginTop: spacing[3] },
  noticeRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: spacing[2], marginTop: spacing[3] },
  noticeCopy: { flexShrink: 1 },
  signInPill: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.brand.ivory },
  syncWarn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: spacing[2], backgroundColor: color.surface.tint, borderRadius: radius.md, padding: spacing[3], marginTop: spacing[3] },
  retryButton: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[3] },
  deviceOnlyNotice: { marginTop: spacing[3], opacity: 0.72 },
  sectionHeading: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: spacing[6] },
  sectionTitle: { marginTop: spacing[8], marginBottom: spacing[2] },
  newListButton: { width: 'auto', paddingHorizontal: spacing[4] },
  createCard: { gap: spacing[2], marginTop: spacing[3] },
  input: { minHeight: 44, paddingHorizontal: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  createActions: { flexDirection: 'row', gap: spacing[2] },
  createActionButton: { flex: 1 },
  empty: { gap: spacing[1], marginTop: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3], marginTop: spacing[3] },
  listCardWide: { width: '31%' },
  listCard: { width: '47%', gap: spacing[1], padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card },
  pressed: { opacity: 0.85 },
  listPreview: { flexDirection: 'row', flexWrap: 'wrap', height: 96, borderRadius: radius.md, overflow: 'hidden', backgroundColor: color.surface.soft },
  previewImage: { width: '50%', height: '50%' },
  previewPlaceholder: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  recentList: { gap: spacing[3] },
  recentRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card },
  recentPhoto: { width: 56, height: 56, borderRadius: radius.md },
  recentPhotoPlaceholder: { backgroundColor: color.surface.soft },
  grow: { flex: 1, gap: spacing[1] },
  recentMeta: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  chip: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, backgroundColor: color.surface.soft },
});
