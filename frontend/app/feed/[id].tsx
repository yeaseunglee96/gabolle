// 기록 상세 — 피드 카드를 누르면 오는 화면.
import { useCallback, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import * as Clipboard from 'expo-clipboard';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { DropdownMenu, type DropdownMenuItem } from '@/components/DropdownMenu';
import { MarkdownBody } from '@/components/MarkdownBody';
import { PhotoGrid } from '@/components/PhotoGrid';
import { Button } from '@/components/Button';
import { ReportModal } from '@/components/ReportModal';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { BlockUserDialog } from '@/social/BlockUserDialog';
import { createStory, deleteStory, getCachedStory, getStory, getStoryReplies, getUserProfile, recordStoryLinkCopy, relativeStoryTime, reportStory, setBlocked, setFollowing, setStoryReaction, storyMetricLabels, storyShareUrl, updateStory, VISIBILITY_LABEL, type StoryDto, type StoryReportReason } from '@/social/stories';
import { applyReaction, nextReaction, StoryReactionRow, type Reaction } from '@/social/StoryReactionRow';

type State = { status: 'loading'; cached: StoryDto | null } | { status: 'loaded'; story: StoryDto } | { status: 'not-found' } | { status: 'error'; message: string };

/** 상세 전용 사진 격자 — 3열 2행, 넘치면 마지막 칸에 「+N」 (시안 2a). */
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

/** 장소 제목 블록 — 시안 2a 의 맨 위 */
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

/** 본문 글자 상한. */
const BODY_MAX = 500;

/** 댓글 한 장 — 원글과 같은 StoryDto 를 받는다 */
function ReplyCard({
  reply,
  accessToken,
  onUpdated,
  onDeleted,
  onReport,
}: {
  reply: StoryDto;
  accessToken: string | null;
  onUpdated: (updated: StoryDto) => void;
  onDeleted: (id: string) => void;
  onReport: (id: string) => void;
}) {
  const { tx } = useI18n();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(reply.body);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const startEdit = () => { setDraft(reply.body); setSaveError(''); setEditing(true); };

  const saveEdit = async () => {
    const body = draft.trim();
    if (!body || saving) return;
    setSaving(true);
    const outcome = await updateStory(reply.id, body, accessToken);
    setSaving(false);
    if (outcome.state !== 'success') { setSaveError(outcome.message); return; }
    setEditing(false);
    setSaveError('');
    onUpdated(outcome.story);
  };

  const confirmDelete = async () => {
    if (deleting) return;
    setDeleting(true);
    const outcome = await deleteStory(reply.id, accessToken);
    setDeleting(false);
    // 실패를 조용히 삼키지 않는다 — 다시 확인 상태로 돌아가서 한 번 더 시도할 수 있게 둔다.
    if (outcome.state === 'success') onDeleted(reply.id);
  };

  return (
    <View style={styles.reply}>
      <View style={styles.replyHead}>
        <View style={styles.replyAvatar}>
          <Text variant="caption" weight="bold" color={color.text.onAction}>{reply.author.displayName.slice(0, 1)}</Text>
        </View>
        <Text variant="caption" weight="bold" color={color.text.heading} numberOfLines={1} style={styles.grow}>{reply.author.displayName}</Text>
        <Text variant="caption" color={color.text.muted}>{relativeStoryTime(reply.createdAt, tx)}</Text>
      </View>

      {editing ? (
        <View style={styles.replyEdit}>
          <TextInput
            accessibilityLabel={tx('댓글 수정', 'Edit comment')}
            value={draft}
            onChangeText={(value) => setDraft(value.slice(0, BODY_MAX))}
            maxLength={BODY_MAX}
            multiline
            style={styles.composerInput}
          />
          {saveError ? <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{saveError}</Text> : null}
          <View style={styles.confirmButtons}>
            <Button label={tx('취소', 'Cancel')} variant="tertiary" disabled={saving} onPress={() => setEditing(false)} containerStyle={styles.confirmButton} />
            <Button label={saving ? tx('저장 중…', 'Saving…') : tx('저장', 'Save')} disabled={saving || !draft.trim()} onPress={() => void saveEdit()} containerStyle={styles.confirmButton} />
          </View>
        </View>
      ) : (
        <MarkdownBody source={reply.body} />
      )}

      {!editing && reply.images.length
        ? <PhotoGrid photos={reply.images.map((image) => ({ uri: image.url }))} compact accessibilityLabel={tx('댓글 사진', 'Comment photo')} style={styles.replyPhotos} />
        : null}

      {!editing ? (
        <View style={styles.replyActions}>
          {reply.mine ? (
            confirmingDelete ? (
              <View style={styles.confirmRow}>
                <Text variant="caption" color={color.text.body} style={styles.confirmText}>{tx('댓글을 삭제할까요?', 'Delete this comment?')}</Text>
                <View style={styles.confirmButtons}>
                  <Button label={tx('취소', 'Cancel')} variant="tertiary" disabled={deleting} onPress={() => setConfirmingDelete(false)} containerStyle={styles.confirmButton} />
                  <Button label={deleting ? tx('삭제 중…', 'Deleting…') : tx('삭제 확정', 'Confirm delete')} disabled={deleting} onPress={() => void confirmDelete()} containerStyle={styles.confirmButton} />
                </View>
              </View>
            ) : (
              <>
                <Pressable accessibilityRole="button" accessibilityLabel={tx('댓글 수정', 'Edit comment')} onPress={startEdit} style={styles.replyTextAction}>
                  <Text variant="caption" weight="bold" color={color.text.accent}>{tx('수정', 'Edit')}</Text>
                </Pressable>
                <Pressable accessibilityRole="button" accessibilityLabel={tx('댓글 삭제', 'Delete comment')} onPress={() => setConfirmingDelete(true)} style={styles.replyTextAction}>
                  <Text variant="caption" weight="bold" color={color.state.danger}>{tx('삭제', 'Delete')}</Text>
                </Pressable>
              </>
            )
          ) : (
            <Pressable accessibilityRole="button" accessibilityLabel={tx('댓글 신고', 'Report comment')} onPress={() => onReport(reply.id)} style={styles.replyTextAction}>
              <Text variant="caption" weight="bold" color={color.text.muted}>{tx('신고', 'Report')}</Text>
            </Pressable>
          )}
        </View>
      ) : null}
    </View>
  );
}

export default function StoryDetail() {
  const router = useRouter();
  const { accessToken } = useAuth();
  const { tx } = useI18n();
  const { id } = useLocalSearchParams<{ id: string }>();
  const [state, setState] = useState<State>({ status: 'loading', cached: null });
  // 어느 글을 신고하는 중인지 — 원글(id)일 수도, 댓글(reply.id)일 수도 있다. 신고 모달은
  // 하나만 두고 대상만 바꿔 재사용한다 — ReportModal 은 원글이 이미 쓰는 부품이다.
  const [reportingTargetId, setReportingTargetId] = useState<string | null>(null);
  const [reported, setReported] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [confirmingBlock, setConfirmingBlock] = useState(false);
  const [blockNotice, setBlockNotice] = useState('');
  const [copyNotice, setCopyNotice] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  // null = 아직 모른다. StoryDto 에는 "내가 이 작성자를 팔로우하는가" 칸이 없어서
  // (반응 카운트와 달리 얹지 않기로 했다) getUserProfile 로 따로 물어봐야 한다
  // 안 물어본 상태를 false 로 두면 실제로 팔로우 중인데 "팔로우" 로 잘못 그린다.
  const [authorFollowing, setAuthorFollowing] = useState<boolean | null>(null);
  const [followBusy, setFollowBusy] = useState(false);
  // null 은 「아직 안 불러왔다」이고 빈 배열은 「댓글이 없다」다. 둘을 같게 두면
  // 불러오는 중과 없음이 화면에서 구분이 안 된다.
  const [replies, setReplies] = useState<StoryDto[] | null>(null);
  // — 반응 요청이 도는 중인가. 연타로 낙관적 수가 어긋나는 것을 막는다.
  const [reacting, setReacting] = useState(false);
  const [repliesError, setRepliesError] = useState('');
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState('');

  const load = useCallback(async () => {
    if (!id) return;
    setState({ status: 'loading', cached: getCachedStory(id) });
    const result = await getStory(id, accessToken);
    if (result.state === 'success') {
      setState({ status: 'loaded', story: result.story });
      // 내 글이면 안 물어본다 — 자기 자신을 팔로우하는 개념이 없다.
      if (!result.story.mine) {
        const profile = await getUserProfile(result.story.author.id, accessToken);
        if (profile.state === 'success') setAuthorFollowing(profile.profile.following);
      }
    }
    else if (result.state === 'not-found') setState({ status: 'not-found' });
    else setState({ status: 'error', message: result.message });
  }, [id, accessToken]);

  // 실패를 빈 목록으로 바꾸지 않는다. 「댓글이 없다」와 「못 불러왔다」는 다른 말이고
  // 둘을 같게 그리면 사용자가 없는 것으로 믿는다.
  const loadReplies = useCallback(async () => {
    if (!id) return;
    const outcome = await getStoryReplies(id, accessToken);
    if (outcome.state === 'success') { setReplies(outcome.replies); setRepliesError(''); }
    else { setReplies(null); setRepliesError(outcome.message); }
  }, [id, accessToken]);

  useFocusEffect(useCallback(() => { void load(); void loadReplies(); }, [load, loadReplies]));

  // 새로 가져오는 동안에도 목록에서 이미 받은 내용을 자리표시로 먼저 보여준다.
  const story = state.status === 'loaded' ? state.story : state.status === 'loading' ? state.cached : null;

  const shownReplies = replies ?? [];
  // 서버는 기본 50개까지만 준다. 잘린 것을 조용히 숨기면 사용자는 그게 전부인 줄 안다
  // 상한과 「더 있다」는 언제나 짝이다. replyCount 는 서버가 세는 값이라 이쪽이 진짜다.
  const totalReplies = typeof story?.replyCount === 'number' ? story.replyCount : null;
  const hasMoreReplies = totalReplies !== null && totalReplies > shownReplies.length;

  // 서버가 준 지표만 말한다. 아무것도 안 오면 줄 자체를 안 그린다 — 0 을 지어내 그리면
  // 「아무도 안 봤다」는 주장이 되는데, 실제로는 서버가 아직 안 세는 것일 수 있다.
  const metricLabels = story ? storyMetricLabels(story, tx) : [];

  /**
   * 링크 복사. 지표 줄이 「인용 N」을 그리는데 누를 자리가 없어 그 수가 영원히 0이었다.
   *
   * 🔴 복사가 먼저고 세는 것은 나중이다. 세는 쪽이 실패해도 「복사했다」고 말한다 —
   * 클립보드에는 이미 들어갔고, 수를 못 센 것 때문에 복사가 안 된 것처럼 보이면
   * 사용자가 다시 누른다.
   *
   * 🔴 그리고 load() 를 다시 부르지 않는다. 그 호출이 조회수를 올려서 「복사한 것」이
   * 「본 것」으로 세어진다. 서버가 돌려준 글을 그대로 넣는다.
   *
   * 🔴 수가 안 올라가도 성공이다 — 오늘 이미 센 사람, 작성자 본인. 서버가 그렇게 답한다.
   */
  const copyLink = async () => {
    if (!story) return;
    await Clipboard.setStringAsync(storyShareUrl(story.id));
    setCopyNotice(tx('링크를 복사했어요.', 'Link copied.'));
    const outcome = await recordStoryLinkCopy(story.id, accessToken);
    if (outcome.state === 'success') setState({ status: 'loaded', story: outcome.story });
  };

  /**
   * 우상단 ⋯ 메뉴 —. 사용자 요청으로 삭제·팔로우·신고·차단을 여기
   * 하나로 몰아넣는다. 시안(FeedDetail.dc.html)의 "내 글=연필/남의 글=⋯" 구분은
   * 이번 결정으로 폐기하고 항상 ⋯ 하나로 통일한다.
   *
   * 링크 복사는 내 글·남의 글 양쪽에 둔다 — 내 글을 남에게 보내는 것이 더 잦다.
   */
  const menuItems: DropdownMenuItem[] = story
    ? [
        { key: 'copy-link', label: tx('링크 복사', 'Copy link'), onPress: () => void copyLink() },
        ...(story.mine
        ? [{ key: 'delete', label: tx('삭제', 'Delete'), destructive: true, onPress: () => setConfirmingDelete(true) }]
        : [
            ...(authorFollowing !== null
              ? [{
                  key: 'follow',
                  label: authorFollowing ? tx('팔로잉 취소', 'Unfollow') : tx('팔로우', 'Follow'),
                  onPress: () => void toggleFollow(),
                }]
              : []),
            { key: 'report', label: tx('이 글 신고', 'Report this post'), onPress: () => setReportingTargetId(story.id) },
            { key: 'block', label: tx('사용자 차단', 'Block user'), destructive: true, onPress: () => setConfirmingBlock(true) },
          ]),
      ]
    : [];

  const submitReport = async (reason: StoryReportReason, detail: string | undefined) => {
    const targetId = reportingTargetId;
    if (!targetId) return false;
    const outcome = await reportStory(targetId, reason, detail, accessToken);
    if (outcome.state !== 'success') return false;
    if (targetId === id) {
      // 원글 신고 — 서버가 즉시 검토 대기로 옮겨 비노출한다. 화면도 그 기록을 계속
      // 보여주지 않고 접수됐다는 안내로 바꾼다(완료 기준: "신고를 보내고 나면 그 기록이
      // 화면에서 사라지고 접수됐다는 안내를 보여준다"). feed.tsx의 목록 제거와 같은 원칙이다.
      setReported(true);
    } else {
      // 댓글 신고 — 화면 전체를 안내로 바꾸지 않는다. 그 댓글 한 장만 목록에서 뺀다.
      removeReply(targetId);
    }
    return true;
  };

  // — 이 화면에 팔로우가 지금까지 없었다. 낙관적으로 먼저 바꾸지
  // 않는다 — 실패하면 "팔로우했다고 나왔는데 실제로는 아니었다"가 되고, 다음에 이
  // 화면을 다시 열었을 때 서버 값과 달라 보인다.
  const toggleFollow = async () => {
    const authorId = story?.author.id;
    if (!authorId || authorFollowing === null || followBusy) return;
    setFollowBusy(true);
    const outcome = await setFollowing(authorId, !authorFollowing, accessToken);
    setFollowBusy(false);
    if (outcome.state === 'success') setAuthorFollowing(outcome.following);
  };

  // 차단은 「이 글」이 아니라 「이 사람」에 대한 것이다. 차단해도 이 글은 내 화면에서 그대로
  // 보인다 — 거르는 일은 서버가 상대 쪽 화면에서 한다.
  const confirmBlock = async () => {
    const authorId = story?.author.id;
    if (!authorId) return false;
    const outcome = await setBlocked(authorId, true, accessToken);
    if (outcome.state !== 'success') return false;
    setBlockNotice(tx('이제 이 사용자에게 내 글이 보이지 않아요.', "This user can no longer see your posts."));
    return true;
  };

  const submitReply = async () => {
    const body = draft.trim();
    if (!id || !body || sending) return;
    setSending(true);
    // 댓글도 글이다 — 같은 만들기 경로에 부모 id 만 실어 보낸다.
    const outcome = await createStory({ body, imageUrls: [], parentStoryId: id, accessToken });
    setSending(false);
    if (outcome.state !== 'success') { setSendError(outcome.message); return; }
    setDraft('');
    setSendError('');
    // 서버를 다시 부르지 않고 방금 받은 것을 뒤에 붙인다 — 목록 순서가 오래된 것부터다.
    setReplies((current) => [...(current ?? []), outcome.story]);
  };

  const confirmDelete = async () => {
    if (!id || deleting) return;
    setDeleting(true);
    const outcome = await deleteStory(id, accessToken);
    setDeleting(false);
    if (outcome.state === 'success') router.replace('/feed');
  };

  /** 댓글 한 장을 목록에서 뺀다 — 삭제됐거나 신고로 사라졌을 때. */
  const removeReply = (replyId: string) => {
    setReplies((current) => current?.filter((reply) => reply.id !== replyId) ?? current);
    setState((current) => {
      if (current.status !== 'loaded' || typeof current.story.replyCount !== 'number') return current;
      return { ...current, story: { ...current.story, replyCount: Math.max(0, current.story.replyCount - 1) } };
    });
  };

  /** 좋아요·싫어요 —. 목록과 같은 규칙이다(같은 것을 다시 누르면 꺼진다). */
  const react = async (reaction: Reaction) => {
    if (!story || reacting) return;
    if (!accessToken) { router.push({ pathname: '/sign-in', params: { returnTo: `/feed/${id}` } }); return; }
    const next = nextReaction(story.myReaction, reaction);
    setReacting(true);
    const outcome = await setStoryReaction(story.id, next, accessToken);
    setReacting(false);
    if (outcome.state !== 'success') return;
    setState((current) => (current.status === 'loaded'
      ? { ...current, story: applyReaction(current.story, next) }
      : current));
  };

  const updateReply = (updated: StoryDto) => {
    setReplies((current) => current?.map((reply) => (reply.id === updated.id ? updated : reply)) ?? current);
  };

  return (
    <Screen scroll>
      {/* — 목적지를 약속하지 않는다. 이 화면에 들어오는 입구가 일곱인데
          피드는 그중 하나라, 「피드로」라고 적으면 대부분의 경로에서 라벨과 결과가 어긋난다.
          place/[id]·collection/[id]·user/[id]·feed/[id]/coauthors 가 쓰는 규칙과 같다.
      */}
      <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => (router.canGoBack() ? router.back() : router.replace('/feed'))} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
        <Text variant="title" weight="bold">‹ {tx('뒤로', 'Back')}</Text>
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
            <Pressable accessibilityRole="button" accessibilityLabel={tx('더 보기', 'More options')} onPress={() => setMenuOpen(true)} style={styles.menuButton}>
              <Text variant="body" weight="bold" color={color.text.muted}>⋯</Text>
            </Pressable>
          </View>

          {/* 시안 2a 의 순서 사진 → 장소 제목 → 본문.
              전에는 본문이 맨 위였다. 기록을 다시 열었을 때 먼저 보고 싶은 것은
              글이 아니라 그때의 사진이라는 것이 이 순서의 뜻이다.
          */}
          <DetailPhotoGrid images={story.images} />

          <PlaceHeading story={story} onOpen={() => router.push(`/place/${story.place!.id}`)} />

          {/* — 마크다운을 그린다. 마크다운을 안 쓴 기존 글은
              문단 하나가 되므로 지금과 똑같이 보인다.
          */}
          <MarkdownBody source={story.body} />

 {/*— 삭제·신고·차단은 우상단 ⋯ 메뉴로 옮겼다. 공동 작성자는
              "더 보기" 성격이 아니라 주된 이동이라 그대로 남긴다. 삭제 확인은 메뉴에서
              "삭제"를 고르면 여기 그대로 펼쳐진다 — 자리만 옮기고 확인 흐름은 안 바꿨다. */}
          <View style={styles.actionRow}>
            {!confirmingDelete && (
              <Pressable accessibilityRole="button" accessibilityLabel={tx('공동 작성자 보기', 'View co-authors')} onPress={() => router.push(`/feed/${story.id}/coauthors`)} style={styles.textAction}>
                <Text variant="caption" weight="bold" color={color.text.accent}>{tx('공동 작성자', 'Co-authors')}</Text>
              </Pressable>
            )}
            {confirmingDelete ? (
              <View style={styles.confirmRow}>
                <Text variant="caption" color={color.text.body} style={styles.confirmText}>{tx('정말 삭제할까요? 되돌릴 수 없어요.', 'Delete this record? This cannot be undone.')}</Text>
                <View style={styles.confirmButtons}>
                  <Button label={tx('취소', 'Cancel')} variant="tertiary" disabled={deleting} onPress={() => setConfirmingDelete(false)} containerStyle={styles.confirmButton} />
                  <Button label={deleting ? tx('삭제 중…', 'Deleting…') : tx('삭제 확정', 'Confirm delete')} disabled={deleting} onPress={() => void confirmDelete()} containerStyle={styles.confirmButton} />
                </View>
              </View>
            ) : null}
          </View>
        </View>
      ) : null}

      {/* 좋아요·싫어요 —. 목록과 같은 부품을 쓴다. 칸 이름을 못 받아
          비워 뒀던 자리인데가 상세 응답에도 실어 주면서 채웠다.
      */}
      {story && !reported ? (
        <StoryReactionRow story={story} reacting={reacting} onReact={(reaction) => void react(reaction)} />
      ) : null}

      {/* 지표 줄 —. 시안이 정한 자리가 댓글 바로 위다. */}
      {story && !reported && metricLabels.length ? (
        <View style={styles.metrics}>
          <Text variant="caption" color={color.text.muted}>{metricLabels.join(' · ')}</Text>
        </View>
      ) : null}

      {story && !reported ? (
        <View style={styles.comments}>
          <Text variant="title" weight="bold" color={color.text.heading}>
            {replies === null ? tx('댓글', 'Comments') : tx(`댓글 ${shownReplies.length}`, `Comments ${shownReplies.length}`)}
          </Text>

          {repliesError ? (
            <View accessibilityRole="alert" style={styles.replyNotice}>
              <Text variant="caption" color={color.text.body}>{tx('댓글을 불러오지 못했어요.', "We couldn't load the comments.")}</Text>
              <Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void loadReplies()} containerStyle={styles.recoveryButton} />
            </View>
          ) : replies === null ? (
            <ActivityIndicator color={color.brand.orange} />
          ) : shownReplies.length === 0 ? (
            <Text variant="caption" color={color.text.muted}>{tx('아직 댓글이 없어요.', 'No comments yet.')}</Text>
          ) : (
            <>
              {shownReplies.map((reply) => (
                <ReplyCard
                  key={reply.id}
                  reply={reply}
                  accessToken={accessToken}
                  onUpdated={updateReply}
                  onDeleted={removeReply}
                  onReport={setReportingTargetId}
                />
              ))}
              {hasMoreReplies ? (
                <Text variant="caption" color={color.text.muted}>
                  {tx(`댓글 ${totalReplies}개 중 ${shownReplies.length}개를 보여드렸어요.`, `Showing ${shownReplies.length} of ${totalReplies} comments.`)}
                </Text>
              ) : null}
            </>
          )}

          {/* 로그인 안 한 사람에게 눌러도 아무 일 없는 입력창을 두지 않는다.
              보낼 수 없는 창은 「썼는데 사라졌다」로 끝난다. 갈 곳을 알려 준다.
          */}
          {accessToken ? (
            <View style={styles.composer}>
              <TextInput
                accessibilityLabel={tx('댓글 입력', 'Write a comment')}
                value={draft}
                onChangeText={(value) => setDraft(value.slice(0, BODY_MAX))}
                maxLength={BODY_MAX}
                multiline
                placeholder={tx('댓글을 남겨 보세요', 'Leave a comment')}
                placeholderTextColor={color.text.muted}
                style={styles.composerInput}
              />
              <Button
                label={sending ? tx('보내는 중…', 'Sending…') : tx('남기기', 'Post')}
                disabled={sending || !draft.trim()}
                onPress={() => void submitReply()}
                containerStyle={styles.composerButton}
              />
            </View>
          ) : (
            <Pressable accessibilityRole="button" onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: `/feed/${id}` } })} style={styles.textAction}>
              <Text variant="caption" weight="bold" color={color.text.accent}>{tx('로그인하면 댓글을 남길 수 있어요', 'Sign in to leave a comment')}</Text>
            </Pressable>
          )}

          {sendError ? (
            <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{sendError}</Text>
          ) : null}
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
          <Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void load()} containerStyle={styles.recoveryButton} />
        </View>
      ) : null}

      {blockNotice ? (
        <View accessibilityLiveRegion="polite" style={styles.notice}>
          <Text color={color.text.body}>{blockNotice}</Text>
        </View>
      ) : null}

      {copyNotice ? (
        <View accessibilityLiveRegion="polite" style={styles.notice}>
          <Text color={color.text.body}>{copyNotice}</Text>
        </View>
      ) : null}

      <DropdownMenu visible={menuOpen} items={menuItems} onClose={() => setMenuOpen(false)} />
      <ReportModal visible={reportingTargetId !== null} onClose={() => setReportingTargetId(null)} onSubmit={submitReport} />
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
  menuButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  images: { marginTop: spacing[2] },
  placeCard: { gap: spacing[1], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  // ── 상세 2a ──────────────────────────────────────────────
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  gridCell: { position: 'relative', flexBasis: '31.5%', flexGrow: 1, aspectRatio: 1, borderRadius: radius.md, overflow: 'hidden', backgroundColor: color.surface.soft },
  gridImage: { width: '100%', height: '100%' },
  gridMore: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(11,29,58,0.45)' },

  placeHeading: { gap: spacing[2] },
  placeMetaRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  placePin: { width: 16, height: 16 },
  actionRow: { flexDirection: 'row', justifyContent: 'space-between' },
  textAction: { minHeight: 44, paddingHorizontal: spacing[2], alignItems: 'center', justifyContent: 'center' },
  confirmRow: { flex: 1, gap: spacing[2] },
  confirmText: { textAlign: 'right' },
  confirmButtons: { flexDirection: 'row', justifyContent: 'flex-end', gap: spacing[2] },
  confirmButton: { width: 'auto', paddingHorizontal: spacing[4] },
  recoveryButton: { marginTop: spacing[2] },

  // 지표 줄 — 댓글 머리 바로 위. 붙는 자리라 위 여백만 준다.
  metrics: { marginTop: spacing[4] },

  // ── 댓글 ────────────────────────────────────────────────
  comments: { gap: spacing[3], marginTop: spacing[4] },
  reply: { gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card },
  replyHead: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  replyAvatar: { width: 24, height: 24, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },
  replyPhotos: { marginTop: spacing[1] },
  replyNotice: { gap: spacing[2], alignItems: 'flex-start' },
  replyEdit: { gap: spacing[2] },
  replyActions: { flexDirection: 'row', gap: spacing[1] },
  replyTextAction: { minHeight: 36, paddingHorizontal: spacing[2], alignItems: 'center', justifyContent: 'center' },
  composer: { gap: spacing[2] },
  // textAlignVertical 은 안드로이드에서 여러 줄 입력이 가운데로 쏠리는 것을 막는다.
  composerInput: { minHeight: 88, padding: spacing[3], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.md, backgroundColor: color.surface.card, color: color.text.heading, textAlignVertical: 'top' },
  composerButton: { alignSelf: 'flex-end', width: 'auto', paddingHorizontal: spacing[6] },
});
