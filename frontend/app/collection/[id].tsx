// 부슐랭 리스트 상세 — 장소를 담고, 한줄메모를 남기고, 뺀다.
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { WheelPicker } from '@/collection/WheelPicker';
import { categoryLabel, categoryWheelCodes, localityLabel, PLACE_LOCALITY_LABELS, WRITE_MY_OWN } from '@/discovery/placeCategoryLabels';
import { getPlaceCategories } from '@/discovery/placeCategories';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useAuth } from '@/auth/AuthProvider';
import { useI18n } from '@/i18n';
import { useCollection } from '@/collection/CollectionProvider';
import { COLLECTION_LIMITS } from '@/collection/collectionsApi';
import { searchOrigins, type OriginCandidate } from '@/plan/origins';
import { txf } from '@/i18n/format';

// : "카카오맵 자동완성으로 위치 자동 입력" 리포트 — 새 지도 SDK를 또 불러오는
// 대신, 이미 카카오 로컬 검색으로 동작 중인 이 엔드포인트를 그대로 쓴다(여행 만들기 출발지
// 검색과 같은 계약). 이름은 "origins"지만 실제로는 임의 장소 검색이라 여기 그대로 맞는다.
const SEARCH_DEBOUNCE_MS = 300;
const SEARCH_MIN_QUERY_LENGTH = 2;

export default function CollectionListDetail() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { accessToken } = useAuth();
  const { ready, lists, places, syncedToServer, addNewPlaceToList, removePlaceFromList, deleteList, renameList } = useCollection();
  const list = lists.find((entry) => entry.id === id);
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  // 휠에서 고른 코드와 직접 쓴 문자열을 따로 들고 있는다. 한 칸에 섞으면
  // 나중에 어느 쪽인지 못 가른다 — 코드는 아는 값들의 집합이라 가를 수 있지만, 그건
  // 표가 안 바뀔 때만 참이다.
  // 서버가 주는 분류 목록. 못 받으면 null 로 두고, 휠은 우리가 아는 코드로 채운다
  // 휠이 비면 그날은 분류를 아예 못 고르게 된다.
  const [serverCategories, setServerCategories] = useState<string[] | null>(null);
  const [categoryCode, setCategoryCode] = useState(WRITE_MY_OWN);
  const [localityCode, setLocalityCode] = useState(WRITE_MY_OWN);
  // 휠에 놓을 목록. 아는 코드를 표 순서대로 먼저 놓고, 맨 뒤에 「직접 쓰기」를 둔다.
  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      const result = await getPlaceCategories(controller.signal);
      if (result.state === 'success') setServerCategories(result.categories.map((item) => item.code));
    })();
    return () => controller.abort();
  }, []);

  const categoryOptions = [
    ...categoryWheelCodes(serverCategories).map((code) => ({ value: code, label: categoryLabel(code, language) })),
    { value: WRITE_MY_OWN, label: tx('직접 쓰기', 'Write my own') },
  ];
  const localityOptions = [
    ...Object.keys(PLACE_LOCALITY_LABELS).map((code) => ({ value: code, label: localityLabel(code, language) })),
    { value: WRITE_MY_OWN, label: tx('직접 쓰기', 'Write my own') },
  ];

  const [category, setCategory] = useState('');
  const [locality, setLocality] = useState('');
  const [note, setNote] = useState('');
  const [photoUri, setPhotoUri] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [searchResults, setSearchResults] = useState<OriginCandidate[]>([]);
  const [searching, setSearching] = useState(false);
  const [searched, setSearched] = useState(false);
  const searchDebounce = useRef<ReturnType<typeof setTimeout> | null>(null);
  const searchAbort = useRef<AbortController | null>(null);

  const performSearch = async (query: string) => {
    searchAbort.current?.abort();
    const controller = new AbortController();
    searchAbort.current = controller;
    setSearching(true);
    const result = await searchOrigins(query, accessToken, controller.signal);
    if (controller.signal.aborted) return;
    setSearching(false);
    setSearched(true);
    setSearchResults(result.state === 'success' ? result.items : []);
  };
  const handleNameChange = (value: string) => {
    setName(value);
    setCoords(null); // 직접 고치면 이전 선택은 더 이상 안 맞는다 — 좌표를 버린다.
    if (searchDebounce.current) clearTimeout(searchDebounce.current);
    const trimmed = value.trim();
    if (trimmed.length < SEARCH_MIN_QUERY_LENGTH) {
      searchAbort.current?.abort();
      setSearching(false); setSearched(false); setSearchResults([]);
      return;
    }
    searchDebounce.current = setTimeout(() => void performSearch(trimmed), SEARCH_DEBOUNCE_MS);
  };
  const selectSuggestion = (candidate: OriginCandidate) => {
    if (searchDebounce.current) clearTimeout(searchDebounce.current);
    searchAbort.current?.abort();
    setName(candidate.name);
    setCoords({ lat: candidate.lat, lng: candidate.lng });
    if (!locality.trim()) { setLocality(candidate.address); setLocalityCode(WRITE_MY_OWN); }
    setSearchResults([]); setSearched(false); setSearching(false);
  };

  // — 이름을 고치는 자리가 화면에 아예 없었다.
  // 🔴 훅은 아래 「리스트가 없으면 돌아가기」보다 **위**에 둔다(S15P21E201-1530). 아래에 있으면 첫 렌더(부슐랭을
  //    아직 못 불러와 리스트가 없음)와 다음 렌더(불러옴)의 훅 수가 달라 화면이 통째로 죽었다 —
  //    「Rendered more hooks than during the previous render」. 목록에서 눌러 들어오면 안 보이고,
  //    이 화면에서 새로고침하거나 주소로 바로 열면 매번 났다.
  const [editing, setEditing] = useState(false);
  const [draftName, setDraftName] = useState('');
  const [draftDescription, setDraftDescription] = useState('');

  // 아직 불러오는 중이면 「못 찾았다」고 하지 않는다 — 모르는 것을 없다고 말하게 된다.
  if (!list && !ready) return <View style={styles.shell}><Screen scroll><View style={styles.empty}><ActivityIndicator color={color.action.primary} /></View></Screen></View>;
  if (!list) return <View style={styles.shell}><Screen scroll><View style={styles.empty}><Text variant="title" weight="bold">{tx('리스트를 찾을 수 없어요', "Couldn't find this list")}</Text><Button compact label={tx('부슐랭으로', 'Back to collection')} variant="tertiary" onPress={() => router.replace('/collection')} /></View></Screen></View>;

  const listPlaces = list.placeIds.map((placeId) => places[placeId]).filter((entry): entry is NonNullable<typeof entry> => Boolean(entry));

  const pickPhoto = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (!result.canceled && result.assets[0]) setPhotoUri(result.assets[0].uri);
  };

  const submitPlace = () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    addNewPlaceToList(list.id, { name: trimmed, category: categoryCode === WRITE_MY_OWN ? (category.trim() || null) : categoryCode, locality: localityCode === WRITE_MY_OWN ? (locality.trim() || null) : localityCode, photoUri, note: note.trim() || null, lat: coords?.lat ?? null, lng: coords?.lng ?? null });
    setName(''); setCategory(''); setLocality(''); setNote(''); setPhotoUri(null); setCoords(null); setSearchResults([]); setSearched(false); setAdding(false);
  };


  const openEdit = () => {
    // 지금 값으로 채워서 연다 — 빈 칸으로 열면 고치려던 사람이 처음부터 다시 쓴다.
    setDraftName(list?.name ?? '');
    setDraftDescription(list?.description ?? '');
    setEditing(true);
  };

  const saveEdit = () => {
    if (!list) return;
    const trimmed = draftName.trim();
    // 빈 이름은 저장하지 않는다. 이름이 없으면 목록에서 그 리스트를 가리킬 말이 없다.
    if (!trimmed) return;
    renameList(list.id, trimmed, draftDescription);
    setEditing(false);
  };

  const confirmDeleteList = () => {
    deleteList(list.id);
    router.replace('/collection');
  };

  return <View style={styles.shell}><Screen scroll withTabBar>
    <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/collection')} style={styles.back}><Text variant="title">‹ {tx('뒤로', 'Back')}</Text></Pressable>

    <View style={styles.headerRow}>
      <View style={styles.grow}>{editing
        ? <TextInput accessibilityLabel={tx('리스트 이름', 'List name')} maxLength={COLLECTION_LIMITS.name} value={draftName} onChangeText={setDraftName} placeholder={tx('리스트 이름', 'List name')} placeholderTextColor={color.text.muted} style={styles.input} />
        : <Text variant="display" weight="bold">{list.name}</Text>}
        {/* 어디에 저장되는지를 이 화면에서도 말한다. 홈에서만 말하면 여기 들어온
            사람은 못 본다 — 저장되는 곳은 화면마다 달라지지 않지만 사람의 기억은 달라진다.
        */}
        <Text variant="caption" color={color.text.muted}>{txf(tx, '저장한 곳 %s · %s', '%s place(s) · %s', list.placeIds.length, syncedToServer ? tx('내 계정에 저장돼요', 'saved to your account') : tx('이 기기에만 저장돼요', 'saved on this device only'))}</Text>{editing
          ? <><TextInput accessibilityLabel={tx('리스트 설명', 'List description')} maxLength={COLLECTION_LIMITS.description} value={draftDescription} onChangeText={setDraftDescription} placeholder={tx('설명 (선택)', 'Description (optional)')} placeholderTextColor={color.text.muted} style={styles.input} />
            <View style={styles.deleteConfirm}>
              <Pressable accessibilityRole="button" onPress={() => setEditing(false)}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('취소', 'Cancel')}</Text></Pressable>
              <Pressable accessibilityRole="button" accessibilityLabel={tx('이름 저장', 'Save name')} onPress={saveEdit}><Text variant="caption" weight="bold" color={color.action.secondary}>{tx('저장', 'Save')}</Text></Pressable>
            </View></>
          : <>{list.description ? <Text variant="caption" color={color.text.muted}>{list.description}</Text> : null}
            <Pressable accessibilityRole="button" accessibilityLabel={tx('리스트 이름 고치기', 'Edit list name')} onPress={openEdit}><Text variant="caption" weight="bold" color={color.action.secondary}>{tx('이름 고치기', 'Edit name')}</Text></Pressable></>}</View>
      {confirmDelete ? <View style={styles.deleteConfirm}><Pressable accessibilityRole="button" onPress={() => setConfirmDelete(false)}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('취소', 'Cancel')}</Text></Pressable><Pressable accessibilityRole="button" onPress={confirmDeleteList}><Text variant="caption" weight="bold" color={color.state.danger}>{tx('삭제 확정', 'Confirm delete')}</Text></Pressable></View> : <Pressable accessibilityRole="button" accessibilityLabel={tx('리스트 삭제', 'Delete list')} onPress={() => setConfirmDelete(true)}><Text variant="caption" weight="bold" color={color.state.danger}>{tx('삭제', 'Delete')}</Text></Pressable>}
    </View>

    <Button label={adding ? tx('취소', 'Cancel') : tx('+ 장소 담기', '+ Add a place')} variant={adding ? 'tertiary' : 'primary'} onPress={() => setAdding((value) => !value)} containerStyle={styles.addToggle} />

    {adding ? <Card style={styles.form}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('사진 선택', 'Choose photo')} onPress={() => void pickPhoto()} style={styles.photoPicker}>
        {photoUri ? <Image source={{ uri: photoUri }} resizeMode="cover" style={styles.photoPreview} /> : <Text variant="caption" color={color.text.muted}>{tx('사진 추가 (선택)', 'Add photo (optional)')}</Text>}
      </Pressable>
      <TextInput accessibilityLabel={tx('장소 이름', 'Place name')} maxLength={COLLECTION_LIMITS.itemName} value={name} onChangeText={handleNameChange} placeholder={tx('장소 이름 (검색해서 골라도 돼요)', 'Place name (search and pick, or type your own)')} placeholderTextColor={color.text.muted} style={styles.input} />
      {searchResults.length > 0 && <View accessibilityRole="list" style={styles.suggestionList}>
        {searchResults.map((item) => <Pressable key={item.externalId} accessibilityRole="button" accessibilityLabel={txf(tx, '%s 선택', 'Choose %s', item.name)} onPress={() => selectSuggestion(item)} style={({ pressed }) => [styles.suggestionItem, pressed && styles.suggestionItemPressed]}>
          <Text variant="body" weight="bold">{item.name}</Text>
          <Text variant="caption" color={color.text.muted}>{item.address}</Text>
        </Pressable>)}
      </View>}
      {!searching && searched && searchResults.length === 0 && <Text variant="caption" color={color.text.muted}>{tx('검색 결과가 없어요. 이름을 그대로 적어도 돼요.', 'No results — you can still type the name as-is.')}</Text>}
      {coords && <Text variant="caption" weight="bold" color={color.state.success}>{tx('📍 위치를 찾았어요 — 지역 칸에 주소를 채워 뒀어요.', '📍 Location found — filled in the area field with the address.')}</Text>}
      {/* 돌려서 고르는 휠. 고르면 그 아래 입력칸이 사라진다 — 사람이 정한 규칙이다.
          휠에서 고른 것과 직접 쓴 것이 한 칸에 섞이면 나중에 어느 쪽인지 못 가른다.
          「직접 쓰기」를 고를 때만 쓸 수 있다.
      */}
      <View style={styles.row}>
        <WheelPicker
          label={tx('카테고리', 'Category')}
          options={categoryOptions}
          value={categoryCode}
          onChange={setCategoryCode}
        />
        <WheelPicker
          label={tx('지역', 'Area')}
          options={localityOptions}
          value={localityCode}
          onChange={setLocalityCode}
        />
      </View>
      {categoryCode === WRITE_MY_OWN ? (
        <TextInput accessibilityLabel={tx('카테고리 직접 쓰기', 'Write your own category')} maxLength={COLLECTION_LIMITS.category} value={category} onChangeText={setCategory} placeholder={tx('카테고리 직접 쓰기', 'Write your own category')} placeholderTextColor={color.text.muted} style={styles.input} />
      ) : null}
      {localityCode === WRITE_MY_OWN ? (
        <TextInput accessibilityLabel={tx('지역 직접 쓰기', 'Write your own area')} maxLength={COLLECTION_LIMITS.locality} value={locality} onChangeText={setLocality} placeholder={tx('지역 직접 쓰기 (예: 영도구)', 'Write your own area (e.g. Yeongdo-gu)')} placeholderTextColor={color.text.muted} style={styles.input} />
      ) : null}
      <Text variant="caption" color={color.text.muted}>{tx('돌려서 고르세요. 지역은 검색에서 고르면 자동으로 채워져요.', 'Spin to choose. Picking a search result fills the area for you.')}</Text>
      <TextInput accessibilityLabel={tx('한줄 메모', 'One-line note')} maxLength={COLLECTION_LIMITS.note} value={note} onChangeText={setNote} placeholder={tx('한줄 메모 (선택)', 'One-line note (optional)')} placeholderTextColor={color.text.muted} style={styles.input} />
      {/* 🔴 영어를 'Save' 로 두지 않는다 — S15P21E201-1489(B-11). 장소 상세의
          「내 여행 후보에 저장」도 영어가 'Save…' 라, 둘 다 「저장」으로 읽혀 한쪽에
          저장하고 다른 쪽을 열어 보고 「저장이 안 됐다」고 오해한다. 실제로 둘은 다른
          저장소다 — 이쪽은 부슐랭(컬렉션), 저쪽은 일정 생성에 쓰는 mustVisitPlaces. */}
      <Button label={tx('담기', 'Add to list')} disabled={!name.trim()} onPress={submitPlace} />
    </Card> : null}

    {listPlaces.length === 0 && !adding ? <View style={styles.empty}><Text variant="body" weight="bold">{tx('아직 담은 장소가 없어요.', 'No places in this list yet.')}</Text></View> : null}

    <View style={styles.places}>{listPlaces.map((place) => <View key={place.id} style={styles.placeRow}>
      {place.photoUri ? <Image source={{ uri: place.photoUri }} resizeMode="cover" style={styles.placePhoto} /> : <View style={[styles.placePhoto, styles.placePhotoPlaceholder]} />}
      <View style={styles.grow}>
        <Text variant="body" weight="bold">{place.name}</Text>
        <View style={styles.metaRow}>{place.category ? <Text variant="caption" color={color.text.muted}>{place.category}</Text> : null}{place.locality ? <Text variant="caption" color={color.text.muted}> · {place.locality}</Text> : null}{place.lat != null && place.lng != null ? <Text variant="caption" color={color.text.muted}> · 📍</Text> : null}</View>
        {place.note ? <Text variant="caption" color={color.text.body}>{place.note}</Text> : null}
      </View>
      <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 빼기', 'Remove %s', place.name)} onPress={() => removePlaceFromList(list.id, place.id)} style={styles.removeButton}><Text variant="caption" weight="bold" color={color.state.danger}>{tx('빼기', 'Remove')}</Text></Pressable>
    </View>)}</View>
  </Screen><TabBar active="map" /></View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.canvas },
  back: { minHeight: 44, alignSelf: 'flex-start', justifyContent: 'center', marginBottom: spacing[3] },
  headerRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3] },
  grow: { flex: 1, gap: spacing[1] },
  deleteConfirm: { flexDirection: 'row', gap: spacing[3] },
  addToggle: { marginTop: spacing[4] },
  form: { gap: spacing[2], marginTop: spacing[3] },
  photoPicker: { minHeight: 120, borderRadius: radius.md, borderWidth: 1, borderStyle: 'dashed', borderColor: color.surface.field, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  photoPreview: { width: '100%', height: 120 },
  suggestionList: { gap: spacing[1] }, suggestionItem: { minHeight: 44, padding: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.soft }, suggestionItemPressed: { opacity: 0.7 },
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
