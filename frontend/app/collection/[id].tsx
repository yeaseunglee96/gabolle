// 부슐랭 리스트 상세 — 장소를 담고, 한줄메모를 남기고, 뺀다.
import { useState } from 'react';
import { Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useCollection } from '@/collection/CollectionProvider';

export default function CollectionListDetail() {
  const router = useRouter();
  const { tx } = useI18n();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { lists, places, addNewPlaceToList, removePlaceFromList, deleteList } = useCollection();
  const list = lists.find((entry) => entry.id === id);
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const [category, setCategory] = useState('');
  const [locality, setLocality] = useState('');
  const [note, setNote] = useState('');
  const [photoUri, setPhotoUri] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  if (!list) return <View style={styles.shell}><Screen scroll><View style={styles.empty}><Text variant="title" weight="bold">{tx('리스트를 찾을 수 없어요', "Couldn't find this list")}</Text><Button label={tx('부슐랭으로', 'Back to collection')} variant="ghost" onPress={() => router.replace('/collection')} /></View></Screen></View>;

  const listPlaces = list.placeIds.map((placeId) => places[placeId]).filter((entry): entry is NonNullable<typeof entry> => Boolean(entry));

  const pickPhoto = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (!result.canceled && result.assets[0]) setPhotoUri(result.assets[0].uri);
  };

  const submitPlace = () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    addNewPlaceToList(list.id, { name: trimmed, category: category.trim() || null, locality: locality.trim() || null, photoUri, note: note.trim() || null });
    setName(''); setCategory(''); setLocality(''); setNote(''); setPhotoUri(null); setAdding(false);
  };

  const confirmDeleteList = () => {
    deleteList(list.id);
    router.replace('/collection');
  };

  return <View style={styles.shell}><Screen scroll>
    <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/collection')} style={styles.back}><Text variant="title">‹ {tx('뒤로', 'Back')}</Text></Pressable>

    <View style={styles.headerRow}>
      <View style={styles.grow}><Text variant="display" weight="bold">{list.name}</Text>{list.description ? <Text variant="caption" color={color.text.muted}>{list.description}</Text> : null}<Text variant="caption" color={color.text.muted}>{tx(`${listPlaces.length}곳`, `${listPlaces.length} places`)}</Text></View>
      {confirmDelete ? <View style={styles.deleteConfirm}><Pressable accessibilityRole="button" onPress={() => setConfirmDelete(false)}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('취소', 'Cancel')}</Text></Pressable><Pressable accessibilityRole="button" onPress={confirmDeleteList}><Text variant="caption" weight="bold" color={color.state.danger}>{tx('삭제 확정', 'Confirm delete')}</Text></Pressable></View> : <Pressable accessibilityRole="button" accessibilityLabel={tx('리스트 삭제', 'Delete list')} onPress={() => setConfirmDelete(true)}><Text variant="caption" weight="bold" color={color.state.danger}>{tx('삭제', 'Delete')}</Text></Pressable>}
    </View>

    <Button label={adding ? tx('취소', 'Cancel') : tx('+ 장소 담기', '+ Add a place')} variant={adding ? 'ghost' : 'primary'} onPress={() => setAdding((value) => !value)} containerStyle={styles.addToggle} />

    {adding ? <Card style={styles.form}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('사진 선택', 'Choose photo')} onPress={() => void pickPhoto()} style={styles.photoPicker}>
        {photoUri ? <Image source={{ uri: photoUri }} resizeMode="cover" style={styles.photoPreview} /> : <Text variant="caption" color={color.text.muted}>{tx('사진 추가 (선택)', 'Add photo (optional)')}</Text>}
      </Pressable>
      <TextInput accessibilityLabel={tx('장소 이름', 'Place name')} value={name} onChangeText={setName} placeholder={tx('장소 이름', 'Place name')} placeholderTextColor={color.text.muted} style={styles.input} />
      <View style={styles.row}>
        <TextInput accessibilityLabel={tx('카테고리', 'Category')} value={category} onChangeText={setCategory} placeholder={tx('카테고리 (예: 카페)', 'Category (e.g. Café)')} placeholderTextColor={color.text.muted} style={[styles.input, styles.rowItem]} />
        <TextInput accessibilityLabel={tx('지역', 'Locality')} value={locality} onChangeText={setLocality} placeholder={tx('지역 (예: 영도구)', 'Area (e.g. Yeongdo-gu)')} placeholderTextColor={color.text.muted} style={[styles.input, styles.rowItem]} />
      </View>
      <TextInput accessibilityLabel={tx('한줄 메모', 'One-line note')} value={note} onChangeText={setNote} placeholder={tx('한줄 메모 (선택)', 'One-line note (optional)')} placeholderTextColor={color.text.muted} style={styles.input} />
      <Button label={tx('담기', 'Save')} disabled={!name.trim()} onPress={submitPlace} />
    </Card> : null}

    {listPlaces.length === 0 && !adding ? <View style={styles.empty}><Text variant="body" weight="bold">{tx('아직 담은 장소가 없어요.', 'No places in this list yet.')}</Text></View> : null}

    <View style={styles.places}>{listPlaces.map((place) => <View key={place.id} style={styles.placeRow}>
      {place.photoUri ? <Image source={{ uri: place.photoUri }} resizeMode="cover" style={styles.placePhoto} /> : <View style={[styles.placePhoto, styles.placePhotoPlaceholder]} />}
      <View style={styles.grow}>
        <Text variant="body" weight="bold">{place.name}</Text>
        <View style={styles.metaRow}>{place.category ? <Text variant="caption" color={color.text.muted}>{place.category}</Text> : null}{place.locality ? <Text variant="caption" color={color.text.muted}> · {place.locality}</Text> : null}</View>
        {place.note ? <Text variant="caption" color={color.text.body}>{place.note}</Text> : null}
      </View>
      <Pressable accessibilityRole="button" accessibilityLabel={tx(`${place.name} 빼기`, `Remove ${place.name}`)} onPress={() => removePlaceFromList(list.id, place.id)} style={styles.removeButton}><Text variant="caption" weight="bold" color={color.state.danger}>{tx('빼기', 'Remove')}</Text></Pressable>
    </View>)}</View>
  </Screen></View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.brand.ivory },
  back: { minHeight: 44, alignSelf: 'flex-start', justifyContent: 'center', marginBottom: spacing[3] },
  headerRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3] },
  grow: { flex: 1, gap: spacing[1] },
  deleteConfirm: { flexDirection: 'row', gap: spacing[3] },
  addToggle: { marginTop: spacing[4] },
  form: { gap: spacing[2], marginTop: spacing[3] },
  photoPicker: { minHeight: 120, borderRadius: radius.md, borderWidth: 1, borderStyle: 'dashed', borderColor: color.surface.field, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  photoPreview: { width: '100%', height: 120 },
  row: { flexDirection: 'row', gap: spacing[2] },
  rowItem: { flex: 1 },
  input: { minHeight: 44, paddingHorizontal: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  empty: { gap: spacing[1], marginTop: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  places: { gap: spacing[3], marginTop: spacing[4] },
  placeRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card },
  placePhoto: { width: 56, height: 56, borderRadius: radius.md },
  placePhotoPlaceholder: { backgroundColor: color.surface.soft },
  metaRow: { flexDirection: 'row' },
  removeButton: { minHeight: 44, paddingHorizontal: spacing[2], alignItems: 'center', justifyContent: 'center' },
});
