// 기록 쓰기 — 글 · 사진 · 지역 · 공개 시점 · 공개 범위. 두 곳이 이 한 벌을 쓴다(S15P21E201-1760).
//
//   screen  글쓰기 화면(app/feed/compose.tsx) — 피드·마이페이지에서 온다. 뒤로 가기와 제목까지 그린다
//   panel   여행 화면의 창 «안» — 날씨·동행 초대처럼 창 안에서 내용만 바뀐다. 뒤로 가기와 제목은 창이 그린다
//
// 🔴 전에는 여행 화면의 「기록 남기기」만 글쓰기 화면으로 넘어갔다. 지도와 창이 통째로 사라졌다가, 돌아오면 여행 화면이
//    처음부터 다시 그려졌다 — 날씨·동행 초대는 창 안에서 이어지는데 이것만 끊겼다(사용자: 「기록 남기기만 연속성이 유지가 안 된다」).
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, TextInput, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { useAuth } from '@/auth/AuthProvider';
import { RegionPicker } from '@/components/RegionPicker';
import { MarkdownBody } from '@/components/MarkdownBody';
import { PhotoGrid } from '@/components/PhotoGrid';
import { looksLikeMarkdown } from '@/social/markdown';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { formatMonthDay } from '@/i18n/datetime';
import { txf } from '@/i18n/format';
import { createStory, FEED_QUERY_PREFIX, VISIBILITY_LABEL, type StoryVisibility } from '@/social/stories';
import type { StoryPlaceSnapshot } from '@/social/regionSearch';
import { markChecklistStep } from '@/onboarding/firstRun';
import { MAX_STORY_IMAGES, useStoryImages } from '@/social/useStoryImages';
import { localizeMessage } from '@/i18n/messages';
import { issueShareLink } from '@/share/sharedItinerary';
import { appendCourseLink } from '@/social/courseLink';

const BODY_MAX = 500;
// 업로드 실패 뒤 화면을 새로 고쳐도 쓰던 글이 남아 있어야 한다완료 기준).
//
// 🔴 사진은 **올라간 것만** 남긴다 (S15P21E201-1312). 둘을 갈라야 하는 이유가 있다.
//
//   아직 안 올라간 것  로컬 uri 뿐이고, 새로고침 뒤에는 의미가 없어질 수 있다
//                     (웹의 blob: URL 등). 남겨도 못 그리고 못 보낸다
//   이미 올라간 것     **서버가 준 주소**다. 새로고침해도 다른 기기에서도 그대로
//                     쓸 수 있고, 글을 보낼 때 실려 가는 것이 바로 이 값이다
//
// 전에는 둘을 같이 버렸다. 그래서 세 장을 다 올려 놓고 기다린 뒤에 나갔다 와도 처음부터
// 다시 올려야 했다 — 시간만 드는 게 아니라 데이터 요금이 든다(한 장 3MB).
// 여행 화면 창에서 쓰다 닫아도 같은 칸에 남는다 — 다시 열면(글쓰기 화면에서 열어도) 이어서 쓴다.
const DRAFT_KEY = 'gabolle.story-compose-draft';
type PublishTiming = 'AFTER_TRIP' | 'NOW';

export function StoryComposeForm({ tripId, onClose, variant = 'screen' }: {
  /** 여행 화면에서 왔으면 그 여행에 글이 달린다(S15P21E201-418). 없으면 여행 없는 글. */
  tripId?: string;
  /** 뒤로 가기 · 다 올린 뒤 · 공개 예정 안내의 「확인」. 화면이면 앞 화면으로, 창이면 일정으로 돌아간다. */
  onClose: () => void;
  variant?: 'screen' | 'panel';
}) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const { tx, locale } = useI18n();
  const [body, setBody] = useState('');
  // — 쓴 것이 어떻게 보일지 미리 본다.
  const [preview, setPreview] = useState(false);
  const [region, setRegion] = useState('');
  // 우리 DB 장소를 고르면 채워진다. 손으로 고쳐 쓰면 다시 비워진다 (RegionPicker).
  const [placeId, setPlaceId] = useState<string | undefined>(undefined);
  // 카카오·대체 목록에서 고르면 채워진다 — 서버가 그 장소를 찾거나 만들어 글에 잇는다 (S15P21E201-1527).
  const [place, setPlace] = useState<StoryPlaceSnapshot | undefined>(undefined);
  const [visibility, setVisibility] = useState<StoryVisibility>('PUBLIC');
  const [publishTiming, setPublishTiming] = useState<PublishTiming>('AFTER_TRIP');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 공개 전으로 올라간 글의 공개 시각. 차 있으면 올린 뒤 안내를 보여 주고 확인을 받아 돌아간다(S15P21E201-1737).
  const [scheduledAt, setScheduledAt] = useState<string | null>(null);
  // 「코스 링크 함께 올리기」(S15P21E201-1593) — 여행 화면의 「기록 남기기」로 들어온 글쓰기에만 있다. 켜야 붙는다.
  const [attachCourse, setAttachCourse] = useState(false);
  const draftLoaded = useRef(false);

  // 사진은 공용 훅이 맡는다 — 피드의 인라인 글쓰기와 같은 코드를 쓴다
  // 줄이기·3MB 판정·재시도 규칙이 두 벌이 되지 않게 하려고.
  const { images, addImage, retryImage, removeImage, restoreUploaded, anyUploading, uploadedUrls, canAddMore } = useStoryImages(accessToken, tx);

  useEffect(() => {
    void AsyncStorage.getItem(DRAFT_KEY).then((raw) => {
      draftLoaded.current = true;
      if (!raw) return;
      try {
        const draft = JSON.parse(raw) as { body?: string; region?: string; visibility?: StoryVisibility; publishTiming?: PublishTiming; imageUrls?: unknown };
        if (draft.body) setBody(draft.body.slice(0, BODY_MAX));
        if (draft.region) setRegion(draft.region);
        if (draft.visibility) setVisibility(draft.visibility);
        if (draft.publishTiming) setPublishTiming(draft.publishTiming);
        // 🔴 저장해 둔 것이 정말 주소 목록인지 여기서 본다. 이 값은 이 기기에 남아 있던
        //    것이라 앱 판이 바뀌면 모양이 다를 수 있고, 그대로 믿으면 사진 자리가 깨진다.
        if (Array.isArray(draft.imageUrls)) {
          const urls = draft.imageUrls.filter((url): url is string => typeof url === 'string' && url !== '');
          if (urls.length > 0) restoreUploaded(urls);
        }
      } catch {
        void AsyncStorage.removeItem(DRAFT_KEY);
      }
    });
  }, []);

  useEffect(() => {
    if (!draftLoaded.current) return;
    void AsyncStorage.setItem(DRAFT_KEY, JSON.stringify({ body, region, visibility, publishTiming, imageUrls: uploadedUrls }));
  }, [body, region, visibility, publishTiming, uploadedUrls]);

  const bodyValid = body.trim().length >= 1 && body.trim().length <= BODY_MAX;
  const canSubmit = bodyValid && !anyUploading && !submitting;

  const submit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    // 코스 링크를 켰으면 올리는 순간 읽기 전용 링크를 만들어 본문 끝에 붙인다 — 「공유」와 같은 링크다(새 API 없음).
    // 🔴 링크를 못 만들거나 붙이면 한도를 넘으면 올리지 않고 말한다. 링크 없이 몰래 올리지 않는다.
    let finalBody = body.trim();
    if (tripId && attachCourse) {
      try {
        if (!accessToken) throw new Error('no session');
        const joined = appendCourseLink(finalBody, (await issueShareLink(tripId, accessToken)).shareUrl);
        if (joined.length > BODY_MAX) {
          setSubmitting(false);
          setError(tx('본문이 길어 코스 링크를 붙일 수 없어요. 조금 줄이거나 코스 링크를 꺼 주세요.', 'The text is too long to add the course link. Shorten it or turn the course link off.'));
          return;
        }
        finalBody = joined;
      } catch {
        setSubmitting(false);
        setError(tx('코스 링크를 만들지 못했어요. 잠시 뒤 다시 올리거나 코스 링크를 끄고 올려 주세요.', "Couldn't create the course link. Try again shortly, or post with the course link off."));
        return;
      }
    }
    const outcome = await createStory({
      body: finalBody,
      imageUrls: uploadedUrls,
      region: region.trim() || undefined,
      // 우리 DB 장소를 고르면 placeId, 카카오·대체 목록을 고르면 place 가 실려 간다(S15P21E201-1527).
      // 둘은 동시에 차지 않는다(RegionPicker) — 그래도 createStory 가 placeId 를 먼저 본다.
      placeId,
      place,
      tripId,
      visibility,
      publishAt: publishTiming === 'NOW' ? new Date().toISOString() : undefined,
      accessToken,
    });
    setSubmitting(false);
    if (outcome.state === 'success') {
      void markChecklistStep('story');
      await AsyncStorage.removeItem(DRAFT_KEY);
      // — 돌아가기 전에 피드 보관본을 버린다.
      await queryClient.invalidateQueries({ queryKey: FEED_QUERY_PREFIX });
      // 🔴 공개 전이면 바로 돌아가지 않는다 — 피드에 안 보여서 「올라갔나?」가 된다.
      if (outcome.story.published === false) { setScheduledAt(outcome.story.publishAt); return; }
      onClose();
    } else {
      setError(outcome.message);
    }
  };

  if (scheduledAt) {
    const when = formatMonthDay(scheduledAt, locale) ?? scheduledAt.slice(5, 10);
    const notice = (
      <View testID="compose-scheduled" accessibilityRole="alert" style={styles.scheduled}>
        <Text variant="title" weight="bold">{tx('기록을 올렸어요', 'Record posted')}</Text>
        <Text color={color.text.body}>{txf(tx, '%s에 공개돼요. 그전까지는 나만 볼 수 있고, 마이페이지 → 기록에서 고치거나 지울 수 있어요.', 'It goes live on %s. Until then only you can see it — edit or delete it from My page → Records.', when)}</Text>
        {/* 🔴 동백 채움(primary)은 파일에 하나 — 「기록 올리기」가 이미 쓴다. */}
        <Button variant="secondary" label={tx('확인', 'OK')} onPress={onClose} />
      </View>
    );
    return variant === 'panel' ? notice : <Screen>{notice}</Screen>;
  }

  const form = <View>
    <TextInput
      testID="compose-body"
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
    {/* 여행 화면의 「기록 남기기」로 왔으면 그 여행과 이어진다 — 서버에 tripId 가 함께 간다(S15P21E201-1593). */}
    {tripId ? (
      <View style={styles.tripLink}>
        <Text variant="caption" weight="bold" color={color.state.success}>{tx('✓ 이 여행과 연결됨', '✓ Linked to this trip')}</Text>
        <Pressable accessibilityRole="switch" accessibilityState={{ checked: attachCourse }} accessibilityLabel={tx('코스 링크 함께 올리기', 'Include the course link')} onPress={() => setAttachCourse((on) => !on)} style={styles.toggleRow}>
          <View style={[styles.checkbox, attachCourse && styles.checkboxOn]}>{attachCourse ? <Text variant="caption" weight="bold" color={color.text.onAction}>✓</Text> : null}</View>
          <View style={styles.toggleCopy}>
            <Text weight="bold">{tx('코스 링크 함께 올리기', 'Include the course link')}</Text>
            <Text variant="caption" color={color.text.muted}>{tx('올릴 때 읽기 전용 링크를 만들어 본문 끝에 붙여요. 피드에는 코스 카드로 보여요. 30일 뒤 만료돼요.', 'When you post, we add a read-only link at the end. It shows as a course card in the feed and expires in 30 days.')}</Text>
          </View>
        </Pressable>
      </View>
    ) : null}

    {/* — 쓴 것이 어떻게 보일지 미리 본다.
        마크다운을 몰라도 된다. 그냥 쓰면 평범한 글이 되므로 아무것도 막지 않고
        글을 쓰기 시작했을 때만 이 줄이 생겨 빈 화면을 어지럽히지 않는다.
    */}
    {body.trim() ? <View style={styles.previewRow}>
      <Pressable accessibilityRole="button" accessibilityState={{ expanded: preview }} onPress={() => setPreview((on) => !on)} style={styles.previewToggle}>
        <Text variant="caption" weight="bold" color={color.action.primary}>{preview ? tx('← 다시 쓰기', '← Back to editing') : tx('미리보기', 'Preview')}</Text>
      </Pressable>
      {looksLikeMarkdown(body) ? <Text variant="caption" color={color.text.muted}>{tx('굵게 · 목록 · 제목이 적용돼요', 'Bold, lists and headings will apply')}</Text> : null}
    </View> : null}
    {preview && body.trim() ? <View style={styles.previewBox}><MarkdownBody source={body} /></View> : null}

    <Text variant="caption" weight="bold" style={styles.label}>{tx(`사진 (최대 ${MAX_STORY_IMAGES}장)`, `Photos (up to ${MAX_STORY_IMAGES})`)}</Text>

    {/* — 사진을 가로로 줄 세우던 것을 장수·방향에 따른 배치로 바꾼다.
        목록 카드·글 상세와 같은 부품을 쓴다 — 올릴 때 본 모양과 올라간 뒤 모양이
        다르면 사용자는 무엇이 맞는지 알 수 없다.
    */}
    {images.length ? <PhotoGrid
      photos={images.map((image) => ({ uri: image.localUri }))}
      accessibilityLabel={tx('선택한 사진', 'Selected photo')}
      style={styles.imageGrid}
      renderOverlay={(index) => {
        const image = images[index];
        if (!image) return null;
        return <>
          {image.uploading ? <View style={styles.imageOverlay}><ActivityIndicator color={color.text.onAction} /></View> : null}
        {/* — 실패 사유를 함께 보여 준다.
        */}
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
    {canAddMore ? <Pressable testID="compose-add-photo" accessibilityRole="button" accessibilityLabel={tx('사진 추가', 'Add photo')} onPress={() => void addImage()} style={styles.imageAddRow}><Text variant="caption" weight="bold" color={color.action.primary}>{tx('+ 사진 추가', '+ Add photo')}</Text></Pressable> : null}
    {/* — 약관 제6조의2 를 화면 말로 옮긴다. 약관에 적혀 있다고
        화면에서 숨기면, 사용자는 자기 사진이 어디에 쓰이는지 모른 채 올리게 된다.
    */}
    <Text variant="caption" color={color.text.muted} style={styles.hint}>{tx('사진의 위치 정보는 자동으로 제거되고, 위치는 지역 단위로만 저장돼요. 장소를 연결하면 그 장소 소개에도 사진이 함께 보일 수 있고, 글을 지우면 거기서도 빠져요.', 'Location data is removed from photos, and only a general region is stored. If you link a place, your photo may also appear on that place — and it comes down when you delete the record.')}</Text>

    <Text variant="caption" weight="bold" style={styles.label}>{tx('지역 (선택)', 'Region (optional)')}</Text>
    {/* — 피드 탭 안 글쓰기와 같은 부품을 쓴다. 두 화면이 다르게
        동작하면 같은 앱에서 지역을 고르는 방법이 두 가지가 된다.
    */}
    <RegionPicker
      region={region}
      onChangeRegion={setRegion}
      placeId={placeId}
      onChangePlaceId={setPlaceId}
      onChangePlace={setPlace}
      accessToken={accessToken}
    />

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

    {error ? <Text accessibilityRole="alert" color={color.state.danger} style={styles.error}>{localizeMessage(tx, error)}</Text> : null}

    <Button testID="compose-submit" label={submitting ? tx('올리는 중…', 'Posting…') : tx('기록 올리기', 'Post record')} disabled={!canSubmit} onPress={() => void submit()} containerStyle={styles.submit} />
  </View>;

  if (variant === 'panel') return form;
  return <Screen scroll>
    <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={onClose} style={styles.back}><Text variant="title">‹</Text></Pressable>
    <Text variant="display" weight="bold" style={styles.title}>{tx('기록 남기기', 'Write a record')}</Text>
    {form}
  </Screen>;
}

const styles = StyleSheet.create({
  scheduled: { flex: 1, justifyContent: 'center', gap: spacing[4], padding: spacing[4] },
  back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  title: { marginTop: spacing[4], marginBottom: spacing[4] },
  bodyInput: { minHeight: 120, padding: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, backgroundColor: color.surface.card, color: color.text.heading, textAlignVertical: 'top' },
  counter: { textAlign: 'right', marginTop: spacing[1] },
  previewRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], marginTop: spacing[1] },
  previewToggle: { minHeight: 44, justifyContent: 'center' },
  previewBox: { marginTop: spacing[1], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft },
  label: { marginTop: spacing[6], marginBottom: spacing[2] },
  imageGrid: { marginTop: spacing[2] },
  // 배치에 빈 칸을 끼울 자리가 없어 「사진 추가」를 아래로 내렸다.
  imageAddRow: { minHeight: 44, marginTop: spacing[2], alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, borderStyle: 'dashed' },
  imageOverlay: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(25,25,25,0.55)' },
  // 사유는 사진 위에 얹히므로 좁다. 줄바꿈을 허용하고 가운데로 모은다.
  imageErrorReason: { marginTop: spacing[1], paddingHorizontal: spacing[2], textAlign: 'center' },
  imageRemove: { position: 'absolute', top: 4, right: 4, width: 24, height: 24, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(25,25,25,0.72)' },
  visibilityRow: { flexDirection: 'row', gap: spacing[2] },
  visibilityOption: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, backgroundColor: color.surface.card },
  visibilityOptionSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  hint: { marginTop: spacing[2] },
  error: { marginTop: spacing[4] },
  submit: { marginTop: spacing[6] },
  tripLink: { gap: spacing[2], marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  toggleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 44 },
  checkbox: { width: 24, height: 24, borderRadius: radius.sm, borderWidth: 2, borderColor: color.text.muted, alignItems: 'center', justifyContent: 'center' },
  // 동백 채움은 이 화면의 다음 할 일(「기록 올리기」) 하나뿐이다 — 고른 표시는 짙은 회색(배색 검사).
  checkboxOn: { borderColor: color.action.secondary, backgroundColor: color.action.secondary },
  toggleCopy: { flex: 1, gap: 2 },
});
