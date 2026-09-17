// 여행 기록 피드 — S15P21E201-958 재설계 1단계(구조).
//
// 인계 자료: 바탕화면/가볼래-디자인-재작업-2026-09-14/07-디자인-인계-피드/
//
// 🔴 프로토타입의 픽셀을 베끼지 않는다. 색·간격·반경은 전부 tokens.ts 를 거친다.
// 🔴 폭 임계값도 여기 안 적는다 — layout/breakpoints.ts 의 isAtLeast 만 쓴다
//    (tools/check-breakpoints.mjs 가 이 파일 밖의 숫자를 잡아낸다).
// 🔴 아이콘은 png 를 쓴다. 이 저장소에 react-native-svg 가 없어서, svg 를 Image 에
//    넣으면 웹에서만 보이고 휴대폰에서는 빈칸이 된다.
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { RegionPicker } from '@/components/RegionPicker';
import { composeEntryFor } from '@/social/composeEntry';
import { PhotoGrid } from '@/components/PhotoGrid';
import { markdownToPlain } from '@/social/markdown';
import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { ReportModal } from '@/components/ReportModal';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { RouteMap } from '@/map/RouteMap';
import { createStory, feedQueryKey, loadFeed, relativeStoryTime, reportStory, setFollowing, VISIBILITY_LABEL, type FeedLoadResult, type FeedScope, type StoryDto, type StoryReportReason, type StoryVisibility } from '@/social/stories';
import { shouldPromptSignIn } from '@/social/signInPrompt';
import { SignInPromptModal } from '@/social/SignInPromptModal';
import { useStoryImages } from '@/social/useStoryImages';

// 열쇠는 src/social/stories.ts 로 옮겼다 — 글쓰기 화면도 같은 것을 써야 해서다
// (S15P21E201-1124). 이름은 그대로 둬서 아래 쓰는 곳들을 건드리지 않는다.
const FEED_KEY = feedQueryKey;

/** 본문 상한 — 글쓰기 화면(compose.tsx)과 같은 값이어야 한다. */
const BODY_MAX = 500;

/** 사진 장수에 따라 칸을 다르게 쓴다 — 한 장은 넓게, 여러 장은 정사각으로 나눈다. */
// S15P21E201-1135 — 사진을 가로로 줄 세우던 것을 장수·방향에 따른 배치로 바꾼다.
// 배치 규칙은 @/social/photoGrid 한 곳에 있고, 작성 미리보기·글 상세도 같은 것을 쓴다.
function StoryImages({ images, compact }: { images: StoryDto['images']; compact: boolean }) {
  const { tx } = useI18n();
  if (!images.length) return null;
  return <PhotoGrid
    photos={images.map((image) => ({ uri: image.url }))}
    compact={compact}
    accessibilityLabel={tx('여행 기록 사진', 'Trip record photo')}
    style={styles.images}
  />;
}

/** 이름 첫 글자를 둥근 칸에 넣는다. 프로필 사진은 StoryDto 계약에 아직 없다. */
function Avatar({ name, compact }: { name: string; compact: boolean }) {
  return <View style={[styles.avatar, compact && styles.avatarCompact]}>
    <Text variant="body" weight="bold" color={color.text.onAction}>{name.slice(0, 1)}</Text>
  </View>;
}

function StoryCard({ story, compact, showUnfollow, unfollowBusy, onUnfollow, onOpen, onOpenAuthor, onReport }: {
  story: StoryDto; compact: boolean; showUnfollow: boolean; unfollowBusy: boolean;
  onUnfollow: () => void; onOpen: () => void; onOpenAuthor: () => void; onReport: () => void;
}) {
  const { tx } = useI18n();
  const place = story.place?.name ?? story.region ?? null;
  // 좌표가 있는 기록만 지도에 찍힌다 — 없는 것을 있다고 말하지 않는다 (S15P21E201-829).
  const mapped = typeof story.place?.lat === 'number' && typeof story.place?.lng === 'number';

  return <View style={[styles.card, compact && styles.cardCompact]}>
    <View style={styles.cardHeader}>
      <Pressable
        accessibilityRole="link"
        accessibilityLabel={tx(`${story.author.displayName} 프로필 보기`, `View ${story.author.displayName}'s profile`)}
        onPress={onOpenAuthor}
        style={styles.authorRow}
      >
        <Avatar name={story.author.displayName} compact={compact} />
        <View style={styles.authorText}>
          <Text variant="body" weight="bold" color={color.text.heading}>{story.author.displayName}</Text>
          <Text variant="caption" color={color.text.muted}>{relativeStoryTime(story.createdAt, tx)}{place ? ` · ${place}` : ''}</Text>
        </View>
      </Pressable>
      {story.mine && story.visibility !== 'PUBLIC'
        ? <View style={styles.visibilityBadge}><Text variant="caption" weight="bold" color={color.text.muted}>{tx(...VISIBILITY_LABEL[story.visibility])}</Text></View>
        : null}
      {showUnfollow
        ? <Pressable
            accessibilityRole="button"
            accessibilityLabel={tx(`${story.author.displayName} 언팔로우`, `Unfollow ${story.author.displayName}`)}
            accessibilityState={{ busy: unfollowBusy }}
            disabled={unfollowBusy}
            onPress={onUnfollow}
            style={[styles.pillButton, unfollowBusy && styles.busy]}
          ><Text variant="caption" weight="bold" color={color.text.body}>{unfollowBusy ? tx('처리 중', 'Working') : tx('팔로잉', 'Following')}</Text></Pressable>
        : null}
      {!story.mine
        ? <Pressable accessibilityRole="button" accessibilityLabel={tx('신고하기', 'Report')} onPress={onReport} style={styles.menuButton}>
            <Text variant="body" weight="bold" color={color.text.muted}>⋯</Text>
          </Pressable>
        : null}
    </View>

    {/* 🔴 S15P21E201-1136 — 목록에서는 효과를 벗긴다. 제목을 크게 그리면 카드 높이가
        글마다 들쭉날쭉해져서 목록이 읽기 어려워진다. 온전한 모양은 상세에서만 보여준다. */}
    <Text color={color.text.body} style={styles.body} numberOfLines={compact ? 3 : 6}>{markdownToPlain(story.body)}</Text>
    <StoryImages images={story.images} compact={compact} />

    <View style={styles.cardFooter}>
      <Pressable accessibilityRole="link" accessibilityLabel={tx('기록 자세히 보기', 'View record details')} onPress={onOpen} style={styles.detailLink}>
        <Text variant="body" weight="bold" color={color.brand.navy}>{compact ? tx('자세히 →', 'Details →') : tx('기록 자세히 보기 →', 'View this record →')}</Text>
      </Pressable>
      {mapped
        ? <View style={styles.mappedRow}>
            <Image source={require('../../assets/icons/common/pin.png')} resizeMode="contain" accessibilityLabel={tx('장소 표시', 'Place marker')} style={styles.pin} />
            <Text variant="caption" color={color.text.muted}>{tx('지도에 표시됨', 'Shown on the map')}</Text>
          </View>
        : null}
    </View>
  </View>;
}

/**
 * 이 피드에 나온 장소 — 🔴 집계 API 가 없다.
 *
 * <p>지금 불러온 items 의 place 를 화면에서 묶어 센다. 그래서 이 숫자는 "전체에서
 * 몇 번" 이 아니라 "지금 보이는 목록에서 몇 번" 이다. 집계 API 가 생기면 이 함수를
 * 지우고 그것으로 바꾼다 — 지어낸 숫자를 보여주지 않으려고 세는 범위를 좁게 잡았다.
 */
function placesInFeed(items: StoryDto[]) {
  const counted = new Map<string, { name: string; count: number }>();
  for (const story of items) {
    const name = story.place?.name ?? story.region;
    if (!name) continue;
    const found = counted.get(name);
    if (found) found.count += 1;
    else counted.set(name, { name, count: 1 });
  }
  return [...counted.values()].sort((a, b) => b.count - a.count).slice(0, 8);
}

/**
 * 피드 맨 위에서 바로 쓰는 글쓰기 카드 — 데스크톱 폭(1024+) 전용.
 *
 * <p>폰은 기존대로 「기록」 버튼으로 /feed/compose 에 간다. 좁은 화면에서 본문·사진·
 * 공개범위를 한 카드에 넣으면 정작 보러 온 목록이 한참 밀려 내려간다.
 *
 * <p>🔴 S15P21E201-1142 — 전에는 {@code wide}(1440+)에서만 켰다. 그런데 헤더의 「기록」
 * 버튼은 {@code compact}(1024 미만)에서만 나오므로 **1024~1439 구간에는 글 쓸 입구가
 * 하나도 없었다.** 흔한 데스크톱 창 폭이 통째로 비어 있었던 것이다.
 *
 * <p>목록이 비었을 때만 빈 화면 안내에 버튼이 있어서, **글이 하나라도 쌓이면 입구가
 * 사라졌다** — 처음 써 본 사람은 되는데 쓰고 나면 다시 못 쓴다. 그래서 더 안 보였다.
 *
 * <p>🔴 사진 처리는 {@link useStoryImages} 한 곳에서 온다 — 글쓰기 화면과 같은 코드다.
 * 줄이기(1600px)·EXIF 제거·3MB 판정 규칙이 두 벌이 되지 않게 하려고 뺐다.
 *
 * <p>🔴 인계 문서의 「장소」·「여행 연결」 버튼은 넣지 않았다. createStory 는
 * placeId·tripId 를 받지만 <b>고르는 화면이 저장소 어디에도 없다</b> — 기존 글쓰기
 * 화면도 자유 입력 「지역」만 받는다. 눌러도 아무 일이 없는 버튼을 두는 대신 그
 * 자유 입력을 같은 자리에 둔다. 선택기가 생기면 그때 바꾼다.
 */
function InlineCompose({ onPosted }: { onPosted: () => void }) {
  const { tx } = useI18n();
  const { accessToken } = useAuth();
  const [body, setBody] = useState('');
  const [region, setRegion] = useState('');
  const [regionOpen, setRegionOpen] = useState(false);
  // 우리 DB 장소를 고르면 채워진다. 손으로 고쳐 쓰면 다시 비워진다 (RegionPicker).
  const [placeId, setPlaceId] = useState<string | undefined>(undefined);
  const [visibility, setVisibility] = useState<StoryVisibility>('PUBLIC');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { images, addImage, retryImage, removeImage, anyUploading, uploadedUrls, canAddMore, clearImages } = useStoryImages(accessToken, tx);

  const bodyValid = body.trim().length >= 1 && body.trim().length <= BODY_MAX;
  const canPost = bodyValid && !anyUploading && !submitting;

  const post = async () => {
    if (!canPost) return;
    setSubmitting(true);
    setError(null);
    const outcome = await createStory({
      body: body.trim(),
      imageUrls: uploadedUrls,
      region: region.trim() || undefined,
      // 🔴 우리 DB 장소를 골랐을 때만 실려 간다. 카카오 검색 결과에는 placeId 가 아예
      // 없으므로(regionSearch.ts) 저장하면 안 되는 것이 여기로 흘러들 수 없다.
      placeId,
      visibility,
      accessToken,
    });
    setSubmitting(false);
    if (outcome.state !== 'success') { setError(outcome.message); return; }
    setBody(''); setRegion(''); setPlaceId(undefined); setRegionOpen(false); clearImages();
    onPosted();
  };

  // 공개 범위는 셋뿐이라 눌러서 돌린다 — 넓은 화면 도구 행에 드롭다운을 하나 더
  // 띄우는 것보다 조용하다. 지금 값이 버튼에 그대로 적혀 있어 무엇인지 보인다.
  const cycleVisibility = () => {
    const order: StoryVisibility[] = ['PUBLIC', 'FOLLOWERS', 'PRIVATE'];
    setVisibility(order[(order.indexOf(visibility) + 1) % order.length]);
  };

  return <View style={styles.compose}>
    <TextInput
      accessibilityLabel={tx('기록 내용', 'Record body')}
      style={styles.composeInput}
      placeholder={tx('이번 부산 여행, 어땠어요?', 'How was your trip to Busan?')}
      placeholderTextColor={color.text.muted}
      value={body}
      onChangeText={setBody}
      maxLength={BODY_MAX}
      multiline
    />

    {/* S15P21E201-1135 — 글쓰기가 두 곳(여기와 app/feed/compose.tsx)에 있는데
        둘이 다른 모양이면 같은 앱에서 사진이 두 가지로 보인다. 같은 부품을 쓴다. */}
    {images.length ? <PhotoGrid
      photos={images.map((image) => ({ uri: image.localUri }))}
      compact
      accessibilityLabel={tx('고른 사진', 'Selected photo')}
      style={styles.composeImages}
      renderOverlay={(index) => {
        const image = images[index];
        if (!image) return null;
        return <>
          {image.uploading ? <View style={styles.composeImageOverlay}><ActivityIndicator color={color.text.onAction} /></View> : null}
          {image.error ? <Pressable accessibilityRole="button" accessibilityLabel={tx('업로드 다시 시도', 'Retry upload')} onPress={() => retryImage(index)} style={styles.composeImageOverlay}>
            <Text variant="caption" weight="bold" color={color.text.onAction}>{tx('다시 시도', 'Retry')}</Text>
          </Pressable> : null}
          <Pressable accessibilityRole="button" accessibilityLabel={tx('사진 삭제', 'Remove photo')} onPress={() => removeImage(index)} style={styles.composeImageRemove}>
            <Text weight="bold" color={color.text.onAction}>×</Text>
          </Pressable>
        </>;
      }}
    /> : null}

    {/* 사진이 상한을 넘었을 때만 그 이유가 뜬다 — 미리 겁주지 않는다 (S15P21E201-955). */}
    {images.map((image, index) => image.error
      ? <Text key={`image-error-${index}`} variant="caption" color={color.state.danger}>{image.error}</Text>
      : null)}

    {/* S15P21E201-1145 — 자유 입력 한 칸이던 것을 검색으로 바꾼다. 우리 DB 장소를
        고르면 placeId 가 따라와 글이 그 장소에 달린다. 손으로 고쳐 쓰는 길은 그대로다. */}
    {regionOpen ? <RegionPicker
      region={region}
      onChangeRegion={setRegion}
      placeId={placeId}
      onChangePlaceId={setPlaceId}
      accessToken={accessToken}
    /> : null}

    {/* S15P21E201-1146 — 전체 화면 글쓰기에는 있던 안내가 여기엔 없었다.
        같은 앱에서 같은 일을 하는데 한쪽만 말해 주면 안 된다. */}
    <Text variant="caption" color={color.text.muted}>{tx('사진의 위치 정보는 지워져요. 장소를 연결하면 그 장소 소개에도 사진이 함께 보일 수 있어요.', 'Location data is removed from photos. If you link a place, your photo may also appear on that place.')}</Text>

    <View style={styles.composeTools}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('사진 추가', 'Add photo')} disabled={!canAddMore} onPress={() => void addImage()} style={[styles.toolButton, !canAddMore && styles.busy]}>
        <Image source={require('../../assets/icons/common/camera.png')} resizeMode="contain" accessibilityLabel="" style={styles.toolIcon} />
        <Text variant="body" color={color.text.body}>{tx(`사진 ${images.length}/3`, `Photos ${images.length}/3`)}</Text>
      </Pressable>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('지역 적기', 'Add a region')} accessibilityState={{ expanded: regionOpen }} onPress={() => setRegionOpen((open) => !open)} style={[styles.toolButton, regionOpen && styles.toolButtonOn]}>
        <Image source={require('../../assets/icons/common/pin.png')} resizeMode="contain" accessibilityLabel="" style={styles.toolIcon} />
        <Text variant="body" color={color.text.body}>{region.trim() || tx('지역', 'Region')}</Text>
      </Pressable>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('공개 범위 바꾸기', 'Change visibility')} onPress={cycleVisibility} style={styles.toolButton}>
        <Text variant="caption" weight="bold" color={color.text.muted}>{tx(...VISIBILITY_LABEL[visibility])}</Text>
      </Pressable>
      <View style={styles.grow} />
      <Button label={submitting ? tx('올리는 중…', 'Posting…') : tx('게시', 'Post')} disabled={!canPost} onPress={() => void post()} containerStyle={styles.composePost} />
    </View>

    {error ? <Text variant="caption" color={color.state.danger}>{error}</Text> : null}
  </View>;
}

/**
 * 추억 지도 — 좌표가 붙은 기록만 지도에 찍는다.
 *
 * <p>기존 {@link RouteMap} 을 그대로 쓴다. 마커를 누르면 그 기록으로 간다 —
 * 여행 준비 화면의 추억 지도가 이미 그렇게 동작한다(S15P21E201-906 범위 밖 항목).
 *
 * <p>🔴 좌표가 하나도 없으면 카드를 아예 안 그린다. 빈 지도에 「0개」를 적어 두는
 * 것은 자리만 차지하고 아무것도 알려주지 않는다.
 *
 * <p>🔴 지도는 웹에서만 그려진다. 휴대폰 앱에는 지도가 없고, 이 카드는 넓은 화면의
 * 보조 칸에만 있으므로 폰에서는 애초에 안 보인다.
 *
 * <p>🔴 인계 문서의 「크게 보기」 링크는 넣지 않았다. 갈 곳이 없다 — 추억 지도는
 * 여행별로만 있고(app/(trip)/[id]), 피드 전체를 담는 지도 화면은 저장소에 없다.
 */
function MemoryMap({ items, onOpenStory }: { items: StoryDto[]; onOpenStory: (id: string) => void }) {
  const { tx } = useI18n();
  const stops = items
    .filter((story) => typeof story.place?.lat === 'number' && typeof story.place?.lng === 'number')
    .map((story, index) => ({
      id: story.id,
      number: index + 1,
      name: story.place?.name ?? story.region ?? tx('기록', 'Record'),
      latitude: story.place?.lat as number,
      longitude: story.place?.lng as number,
    }));

  if (!stops.length) return null;

  return <View>
    <Eyebrow>{tx('추억 지도', 'Memory map')}</Eyebrow>
    <View style={styles.mapCard}>
      <RouteMap stops={stops} selectedId={stops[0].id} onSelect={onOpenStory} height={200} />
      <View style={styles.mapFooter}>
        <Text variant="body">{tx(`좌표 있는 기록 ${stops.length}개`, `${stops.length} records with coordinates`)}</Text>
      </View>
    </View>
  </View>;
}

function EmptyState({ scope, signedIn, compact, onSeeAll, onWrite }: {
  scope: FeedScope; signedIn: boolean; compact: boolean; onSeeAll: () => void; onWrite: () => void;
}) {
  const { tx } = useI18n();
  const following = scope === 'FOLLOWING';
  return <View style={[styles.emptyCard, compact && styles.emptyCardCompact]}>
    <Image
      source={require('../../assets/mascot/dongbaek-idle.png')}
      resizeMode="contain"
      accessibilityLabel={tx('동백 마스코트', 'Dongbaek mascot')}
      style={[styles.mascot, compact && styles.mascotCompact]}
    />
    <View style={styles.emptyText}>
      <Text variant={compact ? 'title' : 'display'} weight="bold">
        {following ? tx('아직 팔로우한 사람의 기록이 없어요', 'No records from people you follow yet')
          : tx('부산 여행 기록을 모으고 있어요', 'Collecting Busan travel stories')}
      </Text>
      <Text color={color.text.body} style={styles.emptyDescription}>
        {following ? tx('전체 피드에서 마음에 드는 여행자를 팔로우하면 여기에 모여요.', 'Follow travellers you like in the all feed and their stories gather here.')
          : tx('아직 올라온 기록이 없어요. 여행을 다녀왔다면 첫 이야기를 남겨 보세요 — 사진 3장까지.', 'No records yet. If you have travelled, share the first story — up to 3 photos.')}
      </Text>
      <View style={styles.emptyActions}>
        {following
          ? <Button label={tx('전체 보기', 'See all')} onPress={onSeeAll} containerStyle={styles.emptyPrimary} />
          : <Button label={signedIn ? tx('기록 남기기', 'Write a record') : tx('로그인', 'Sign in')} onPress={onWrite} containerStyle={styles.emptyPrimary} />}
      </View>
    </View>
  </View>;
}

export default function Feed() {
  const router = useRouter();
  const { accessToken } = useAuth();
  const { tx } = useI18n();
  const { width } = useLayout();
  const queryClient = useQueryClient();

  // 🔴 1024 이상에서만 보조 칸을 붙인다. 저장소 반응형 표가 「1024~ 사이드바 + 본문」
  // 이라고 정해 두었다(layout/breakpoints.ts). 인계 문서는 'md' 라고 적었지만 이
  // 저장소의 md 는 600 이라, 거기서 320 보조 칸을 붙이면 본문이 짓눌린다.
  const wide = isAtLeast(width, 'lg');
  const compact = !isAtLeast(width, 'md');

  const [scope, setScope] = useState<FeedScope>('ALL');
  const [loadingMore, setLoadingMore] = useState(false);
  const [unfollowingId, setUnfollowingId] = useState<string | null>(null);
  const [reportingStoryId, setReportingStoryId] = useState<string | null>(null);
  // S15P21E201-1012 — 로그인 유도. 🔴 주소를 나누지 않고 이 화면의 상태로만 다룬다.
  // 주소를 가르면 뒤로 가기·공유 링크·검색이 전부 갈라진다.
  const [promptingSignIn, setPromptingSignIn] = useState(false);
  const [lastPromptedAt, setLastPromptedAt] = useState(0);

  const signedIn = Boolean(accessToken);
  // 🔴 S15P21E201-1142 — 글쓰기 입구는 여기서 고르지 않고 composeEntry 한 곳에서 받는다.
  // 조건을 화면 두 곳에 나눠 적었더니 그 사이 폭(600~1023)에 입구가 하나도 없었다.
  const composeEntry = composeEntryFor(width, signedIn);
  const key = FEED_KEY(scope, signedIn);

  // 화면 밖 보관소에서 읽는다 — 탭을 오가도 다시 안 부른다 (S15P21E201-957).
  const feedQuery = useQuery({
    queryKey: key,
    queryFn: () => loadFeed({ scope, accessToken }),
  });
  const result: FeedLoadResult = feedQuery.data ?? { state: 'success', items: [], nextCursor: null };
  const loading = feedQuery.isPending;
  const items = result.state === 'success' ? result.items : [];
  const places = placesInFeed(items);

  /** 목록만 바꿔 치운다 — 서버에 다시 묻지 않고 화면을 맞춘다. */
  const replaceItems = (next: (current: StoryDto[]) => StoryDto[]) => {
    queryClient.setQueryData<FeedLoadResult>(key, (current) =>
      current && current.state === 'success' ? { ...current, items: next(current.items) } : current);
  };

  const loadMore = async () => {
    if (result.state !== 'success' || !result.nextCursor || loadingMore) return;
    setLoadingMore(true);
    const next = await loadFeed({ scope, cursor: result.nextCursor, accessToken });
    setLoadingMore(false);
    if (next.state !== 'success') return;
    const seenCount = result.items.length + next.items.length;
    queryClient.setQueryData<FeedLoadResult>(key, (current) =>
      current && current.state === 'success'
        ? { state: 'success', items: [...current.items, ...next.items], nextCursor: next.nextCursor }
        : current);
    // 더 보기까지 눌렀다는 것은 이 제품이 뭔지 이미 봤다는 뜻이다. 그때 권한다 — 막지는 않는다.
    if (shouldPromptSignIn({ signedIn, seenCount, lastPromptedAt })) {
      setLastPromptedAt(seenCount);
      setPromptingSignIn(true);
    }
  };

  const unfollow = async (story: StoryDto) => {
    setUnfollowingId(story.author.id);
    const outcome = await setFollowing(story.author.id, false, accessToken);
    setUnfollowingId(null);
    if (outcome.state === 'success') replaceItems((current) => current.filter((item) => item.author.id !== story.author.id));
  };

  const submitReport = async (reason: StoryReportReason, detail: string | undefined) => {
    if (!reportingStoryId) return false;
    const outcome = await reportStory(reportingStoryId, reason, detail, accessToken);
    if (outcome.state !== 'success') return false;
    // 신고 즉시 서버가 그 글을 검토 대기로 옮겨 비노출한다 — 화면에서도 새로고침을
    // 기다리지 않고 바로 지운다("신고까지 화면을 벗어나지 않고 끝난다" 완료 기준).
    replaceItems((current) => current.filter((item) => item.id !== reportingStoryId));
    return true;
  };

  const scopeButton = (target: FeedScope, label: string) => {
    const selected = scope === target;
    const disabled = target === 'FOLLOWING' && !signedIn;
    return <Pressable
      accessibilityRole="tab"
      accessibilityState={{ selected, disabled }}
      disabled={disabled}
      onPress={() => setScope(target)}
      style={[compact ? styles.scopeChip : styles.scopeSegment, selected && styles.scopeSelected, disabled && styles.scopeDisabled]}
    >
      <Text variant={compact ? 'caption' : 'body'} weight="bold" color={selected ? color.text.onAction : color.text.body}>{label}</Text>
    </Pressable>;
  };

  const header = <View style={styles.headerRow}>
    <View>
      <Eyebrow>{tx('여행 기록 피드', 'Travel story feed')}</Eyebrow>
      <Text variant="display" weight="bold" style={styles.headerTitle}>{tx('여행 이야기', 'Travel stories')}</Text>
    </View>
    <View style={styles.headerActions}>
      <View accessibilityRole="tablist" style={compact ? styles.scopeChips : styles.scopeSegments}>
        {scopeButton('ALL', tx('전체', 'All'))}
        {scopeButton('FOLLOWING', tx('팔로잉', 'Following'))}
      </View>
      {composeEntry === 'headerButton'
        ? <Button label={tx('기록', 'Write')} onPress={() => router.push('/feed/compose')} compact />
        : null}
    </View>
  </View>;

  const feedColumn = <View style={styles.feedColumn}>
    {header}

    {!signedIn
      ? <View style={styles.loginNotice}>
          <Text variant="caption" color={color.text.body}>{tx('로그인하면 기록을 남기고 팔로잉 피드를 볼 수 있어요.', 'Sign in to write records and see your following feed.')}</Text>
          <Pressable accessibilityRole="link" onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/feed' } })}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('로그인 →', 'Sign in →')}</Text></Pressable>
        </View>
      : null}

    {/* 🔴 S15P21E201-1142 — 데스크톱 폭(1024+)이면 맨 위에 둔다. 전에는 1440+ 였고,
        헤더 버튼은 1024 미만에서만 나와서 그 사이 폭에 입구가 없었다.

        경계값을 새로 만들지 않고 이미 있는 compact 를 쓴다 — 폭 숫자를 화면 코드에
        적지 않는다는 이 파일의 규칙 그대로다(layout/breakpoints.ts 만 쓴다).

        올리고 나면 이 범위를 낡은 것으로 표시해 다시 불러온다 — 방금 쓴 글이 목록에
        바로 보이게. */}
    {composeEntry === 'inline'
      ? <InlineCompose onPosted={() => void queryClient.invalidateQueries({ queryKey: key })} />
      : null}

    {loading
      ? <View accessibilityLiveRegion="polite" style={styles.stateCard}><ActivityIndicator color={color.brand.orange} /><Text variant="title" weight="bold">{tx('피드를 불러오고 있어요', 'Loading the feed')}</Text></View>
      : null}

    {!loading && result.state !== 'success'
      ? <View style={styles.stateCard}>
          <Text variant="title" weight="bold">{result.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : result.state === 'unavailable' ? tx('피드 API를 기다리고 있어요', 'Waiting for the feed API') : tx('피드를 불러오지 못했어요', 'Could not load the feed')}</Text>
          <Text color={color.text.body}>{result.message}</Text>
          <Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void feedQuery.refetch()} />
        </View>
      : null}

    {!loading && result.state === 'success' && !items.length
      ? <EmptyState
          scope={scope}
          signedIn={signedIn}
          compact={compact}
          onSeeAll={() => setScope('ALL')}
          onWrite={() => router.push(signedIn ? '/feed/compose' : { pathname: '/sign-in', params: { returnTo: '/feed/compose' } })}
        />
      : null}

    {/* 계정이 필요한 행동(신고)을 누르면 곧바로 로그인 화면으로 보내지 않고 같은 창을 띄운다 —
        왜 필요한지 모른 채 쫓겨난 것처럼 느끼게 하지 않는다 (S15P21E201-1012). */}
    {!loading && result.state === 'success' && items.length
      ? <View style={styles.list}>{items.map((story) => <StoryCard
          key={story.id}
          story={story}
          compact={compact}
          showUnfollow={scope === 'FOLLOWING'}
          unfollowBusy={unfollowingId === story.author.id}
          onUnfollow={() => void unfollow(story)}
          onOpen={() => router.push(`/feed/${story.id}`)}
          onOpenAuthor={() => router.push(`/user/${story.author.id}`)}
          onReport={() => signedIn ? setReportingStoryId(story.id) : setPromptingSignIn(true)}
        />)}</View>
      : null}

    {!loading && result.state === 'success' && result.nextCursor
      ? <Button label={loadingMore ? tx('불러오는 중…', 'Loading…') : tx('더 보기', 'Load more')} variant="ghost" disabled={loadingMore} onPress={() => void loadMore()} containerStyle={styles.loadMore} />
      : null}
  </View>;

  const aside = <View style={styles.aside}>
    <MemoryMap items={items} onOpenStory={(id) => router.push(`/feed/${id}`)} />

    <Eyebrow>{tx('이 피드에 나온 장소', 'Places in this feed')}</Eyebrow>
    <View style={styles.asideCard}>
      {places.length
        ? places.map((place, index) => <View key={place.name} style={[styles.asideRow, index > 0 && styles.asideRowDivided]}>
            <Image source={require('../../assets/icons/common/pin.png')} resizeMode="contain" accessibilityLabel="" style={styles.pin} />
            <Text variant="body" style={styles.grow}>{place.name}</Text>
            <Text variant="caption" color={color.text.muted}>{place.count}</Text>
          </View>)
        : <View style={styles.asideRow}><Text variant="caption" color={color.text.muted}>{tx('아직 장소가 붙은 기록이 없어요.', 'No records with a place yet.')}</Text></View>}
    </View>
  </View>;

  return <View style={styles.shell}>
    {/* 🔴 wide 를 넘겨야 최대 폭이 720 → 1180 으로 열린다. 안 넘기면 2단이
        좁은 칸 안에서 또 나뉘어 양쪽 다 짓눌린다 — Screen 주석이 경고하는
        바로 그 고장이고, tsc 는 잡지 못한다(폭이 좁은 것은 문법 오류가 아니다). */}
    <Screen scroll withTabBar wide={wide}>
      {wide
        ? <View style={styles.wideGrid}>{feedColumn}{aside}</View>
        : feedColumn}
      <ReportModal visible={reportingStoryId !== null} onClose={() => setReportingStoryId(null)} onSubmit={submitReport} />
      <SignInPromptModal
        visible={promptingSignIn}
        onClose={() => setPromptingSignIn(false)}
        onSignIn={() => { setPromptingSignIn(false); router.push({ pathname: '/sign-in', params: { returnTo: '/feed' } }); }}
      />
    </Screen>
    <TabBar active="feed" />
  </View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.brand.ivory },

  // 넓은 화면: 본문 + 보조 칸. 보조 칸은 폭 고정, 본문이 남는 만큼 가져간다.
  wideGrid: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[6], marginTop: spacing[6] },
  feedColumn: { flex: 1, minWidth: 0 },
  aside: { width: 320, gap: spacing[2] },
  asideCard: { borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.md, backgroundColor: color.surface.card },
  mapCard: { borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card, overflow: 'hidden' },
  mapFooter: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], paddingVertical: spacing[3], paddingHorizontal: spacing[4] },
  asideRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], paddingVertical: spacing[3], paddingHorizontal: spacing[4] },
  asideRowDivided: { borderTopWidth: 1, borderTopColor: color.surface.border },

  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: spacing[3], flexWrap: 'wrap' },
  headerTitle: { marginTop: spacing[1] },
  headerActions: {
    flexDirection: 'row', alignItems: 'center', gap: spacing[2],
    // 🔴 넘치면 **내려간다** (S15P21E201-1161). 이 줄이 없어서 오른쪽으로 삐져나갔다.
    //
    // 이 안에는 「전체」·「팔로잉」 칩과 「기록」 버튼이 선다. 그런데 「기록」은
    // composeEntryFor() 상 **폰 폭에서만** 뜬다 — 자리가 제일 좁을 때만 등장하는 버튼이다.
    // flex 자식은 기본이 「안 줄어듦」이라, 셋을 합친 폭이 줄을 넘으면 줄바꿈도 축소도 없이
    // 그냥 화면 밖으로 나간다. 바깥 headerRow 의 flexWrap 은 여기 안쪽까지 안 미친다.
    //
    // 안 넘칠 때는 아무것도 안 바뀐다.
    flexWrap: 'wrap', justifyContent: 'flex-end',
  },
  // 🔴 writeButton 을 지웠다 (S15P21E201-1161). { width:'auto', paddingHorizontal } 을
  // containerStyle 로 넘겼는데 그건 **껍데기**의 폭일 뿐이라, 안쪽 버튼은 여전히
  // width:'100%' 를 원했다. 지금은 버튼이 직접 자기 폭을 정한다 — `compact`.


  // 넓은 화면은 붙은 세그먼트, 폰은 떨어진 칩.
  scopeSegments: { flexDirection: 'row', gap: spacing[1], padding: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
  scopeSegment: { minHeight: 36, paddingHorizontal: spacing[4], borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' },
  scopeChips: { flexDirection: 'row', gap: spacing[2] },
  scopeChip: { minHeight: 40, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, alignItems: 'center', justifyContent: 'center' },
  scopeSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  scopeDisabled: { opacity: 0.5 },

  loginNotice: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], alignItems: 'center', marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft },

  stateCard: { minHeight: 240, marginTop: spacing[6], padding: spacing[6], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center', gap: spacing[3] },

  // 빈 상태 — 넓은 화면은 마스코트를 옆에, 폰은 위에.
  emptyCard: { flexDirection: 'row', alignItems: 'center', gap: spacing[6], marginTop: spacing[6], padding: spacing[6], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card },
  emptyCardCompact: { flexDirection: 'column', alignItems: 'center', gap: spacing[4], padding: spacing[4] },
  mascot: { width: 120, height: 120 },
  mascotCompact: { width: 96, height: 96 },
  emptyText: { flex: 1, gap: spacing[2], minWidth: 0 },
  emptyDescription: { maxWidth: 420 },
  emptyActions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2], flexWrap: 'wrap' },
  emptyPrimary: { width: 'auto', minWidth: 180, paddingHorizontal: spacing[4], backgroundColor: color.brand.navy },

  list: { gap: spacing[3], marginTop: spacing[4] },

  card: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  cardCompact: { padding: spacing[4] },
  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  authorRow: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: spacing[3], minWidth: 0 },
  authorText: { flex: 1, minWidth: 0 },
  avatar: { width: 40, height: 40, borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' },
  avatarCompact: { width: 36, height: 36 },

  visibilityBadge: { minHeight: 28, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  pillButton: { minWidth: 72, minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  busy: { opacity: 0.6 },
  menuButton: { minWidth: 32, minHeight: 32, alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm },

  body: { lineHeight: 22 },

  // 배치는 PhotoGrid 가 정한다 (S15P21E201-1135) — 여기서는 위아래 간격만 준다.
  images: { marginTop: spacing[2] },

  cardFooter: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], flexWrap: 'wrap' },
  detailLink: { minHeight: 44, justifyContent: 'center' },
  mappedRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[1] },
  pin: { width: 14, height: 14, tintColor: color.text.muted },
  grow: { flex: 1, minWidth: 0 },

  loadMore: { marginTop: spacing[4] },

  // 인라인 글쓰기 — 넓은 화면에서 피드 맨 위에 놓인다.
  compose: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  composeInput: { minHeight: 64, fontSize: 18, lineHeight: 24, color: color.text.heading },
  composeImages: { flexDirection: 'row', gap: spacing[2] },
  composeImageOverlay: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(11,29,58,0.45)' },
  composeImageRemove: { position: 'absolute', top: spacing[1], right: spacing[1], width: 24, height: 24, borderRadius: radius.full, backgroundColor: 'rgba(11,29,58,0.6)', alignItems: 'center', justifyContent: 'center' },
  composeTools: { flexDirection: 'row', alignItems: 'center', gap: spacing[1], paddingTop: spacing[3], borderTopWidth: 1, borderTopColor: color.surface.border, flexWrap: 'wrap' },
  toolButton: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 36, paddingHorizontal: spacing[3], borderRadius: radius.sm },
  toolButtonOn: { backgroundColor: color.surface.soft },
  toolIcon: { width: 16, height: 16, tintColor: color.brand.navy },
  // 🔴 S15P21E201-1144 — 배경색을 여기 칠하지 않는다. Button 의 variant='primary' 가
  // 이미 같은 남색을 «안쪽» 에 칠하고, 안쪽에만 모서리가 있다. 바깥 껍데기에 같은 색을
  // 덧칠하면 둥근 버튼 뒤에 네모난 판이 깔려 각져 보인다 — 두 색이 같아 네모만 보였다.
  composePost: { width: 'auto', minWidth: 96, paddingHorizontal: spacing[4] },
});
