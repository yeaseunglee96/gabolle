// 기록 상세 — 피드 카드를 누르면 오는 화면 (S15P21E201-228).
import { useCallback, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, View } from 'react-native';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { MarkdownBody } from '@/components/MarkdownBody';
import { PhotoGrid } from '@/components/PhotoGrid';
import { Button } from '@/components/Button';
import { ReportModal } from '@/components/ReportModal';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { BlockUserDialog } from '@/social/BlockUserDialog';
import { deleteStory, getCachedStory, getStory, relativeStoryTime, reportStory, setBlocked, VISIBILITY_LABEL, type StoryDto, type StoryReportReason } from '@/social/stories';

type State = { status: 'loading'; cached: StoryDto | null } | { status: 'loaded'; story: StoryDto } | { status: 'not-found' } | { status: 'error'; message: string };

/**
 * 상세 전용 사진 격자 — 3열 2행, 넘치면 마지막 칸에 「+N」 (S15P21E201-1177, 시안 2a).
 *
 * 🔴 목록·작성 미리보기가 쓰는 `PhotoGrid` 를 안 쓴다. 그 부품은 **장수에 따라 배치를
 * 바꾸는** 것이 일이고(한 장은 넓게, 넷은 사분면), 여기는 **다 보여주는 갤러리**라 하는
 * 일이 다르다. 한 부품에 두 성격을 넣으면 한쪽을 고칠 때마다 다른 쪽이 흔들린다.
 *
 * 🔴 그래서 목록과 상세의 사진 배치가 달라진다. 이건 시안이 그렇게 정한 것이다 —
 * 목록은 커버 한 장, 상세는 전부. 「같은 글이 자리마다 다르게 보이면 안 된다」는 예전
 * 판단(S15P21E201-1135)과 어긋나 보이지만, 그때는 **같은 갤러리를 다르게 그리는** 것이
 * 문제였고 지금은 **커버와 갤러리라는 다른 것**이다.
 */
function DetailPhotoGrid({ images }: { images: StoryDto['images'] }) {
  const { tx } = useI18n();
  if (!images.length) return null;
  const SLOTS = 6;
  const shown = images.slice(0, SLOTS);
  const rest = images.length - shown.length;
  return (
    <View accessibilityLabel={tx('여행 기록 사진', 'Trip record photos')} style={styles.grid}>
      {shown.map((image, index) => (
        <View key={image.url} style={styles.gridCell}>
          <Image source={{ uri: image.url }} resizeMode="cover" style={styles.gridImage} accessibilityIgnoresInvertColors />
          {/* 마지막 칸에만, 그리고 남은 장수가 있을 때만 덮는다. */}
          {rest > 0 && index === shown.length - 1
            ? <View style={styles.gridMore}><Text variant="title" weight="bold" color={color.text.onAction}>+{rest}</Text></View>
            : null}
        </View>
      ))}
    </View>
  );
}

/**
 * 장소 제목 블록 — 시안 2a 의 맨 위 (S15P21E201-1177).
 *
 * 🔴 시안은 제목 아래에 **주소**를 넣으라고 하는데 그 칸이 없다. `StoryDto` 의 place 는
 * `{ id, name, lat, lng }` 뿐이다. 없는 것을 지어내지 않고, 있는 `region` 을 대신 쓴다.
 * 주소가 계약에 생기면 그때 바꾼다.
 */
function PlaceHeading({ story, onOpen }: { story: StoryDto; onOpen: () => void }) {
  const { tx } = useI18n();
  if (!story.place) return null;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={tx(`${story.place.name} 상세 보기`, `View details for ${story.place.name}`)}
      onPress={onOpen}
      style={({ pressed }) => [styles.placeHeading, pressed && styles.pressed]}
    >
      <Text variant="display" weight="bold" color={color.text.heading}>{story.place.name}</Text>
      {story.region
        ? <View style={styles.placeMetaRow}>
            <Image source={require('../../assets/icons/common/pin.png')} resizeMode="contain" accessibilityIgnoresInvertColors style={styles.placePin} />
            <Text variant="body" color={color.text.body}>{story.region}</Text>
          </View>
        : null}
    </Pressable>
  );
}

export default function StoryDetail() {
  const router = useRouter();
  const { accessToken } = useAuth();
  const { tx } = useI18n();
  const { id } = useLocalSearchParams<{ id: string }>();
  const [state, setState] = useState<State>({ status: 'loading', cached: null });
  const [reporting, setReporting] = useState(false);
  const [reported, setReported] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [confirmingBlock, setConfirmingBlock] = useState(false);
  const [blockNotice, setBlockNotice] = useState('');

  const load = useCallback(async () => {
    if (!id) return;
    setState({ status: 'loading', cached: getCachedStory(id) });
    const result = await getStory(id, accessToken);
    if (result.state === 'success') setState({ status: 'loaded', story: result.story });
    else if (result.state === 'not-found') setState({ status: 'not-found' });
    else setState({ status: 'error', message: result.message });
  }, [id, accessToken]);

  useFocusEffect(useCallback(() => { void load(); }, [load]));

  // 새로 가져오는 동안에도 목록에서 이미 받은 내용을 자리표시로 먼저 보여준다.
  const story = state.status === 'loaded' ? state.story : state.status === 'loading' ? state.cached : null;

  const submitReport = async (reason: StoryReportReason, detail: string | undefined) => {
    if (!id) return false;
    const outcome = await reportStory(id, reason, detail, accessToken);
    // 신고 즉시 서버가 검토 대기로 옮겨 비노출한다 — 화면도 그 기록을 계속 보여주지 않고,
    // 접수됐다는 안내로 바꾼다(완료 기준: "신고를 보내고 나면 그 기록이 화면에서 사라지고
    // 접수됐다는 안내를 보여준다"). feed.tsx의 목록 제거와 같은 원칙이다.
    if (outcome.state === 'success') setReported(true);
    return outcome.state === 'success';
  };

  // 차단은 「이 글」이 아니라 「이 사람」에 대한 것이다. 차단해도 이 글은 내 화면에서 그대로
  // 보인다 — 거르는 일은 서버가 상대 쪽 화면에서 한다 (S15P21E201-991).
  const confirmBlock = async () => {
    const authorId = story?.author.id;
    if (!authorId) return false;
    const outcome = await setBlocked(authorId, true, accessToken);
    if (outcome.state !== 'success') return false;
    setBlockNotice(tx('이제 이 사용자에게 내 글이 보이지 않아요.', "This user can no longer see your posts."));
    return true;
  };

  const confirmDelete = async () => {
    if (!id || deleting) return;
    setDeleting(true);
    const outcome = await deleteStory(id, accessToken);
    setDeleting(false);
    if (outcome.state === 'success') router.replace('/feed');
  };

  return (
    <Screen scroll>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('피드로 돌아가기', 'Back to feed')} onPress={() => (router.canGoBack() ? router.back() : router.replace('/feed'))} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
        <Text variant="title" weight="bold">‹ {tx('피드', 'Feed')}</Text>
      </Pressable>

      {state.status === 'loading' && !story ? (
        <View accessibilityLiveRegion="polite" style={styles.notice}><ActivityIndicator color={color.brand.orange} /><Text color={color.text.body}>{tx('기록을 불러오고 있어요', 'Loading the record')}</Text></View>
      ) : null}

      {reported ? (
        <View style={styles.notice} accessibilityRole="alert" accessibilityLiveRegion="polite">
          <Text variant="title" weight="bold">{tx('신고가 접수됐어요', 'Report submitted')}</Text>
          <Text color={color.text.body}>{tx('신고한 기록은 더 이상 보이지 않아요. 24시간 안에 처리돼요.', 'This record is no longer shown to you. It will be reviewed within 24 hours.')}</Text>
          <Button label={tx('피드로 돌아가기', 'Back to feed')} onPress={() => router.replace('/feed')} containerStyle={styles.recoveryButton} />
        </View>
      ) : null}

      {story && !reported ? (
        <View style={styles.card}>
          <View style={styles.headerRow}>
            <Pressable accessibilityRole="link" accessibilityLabel={tx(`${story.author.displayName} 프로필 보기`, `View ${story.author.displayName}'s profile`)} onPress={() => router.push(`/user/${story.author.id}`)} style={styles.grow}>
              <Text variant="title" weight="bold">{story.author.displayName}</Text>
              <Text variant="caption" color={color.text.muted}>
                {relativeStoryTime(story.createdAt, tx)}
                {story.region ? ` · ${story.region}` : ''}
              </Text>
            </Pressable>
            {story.mine && story.visibility !== 'PUBLIC' ? (
              <View style={styles.visibilityBadge}><Text variant="caption" weight="bold" color={color.text.muted}>{tx(...VISIBILITY_LABEL[story.visibility])}</Text></View>
            ) : null}
          </View>

          {/* 🔴 시안 2a 의 순서 (S15P21E201-1177): 사진 → 장소 제목 → 본문.
              전에는 본문이 맨 위였다. 기록을 다시 열었을 때 먼저 보고 싶은 것은
              글이 아니라 그때의 사진이라는 것이 이 순서의 뜻이다. */}
          <DetailPhotoGrid images={story.images} />

          <PlaceHeading story={story} onOpen={() => router.push(`/place/${story.place!.id}`)} />

          {/* S15P21E201-1136 — 마크다운을 그린다. 마크다운을 안 쓴 기존 글은
              문단 하나가 되므로 지금과 똑같이 보인다. */}
          <MarkdownBody source={story.body} />

          <View style={styles.actionRow}>
            {!confirmingDelete && (
              <Pressable accessibilityRole="button" accessibilityLabel={tx('공동 작성자 보기', 'View co-authors')} onPress={() => router.push(`/feed/${story.id}/coauthors`)} style={styles.textAction}>
                <Text variant="caption" weight="bold" color={color.text.accent}>{tx('공동 작성자', 'Co-authors')}</Text>
              </Pressable>
            )}
            {story.mine ? (
              confirmingDelete ? (
                <View style={styles.confirmRow}>
                  <Text variant="caption" color={color.text.body} style={styles.confirmText}>{tx('정말 삭제할까요? 되돌릴 수 없어요.', 'Delete this record? This cannot be undone.')}</Text>
                  <View style={styles.confirmButtons}>
                    <Button label={tx('취소', 'Cancel')} variant="ghost" disabled={deleting} onPress={() => setConfirmingDelete(false)} containerStyle={styles.confirmButton} />
                    <Button label={deleting ? tx('삭제 중…', 'Deleting…') : tx('삭제 확정', 'Confirm delete')} disabled={deleting} onPress={() => void confirmDelete()} containerStyle={styles.confirmButton} />
                  </View>
                </View>
              ) : (
                <Pressable accessibilityRole="button" accessibilityLabel={tx('기록 삭제', 'Delete record')} onPress={() => setConfirmingDelete(true)} style={styles.textAction}>
                  <Text variant="caption" weight="bold" color={color.state.danger}>{tx('삭제', 'Delete')}</Text>
                </Pressable>
              )
            ) : (
              <>
                <Pressable accessibilityRole="button" accessibilityLabel={tx('신고하기', 'Report')} onPress={() => setReporting(true)} style={styles.textAction}>
                  <Text variant="caption" weight="bold" color={color.text.muted}>{tx('이 글 신고', 'Report this post')}</Text>
                </Pressable>
                {/* 🔴 신고와 차단을 같은 것처럼 보이게 하지 않는다 — 신고는 「이 글」에 대한
                    것이고 차단은 「이 사람」에 대한 것이다. 구분선과 문구로 갈라 준다. */}
                <View style={styles.actionDivider} />
                <Pressable accessibilityRole="button" accessibilityLabel={tx('사용자 차단하기', 'Block this user')} onPress={() => setConfirmingBlock(true)} style={styles.textAction}>
                  <Text variant="caption" weight="bold" color={color.state.danger}>{tx('사용자 차단', 'Block user')}</Text>
                </Pressable>
              </>
            )}
          </View>
        </View>
      ) : null}

      {state.status === 'not-found' ? (
        <View style={styles.notice} accessibilityRole="alert">
          <Text variant="title" weight="bold">{tx('기록을 찾을 수 없어요', 'Could not find this record')}</Text>
          <Text color={color.text.body}>{tx('삭제됐거나, 볼 수 없는 기록이에요.', "It's been deleted, or you don't have access to it.")}</Text>
          <Button label={tx('피드로 돌아가기', 'Back to feed')} onPress={() => router.replace('/feed')} containerStyle={styles.recoveryButton} />
        </View>
      ) : null}

      {state.status === 'error' ? (
        <View style={styles.notice} accessibilityRole="alert">
          <Text variant="title" weight="bold">{tx('기록을 불러오지 못했어요', "We couldn't load this record")}</Text>
          <Text color={color.text.body}>{state.message}</Text>
          <Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void load()} containerStyle={styles.recoveryButton} />
        </View>
      ) : null}

      {blockNotice ? (
        <View accessibilityLiveRegion="polite" style={styles.notice}>
          <Text color={color.text.body}>{blockNotice}</Text>
        </View>
      ) : null}

      <ReportModal visible={reporting} onClose={() => setReporting(false)} onSubmit={submitReport} />
      <BlockUserDialog visible={confirmingBlock} displayName={story?.author.displayName ?? ''} onClose={() => setConfirmingBlock(false)} onConfirm={confirmBlock} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  back: { minHeight: 44, alignSelf: 'flex-start', justifyContent: 'center', marginBottom: spacing[3] },
  pressed: { opacity: 0.72 },
  notice: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  card: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  headerRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2] },
  grow: { flex: 1, gap: spacing[1] },
  visibilityBadge: { minHeight: 28, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  images: { marginTop: spacing[2] },
  placeCard: { gap: spacing[1], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  // ── 상세 2a (S15P21E201-1177) ──────────────────────────────────────────────
  //
  // 사진 격자: 3열. 셀은 정사각(aspectRatio 1)이라 폭이 바뀌어도 줄이 맞는다.
  // 시안은 "각 행 140" 이라고 적었는데 그건 시안 폭 기준의 결과값이다 — 숫자를 박으면
  // 좁은 폰에서 넘치고 넓은 화면에서 빈다. 비율로 둔다.
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  gridCell: { position: 'relative', flexBasis: '31.5%', flexGrow: 1, aspectRatio: 1, borderRadius: radius.md, overflow: 'hidden', backgroundColor: color.surface.soft },
  gridImage: { width: '100%', height: '100%' },
  gridMore: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(11,29,58,0.45)' },

  placeHeading: { gap: spacing[2] },
  placeMetaRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  placePin: { width: 16, height: 16 },
  actionRow: { flexDirection: 'row', justifyContent: 'space-between' },
  textAction: { minHeight: 44, paddingHorizontal: spacing[2], alignItems: 'center', justifyContent: 'center' },
  actionDivider: { width: 1, alignSelf: 'stretch', marginVertical: spacing[2], backgroundColor: color.surface.border },
  confirmRow: { flex: 1, gap: spacing[2] },
  confirmText: { textAlign: 'right' },
  confirmButtons: { flexDirection: 'row', justifyContent: 'flex-end', gap: spacing[2] },
  confirmButton: { width: 'auto', paddingHorizontal: spacing[4] },
  recoveryButton: { marginTop: spacing[2] },
});
