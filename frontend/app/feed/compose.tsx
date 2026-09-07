import { useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { createStory, uploadStoryImage, VISIBILITY_LABEL, type StoryVisibility } from '@/social/stories';

const MAX_IMAGES = 3;
const BODY_MAX = 500;

type PendingImage = { localUri: string; imageUrl: string | null; uploading: boolean; error: string | null };

export default function ComposeStory() {
  const router = useRouter();
  const { accessToken } = useAuth();
  const { tx } = useI18n();
  const [body, setBody] = useState('');
  const [region, setRegion] = useState('');
  const [visibility, setVisibility] = useState<StoryVisibility>('PUBLIC');
  const [images, setImages] = useState<PendingImage[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const bodyValid = body.trim().length >= 1 && body.trim().length <= BODY_MAX;
  const anyUploading = images.some((image) => image.uploading);
  const canSubmit = bodyValid && !anyUploading && !submitting;

  const addImage = async () => {
    if (images.length >= MAX_IMAGES) return;
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (result.canceled) return;
    const asset = result.assets[0];
    const index = images.length;
    setImages((prev) => [...prev, { localUri: asset.uri, imageUrl: null, uploading: true, error: null }]);
    const outcome = await uploadStoryImage({ uri: asset.uri, fileName: asset.fileName, mimeType: asset.mimeType }, accessToken);
    setImages((prev) => prev.map((image, position) => position === index
      ? (outcome.state === 'success' ? { ...image, imageUrl: outcome.imageUrl, uploading: false } : { ...image, uploading: false, error: outcome.message })
      : image));
  };

  const removeImage = (index: number) => setImages((prev) => prev.filter((_, position) => position !== index));

  const submit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    const outcome = await createStory({
      body: body.trim(),
      imageUrls: images.filter((image) => image.imageUrl).map((image) => image.imageUrl as string),
      region: region.trim() || undefined,
      visibility,
      accessToken,
    });
    setSubmitting(false);
    if (outcome.state === 'success') router.back();
    else setError(outcome.message);
  };

  return <Screen scroll>
    <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.back()} style={styles.back}><Text variant="title">‹</Text></Pressable>
    <Text variant="display" weight="bold" style={styles.title}>{tx('기록 남기기', 'Write a record')}</Text>

    <TextInput
      accessibilityLabel={tx('기록 내용', 'Record body')}
      style={styles.bodyInput}
      multiline
      placeholder={tx('오늘 부산에서 있었던 일을 남겨보세요', 'Write about your day in Busan')}
      placeholderTextColor={color.text.muted}
      value={body}
      onChangeText={(value) => setBody(value.slice(0, BODY_MAX))}
      maxLength={BODY_MAX}
    />
    <Text variant="caption" color={color.text.muted} style={styles.counter}>{body.trim().length}/{BODY_MAX}</Text>

    <Text variant="caption" weight="bold" style={styles.label}>{tx('사진 (최대 3장)', 'Photos (up to 3)')}</Text>
    <View style={styles.imageRow}>
      {images.map((image, index) => <View key={`${image.localUri}-${index}`} style={styles.imageSlot}>
        <Image source={{ uri: image.localUri }} resizeMode="cover" accessibilityLabel={tx('선택한 사진', 'Selected photo')} style={styles.imagePreview} />
        {image.uploading ? <View style={styles.imageOverlay}><ActivityIndicator color={color.text.onAction} /></View> : null}
        {image.error ? <View style={styles.imageOverlay}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('실패', 'Failed')}</Text></View> : null}
        <Pressable accessibilityRole="button" accessibilityLabel={tx('사진 삭제', 'Remove photo')} onPress={() => removeImage(index)} style={styles.imageRemove}><Text weight="bold" color={color.text.onAction}>×</Text></Pressable>
      </View>)}
      {images.length < MAX_IMAGES ? <Pressable accessibilityRole="button" accessibilityLabel={tx('사진 추가', 'Add photo')} onPress={() => void addImage()} style={styles.imageAdd}><Text variant="title" color={color.text.muted}>+</Text></Pressable> : null}
    </View>

    <Text variant="caption" weight="bold" style={styles.label}>{tx('지역 (선택)', 'Region (optional)')}</Text>
    <TextInput accessibilityLabel={tx('지역', 'Region')} style={styles.input} placeholder={tx('예: 해운대구', 'e.g. Haeundae-gu')} placeholderTextColor={color.text.muted} value={region} onChangeText={setRegion} maxLength={60} />

    <Text variant="caption" weight="bold" style={styles.label}>{tx('공개 범위', 'Visibility')}</Text>
    <View accessibilityRole="radiogroup" style={styles.visibilityRow}>
      {(['PUBLIC', 'FOLLOWERS', 'PRIVATE'] as const).map((value) => {
        const selected = visibility === value;
        return <Pressable key={value} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => setVisibility(value)} style={[styles.visibilityOption, selected && styles.visibilityOptionSelected]}>
          <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.heading}>{tx(...VISIBILITY_LABEL[value])}</Text>
        </Pressable>;
      })}
    </View>
    <Text variant="caption" color={color.text.muted} style={styles.hint}>{tx('공개 시점을 따로 정하지 않으면 여행이 끝난 다음 날부터 보여요.', "If you don't set a publish time, it appears the day after your trip ends.")}</Text>

    {error ? <Text accessibilityRole="alert" color={color.state.danger} style={styles.error}>{error}</Text> : null}

    <Button label={submitting ? tx('올리는 중…', 'Posting…') : tx('기록 올리기', 'Post record')} disabled={!canSubmit} onPress={() => void submit()} containerStyle={styles.submit} />
  </Screen>;
}

const styles = StyleSheet.create({
  back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  title: { marginTop: spacing[4], marginBottom: spacing[4] },
  bodyInput: { minHeight: 120, padding: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, backgroundColor: color.surface.card, color: color.text.heading, textAlignVertical: 'top' },
  counter: { textAlign: 'right', marginTop: spacing[1] },
  label: { marginTop: spacing[6], marginBottom: spacing[2] },
  imageRow: { flexDirection: 'row', gap: spacing[2] },
  imageSlot: { width: 88, height: 88, borderRadius: radius.md, overflow: 'hidden', backgroundColor: color.surface.soft },
  imagePreview: { width: '100%', height: '100%' },
  imageOverlay: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(11,29,58,0.55)' },
  imageRemove: { position: 'absolute', top: 4, right: 4, width: 24, height: 24, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(11,29,58,0.72)' },
  imageAdd: { width: 88, height: 88, borderRadius: radius.md, borderWidth: 1, borderStyle: 'dashed', borderColor: color.surface.field, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  input: { minHeight: 48, paddingHorizontal: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, color: color.text.heading, backgroundColor: color.surface.card },
  visibilityRow: { flexDirection: 'row', gap: spacing[2] },
  visibilityOption: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, backgroundColor: color.surface.card },
  visibilityOptionSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  hint: { marginTop: spacing[2] },
  error: { marginTop: spacing[4] },
  submit: { marginTop: spacing[6] },
});
