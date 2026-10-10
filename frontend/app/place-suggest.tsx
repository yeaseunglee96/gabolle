// 「장소 제안하기」 — 현지인이 앱에 없는 장소·맛집을 알려 준다(S15P21E201-2031, 모진성 제보).
//
// 🔴 보내도 바로 올라가지 않는다. 운영자가 확인한 뒤에 장소가 되고, 그때 「현지인 추천」이 붙고 알림이 간다.
//    그래서 보낸 뒤의 말은 「검토 뒤에 올라가요」다 — 「추가됐어요」라고 하면 검색에 안 나와 고장으로 읽힌다.
// 들어오는 길: 장소 검색 결과 없음(이름을 들고 온다) · 로컬 탐색 · 마이페이지.
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as ImagePicker from 'expo-image-picker';

import { useAuth } from '@/auth/AuthProvider';
import { AppImage } from '@/components/AppImage';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { LocationPickMap } from '@/discovery/LocationPickMap';
import { type LatLng } from '@/discovery/LocationPickParts';
import { BUSAN_CENTER } from '@/discovery/locationPickHtml';
import {
  draftProblems, fetchMySuggestions, findDuplicates, NAME_MAX, REASON_MAX, submitPlaceSuggestion, SUGGEST_CATEGORIES,
  type DuplicatePlace, type PlaceSuggestion, type SubmitResult,
} from '@/discovery/placeSuggestions';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { useLayout } from '@/layout/useLayout';
import { readCurrentPosition } from '@/location/currentPosition';
import { useLocationGate } from '@/personalization/useLocationGate';
import { resizeForUpload } from '@/social/imageResize';
import { resolveStoryImageUrl, uploadStoryImage } from '@/social/stories';

type Tx = (ko: string, en: string) => string;

function failureText(result: Exclude<SubmitResult, { state: 'success' } | { state: 'duplicate' }>, tx: Tx): string {
  switch (result.state) {
    case 'outside': return tx('부산 안의 장소만 제안할 수 있어요. 핀을 부산 안으로 옮겨 주세요.', 'Only places in Busan can be suggested. Move the pin inside Busan.');
    case 'limited': return tx('오늘은 더 제안할 수 없어요. 하루 5곳까지예요.', 'You can suggest up to 5 places a day. Please try again tomorrow.');
    case 'blocked': return tx('욕설·링크·연락처 광고는 넣을 수 없어요.', 'Profanity, links and contact ads are not allowed.');
    case 'photo': return tx('사진이 올라가지 않았어요. 다시 골라 주세요.', 'The photo was not uploaded. Please choose it again.');
    case 'login': return tx('로그인이 필요해요.', 'Please sign in.');
    default: return tx('보내지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not send. Please try again shortly.');
  }
}

function statusLabel(status: PlaceSuggestion['status'], tx: Tx): string {
  if (status === 'APPROVED') return tx('승인됨', 'Approved');
  if (status === 'REJECTED') return tx('반려됨', 'Not accepted');
  return tx('검토 중', 'In review');
}

export default function PlaceSuggestScreen() {
  const router = useRouter();
  const params = useLocalSearchParams<{ name?: string }>();
  const { tx } = useI18n();
  const { accessToken, user, ready } = useAuth();
  const { desktop } = useLayout();
  // 「내 위치로」도 위치 동의 문을 지난다 — 다른 화면과 같다(locationConsent 시험).
  const locationGate = useLocationGate(accessToken);

  const [name, setName] = useState(typeof params.name === 'string' ? params.name.slice(0, NAME_MAX) : '');
  const [category, setCategory] = useState<string | null>(null);
  const [center, setCenter] = useState<LatLng | null>(null);
  const [moveTo, setMoveTo] = useState<LatLng | null>(null);
  const [reason, setReason] = useState('');
  const [hours, setHours] = useState('');
  const [phone, setPhone] = useState('');
  const [photoUrl, setPhotoUrl] = useState<string | null>(null);
  const [photoBusy, setPhotoBusy] = useState(false);
  const [duplicates, setDuplicates] = useState<DuplicatePlace[]>([]);
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState<{ danger: boolean; text: string } | null>(null);
  const [sent, setSent] = useState(false);
  const [mine, setMine] = useState<PlaceSuggestion[] | null>(null);
  const [locating, setLocating] = useState(false);

  const loadMine = useCallback(async () => {
    if (!accessToken) return;
    setMine(await fetchMySuggestions(accessToken));
  }, [accessToken]);
  useEffect(() => { void loadMine(); }, [loadMine]);

  // 이름과 핀이 멈추면 이미 있는 장소인지 본다 — 보내고 나서 거절당하는 것보다 쓰는 동안 아는 편이 낫다.
  const dupTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    if (dupTimer.current) clearTimeout(dupTimer.current);
    if (!accessToken || !center || name.trim().length < 2) { setDuplicates([]); return; }
    const controller = new AbortController();
    dupTimer.current = setTimeout(() => {
      void findDuplicates(name, center.lat, center.lng, accessToken, controller.signal).then(setDuplicates);
    }, 400);
    return () => { controller.abort(); if (dupTimer.current) clearTimeout(dupTimer.current); };
  }, [accessToken, center, name]);

  const problems = useMemo(() => draftProblems({ name, category: category ?? undefined, lat: center?.lat, lng: center?.lng, reason }), [name, category, center, reason]);
  const canSend = problems.length === 0 && duplicates.length === 0 && !sending && !photoBusy;

  async function useMyLocation() {
    if (!(await locationGate.request())) return;
    setLocating(true);
    try {
      const position = await readCurrentPosition();
      setMoveTo({ lat: position.coords.latitude, lng: position.coords.longitude });
    } catch {
      setMessage({ danger: true, text: tx('내 위치를 못 읽었어요. 지도를 직접 움직여 주세요.', "Couldn't read your location. Move the map yourself.") });
    } finally {
      setLocating(false);
    }
  }

  async function pickPhoto() {
    if (!accessToken || photoBusy) return;
    setPhotoBusy(true);
    setMessage(null);
    try {
      const picked = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
      if (picked.canceled) return;
      const asset = picked.assets[0];
      const small = await resizeForUpload(asset.uri).catch(() => null);
      const result = await uploadStoryImage(small ? { uri: small.uri, fileName: 'suggest.jpg', mimeType: 'image/jpeg' } : { uri: asset.uri, fileName: asset.fileName, mimeType: asset.mimeType }, accessToken);
      if (result.state === 'success') setPhotoUrl(result.imageUrl);
      else setMessage({ danger: true, text: result.message });
    } catch {
      setMessage({ danger: true, text: tx('사진을 올리지 못했어요.', 'Could not upload the photo.') });
    } finally {
      setPhotoBusy(false);
    }
  }

  async function send() {
    if (!canSend || !center || !category) return;
    setSending(true);
    setMessage(null);
    const result = await submitPlaceSuggestion({ name, category, lat: center.lat, lng: center.lng, reason, photoUrl, openingHours: hours, phone }, accessToken);
    setSending(false);
    if (result.state === 'success') {
      setSent(true);
      void loadMine();
      return;
    }
    if (result.state === 'duplicate') {
      setDuplicates([{ placeId: result.placeId, name: name.trim(), distanceM: 0 }]);
      return;
    }
    setMessage({ danger: true, text: failureText(result, tx) });
  }

  function reset() {
    setSent(false); setName(''); setCategory(null); setReason(''); setHours(''); setPhone(''); setPhotoUrl(null); setDuplicates([]);
  }

  if (ready && !user) {
    return (
      <Screen scroll>
        <Header tx={tx} onBack={() => (router.canGoBack() ? router.back() : router.replace('/home'))} />
        <View style={styles.card}>
          <Text color={color.text.body}>{tx('장소를 제안하려면 로그인해 주세요. 누가 추천했는지 장소에 함께 보여요.', 'Please sign in to suggest a place. Your name is shown with the place.')}</Text>
          <Button label={tx('로그인', 'Sign in')} variant="secondary" onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/place-suggest' } })} />
        </View>
      </Screen>
    );
  }

  const form = (
    <View style={styles.form}>
      <Field label={tx('이름', 'Name')}>
        <TextInput value={name} onChangeText={setName} maxLength={NAME_MAX} placeholder={tx('예: 민락동 할매국밥', 'e.g. Millak grandma gukbap')} placeholderTextColor={color.text.muted} style={styles.input} accessibilityLabel={tx('이름', 'Name')} />
      </Field>

      <Field label={tx('갈래', 'Category')}>
        <View style={styles.chips}>
          {SUGGEST_CATEGORIES.map((c) => {
            const on = c.code === category;
            return (
              <Pressable key={c.code} accessibilityRole="button" accessibilityState={{ selected: on }} onPress={() => setCategory(c.code)} style={[styles.chip, on && styles.chipOn]}>
                <Text variant="caption" weight="bold" color={on ? color.text.onAction : color.text.heading}>{tx(c.ko, c.en)}</Text>
              </Pressable>
            );
          })}
        </View>
      </Field>

      <Field label={tx('위치', 'Location')} hint={tx('지도를 움직여 핀을 장소에 맞춰 주세요. 부산 안만 받아요.', 'Move the map so the pin sits on the place. Busan only.')}>
        <LocationPickMap initial={{ lat: BUSAN_CENTER.lat, lng: BUSAN_CENTER.lng }} moveTo={moveTo} onCenter={setCenter} height={desktop ? 360 : 260} />
        <View style={styles.row}>
          <Button label={locating ? tx('위치 찾는 중…', 'Locating…') : tx('내 위치로', 'Use my location')} variant="tertiary" compact onPress={() => void useMyLocation()} disabled={locating} />
        </View>
      </Field>

      {duplicates.length > 0 ? (
        <View accessibilityRole="alert" style={styles.dup}>
          <Text weight="bold">{tx('이미 있는 장소예요', 'This place is already here')}</Text>
          <Text variant="caption" color={color.text.body}>
            {duplicates[0].placeId
              ? txf(tx, '근처에 「%s」이(가) 있어요.', '"%s" is already nearby.', duplicates[0].name)
              : txf(tx, '「%s」은(는) 다른 분이 먼저 제안해 검토 중이에요.', '"%s" was already suggested and is in review.', duplicates[0].name)}
          </Text>
          {duplicates[0].placeId ? (
            <Button label={tx('그 장소 보기', 'Open that place')} variant="outline" compact onPress={() => router.push(`/place/${duplicates[0].placeId}`)} />
          ) : null}
        </View>
      ) : null}

      <Field label={tx('추천 이유 한 줄', 'Why you recommend it (one line)')}>
        <TextInput value={reason} onChangeText={setReason} maxLength={REASON_MAX} placeholder={tx('예: 아침 7시 돼지국밥, 동네 사람만 아는 집', 'e.g. 7am pork soup only locals know')} placeholderTextColor={color.text.muted} style={styles.input} accessibilityLabel={tx('추천 이유 한 줄', 'Why you recommend it (one line)')} />
        <Text variant="caption" color={color.text.muted} style={styles.counter}>{`${reason.length}/${REASON_MAX}`}</Text>
      </Field>

      <Field label={tx('사진 (선택)', 'Photo (optional)')} hint={tx('직접 찍은 사진만 올려 주세요. 촬영 위치 정보는 지워져요.', 'Only photos you took. Location data is removed.')}>
        {photoUrl ? (
          <View style={styles.photoRow}>
            <AppImage source={{ uri: resolveStoryImageUrl(photoUrl) }} style={styles.photo} />
            <Button label={tx('사진 빼기', 'Remove photo')} variant="tertiary" compact onPress={() => setPhotoUrl(null)} />
          </View>
        ) : (
          <Button label={photoBusy ? tx('올리는 중…', 'Uploading…') : tx('사진 고르기', 'Choose a photo')} variant="tertiary" compact onPress={() => void pickPhoto()} disabled={photoBusy} />
        )}
      </Field>

      <View style={styles.twoCol}>
        <Field label={tx('영업시간 (선택)', 'Hours (optional)')} style={styles.col}>
          <TextInput value={hours} onChangeText={setHours} maxLength={120} placeholder="11:00-21:00" placeholderTextColor={color.text.muted} style={styles.input} accessibilityLabel={tx('영업시간 (선택)', 'Hours (optional)')} />
        </Field>
        <Field label={tx('전화 (선택)', 'Phone (optional)')} style={styles.col}>
          <TextInput value={phone} onChangeText={setPhone} maxLength={30} keyboardType="phone-pad" placeholder="051-000-0000" placeholderTextColor={color.text.muted} style={styles.input} accessibilityLabel={tx('전화 (선택)', 'Phone (optional)')} />
        </Field>
      </View>

      {message ? <Text accessibilityRole="alert" color={message.danger ? color.state.danger : color.text.body}>{message.text}</Text> : null}
      <Button label={sending ? tx('보내는 중…', 'Sending…') : tx('제안 보내기', 'Send suggestion')} onPress={() => void send()} disabled={!canSend} />
      <Text variant="caption" color={color.text.muted}>{tx('운영자가 확인한 뒤 앱에 올라가고, 장소에 「현지인 추천」과 내 이름이 붙어요. 승인되면 알림을 보내 드려요.', 'After review it goes live with a "Local pick" badge and your name. We will notify you when approved.')}</Text>
    </View>
  );

  const mineList = (
    <View style={styles.mine}>
      <Text variant="title" weight="bold">{tx('내가 제안한 장소', 'My suggestions')}</Text>
      {mine === null ? <ActivityIndicator color={color.action.primary} /> : mine.length === 0 ? (
        <Text color={color.text.body}>{tx('아직 제안한 장소가 없어요.', 'No suggestions yet.')}</Text>
      ) : mine.map((item) => (
        <Pressable key={item.suggestionId} disabled={!item.placeId} onPress={() => item.placeId && router.push(`/place/${item.placeId}`)} style={styles.mineRow}>
          <View style={styles.flex}>
            <Text weight="bold" numberOfLines={1}>{item.name}</Text>
            <Text variant="caption" color={color.text.body} numberOfLines={1}>{item.reason}</Text>
            {item.status === 'REJECTED' && item.rejectReason ? <Text variant="caption" color={color.text.muted} numberOfLines={2}>{item.rejectReason}</Text> : null}
          </View>
          <View style={[styles.status, item.status === 'APPROVED' && styles.statusOk]}>
            <Text variant="caption" weight="bold" color={item.status === 'APPROVED' ? color.text.onAction : color.text.heading}>{statusLabel(item.status, tx)}</Text>
          </View>
        </Pressable>
      ))}
    </View>
  );

  return (
    <Screen scroll wide={desktop}>
      <Header tx={tx} onBack={() => (router.canGoBack() ? router.back() : router.replace('/home'))} />
      {locationGate.sheet}
      {sent ? (
        <View style={styles.card}>
          <Text variant="title" weight="bold">{tx('제안을 받았어요', 'Suggestion received')}</Text>
          <Text color={color.text.body}>{tx('운영자가 확인한 뒤 앱에 올라가요. 승인되면 알림을 보내 드려요.', 'It goes live after review. We will notify you when it is approved.')}</Text>
          <Button label={tx('다른 장소 제안하기', 'Suggest another place')} variant="tertiary" onPress={reset} />
        </View>
      ) : null}
      <View style={desktop ? styles.split : undefined}>
        {sent ? null : <View style={desktop ? styles.main : undefined}>{form}</View>}
        <View style={desktop ? styles.side : undefined}>{mineList}</View>
      </View>
    </Screen>
  );
}

function Header({ tx, onBack }: { tx: Tx; onBack: () => void }) {
  return (
    <View style={styles.header}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={onBack} style={styles.back}>
        <Text variant="title" weight="bold">‹</Text>
      </Pressable>
      <View style={styles.flex}>
        <Text variant="display" weight="bold" color={color.text.heading}>{tx('장소 제안하기', 'Suggest a place')}</Text>
        <Text color={color.text.body}>{tx('부산 사람만 아는 곳을 알려 주세요.', 'Tell us about a spot only locals know.')}</Text>
      </View>
    </View>
  );
}

function Field({ label, hint, children, style }: { label: string; hint?: string; children: React.ReactNode; style?: object }) {
  return (
    <View style={[styles.field, style]}>
      <Text weight="bold">{label}</Text>
      {hint ? <Text variant="caption" color={color.text.body}>{hint}</Text> : null}
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2], marginBottom: spacing[4] },
  back: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center' },
  flex: { flex: 1, minWidth: 0 },
  split: { flexDirection: 'row', gap: spacing[6], alignItems: 'flex-start' },
  main: { flex: 3, minWidth: 0 },
  side: { flex: 2, minWidth: 0 },
  form: { gap: spacing[4] },
  field: { gap: spacing[2] },
  input: { minHeight: 48, borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, paddingHorizontal: spacing[3], color: color.text.heading, backgroundColor: color.surface.card },
  counter: { alignSelf: 'flex-end' },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  chipOn: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  row: { flexDirection: 'row', gap: spacing[2] },
  dup: { gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  photoRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  photo: { width: 88, height: 88, borderRadius: radius.md },
  twoCol: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3] },
  col: { flexGrow: 1, flexBasis: 160 },
  card: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, marginBottom: spacing[4] },
  mine: { gap: spacing[2], marginTop: spacing[6] },
  mineRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], paddingVertical: spacing[3], borderBottomWidth: 1, borderBottomColor: color.surface.soft },
  status: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
  statusOk: { backgroundColor: color.action.secondary },
});
