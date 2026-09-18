// 기록 상세 — 피드 카드를 누르면 오는 화면 (S15P21E201-228).
import { useCallback, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, View } from 'react-native';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { ReportModal } from '@/components/ReportModal';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { deleteStory, getCachedStory, getStory, relativeStoryTime, reportStory, VISIBILITY_LABEL, type StoryDto, type StoryReportReason } from '@/social/stories';

type State = { status: 'loading'; cached: StoryDto | null } | { status: 'loaded'; story: StoryDto } | { status: 'not-found' } | { status: 'error'; message: string };

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

          <Text color={color.text.body} style={styles.body}>{story.body}</Text>

          {story.images.length ? (
            <View style={styles.images}>
              {story.images.map((image) => (
                <Image key={image.url} source={{ uri: image.url }} resizeMode="cover" accessibilityLabel={tx('여행 기록 사진', 'Trip record photo')} style={styles.image} />
              ))}
            </View>
          ) : null}

          {story.place ? (
            <Pressable accessibilityRole="button" accessibilityLabel={tx(`${story.place.name} 상세 보기`, `View details for ${story.place.name}`)} onPress={() => router.push(`/place/${story.place!.id}`)} style={({ pressed }) => [styles.placeCard, pressed && styles.pressed]}>
              <Text variant="caption" weight="bold" color={color.text.accent}>{tx('연결된 장소', 'Linked place')}</Text>
              <Text variant="body" weight="bold">{story.place.name}</Text>
            </Pressable>
          ) : null}

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
              <Pressable accessibilityRole="button" accessibilityLabel={tx('신고하기', 'Report')} onPress={() => setReporting(true)} style={styles.textAction}>
                <Text variant="caption" weight="bold" color={color.text.muted}>{tx('신고', 'Report')}</Text>
              </Pressable>
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

      <ReportModal visible={reporting} onClose={() => setReporting(false)} onSubmit={submitReport} />
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
  body: { lineHeight: 24 },
  images: { gap: spacing[2] },
  image: { width: '100%', aspectRatio: 4 / 3, borderRadius: radius.md, backgroundColor: color.surface.soft },
  placeCard: { gap: spacing[1], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  actionRow: { flexDirection: 'row', justifyContent: 'space-between' },
  textAction: { minHeight: 44, paddingHorizontal: spacing[2], alignItems: 'center', justifyContent: 'center' },
  confirmRow: { flex: 1, gap: spacing[2] },
  confirmText: { textAlign: 'right' },
  confirmButtons: { flexDirection: 'row', justifyContent: 'flex-end', gap: spacing[2] },
  confirmButton: { width: 'auto', paddingHorizontal: spacing[4] },
  recoveryButton: { marginTop: spacing[2] },
});
