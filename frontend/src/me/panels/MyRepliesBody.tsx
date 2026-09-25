// 마이페이지 「내 댓글」 창 본문 — S15P21E201-1652 (서버 S15P21E201-1600).
//
// 제목과 설명은 껍데기가 그린다(myPanels 의 panelTitle). 여기서 또 그리면 두 번 나온다.
// 🔴 원글이 지워지거나 가려져도 내 댓글은 여기서 찾고 지울 수 있어야 한다 — 그래서 이 목록이 따로 있다.
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { markdownToPlain } from '@/social/markdown';
import { decodeHtmlText, loadMyReplies, type MyReplyItem } from '@/social/myReplies';
import { deleteStory, relativeStoryTime } from '@/social/stories';

type Translate = (ko: string, en: string) => string;

/** 원글 상태 한 줄. 가려졌으면 무엇 때문인지 가르지 않는다 — 신고·공개 범위·차단 모두 「볼 수 없어요」다. */
function parentLine(parent: MyReplyItem['parent'], tx: Translate): string {
  if (parent.state === 'DELETED') return tx('원글이 지워졌어요', 'The original post was deleted');
  if (parent.state !== 'VISIBLE') return tx('원글을 볼 수 없어요', "You can't see the original post");
  const author = parent.authorName ? txf(tx, '%s님의 글', "%s's post", decodeHtmlText(parent.authorName)) : tx('원글', 'Original post');
  return parent.bodyPreview ? `${author} · ${decodeHtmlText(parent.bodyPreview)}` : author;
}

export function MyRepliesBody() {
  const router = useRouter();
  const { tx } = useI18n();
  const { accessToken } = useAuth();
  const [status, setStatus] = useState<'loading' | 'ready' | 'not-ready' | 'error'>('loading');
  const [items, setItems] = useState<MyReplyItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreFailed, setMoreFailed] = useState(false);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [deleteFailedId, setDeleteFailedId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setStatus('loading');
    const result = await loadMyReplies(accessToken);
    if (result.state === 'success') {
      setItems(result.items);
      setNextCursor(result.nextCursor);
      setStatus('ready');
    } else {
      setStatus(result.state);
    }
  }, [accessToken]);

  useEffect(() => { void load(); }, [load]);

  async function loadMore() {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    setMoreFailed(false);
    const result = await loadMyReplies(accessToken, nextCursor);
    setLoadingMore(false);
    if (result.state !== 'success') { setMoreFailed(true); return; }
    // 쪽 사이에 새 댓글을 달면 같은 것이 두 쪽에 걸칠 수 있다 — 한 번만 그린다.
    setItems((current) => [...current, ...result.items.filter((item) => !current.some((seen) => seen.reply.id === item.reply.id))]);
    setNextCursor(result.nextCursor);
  }

  async function remove(id: string) {
    if (deletingId) return;
    setDeletingId(id);
    setDeleteFailedId(null);
    const outcome = await deleteStory(id, accessToken);
    setDeletingId(null);
    // 성공했을 때만 뺀다 — 실패했는데 사라지면 「지워졌나?」를 다시 확인해야 한다(저장한 기록과 같은 판단).
    if (outcome.state === 'success') {
      setItems((current) => current.filter((item) => item.reply.id !== id));
      setConfirmingId(null);
    } else {
      setDeleteFailedId(id);
    }
  }

  if (status === 'loading') return <ActivityIndicator color={color.action.primary} style={styles.loading} />;

  if (status === 'not-ready') {
    return (
      <View style={styles.stateCard}>
        <Text variant="title" weight="bold">{tx('준비 중이에요', 'Coming soon')}</Text>
        <Text style={styles.centerCopy}>{tx('곧 여기서 내가 남긴 댓글을 모아 볼 수 있어요.', "Soon you'll see all your comments here.")}</Text>
      </View>
    );
  }

  if (status === 'error') {
    return (
      <View style={styles.stateCard}>
        <Text variant="title" weight="bold">{tx('내 댓글을 불러오지 못했어요', "We couldn't load your comments")}</Text>
        <Button compact label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void load()} />
      </View>
    );
  }

  if (items.length === 0) {
    return (
      <View style={styles.empty}>
        <GabolleMascot state="idle" style={styles.mascot} />
        <Text variant="title" weight="bold">{tx('아직 남긴 댓글이 없어요', 'No comments yet')}</Text>
        <Button compact label={tx('피드 보러 가기', 'Browse the feed')} onPress={() => router.push('/feed')} containerStyle={styles.emptyCta} />
      </View>
    );
  }

  return (
    <View style={styles.list}>
      {items.map((item) => {
        const { reply, parent } = item;
        // 보이는 원글이면 원글 상세(내 댓글이 그 아래 달려 있다), 아니면 내 댓글 자체를 연다.
        const target = parent.state === 'VISIBLE' ? `/feed/${parent.id}` : `/feed/${reply.id}`;
        const confirming = confirmingId === reply.id;
        const deleting = deletingId === reply.id;
        return (
          <View key={reply.id} style={styles.card}>
            <Text variant="caption" color={parent.state === 'VISIBLE' ? color.text.body : color.text.muted} numberOfLines={2}>{parentLine(parent, tx)}</Text>
            <Text numberOfLines={3} color={color.text.heading} style={styles.body}>{markdownToPlain(reply.body)}</Text>
            <Text variant="caption">{relativeStoryTime(reply.createdAt, tx)}</Text>

            {confirming ? (
              <View style={styles.confirm}>
                <Text variant="caption">{tx('댓글을 삭제할까요?', 'Delete this comment?')}</Text>
                <View style={styles.confirmButtons}>
                  <Button compact label={tx('취소', 'Cancel')} variant="tertiary" disabled={deleting} onPress={() => setConfirmingId(null)} />
                  <Button compact label={deleting ? tx('삭제 중…', 'Deleting…') : tx('삭제 확정', 'Confirm delete')} variant="danger" disabled={deleting} onPress={() => void remove(reply.id)} />
                </View>
              </View>
            ) : (
              <View style={styles.actions}>
                <Pressable accessibilityRole="button" onPress={() => router.push(target)} style={({ pressed }) => [styles.action, pressed && styles.pressed]}>
                  <Text weight="bold" color={color.brand.navy}>{tx('자세히 →', 'Open →')}</Text>
                </Pressable>
                <Pressable accessibilityRole="button" onPress={() => { setDeleteFailedId(null); setConfirmingId(reply.id); }} style={({ pressed }) => [styles.action, styles.actionEnd, pressed && styles.pressed]}>
                  <Text weight="medium" color={color.text.muted}>{tx('삭제', 'Delete')}</Text>
                </Pressable>
              </View>
            )}
            {deleteFailedId === reply.id ? <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{tx('지우지 못했어요. 다시 시도해 주세요.', "We couldn't delete it. Please try again.")}</Text> : null}
          </View>
        );
      })}

      {nextCursor ? (
        <Button compact label={loadingMore ? tx('불러오는 중…', 'Loading…') : tx('더 보기', 'Load more')} variant="tertiary" disabled={loadingMore} onPress={() => void loadMore()} containerStyle={styles.more} />
      ) : null}
      {moreFailed ? <Text accessibilityRole="alert" variant="caption" color={color.state.danger} style={styles.centerCopy}>{tx('더 불러오지 못했어요.', "We couldn't load more.")}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  loading: { marginTop: spacing[4] },
  stateCard: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  centerCopy: { textAlign: 'center' },

  empty: { alignItems: 'center', gap: spacing[2], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  mascot: { width: 88, height: 88 },
  emptyCta: { marginTop: spacing[2] },

  list: { gap: spacing[3] },
  card: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  body: { lineHeight: 22 },

  actions: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.surface.border, paddingTop: spacing[2] },
  action: { minHeight: 44, justifyContent: 'center' },
  actionEnd: { marginLeft: 'auto' },
  pressed: { opacity: 0.7 },

  confirm: { gap: spacing[2], alignItems: 'flex-end', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.surface.border, paddingTop: spacing[2] },
  confirmButtons: { flexDirection: 'row', gap: spacing[2] },
  more: { alignSelf: 'center' },
});
