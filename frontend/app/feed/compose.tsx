import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, TextInput, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { MarkdownBody } from '@/components/MarkdownBody';
import { PhotoGrid } from '@/components/PhotoGrid';
import { looksLikeMarkdown } from '@/social/markdown';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { createStory, FEED_QUERY_PREFIX, VISIBILITY_LABEL, type StoryVisibility } from '@/social/stories';
import { MAX_STORY_IMAGES, useStoryImages } from '@/social/useStoryImages';

const BODY_MAX = 500;
// 업로드 실패 뒤 화면을 새로 고쳐도 쓰던 글이 남아 있어야 한다(S15P21E201-198 완료 기준).
// 사진은 로컬 uri가 새로고침 뒤 의미가 없어질 수 있어(웹의 blob: URL 등) 글·지역·공개
// 설정만 남긴다 — 사진은 다시 골라야 하지만 가장 아까운 글은 잃지 않는다.
const DRAFT_KEY = 'gabolle.story-compose-draft';
type PublishTiming = 'AFTER_TRIP' | 'NOW';

export default function ComposeStory() {
  const router = useRouter();
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const { tx } = useI18n();
  const [body, setBody] = useState('');
  // S15P21E201-1136 — 쓴 것이 어떻게 보일지 미리 본다.
  const [preview, setPreview] = useState(false);
  const [region, setRegion] = useState('');
  const [visibility, setVisibility] = useState<StoryVisibility>('PUBLIC');
  const [publishTiming, setPublishTiming] = useState<PublishTiming>('AFTER_TRIP');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const draftLoaded = useRef(false);

  useEffect(() => {
    void AsyncStorage.getItem(DRAFT_KEY).then((raw) => {
      draftLoaded.current = true;
      if (!raw) return;
      try {
        const draft = JSON.parse(raw) as { body?: string; region?: string; visibility?: StoryVisibility; publishTiming?: PublishTiming };
        if (draft.body) setBody(draft.body.slice(0, BODY_MAX));
        if (draft.region) setRegion(draft.region);
        if (draft.visibility) setVisibility(draft.visibility);
        if (draft.publishTiming) setPublishTiming(draft.publishTiming);
      } catch {
        void AsyncStorage.removeItem(DRAFT_KEY);
      }
    });
  }, []);

  useEffect(() => {
    if (!draftLoaded.current) return;
    void AsyncStorage.setItem(DRAFT_KEY, JSON.stringify({ body, region, visibility, publishTiming }));
  }, [body, region, visibility, publishTiming]);

  // 사진은 공용 훅이 맡는다 — 피드의 인라인 글쓰기와 같은 코드를 쓴다
  // (S15P21E201-958). 줄이기·3MB 판정·재시도 규칙이 두 벌이 되지 않게 하려고.
  const { images, addImage, retryImage, removeImage, anyUploading, uploadedUrls, canAddMore } = useStoryImages(accessToken, tx);

  const bodyValid = body.trim().length >= 1 && body.trim().length <= BODY_MAX;
  const canSubmit = bodyValid && !anyUploading && !submitting;

  const submit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    const outcome = await createStory({
      body: body.trim(),
      imageUrls: uploadedUrls,
      region: region.trim() || undefined,
      visibility,
      publishAt: publishTiming === 'NOW' ? new Date().toISOString() : undefined,
      accessToken,
    });
    setSubmitting(false);
    if (outcome.state === 'success') {
      await AsyncStorage.removeItem(DRAFT_KEY);
      // 🔴 S15P21E201-1124 — 돌아가기 전에 피드 보관본을 버린다.
      //
      //    이게 없으면 목록은 올리기 전에 받아 둔 것을 그대로 다시 보여준다.
      //    사용자는 안 올라간 줄 알고 같은 글을 한 번 더 올리고, 앱을 껐다 켜야
      //    두 개가 보인다(2026-09-16 iOS 실기기에서 실제로 201 이 두 번 찍혔다).
      //
      //    앞자리만 준다 — 전체·팔로잉과 로그인 여부까지 네 갈래라, 지금 어느
      //    칸에 있는지 글쓰기 화면은 알 수 없다. react-query 는 앞자리가 같은
      //    것을 전부 버린다.
      await queryClient.invalidateQueries({ queryKey: FEED_QUERY_PREFIX });
      router.back();
    } else {
      setError(outcome.message);
    }
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

    {/* S15P21E201-1136 — 쓴 것이 어떻게 보일지 미리 본다.
        🔴 마크다운을 몰라도 된다. 그냥 쓰면 평범한 글이 되므로 아무것도 막지 않고,
        글을 쓰기 시작했을 때만 이 줄이 생겨 빈 화면을 어지럽히지 않는다. */}
    {body.trim() ? <View style={styles.previewRow}>
      <Pressable accessibilityRole="button" accessibilityState={{ expanded: preview }} onPress={() => setPreview((on) => !on)} style={styles.previewToggle}>
        <Text variant="caption" weight="bold" color={color.action.primary}>{preview ? tx('← 다시 쓰기', '← Back to editing') : tx('미리보기', 'Preview')}</Text>
      </Pressable>
      {looksLikeMarkdown(body) ? <Text variant="caption" color={color.text.muted}>{tx('굵게 · 목록 · 제목이 적용돼요', 'Bold, lists and headings will apply')}</Text> : null}
    </View> : null}
    {preview && body.trim() ? <View style={styles.previewBox}><MarkdownBody source={body} /></View> : null}

    <Text variant="caption" weight="bold" style={styles.label}>{tx(`사진 (최대 ${MAX_STORY_IMAGES}장)`, `Photos (up to ${MAX_STORY_IMAGES})`)}</Text>

    {/* 🔴 S15P21E201-1135 — 사진을 가로로 줄 세우던 것을 장수·방향에 따른 배치로 바꾼다.
        목록 카드·글 상세와 **같은 부품**을 쓴다 — 올릴 때 본 모양과 올라간 뒤 모양이
        다르면 사용자는 무엇이 맞는지 알 수 없다.

        사진마다 얹히는 것(올리는 중·실패·빼기)은 renderOverlay 로 넘긴다. 「사진 추가」는
        빈 칸을 끼울 자리가 없어 배치 아래로 내렸다. */}
    {images.length ? <PhotoGrid
      photos={images.map((image) => ({ uri: image.localUri }))}
      accessibilityLabel={tx('선택한 사진', 'Selected photo')}
      style={styles.imageGrid}
      renderOverlay={(index) => {
        const image = images[index];
        if (!image) return null;
        return <>
          {image.uploading ? <View style={styles.imageOverlay}><ActivityIndicator color={color.text.onAction} /></View> : null}
        {/* 🔴 S15P21E201-1122 — 실패 사유를 함께 보여 준다.

            전에는 image.error 를 조건으로만 쓰고 내용을 그리지 않았다. useStoryImages 는
            「줄여도 4.2MB 라 올릴 수 없어요」처럼 이유를 정확히 만들어 넣는데, 화면에는
            「실패 · 다시 시도」만 떴다. 그러면 사용자는 왜 실패했는지 모른 채 같은 사진으로
            계속 재시도한다 — 크기가 문제일 때 재시도는 언제나 같은 결과다.

            S15P21E201-955 가 고치려던 것이 정확히 이것이다(「지금은 실패한 뒤에야 안다」).
            문구는 그때 만들어졌는데 화면에 닿지 못하고 있었다. */}
          {image.error ? (
            <Pressable accessibilityRole="button" accessibilityLabel={tx('업로드 다시 시도', 'Retry upload')} onPress={() => retryImage(index)} style={styles.imageOverlay}>
              <Text variant="caption" weight="bold" color={color.text.onAction}>{tx('실패 · 다시 시도', 'Failed · Retry')}</Text>
              <Text variant="caption" color={color.text.onDarkMuted} style={styles.imageErrorReason}>{image.error}</Text>
            </Pressable>
          ) : null}
          <Pressable accessibilityRole="button" accessibilityLabel={tx('사진 삭제', 'Remove photo')} hitSlop={10} onPress={() => removeImage(index)} style={styles.imageRemove}><Text weight="bold" color={color.text.onAction}>×</Text></Pressable>
        </>;
      }}
    /> : null}
    {canAddMore ? <Pressable accessibilityRole="button" accessibilityLabel={tx('사진 추가', 'Add photo')} onPress={() => void addImage()} style={styles.imageAddRow}><Text variant="caption" weight="bold" color={color.action.primary}>{tx('+ 사진 추가', '+ Add photo')}</Text></Pressable> : null}
    <Text variant="caption" color={color.text.muted} style={styles.hint}>{tx('사진의 위치 정보는 자동으로 제거되고, 위치는 지역 단위로만 저장돼요.', 'Location data is automatically removed from photos, and only a general region is stored.')}</Text>

    <Text variant="caption" weight="bold" style={styles.label}>{tx('지역 (선택)', 'Region (optional)')}</Text>
    <TextInput accessibilityLabel={tx('지역', 'Region')} style={styles.input} placeholder={tx('예: 해운대구', 'e.g. Haeundae-gu')} placeholderTextColor={color.text.muted} value={region} onChangeText={setRegion} maxLength={60} />

    <Text variant="caption" weight="bold" style={styles.label}>{tx('공개 시점', 'Publish timing')}</Text>
    <View accessibilityRole="radiogroup" style={styles.visibilityRow}>
      {([{ key: 'AFTER_TRIP', ko: '여행이 끝난 뒤', en: 'After the trip ends' }, { key: 'NOW', ko: '지금 바로', en: 'Right now' }] as const).map((option) => {
        const selected = publishTiming === option.key;
        return <Pressable key={option.key} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => setPublishTiming(option.key)} style={[styles.visibilityOption, selected && styles.visibilityOptionSelected]}>
          <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.heading}>{tx(option.ko, option.en)}</Text>
        </Pressable>;
      })}
    </View>
    <Text variant="caption" color={color.text.muted} style={styles.hint}>{tx('"여행이 끝난 뒤"를 고르면 여행이 끝난 다음 날부터 보여요.', 'Choosing "After the trip ends" makes it visible starting the day after your trip ends.')}</Text>

    <Text variant="caption" weight="bold" style={styles.label}>{tx('공개 범위', 'Visibility')}</Text>
    <View accessibilityRole="radiogroup" style={styles.visibilityRow}>
      {(['PUBLIC', 'FOLLOWERS', 'PRIVATE'] as const).map((value) => {
        const selected = visibility === value;
        return <Pressable key={value} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => setVisibility(value)} style={[styles.visibilityOption, selected && styles.visibilityOptionSelected]}>
          <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.heading}>{tx(...VISIBILITY_LABEL[value])}</Text>
        </Pressable>;
      })}
    </View>

    {error ? <Text accessibilityRole="alert" color={color.state.danger} style={styles.error}>{error}</Text> : null}

    <Button label={submitting ? tx('올리는 중…', 'Posting…') : tx('기록 올리기', 'Post record')} disabled={!canSubmit} onPress={() => void submit()} containerStyle={styles.submit} />
  </Screen>;
}

const styles = StyleSheet.create({
  back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  title: { marginTop: spacing[4], marginBottom: spacing[4] },
  bodyInput: { minHeight: 120, padding: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, backgroundColor: color.surface.card, color: color.text.heading, textAlignVertical: 'top' },
  counter: { textAlign: 'right', marginTop: spacing[1] },
  previewRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], marginTop: spacing[1] },
  previewToggle: { minHeight: 44, justifyContent: 'center' },
  previewBox: { marginTop: spacing[1], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft },
  label: { marginTop: spacing[6], marginBottom: spacing[2] },
  imageGrid: { marginTop: spacing[2] },
  // 배치에 빈 칸을 끼울 자리가 없어 「사진 추가」를 아래로 내렸다 (S15P21E201-1135).
  imageAddRow: { minHeight: 44, marginTop: spacing[2], alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, borderStyle: 'dashed' },
  imageOverlay: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(11,29,58,0.55)' },
  // 사유는 사진 위에 얹히므로 좁다. 줄바꿈을 허용하고 가운데로 모은다.
  imageErrorReason: { marginTop: spacing[1], paddingHorizontal: spacing[2], textAlign: 'center' },
  imageRemove: { position: 'absolute', top: 4, right: 4, width: 24, height: 24, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(11,29,58,0.72)' },
  input: { minHeight: 48, paddingHorizontal: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, color: color.text.heading, backgroundColor: color.surface.card },
  visibilityRow: { flexDirection: 'row', gap: spacing[2] },
  visibilityOption: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, backgroundColor: color.surface.card },
  visibilityOptionSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  hint: { marginTop: spacing[2] },
  error: { marginTop: spacing[4] },
  submit: { marginTop: spacing[6] },
});
