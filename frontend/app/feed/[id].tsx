// 기록 상세 — 피드 카드를 누르면 오는 화면.
import { useCallback, useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { AuthorAvatar } from '@/social/AuthorAvatar';
import { ActivityIndicator, Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import * as Clipboard from 'expo-clipboard';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useAuth } from '@/auth/AuthProvider';
import { DropdownMenu, useDropdownMenu, type DropdownMenuItem } from '@/components/DropdownMenu';
import { MarkdownBody } from '@/components/MarkdownBody';
import { PhotoGrid } from '@/components/PhotoGrid';
import { Button } from '@/components/Button';
import { ReportModal } from '@/components/ReportModal';
import { Screen } from '@/components/Screen';
import { PhotoCarousel } from '@/social/PhotoCarousel';
import { bottomDockPosition } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { BlockUserDialog } from '@/social/BlockUserDialog';
import { CoauthorByline } from '@/social/CoauthorByline';
import { findCourseLink, withoutCourseLink } from '@/social/courseLink';
import { CourseLinkCard } from '@/social/CourseLinkCard';
import { regionText } from '@/social/districtNames';
import { regionBesidePlace } from '@/social/placeRegion';
import { createStory, deleteStory, getCachedStory, getStory, getStoryReplies, getUserProfile, loadSavedStoryIds, recordStoryLinkCopy, relativeStoryTime, reportStory, setBlocked, setFollowing, setStoryReaction, setStorySaved, storyMetricLabels, storyShareUrl, updateStory, VISIBILITY_LABEL, type StoryDto, type StoryReportReason } from '@/social/stories';
import { applyReaction, nextReaction, StoryReactionRow, storyReactionStyles, type ReactableStory, type Reaction } from '@/social/StoryReactionRow';
import { txf } from '@/i18n/format';
import { MAX_STORY_IMAGES, useStoryImages } from '@/social/useStoryImages';

type State = { status: 'loading'; cached: StoryDto | null } | { status: 'loaded'; story: StoryDto } | { status: 'not-found' } | { status: 'error'; message: string };

/**
 * 상세 사진 — 큰 사진 한 장씩 옆으로 넘긴다. 아래 점이 몇 번째인지 따라간다(S15P21E201-1787, 사용자 요청).
 * 전에는 3열 격자(시안 2a)라 사진이 작게만 보였다. 상세는 사진을 보러 오는 곳이라 자르지 않고 전체를 보인다(contain).
 */
function DetailPhotoGrid({ images }: { images: StoryDto['images'] }) {
  const { tx } = useI18n();
  if (!images.length) return null;
  return (
    <View accessibilityLabel={tx('여행 기록 사진', 'Trip record photos')}>
      <PhotoCarousel urls={images.map((image) => image.url)} resizeMode="contain" style={styles.photos} />
    </View>
  );
}

/** 장소 제목 블록 — 시안 2a 의 맨 위 */
function PlaceHeading({ story, onOpen }: { story: StoryDto; onOpen: () => void }) {
  const { tx } = useI18n();
  if (!story.place) return null;
  // 🔴 지역 칸은 「장소 · 구」라 그대로 쓰면 제목의 장소 이름이 한 번 더 나온다(S15P21E201-1759).
  const placeRegion = regionBesidePlace(story.region, story.place.name);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={txf(tx, '%s 상세 보기', 'View details for %s', story.place.name)}
      onPress={onOpen}
      style={({ pressed }) => [styles.placeHeading, pressed && styles.pressed]}
    >
      <Text variant="display" weight="bold" color={color.text.heading}>{story.place.name}</Text>
      {placeRegion
        ? <View style={styles.placeMetaRow}>
            <Image source={require('../../assets/icons/common/pin.png')} resizeMode="contain" accessibilityIgnoresInvertColors style={styles.placePin} />
            <Text variant="body" color={color.text.body}>{regionText(placeRegion, tx)}</Text>
          </View>
        : null}
    </Pressable>
  );
}

/** 본문 글자 상한. */
const BODY_MAX = 500;

/** 들여쓰기를 멈추는 깊이. 더 깊은 답글도 그리기는 그린다 — 안으로 밀지 않을 뿐이다. */
const MAX_INDENT_DEPTH = 2;

/**
 * 댓글 한 장 — 원글과 같은 StoryDto 를 받는다.
 *
 * 내보내는 이유는 시험이 이것만 떼어 그려 보기 위해서다. 화면 주소는 기본 내보내기
 * (StoryDetail) 하나로 정해지므로 이름 있는 내보내기를 더해도 주소가 늘지 않는다.
 */
export function ReplyCard({
  reply,
  accessToken,
  onUpdated,
  onDeleted,
  onReport,
  depth = 0,
}: {
  reply: StoryDto;
  accessToken: string | null;
  onUpdated: (updated: StoryDto) => void;
  onDeleted: (id: string) => void;
  onReport: (id: string) => void;
  /** 몇 단째 댓글인가 — 들여쓰기를 어디서 멈출지에만 쓴다. 0 이 원글에 직접 달린 댓글. */
  depth?: number;
}) {
  const { tx } = useI18n();
  const [editing, setEditing] = useState(false);
  // null 은 「아직 안 불러왔다」이고 빈 배열은 「답글이 없다」다 — 원글 쪽 replies 와 같은 규칙.
  // 실패를 빈 배열로 바꾸면 화면이 「답글이 없다」고 주장하게 된다.
  const [children, setChildren] = useState<StoryDto[] | null>(null);
  const [expanded, setExpanded] = useState(false);
  const [loadingChildren, setLoadingChildren] = useState(false);
  const [childrenError, setChildrenError] = useState('');
  const [draft, setDraft] = useState(reply.body);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const menu = useDropdownMenu();
  // 좋아요는 이 카드가 낙관적으로 맞춘다 — 원글과 같은 규칙(applyReaction). 댓글도 글이라 같은 반응 주소를 쓴다.
  // 인용·저장은 댓글에 없다 — linkCopyCount 를 비워 두면 반응 줄이 인용 칸을 안 그린다.
  const [reaction, setReaction] = useState<ReactableStory>(() => ({
    myReaction: reply.myReaction, likeCount: reply.likeCount, dislikeCount: reply.dislikeCount, linkCopyCount: undefined,
    // 내 댓글이면 원글처럼 잠근다 — 빠뜨리면 단추는 눌리는데 서버가 409(STORY_REACTION_OWN)로 조용히 거절한다(S15P21E201-1783).
    mine: reply.mine,
  }));
  const [reacting, setReacting] = useState(false);

  const startEdit = () => { setDraft(reply.body); setSaveError(''); setEditing(true); };

  const react = async (pressed: Reaction) => {
    if (!accessToken || reacting) return;
    const next = nextReaction(reaction.myReaction, pressed);
    setReacting(true);
    const outcome = await setStoryReaction(reply.id, next, accessToken);
    setReacting(false);
    if (outcome.state === 'success') setReaction((current) => applyReaction(current, next));
  };

  // 수정·삭제·신고는 원글처럼 ⋯ 메뉴 하나로 — 댓글마다 글자 단추가 늘어서면 본문보다 단추가 먼저 읽힌다.
  const menuItems: DropdownMenuItem[] = reply.mine
    ? [
        { key: 'edit', label: tx('수정', 'Edit'), onPress: startEdit },
        { key: 'delete', label: tx('삭제', 'Delete'), destructive: true, onPress: () => setConfirmingDelete(true) },
      ]
    : [{ key: 'report', label: tx('신고', 'Report'), onPress: () => onReport(reply.id) }];

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

  // 서버는 한 단씩만 준다 — 손자는 안 딸려 온다. 그래서 이 댓글의 id 로 같은 경로를 다시 부른다.
  const loadChildren = useCallback(async () => {
    setLoadingChildren(true);
    const outcome = await getStoryReplies(reply.id, accessToken);
    setLoadingChildren(false);
    if (outcome.state === 'success') { setChildren(outcome.replies); setChildrenError(''); }
    else { setChildren(null); setChildrenError(outcome.message); }
  }, [reply.id, accessToken]);

  // 접었다 다시 펴는 것은 요청을 또 보내지 않는다 — 이미 받은 것을 그대로 다시 보여준다.
  const toggleChildren = () => {
    if (expanded) { setExpanded(false); return; }
    setExpanded(true);
    if (children === null) void loadChildren();
  };

  // 답글의 수정·삭제는 이 카드가 들고 있는 목록만 고친다. 위로 올리면 원글의 댓글 목록에서
  // 답글을 찾다가 없어서 조용히 무시된다.
  const updateChild = (updated: StoryDto) => setChildren((current) => current?.map((item) => (item.id === updated.id ? updated : item)) ?? current);
  const removeChild = (removedId: string) => setChildren((current) => current?.filter((item) => item.id !== removedId) ?? current);

  // 서버가 세는 값이라 이쪽이 진짜다. 0 이면 단추 자체를 안 그린다 — 눌러도 빈 목록만 나온다.
  const childCount = reply.replyCount ?? 0;

  // 🔴 트위터 답글형(사용자 결정 2026-09-24, S15P21E201-1576) — 원글과 같은 머리(동그라미·이름·시간·⋯)에 본문·사진,
  //    그 아래 좋아요·답글 줄. 답글을 펼치면 왼쪽 동그라미 밑으로 세로선이 이어져 한 줄기로 읽힌다.
  return (
    <View style={styles.reply}>
      <View style={styles.replyRail}>
        <AuthorAvatar name={reply.author.displayName} uri={reply.author.avatarUrl} style={styles.replyAvatar} />
        {expanded && childCount > 0 ? <View style={styles.replyRailLine} /> : null}
      </View>

      <View style={styles.replyMain}>
      <View style={styles.replyHead}>
        <Text variant="body" weight="bold" color={color.text.heading} numberOfLines={1} style={styles.replyName}>{reply.author.displayName}</Text>
        <Text variant="caption" color={color.text.muted} style={styles.grow}>{relativeStoryTime(reply.createdAt, tx)}</Text>
        {!editing && !confirmingDelete ? (
          <Pressable ref={menu.buttonRef} accessibilityRole="button" accessibilityLabel={tx('댓글 더 보기', 'More comment options')} onPress={menu.openMenu} style={styles.replyMenuButton}>
            <Text variant="body" weight="bold" color={color.text.muted}>⋯</Text>
          </Pressable>
        ) : null}
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
            <Button label={tx('취소', 'Cancel')} variant="tertiary" disabled={saving} onPress={() => setEditing(false)} compact />
            <Button label={saving ? tx('저장 중…', 'Saving…') : tx('저장', 'Save')} variant="secondary" disabled={saving || !draft.trim()} onPress={() => void saveEdit()} compact />
          </View>
        </View>
      ) : (
        <MarkdownBody source={reply.body} />
      )}

      {!editing && reply.images.length
        ? <PhotoGrid photos={reply.images.map((image) => ({ uri: image.url }))} compact accessibilityLabel={tx('댓글 사진', 'Comment photo')} style={styles.replyPhotos} />
        : null}

      {confirmingDelete ? (
        <View style={styles.confirmRow}>
          <Text variant="caption" color={color.text.body} style={styles.confirmText}>{tx('댓글을 삭제할까요?', 'Delete this comment?')}</Text>
          <View style={styles.confirmButtons}>
            <Button label={tx('취소', 'Cancel')} variant="tertiary" disabled={deleting} onPress={() => setConfirmingDelete(false)} compact />
            <Button label={deleting ? tx('삭제 중…', 'Deleting…') : tx('삭제 확정', 'Confirm delete')} variant="danger" disabled={deleting} onPress={() => void confirmDelete()} compact />
          </View>
        </View>
      ) : null}

      {!editing && !confirmingDelete ? (
        // 좋아요 · 답글 — 원글과 같은 알약 부품이다.
        <StoryReactionRow story={reaction} reacting={reacting} onReact={(pressed) => void react(pressed)} style={styles.replyReactions}>
          {childCount > 0 ? (
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ expanded }}
              accessibilityLabel={expanded ? tx('답글 접기', 'Hide replies') : tx(`답글 ${childCount}개 보기`, `Show ${childCount} replies`)}
              onPress={toggleChildren}
              style={storyReactionStyles.button}
            >
              <Text variant="util" weight="bold" color={color.text.body}>
                {expanded ? tx('답글 접기', 'Hide replies') : tx(`답글 ${childCount}개`, `${childCount} replies`)}
              </Text>
            </Pressable>
          ) : null}
        </StoryReactionRow>
      ) : null}

      {!editing && childCount > 0 ? (
        <View style={styles.replyThread}>
          {expanded ? (
            childrenError ? (
              <View accessibilityRole="alert" style={styles.replyNotice}>
                <Text variant="caption" color={color.text.body}>{tx('답글을 불러오지 못했어요.', "We couldn't load the replies.")}</Text>
                <Button compact label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void loadChildren()} containerStyle={styles.recoveryButton} />
              </View>
            ) : loadingChildren || children === null ? (
              <ActivityIndicator color={color.action.primary} />
            ) : (
              /* 폰 폭은 좁다. 계속 밀면 깊은 답글이 한 줄에 한 글자씩 떨어지므로 들여쓰기는 두 단에서 멈춘다. */
              <View style={depth < MAX_INDENT_DEPTH ? styles.replyChildren : styles.replyThread}>
                {children.map((child) => (
                  <ReplyCard
                    key={child.id}
                    reply={child}
                    accessToken={accessToken}
                    onUpdated={updateChild}
                    onDeleted={removeChild}
                    onReport={onReport}
                    depth={depth + 1}
                  />
                ))}
                {children.length < childCount ? (
                  <Text variant="caption" color={color.text.muted}>
                    {tx(`답글 ${childCount}개 중 ${children.length}개를 보여드렸어요.`, `Showing ${children.length} of ${childCount} replies.`)}
                  </Text>
                ) : null}
              </View>
            )
          ) : null}
        </View>
      ) : null}
      </View>

      {/* 열 때만 그린다 — 댓글마다 닫힌 메뉴 창을 하나씩 깔아 두면 댓글이 많을 때 무겁다. */}
      {menu.open ? <DropdownMenu visible anchor={menu.anchor} items={menuItems} onClose={menu.close} /> : null}
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
  // 복사 알림은 목록(feed.tsx)처럼 잠깐 떴다 사라진다. 남겨 두면 다음에 눌렀을 때 같은 글자라 새로 떴는지 모른다.
  useEffect(() => { if (!copyNotice) return; const timer = setTimeout(() => setCopyNotice(''), 2600); return () => clearTimeout(timer); }, [copyNotice]);
  const insets = useSafeAreaInsets();
  const menu = useDropdownMenu();
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
  // 저장 여부 — 목록(feed.tsx)과 같은 열쇠로 같은 집합을 본다. 여기서 저장하면 목록으로
  // 돌아갔을 때도 켜져 있어야 하고, 목록에서 저장한 것이 여기서도 켜져 있어야 한다.
  const queryClient = useQueryClient();
  const signedIn = Boolean(accessToken);
  const savedIdsQuery = useQuery({
    queryKey: ['saved-story-ids', signedIn],
    queryFn: () => loadSavedStoryIds(accessToken),
    enabled: signedIn,
  });
  const savedIds = savedIdsQuery.data?.state === 'success' ? savedIdsQuery.data.ids : new Set<string>();
  const [saving, setSaving] = useState(false);
  const [repliesError, setRepliesError] = useState('');
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState('');
  // 댓글 사진 — 원글과 같은 부품이다(S15P21E201-1651). 서버는 댓글에도 imageUrls 를 3장까지 받는다.
  const replyPhotos = useStoryImages(accessToken, tx);

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
  const courseLink = story ? findCourseLink(story.body) : null;

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
   * 우상단 ⋯ 메뉴 — S15P21E201-1244. 사용자 요청으로 삭제·팔로우·신고·차단을 여기
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

  // 차단은 「이 글」이 아니라 「이 사람」에 대한 것이다. 지금 이미 열어 둔 이 글은 차단해도 화면에서
  // 그대로 보인다(직접 주소로 여는 것은 안 막는다) — 하지만 다음에 피드 목록을 다시 열면 이 사람의
  // 글은 양쪽 다 빠진다(S15P21E201-1714·1722). 「이 글만은 예외」와 「피드 전체가 그렇다」를 헷갈리지 않는다.
  const confirmBlock = async () => {
    const authorId = story?.author.id;
    if (!authorId) return false;
    const outcome = await setBlocked(authorId, true, accessToken);
    if (outcome.state !== 'success') return false;
    setBlockNotice(tx(
      '이제 이 사용자에게 내 글이 안 보이고, 내 피드에도 이 사람 글이 안 보여요.',
      "This user can no longer see your posts, and their posts won't show up in your feed either.",
    ));
    return true;
  };

  const submitReply = async () => {
    const body = draft.trim();
    if (!id || !body || sending || replyPhotos.anyUploading) return;
    setSending(true);
    // 댓글도 글이다 — 같은 만들기 경로에 부모 id 와 올라간 사진 주소를 실어 보낸다.
    const outcome = await createStory({ body, imageUrls: replyPhotos.uploadedUrls, parentStoryId: id, accessToken });
    setSending(false);
    if (outcome.state !== 'success') { setSendError(outcome.message); return; }
    setDraft('');
    setSendError('');
    replyPhotos.clearImages();
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

  /** 좋아요·싫어요 — S15P21E201-1247. 목록과 같은 규칙이다(같은 것을 다시 누르면 꺼진다). */
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

  /** 저장 — 목록에 있고 상세에는 없던 버튼. 로그인 전이면 로그인으로 보내고 돌아온다. */
  const toggleSave = async () => {
    if (!story || saving) return;
    if (!accessToken) { router.push({ pathname: '/sign-in', params: { returnTo: `/feed/${id}` } }); return; }
    const nextSaved = !savedIds.has(story.id);
    setSaving(true);
    const outcome = await setStorySaved(story.id, nextSaved, accessToken);
    setSaving(false);
    if (outcome.state !== 'success') return;
    queryClient.setQueryData<{ state: 'success'; ids: Set<string> }>(['saved-story-ids', signedIn], (current) => {
      const ids = new Set(current?.ids ?? []);
      if (nextSaved) ids.add(story.id); else ids.delete(story.id);
      return { state: 'success', ids };
    });
  };

  const updateReply = (updated: StoryDto) => {
    setReplies((current) => current?.map((reply) => (reply.id === updated.id ? updated : reply)) ?? current);
  };

  return (
    // 복사 알림을 스크롤 밖에 띄우려고 한 겹 감싼다. Screen scroll 은 자식을 전부 굴러가는 판 안에 넣는다.
    <View style={styles.root}>
    <Screen scroll>
      {/* — 목적지를 약속하지 않는다. 이 화면에 들어오는 입구가 일곱인데
          피드는 그중 하나라, 「피드로」라고 적으면 대부분의 경로에서 라벨과 결과가 어긋난다.
          place/[id]·user/[id]·feed/[id]/coauthors 가 쓰는 규칙과 같다.
      */}
      <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => (router.canGoBack() ? router.back() : router.replace('/feed'))} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
        <Text variant="title" weight="bold">‹ {tx('뒤로', 'Back')}</Text>
      </Pressable>

      {state.status === 'loading' && !story ? (
        <View accessibilityLiveRegion="polite" style={styles.notice}><ActivityIndicator color={color.action.primary} /><Text color={color.text.body}>{tx('기록을 불러오고 있어요', 'Loading the record')}</Text></View>
      ) : null}

      {reported ? (
        <View style={styles.notice} accessibilityRole="alert" accessibilityLiveRegion="polite">
          <Text variant="title" weight="bold">{tx('신고가 접수됐어요', 'Report submitted')}</Text>
          <Text color={color.text.body}>{tx('신고한 기록은 더 이상 보이지 않아요. 24시간 안에 처리돼요.', 'This record is no longer shown to you. It will be reviewed within 24 hours.')}</Text>
          <Button compact label={tx('피드로 돌아가기', 'Back to feed')} onPress={() => router.replace('/feed')} containerStyle={styles.recoveryButton} />
        </View>
      ) : null}

      {story && !reported ? (
        <View style={styles.card}>
          <View style={styles.headerRow}>
            {/* 작성자 이름 뒤에 공동 작성자 「· 이예승」(S15P21E201-1583). 이름 버튼 «안»에 둘 수 없어서
                (버튼 안의 버튼) 이름만 프로필로 가는 버튼이 되고, 시각·지역 줄은 그 아래 글자로 선다. */}
            <View style={styles.grow}>
              <View style={styles.bylineRow}>
                <Pressable accessibilityRole="link" accessibilityLabel={txf(tx, '%s 프로필 보기', "View %s's profile", story.author.displayName)} onPress={() => router.push(`/user/${story.author.id}`)} style={styles.authorLink}>
                  <Text variant="title" weight="bold" numberOfLines={1}>{story.author.displayName}</Text>
                </Pressable>
                <CoauthorByline story={story} large />
              </View>
              <Text variant="caption" color={color.text.muted}>
                {relativeStoryTime(story.createdAt, tx)}
                {story.region ? ` · ${regionText(story.region, tx)}` : ''}
              </Text>
            </View>
            {story.mine && story.visibility !== 'PUBLIC' ? (
              <View style={styles.visibilityBadge}><Text variant="caption" weight="bold" color={color.text.muted}>{tx(...VISIBILITY_LABEL[story.visibility])}</Text></View>
            ) : null}
            <Pressable ref={menu.buttonRef} accessibilityRole="button" accessibilityLabel={tx('더 보기', 'More options')} onPress={menu.openMenu} style={styles.menuButton}>
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
          <MarkdownBody source={withoutCourseLink(story.body, courseLink)} />
          {/* 본문의 코스 링크는 코스 카드로 그린다 — 글자로 또 쓰지 않는다(S15P21E201-1593). */}
          {courseLink ? <CourseLinkCard token={courseLink.token} /> : null}

          {/* 좋아요·인용·저장 — 카드 «안»에 둔다(사용자 지적 2026-09-24, S15P21E201-1576). 카드 밖에 두면 그 기록의 것인지
              아래 댓글의 것인지 흐려진다. 목록(feed.tsx)과 같은 부품이다. */}
          <StoryReactionRow story={story} reacting={reacting} onReact={(reaction) => void react(reaction)} saved={savedIds.has(story.id)} saving={saving} onToggleSave={() => void toggleSave()} onQuote={() => void copyLink()} style={styles.inCardReactions} />

 {/*— 삭제·신고·차단은 우상단 ⋯ 메뉴로 옮겼다. 공동 작성자는
              "더 보기" 성격이 아니라 주된 이동이라 그대로 남긴다. 삭제 확인은 메뉴에서
              "삭제"를 고르면 여기 그대로 펼쳐진다 — 자리만 옮기고 확인 흐름은 안 바꿨다. */}
          <View style={styles.actionRow}>
            {/* 🔴 내 글이면 늘(공동 작성자를 초대하는 입구), 남의 글이면 공동 작성자가 있을 때만(S15P21E201-1673) — 남의 글에서
                누르면 빈 목록뿐이었다. 서버가 칸을 안 보내는 옛 판이면 남의 글에서는 안 그린다. */}
            {!confirmingDelete && (story.mine || (story.coauthors?.length ?? 0) > 0) && (
              <Pressable accessibilityRole="button" accessibilityLabel={tx('공동 작성자 보기', 'View co-authors')} onPress={() => router.push(`/feed/${story.id}/coauthors`)} style={styles.textAction}>
                {/* 「›」 — 글자만 있으면 제목처럼 읽혀서 눌러 볼 생각을 안 한다(2026-09-21 실측, S15P21E201-1372). */}
                <Text variant="caption" weight="bold" color={color.text.accent}>{tx('공동 작성자', 'Co-authors')} ›</Text>
              </Pressable>
            )}
            {confirmingDelete ? (
              <View style={styles.confirmRow}>
                <Text variant="caption" color={color.text.body} style={styles.confirmText}>{tx('정말 삭제할까요? 되돌릴 수 없어요.', 'Delete this record? This cannot be undone.')}</Text>
                <View style={styles.confirmButtons}>
                  <Button label={tx('취소', 'Cancel')} variant="tertiary" disabled={deleting} onPress={() => setConfirmingDelete(false)} compact />
                  <Button label={deleting ? tx('삭제 중…', 'Deleting…') : tx('삭제 확정', 'Confirm delete')} variant="danger" disabled={deleting} onPress={() => void confirmDelete()} compact />
                </View>
              </View>
            ) : null}
          </View>
        </View>
      ) : null}

      {/* 지표 줄 — S15P21E201-1213. 시안이 정한 자리가 댓글 바로 위다. */}
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
              <Button compact label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void loadReplies()} containerStyle={styles.recoveryButton} />
            </View>
          ) : replies === null ? (
            <ActivityIndicator color={color.action.primary} />
          ) : shownReplies.length === 0 ? (
            <Text variant="caption" color={color.text.muted}>{tx('아직 댓글이 없어요.', 'No comments yet.')}</Text>
          ) : (
            <>
              {/* 원글에서 내려오는 세로선 — 답글 묶음이 원글에 매달린 것으로 읽히게(트위터형, S15P21E201-1576). */}
              <View style={styles.replyList}>
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
              </View>
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
              {/* 고른 사진 — 피드 탭 글쓰기와 같은 부품·같은 모양이다. 🔴 영상은 없다(사용자에게 따로 묻기로 했다). */}
              {replyPhotos.images.length ? <PhotoGrid
                photos={replyPhotos.images.map((image) => ({ uri: image.localUri }))}
                compact
                accessibilityLabel={tx('고른 사진', 'Selected photo')}
                style={styles.composerPhotos}
                renderOverlay={(index) => {
                  const image = replyPhotos.images[index];
                  if (!image) return null;
                  return <>
                    {image.uploading ? <View style={styles.composerPhotoOverlay}><ActivityIndicator color={color.text.onAction} /></View> : null}
                    {image.error ? <Pressable accessibilityRole="button" accessibilityLabel={tx('업로드 다시 시도', 'Retry upload')} onPress={() => replyPhotos.retryImage(index)} style={styles.composerPhotoOverlay}>
                      <Text variant="caption" weight="bold" color={color.text.onAction}>{tx('다시 시도', 'Retry')}</Text>
                    </Pressable> : null}
                    {/* 🔴 댓글 사진도 같다 — hitSlop 없이는 24pt (S15P21E201-1794).
                        빗나가면 뒤의 사진 타일이 눌려 사진이 열린다. 글쓰기 쪽(feed.tsx)과
                        같은 값을 준다. */}
                    <Pressable accessibilityRole="button" accessibilityLabel={tx('사진 삭제', 'Remove photo')} hitSlop={10} onPress={() => replyPhotos.removeImage(index)} style={styles.composerPhotoRemove}>
                      <Text weight="bold" color={color.text.onAction}>×</Text>
                    </Pressable>
                  </>;
                }}
              /> : null}
              {replyPhotos.images.map((image, index) => image.error
                ? <Text key={`reply-photo-error-${index}`} variant="caption" color={color.state.danger}>{image.error}</Text>
                : null)}
              {replyPhotos.images.length ? <Text variant="caption" color={color.text.muted}>{tx('사진의 위치 정보는 지워져요.', 'Location data is removed from photos.')}</Text> : null}
              <View style={styles.composerActions}>
                <Pressable accessibilityRole="button" accessibilityLabel={tx('댓글에 사진 추가', 'Add photo to comment')} disabled={!replyPhotos.canAddMore} onPress={() => void replyPhotos.addImage()} style={[styles.composerTool, !replyPhotos.canAddMore && styles.composerToolBusy]}>
                  <Image source={require('../../assets/icons/common/camera.png')} resizeMode="contain" accessibilityLabel="" style={styles.composerToolIcon} />
                  <Text variant="body" color={color.text.body}>{tx(`사진 ${replyPhotos.images.length}/${MAX_STORY_IMAGES}`, `Photos ${replyPhotos.images.length}/${MAX_STORY_IMAGES}`)}</Text>
                </Pressable>
                <Button
                  label={sending ? tx('보내는 중…', 'Sending…') : tx('남기기', 'Post')}
                  disabled={sending || !draft.trim() || replyPhotos.anyUploading}
                  onPress={() => void submitReply()}
                  containerStyle={styles.composerButton}
                />
              </View>
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
          <Button compact label={tx('피드로 돌아가기', 'Back to feed')} onPress={() => router.replace('/feed')} containerStyle={styles.recoveryButton} />
        </View>
      ) : null}

      {state.status === 'error' ? (
        <View style={styles.notice} accessibilityRole="alert">
          <GabolleMascot state="sad" style={styles.sadMascot} />
          <Text variant="title" weight="bold">{tx('기록을 불러오지 못했어요', "We couldn't load this record")}</Text>
          <Text color={color.text.body}>{state.message}</Text>
          <Button compact label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void load()} containerStyle={styles.recoveryButton} />
        </View>
      ) : null}

      {blockNotice ? (
        <View accessibilityLiveRegion="polite" style={styles.notice}>
          <Text color={color.text.body}>{blockNotice}</Text>
        </View>
      ) : null}

      <DropdownMenu visible={menu.open} anchor={menu.anchor} items={menuItems} onClose={menu.close} />
      <ReportModal visible={reportingTargetId !== null} onClose={() => setReportingTargetId(null)} onSubmit={submitReport} />
      <BlockUserDialog visible={confirmingBlock} displayName={story?.author.displayName ?? ''} onClose={() => setConfirmingBlock(false)} onConfirm={confirmBlock} />
    </Screen>
    {/* 인용·링크 복사 알림(S15P21E201-1787) — 전에는 댓글 아래 글 맨 끝에 붙어서, 글 중간에서 누르면 아무 일도 없는 것처럼 보였다.
        목록과 같은 토스트로 화면 아래에 띄운다. */}
    {copyNotice ? (
      <View pointerEvents="none" accessibilityLiveRegion="polite" style={[styles.copyNoticeDock, { bottom: insets.bottom + spacing[4] }]}>
        <View style={styles.copyNotice}><Text variant="caption" weight="bold" color={color.text.onAction}>{copyNotice}</Text></View>
      </View>
    ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  copyNoticeDock: { position: bottomDockPosition(), left: 0, right: 0, alignItems: 'center', zIndex: 25 },
  copyNotice: { paddingHorizontal: spacing[4], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: color.action.secondary, shadowColor: color.brand.navy, shadowOpacity: 0.18, shadowRadius: 10, shadowOffset: { width: 0, height: 4 }, elevation: 4 },
  back: { minHeight: 44, alignSelf: 'flex-start', justifyContent: 'center', marginBottom: spacing[3] },
  pressed: { opacity: 0.72 },
  sadMascot: { width: 80, height: 80, alignSelf: 'center' },
  notice: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  card: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  headerRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2] },
  grow: { flex: 1, gap: spacing[1] },
  bylineRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minWidth: 0 },
  authorLink: { flexShrink: 1 },
  visibilityBadge: { minHeight: 28, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  menuButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  images: { marginTop: spacing[2] },
  placeCard: { gap: spacing[1], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  // ── 상세 2a ──────────────────────────────────────────────
  photos: { aspectRatio: 1, borderRadius: radius.md, backgroundColor: color.surface.soft },

  placeHeading: { gap: spacing[2] },
  placeMetaRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  placePin: { width: 16, height: 16 },
  actionRow: { flexDirection: 'row', justifyContent: 'space-between' },
  textAction: { minHeight: 44, paddingHorizontal: spacing[2], alignItems: 'center', justifyContent: 'center' },
  confirmRow: { flex: 1, gap: spacing[2] },
  confirmText: { textAlign: 'right' },
  confirmButtons: { flexDirection: 'row', justifyContent: 'flex-end', gap: spacing[2] },
  recoveryButton: { marginTop: spacing[2] },

  // 지표 줄 — 댓글 머리 바로 위. 붙는 자리라 위 여백만 준다.
  metrics: { marginTop: spacing[4] },

  // ── 댓글 ────────────────────────────────────────────────
  comments: { gap: spacing[3], marginTop: spacing[4] },
  replyList: { gap: spacing[2], marginLeft: spacing[4], paddingLeft: spacing[3], borderLeftWidth: 2, borderLeftColor: color.surface.border },
  // 답글 한 장 — 원글 카드와 같은 흰 바탕·둥근 모서리. 왼쪽 동그라미 기둥 + 오른쪽 내용(트위터 답글형, S15P21E201-1576).
  reply: { flexDirection: 'row', gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  replyRail: { alignItems: 'center' },
  replyRailLine: { flex: 1, width: 2, marginTop: spacing[1], borderRadius: 1, backgroundColor: color.surface.border },
  replyMain: { flex: 1, minWidth: 0, gap: spacing[2] },
  replyHead: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  replyName: { flexShrink: 1 },
  replyMenuButton: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center', marginVertical: -spacing[2] },
  replyAvatar: { width: 36, height: 36, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },
  // 반응 줄은 목록 카드용 좌우 여백을 갖고 있다 — 여백 있는 카드 안에서는 뺀다.
  replyReactions: { paddingHorizontal: 0, paddingBottom: 0, marginTop: 0 },
  inCardReactions: { paddingHorizontal: 0, paddingBottom: 0, marginTop: 0 },
  replyPhotos: { marginTop: spacing[1] },
  replyNotice: { gap: spacing[2], alignItems: 'flex-start' },
  replyEdit: { gap: spacing[2] },
  replyThread: { gap: spacing[2] },
  replyChildren: { gap: spacing[2], marginLeft: spacing[3] },
  composer: { gap: spacing[2] },
  // textAlignVertical 은 안드로이드에서 여러 줄 입력이 가운데로 쏠리는 것을 막는다.
  composerInput: { minHeight: 88, padding: spacing[3], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.md, backgroundColor: color.surface.card, color: color.text.heading, textAlignVertical: 'top' },
  // 🔴 폭을 박는다. Button 안쪽은 width:'100%' 라 껍데기가 «auto» 면 글자 폭으로 쪼그라들어 「남기기」가 잘렸다
  //    (사용자 화면 2026-09-24 — S15P21E201-1524 와 같은 원인).
  composerButton: { alignSelf: 'flex-end', width: 120 },
  // 사진 추가는 왼쪽, 남기기는 오른쪽 — 피드 탭 글쓰기의 도구 줄과 같은 배치다.
  composerActions: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] },
  composerTool: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 36, paddingHorizontal: spacing[3], borderRadius: radius.sm },
  composerToolBusy: { opacity: 0.6 },
  composerToolIcon: { width: 16, height: 16, tintColor: color.brand.navy },
  composerPhotos: { flexDirection: 'row', gap: spacing[2] },
  composerPhotoOverlay: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(25,25,25,0.45)' },
  composerPhotoRemove: { position: 'absolute', top: spacing[1], right: spacing[1], width: 24, height: 24, borderRadius: radius.full, backgroundColor: 'rgba(25,25,25,0.6)', alignItems: 'center', justifyContent: 'center' },
});
