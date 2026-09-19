// 여행 기록 피드 —재설계 1단계(구조).
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Animated, Easing, Image, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import Svg, { Path, Rect } from 'react-native-svg';
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
import { TabBar, TAB_BAR_HEIGHT, tabBarBottomMargin } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, gutter, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { RouteMap } from '@/map/RouteMap';
import { createStory, feedQueryKey, loadFeed, loadSavedStoryIds, relativeStoryTime, reportStory, setFollowing, setStoryReaction, setStorySaved, VISIBILITY_LABEL, type FeedLoadResult, type FeedScope, type StoryDto, type StoryReportReason, type StoryVisibility } from '@/social/stories';
import { shouldPromptSignIn } from '@/social/signInPrompt';
import { applyReaction, nextReaction, StoryReactionRow, storyReactionStyles, storyReactionTouchSlop } from '@/social/StoryReactionRow';
import { SignInPromptModal } from '@/social/SignInPromptModal';
import { useStoryImages } from '@/social/useStoryImages';
import { useStoryVideo } from '@/social/useStoryVideo';

// 열쇠는 src/social/stories.ts 로 옮겼다 — 글쓰기 화면도 같은 것을 써야 해서다
// 이름은 그대로 둬서 아래 쓰는 곳들을 건드리지 않는다.
const FEED_KEY = feedQueryKey;

/** 본문 상한 — 글쓰기 화면(compose.tsx)과 같은 값이어야 한다. */
const BODY_MAX = 500;

/** 사진 장수에 따라 칸을 다르게 쓴다 — 한 장은 넓게, 여러 장은 정사각으로 나눈다. */
// — 사진을 가로로 줄 세우던 것을 장수·방향에 따른 배치로 바꾼다.
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

/** 커버 — 카드 맨 위의 사진 자리 */
function StoryCover({ story, compact, onOpen }: { story: StoryDto; compact: boolean; onOpen: () => void }) {
  const { tx } = useI18n();
  const photos = story.images ?? [];
  const dots = Math.min(photos.length, 5);

  const inner = photos.length
    ? <>
        <Image source={{ uri: photos[0].url }} resizeMode="cover" style={styles.coverImage} accessibilityIgnoresInvertColors />
        {dots > 1
          ? <View style={styles.dots}>{Array.from({ length: dots }).map((_, index) => (
              <View key={index} style={[styles.dot, index === 0 && styles.dotFirst]} />
            ))}</View>
          : null}
      </>
    : <View style={styles.coverEmpty}>
        <Text variant={compact ? 'display' : 'title'} weight="bold" color={color.text.heading} numberOfLines={5} style={styles.coverEmptyText}>
          {markdownToPlain(story.body)}
        </Text>
      </View>;

  return (
    <Pressable
      accessibilityRole="link"
      accessibilityLabel={tx('기록 자세히 보기', 'View record details')}
      onPress={onOpen}
      style={[styles.cover, compact ? styles.coverPhone : styles.coverWide]}
    >
      {inner}
    </Pressable>
  );
}

function StoryCard({ story, compact, showUnfollow, unfollowBusy, saved, savingStar, reacting, onUnfollow, onOpen, onOpenAuthor, onReport, onToggleSave, onReact }: {
  story: StoryDto; compact: boolean; showUnfollow: boolean; unfollowBusy: boolean;
  /** 내가 저장한 기록인가 —. StoryDto엔 없는 칸이라 화면이 따로 들고 다닌다. */
  saved: boolean;
  savingStar: boolean;
  /** 좋아요·싫어요 버튼이 서버 응답을 기다리는 중인가 — 연타 방지. */
  reacting: boolean;
  onUnfollow: () => void; onOpen: () => void; onOpenAuthor: () => void; onReport: () => void;
  onToggleSave: () => void;
  onReact: (reaction: 'LIKE' | 'DISLIKE') => void;
}) {
  const { tx } = useI18n();
  const hasPhoto = (story.images?.length ?? 0) > 0;

  // 제목은 장소 이름이다. 장소가 없으면 「OO의 기록」 — 비워 두지 않는다(시안 「자주 틀리는 것」 5번).
  const title = story.place?.name ?? tx(`${story.author.displayName}의 기록`, `${story.author.displayName}'s record`);

  return <View style={[styles.card, compact ? styles.cardCompact : styles.cardInGrid]}>
 {/* 이름과 ⋯ 를 사진 위에 얹지 않는다 (2026-09-18 실기기).
        사진이 밝거나 사진 자체가 다른 화면의 캡처면 어디까지가 이름이고 어디부터
        ⋯ 인지 구분이 안 됐다. 반투명 배경을 깔아도 사진에 흰 면이 많으면 그대로 묻힌다.
        그래서 **사진 위쪽에 자기 줄**을 준다 — 배경 위에 서므로 항상 읽힌다. */}
    <View style={styles.cardHead}>

      {/* 좌상단 작성자 알약 — 사진 위에 얹히므로 배경을 깔아 글자가 읽히게 한다. */}
      <Pressable
        accessibilityRole="link"
        accessibilityLabel={tx(`${story.author.displayName} 프로필 보기`, `View ${story.author.displayName}'s profile`)}
        onPress={onOpenAuthor}
        style={styles.authorPill}
      >
        <View style={styles.authorPillAvatar}><Text variant="caption" weight="bold" color={color.text.onAction}>{story.author.displayName.slice(0, 1)}</Text></View>
        <Text variant="caption" weight="bold" color={color.text.heading} numberOfLines={1}>{story.author.displayName}</Text>
      </Pressable>

      {/* 우상단 — 내 글이면 공개 범위, 남의 글이면 신고. 시안의 하트 자리는 아직 안 쓴다(아래 참고). */}
      <View style={styles.coverActions}>
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
    </View>
    <View style={styles.coverWrap}>
      <StoryCover story={story} compact={compact} onOpen={onOpen} />
    </View>

    <Pressable accessibilityRole="link" accessibilityLabel={tx('기록 자세히 보기', 'View record details')} onPress={onOpen} style={styles.cardBody}>
      <Text variant="body" weight="bold" color={color.text.heading} numberOfLines={1}>{title}</Text>

      {/* 사진이 없는 글은 본문을 커버에 이미 크게 그렸다. 여기서 또 그리면 같은 글이 두 번
          나온다 — 9/16 에 「N곳」이 두 번 나온 것과 같은 종류다. 시안도 "아래 본문 미리보기는
          생략" 이라고 적었다.
      */}
      {hasPhoto
        ? <Text variant="body" color={color.text.body} numberOfLines={2} style={styles.body}>{markdownToPlain(story.body)}</Text>
        : null}

      {/* 메타 한 줄.
 시안은 여기에 「답글 N· 조회 N」을 넣으라고 한다. 그 칸이 서버에 아직 없다 —
          story 표에 parent 칸도, 조회 표도 없다(마이그레이션 전수 확인, 2026-09-17).
          0 을 하드코딩해 그리지 않는다. 모르는 것을 아는 척하는 것이라, 서버가 칸을 주는 날
 자연히 나타나게 둔다의 백엔드 몫). 지금은 시각과 지역만 말한다. */}
      <Text variant="caption" color={color.text.muted}>
        {relativeStoryTime(story.createdAt, tx)}{story.region ? ` · ${story.region}` : ''}
      </Text>
    </Pressable>

    {/* 좋아요·인용·저장 —(반응 카운트, kojh0124 님)·-1221(저장).
        반응 칸은 상세 화면과 같은 부품을 쓴다
    */}
    <StoryReactionRow story={story} reacting={reacting} onReact={onReact}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={saved ? tx('저장 취소', 'Remove from saved') : tx('저장', 'Save')}
        accessibilityState={{ selected: saved, busy: savingStar }}
        disabled={savingStar}
        hitSlop={storyReactionTouchSlop}
        onPress={onToggleSave}
        style={[storyReactionStyles.button, savingStar && styles.busy]}
      >
        <Text variant="caption" weight="bold" color={saved ? color.action.secondary : color.text.muted}>
          {saved ? tx('★ 저장됨', 'Saved') : tx('☆ 저장', 'Save')}
        </Text>
      </Pressable>
    </StoryReactionRow>
  </View>;
}

/** placesInFeed 를 지웠다 */

/** 피드 맨 위에서 바로 쓰는 글쓰기 카드 — 데스크톱 폭(1024+) 전용. */
/** 동영상 아이콘 — 시안의 인라인 SVG 를 그대로 옮겼다(24 격자, 굵기 2). */
function VideoIcon({ tint }: { tint: string }) {
  return (
    // fill 을 안 주면 react-native-svg 가 검게 채운다 — 선만 있는 그림이라 none 이 필요하다.
    <Svg width={16} height={16} viewBox="0 0 24 24" fill="none">
      <Rect x={3} y={6} width={13} height={12} rx={2} stroke={tint} strokeWidth={2} />
      <Path d="M16 10l5-3v10l-5-3" stroke={tint} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

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
  const { video, addVideo, removeVideo, uploading: videoUploading, uploadedUrl: videoUrl, canAdd: canAddVideo, clearVideo } = useStoryVideo(accessToken, tx);

  const bodyValid = body.trim().length >= 1 && body.trim().length <= BODY_MAX;
  // 사진이든 동영상이든 올라가는 중이면 안 보낸다 — 주소가 아직 없어서 빠진다.
  const canPost = bodyValid && !anyUploading && !videoUploading && !submitting;

  const post = async () => {
    if (!canPost) return;
    setSubmitting(true);
    setError(null);
    const outcome = await createStory({
      body: body.trim(),
      imageUrls: uploadedUrls,
      region: region.trim() || undefined,
      // 우리 DB 장소를 골랐을 때만 실려 간다. 카카오 검색 결과에는 placeId 가 아예
      // 없으므로(regionSearch.ts) 저장하면 안 되는 것이 여기로 흘러들 수 없다.
      placeId,
      visibility,
      // 🔴 올라간 것만 붙인다. 실패한 동영상은 주소가 없어서 여기로 안 온다 —
      //    사진이 같은 규칙이다.
      videoUrl: videoUrl ?? undefined,
      accessToken,
    });
    setSubmitting(false);
    if (outcome.state !== 'success') { setError(outcome.message); return; }
    setBody(''); setRegion(''); setPlaceId(undefined); setRegionOpen(false); clearImages(); clearVideo();
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

    {/* — 글쓰기가 두 곳(여기와 app/feed/compose.tsx)에 있는데
        둘이 다른 모양이면 같은 앱에서 사진이 두 가지로 보인다. 같은 부품을 쓴다.
    */}
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

    {/* 사진이 상한을 넘었을 때만 그 이유가 뜬다 — 미리 겁주지 않는다. */}
    {images.map((image, index) => image.error
      ? <Text key={`image-error-${index}`} variant="caption" color={color.state.danger}>{image.error}</Text>
      : null)}

    {/* — 자유 입력 한 칸이던 것을 검색으로 바꾼다. 우리 DB 장소를
        고르면 placeId 가 따라와 글이 그 장소에 달린다. 손으로 고쳐 쓰는 길은 그대로다.
    */}
    {regionOpen ? <RegionPicker
      region={region}
      onChangeRegion={setRegion}
      placeId={placeId}
      onChangePlaceId={setPlaceId}
      accessToken={accessToken}
    /> : null}

    {/* — 전체 화면 글쓰기에는 있던 안내가 여기엔 없었다.
        같은 앱에서 같은 일을 하는데 한쪽만 말해 주면 안 된다.
    */}
    <Text variant="caption" color={color.text.muted}>{tx('사진의 위치 정보는 지워져요. 장소를 연결하면 그 장소 소개에도 사진이 함께 보일 수 있어요.', 'Location data is removed from photos. If you link a place, your photo may also appear on that place.')}</Text>

    <View style={styles.composeTools}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('사진 추가', 'Add photo')} disabled={!canAddMore} onPress={() => void addImage()} style={[styles.toolButton, !canAddMore && styles.busy]}>
        <Image source={require('../../assets/icons/common/camera.png')} resizeMode="contain" accessibilityLabel="" style={styles.toolIcon} />
        <Text variant="body" color={color.text.body}>{tx(`사진 ${images.length}/3`, `Photos ${images.length}/3`)}</Text>
      </Pressable>
      {/* 🔴 동영상은 사진 세 장 자리를 먹지 않는다 — 서버가 따로 보관한다. 그래서
          「사진 2/3 · 동영상 1/1」처럼 따로 센다 (시안 03).

          🟢 웹에서는 안 그린다. **정해진 것이다 (2026-09-19, S15P21E201-1318).**
             줄이는 것이 기기 기능이라 웹에서는 원본이 그대로 올라가고, 그러면 상한을
             쉽게 넘겨 거절당한다. 고르고 기다렸다가 실패하는 것보다 안 보이는 쪽이 낫다.

             🔴 이 줄을 지우기 전에 웹에서 줄이는 길부터 만들어라. 순서를 바꾸면
                사용자가 큰 파일을 올렸다 거절당하는 것을 우리가 만들어 주는 셈이다. */}
      {Platform.OS === 'web' ? null : <Pressable
        accessibilityRole="button"
        accessibilityLabel={video ? tx('동영상 빼기', 'Remove video') : tx('동영상 추가', 'Add video')}
        accessibilityState={{ busy: videoUploading }}
        disabled={videoUploading}
        onPress={() => (video ? removeVideo() : void addVideo())}
        style={[styles.toolButton, videoUploading && styles.busy]}
      >
        <VideoIcon tint={color.brand.navy} />
        <Text variant="body" color={color.text.body}>
          {videoUploading
            ? tx('동영상 올리는 중…', 'Uploading video…')
            : tx(`동영상 ${videoUrl ? 1 : 0}/1`, `Video ${videoUrl ? 1 : 0}/1`)}
        </Text>
      </Pressable>}
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

    {/* 동영상이 실패하면 이유를 말한다 — 올라간 것만 글에 붙고, 실패한 것은 안 붙는다. */}
    {video?.error ? <Text variant="caption" color={color.state.danger}>{video.error}</Text> : null}
    {error ? <Text variant="caption" color={color.state.danger}>{error}</Text> : null}
  </View>;
}

/** 추억 지도 — 좌표가 붙은 기록만 지도에 찍는다. */
function MemoryMap({ items, onOpenStory, sheet = false, onCollapse }: {
  items: StoryDto[];
  onOpenStory: (id: string) => void;
  /** 폰에서 탭바가 늘어난 시트 안인가. 판 대신 시트 모양으로 그린다. */
  sheet?: boolean;
  onCollapse?: () => void;
}) {
  const { tx } = useI18n();
  // 시트 안에서는 지도가 남는 높이를 다 쓴다. 그런데 지도는 높이를 숫자로 받으므로
  // 자리를 재서 넘긴다 — 시트 높이에서 빼는 산수를 적어 두면 안쪽 여백을 고칠 때마다
  // 그 식이 조용히 틀어진다.
  const [mapHeight, setMapHeight] = useState(0);
  const stops = items
    .filter((story) => typeof story.place?.lat === 'number' && typeof story.place?.lng === 'number')
    .map((story, index) => ({
      id: story.id,
      number: index + 1,
      name: story.place?.name ?? story.region ?? tx('기록', 'Record'),
      latitude: story.place?.lat as number,
      longitude: story.place?.lng as number,
    }));

  // 고른 것을 기억한다. 시안이 「선택 = navy 배경/흰 글자, 나머지
  // 흰 배경」이라고 한 그 상태다. 전에는 언제나 첫 번째가 골라진 채였다 — 지도가 있는데
  // 목록에서 어디를 보는지 고를 수가 없었다.
  const [selected, setSelected] = useState<string | null>(null);
  const current = selected && stops.some((stop) => stop.id === selected) ? selected : stops[0]?.id ?? null;

  if (!stops.length) return null;

  const labels = stops.map((stop) => {
    const active = stop.id === current;
    return (
      <Pressable
        key={stop.id}
        accessibilityRole="button"
        accessibilityState={{ selected: active }}
        accessibilityLabel={active
          ? tx(`${stop.name} 기록 보기`, `Open the record at ${stop.name}`)
          : tx(`${stop.name} 지도에서 보기`, `Show ${stop.name} on the map`)}
        onPress={() => (active ? onOpenStory(stop.id) : setSelected(stop.id))}
        style={[styles.pinLabel, active && styles.pinLabelActive]}
      >
        <Text variant="caption" weight="bold" color={active ? color.text.onAction : color.text.heading} numberOfLines={1}>
          {stop.name}
        </Text>
      </Pressable>
    );
  });

  if (sheet) {
    return <>
      {/* 위 손잡이 — 시트를 내린다. 시안 04b 의 36×4 막대이고, 누르는 자리는 그보다 넓다. */}
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={tx('지도 내리기', 'Hide the map')}
        onPress={onCollapse}
        style={styles.sheetHandleHit}
      >
        <View style={styles.sheetHandle} />
      </Pressable>

      <View style={styles.sheetHeader}>
        <Eyebrow>{tx('추억 지도', 'Memory map')}</Eyebrow>
        <Pressable accessibilityRole="button" onPress={onCollapse} style={styles.sheetClose}>
          <Text variant="caption" weight="bold" color={color.text.heading}>{tx('지도 내리기', 'Hide map')}</Text>
        </Pressable>
      </View>

      <View style={styles.sheetMap} onLayout={(event) => setMapHeight(Math.round(event.nativeEvent.layout.height))}>
        {mapHeight > 0
          ? <RouteMap stops={stops} selectedId={current ?? stops[0].id} onSelect={onOpenStory} height={mapHeight} />
          : null}
      </View>

      {/* 가로로 흐르는 줄. 폰에서는 줄바꿈하면 지도가 그만큼 눌린다. */}
      {/* 🔴 가로 줄은 기본이 「아이들을 세로로 늘리기」다. 그대로 두면 장소 칩이
          지도만큼 키가 커져서 지도를 위로 밀어낸다 — 폰에서만 보이는 종류다. */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.sheetRailBox}
        contentContainerStyle={styles.sheetRail}
      >
        {labels}
      </ScrollView>

      <Text variant="caption" color={color.text.muted}>{tx(`좌표 있는 기록 ${stops.length}개`, `${stops.length} records with coordinates`)}</Text>
    </>;
  }

  return <View style={styles.mapPanel}>
    <Eyebrow>{tx('추억 지도', 'Memory map')}</Eyebrow>
    <View style={styles.mapCard}>
      <RouteMap stops={stops} selectedId={current ?? stops[0].id} onSelect={onOpenStory} height={360} />
    </View>

    {/* 장소 라벨 — 누르면 지도에서 그 핀이 골라지고, 한 번 더 누르면 그 기록으로 간다.
        「누르면 곧바로 이동」이 아니다. 지도를 보며 고르는 자리라, 첫 누름은 지도에서
        찾아 주는 것이어야 한다. 이동은 이미 골라진 것을 다시 누를 때다.
    */}
    <View style={styles.pinLabels}>{labels}</View>

    <Text variant="caption" color={color.text.muted}>{tx(`좌표 있는 기록 ${stops.length}개`, `${stops.length} records with coordinates`)}</Text>
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

  // 1024 이상에서만 보조 칸을 붙인다. 저장소 반응형 표가 「1024~ 사이드바 + 본문」
  // 이라고 정해 두었다(layout/breakpoints.ts). 인계 문서는 'md' 라고 적었지만 이
  // 저장소의 md 는 600 이라, 거기서 320 보조 칸을 붙이면 본문이 짓눌린다.
  const wide = isAtLeast(width, 'lg');
  const compact = !isAtLeast(width, 'md');

  const [scope, setScope] = useState<FeedScope>('ALL');
  const [loadingMore, setLoadingMore] = useState(false);
  const [unfollowingId, setUnfollowingId] = useState<string | null>(null);
  // 폰에서 지도를 폈나. 넓은 화면은 늘 떠 있어 이 값을 안 본다.
  const insets = useSafeAreaInsets();
  const [mapOpen, setMapOpen] = useState(false);
  // 시트가 열리면 떠 있는 단추가 비켜 준다 — 막대가 자라는 것보다 빨리 사라진다.
  const fade = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    Animated.timing(fade, {
      toValue: mapOpen ? 0 : 1,
      duration: 200,
      easing: Easing.out(Easing.quad),
      useNativeDriver: false,
    }).start();
  }, [mapOpen, fade]);
  const [reportingStoryId, setReportingStoryId] = useState<string | null>(null);
  const [savingStoryId, setSavingStoryId] = useState<string | null>(null);
  const [reactingStoryId, setReactingStoryId] = useState<string | null>(null);
  // — 로그인 유도. 주소를 나누지 않고 이 화면의 상태로만 다룬다.
  // 주소를 가르면 뒤로 가기·공유 링크·검색이 전부 갈라진다.
  const [promptingSignIn, setPromptingSignIn] = useState(false);
  const [lastPromptedAt, setLastPromptedAt] = useState(0);

  const signedIn = Boolean(accessToken);
  // — 글쓰기 입구는 여기서 고르지 않고 composeEntry 한 곳에서 받는다.
  // 조건을 화면 두 곳에 나눠 적었더니 그 사이 폭(600~1023)에 입구가 하나도 없었다.
  const composeEntry = composeEntryFor(width, signedIn);
  const key = FEED_KEY(scope, signedIn);

  // 화면 밖 보관소에서 읽는다 — 탭을 오가도 다시 안 부른다.
  const feedQuery = useQuery({
    queryKey: key,
    queryFn: () => loadFeed({ scope, accessToken }),
  });
  const result: FeedLoadResult = feedQuery.data ?? { state: 'success', items: [], nextCursor: null };
  const loading = feedQuery.isPending;
  const items = result.state === 'success' ? result.items : [];

  // 저장 여부 —. StoryDto엔 없는 칸이라 저장 id 집합을 따로 받아 대조한다.
  const savedIdsQuery = useQuery({
    queryKey: ['saved-story-ids', signedIn],
    queryFn: () => loadSavedStoryIds(accessToken),
    enabled: signedIn,
  });
  const savedIds = savedIdsQuery.data?.state === 'success' ? savedIdsQuery.data.ids : new Set<string>();

  const toggleSave = async (story: StoryDto) => {
    if (!signedIn) { setPromptingSignIn(true); return; }
    const nextSaved = !savedIds.has(story.id);
    setSavingStoryId(story.id);
    const outcome = await setStorySaved(story.id, nextSaved, accessToken);
    setSavingStoryId(null);
    if (outcome.state !== 'success') return;
    queryClient.setQueryData<{ state: 'success'; ids: Set<string> }>(['saved-story-ids', signedIn], (current) => {
      const ids = new Set(current?.ids ?? []);
      if (nextSaved) ids.add(story.id); else ids.delete(story.id);
      return { state: 'success', ids };
    });
  };

  /**
   * 좋아요·싫어요 토글 —. 같은 것을 다시 누르면 끄고(DELETE), 다른 것을
   * 누르면 바꾼다(PUT). 서버가 세는 수를 낙관적으로 미리 맞춰 그린다 — 매번 목록을 다시
   * 불러오면 스크롤 위치가 튄다.
   */
  const react = async (story: StoryDto, reaction: 'LIKE' | 'DISLIKE') => {
    if (!signedIn) { setPromptingSignIn(true); return; }
    if (reactingStoryId) return;
    const next = nextReaction(story.myReaction, reaction);
    setReactingStoryId(story.id);
    const outcome = await setStoryReaction(story.id, next, accessToken);
    setReactingStoryId(null);
    if (outcome.state !== 'success') return;
    replaceItems((current) => current.map((item) => (item.id === story.id ? applyReaction(item, next) : item)));
  };

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
    <View style={styles.headerText}>
      <Eyebrow>{tx('여행 기록 피드', 'Travel story feed')}</Eyebrow>
      {/* 🔴 큰 제목(hero)은 기본 글자색이 흰색이다 — 어두운 바탕 위에 쓰라고 만든 것이라서.
          색을 안 주면 아이보리 바탕에 흰 글자가 되어 아무것도 안 보인다. 타입도 시험도
          안 잡는 종류라 여기서 반드시 준다. */}
      <Text
        variant={compact ? 'display' : 'hero'}
        weight="bold"
        color={color.text.heading}
        style={styles.headerTitle}
      >
        {tx('부산에서 남긴 여행 이야기', 'Travel stories from Busan')}
      </Text>
      {/* 폰에는 안 그린다 — 시안 04 가 제목 바로 아래 범위 칩을 둔다. */}
      {compact ? null : (
        <Text color={color.text.body}>
          {tx('사진 3장까지, 장소를 연결하면 그 장소 소개에도 함께 보여요.',
              'Up to three photos. Link a place and your photos can appear on that place too.')}
        </Text>
      )}
    </View>
    <View style={styles.headerActions}>
      <View accessibilityRole="tablist" style={compact ? styles.scopeChips : styles.scopeSegments}>
        {scopeButton('ALL', tx('전체', 'All'))}
        {scopeButton('FOLLOWING', tx('팔로잉', 'Following'))}
      </View>
      {/* 폰의 글쓰기 진입은 아래 떠 있는 단추(FAB)로 옮겼다 (시안 5번).
          여기 남겨 두면 같은 행동이 한 화면에 두 자리에 있게 된다. composeEntryFor 의
          'headerButton' 은 이제 「폰이다」를 뜻하고, 그 자리를 FAB 가 맡는다.
      */}
    </View>
  </View>;

  const feedColumn = <View style={styles.feedColumn}>
    {header}

    {!signedIn
      ? <View style={styles.loginNotice}>
          <Text variant="caption" color={color.text.body}>{tx('로그인하면 기록을 남기고 팔로잉 피드를 볼 수 있어요.', 'Sign in to write records and see your following feed.')}</Text>
          <Pressable accessibilityRole="link" onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/feed' } })}><Text variant="caption" weight="bold" color={color.state.info}>{tx('로그인 →', 'Sign in →')}</Text></Pressable>
        </View>
      : null}

    {/* — 데스크톱 폭(1024+)이면 맨 위에 둔다. 전에는 1440+ 였고
        헤더 버튼은 1024 미만에서만 나와서 그 사이 폭에 입구가 없었다.
    */}
    {composeEntry === 'inline'
      ? <InlineCompose onPosted={() => void queryClient.invalidateQueries({ queryKey: key })} />
      : null}

    {loading
      ? <View accessibilityLiveRegion="polite" style={styles.stateCard}><ActivityIndicator color={color.action.primary} /><Text variant="title" weight="bold">{tx('피드를 불러오고 있어요', 'Loading the feed')}</Text></View>
      : null}

    {!loading && result.state !== 'success'
      ? <View style={styles.stateCard}>
          <Text variant="title" weight="bold">{result.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : result.state === 'unavailable' ? tx('피드 API를 기다리고 있어요', 'Waiting for the feed API') : tx('피드를 불러오지 못했어요', 'Could not load the feed')}</Text>
          <Text color={color.text.body}>{result.message}</Text>
          <Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void feedQuery.refetch()} />
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

    {/* 계정이 필요한 행동(신고)을 누르면 곧바로 로그인 화면으로 보내지 않고 같은 창을 띄운다
        왜 필요한지 모른 채 쫓겨난 것처럼 느끼게 하지 않는다
    */}
    {!loading && result.state === 'success' && items.length
      ? <View style={compact ? styles.list : styles.listWide}>{items.map((story) => <StoryCard
          key={story.id}
          story={story}
          compact={compact}
          showUnfollow={scope === 'FOLLOWING'}
          unfollowBusy={unfollowingId === story.author.id}
          onUnfollow={() => void unfollow(story)}
          onOpen={() => router.push(`/feed/${story.id}`)}
          onOpenAuthor={() => router.push(`/user/${story.author.id}`)}
          onReport={() => signedIn ? setReportingStoryId(story.id) : setPromptingSignIn(true)}
          saved={savedIds.has(story.id)}
          savingStar={savingStoryId === story.id}
          onToggleSave={() => void toggleSave(story)}
          reacting={reactingStoryId === story.id}
          onReact={(reaction) => void react(story, reaction)}
        />)}</View>
      : null}

    {!loading && result.state === 'success' && result.nextCursor
      ? <Button label={loadingMore ? tx('불러오는 중…', 'Loading…') : tx('더 보기', 'Load more')} variant="tertiary" disabled={loadingMore} onPress={() => void loadMore()} containerStyle={styles.loadMore} />
      : null}
  </View>;

  // 「이 피드에 나온 장소」 목록을 뺐다.
  const aside = <View style={styles.aside}>
    <MemoryMap items={items} onOpenStory={(id) => router.push(`/feed/${id}`)} />
  </View>;

  return <View style={styles.shell}>
    {/* wide 를 넘겨야 최대 폭이 720 → 1180 으로 열린다. 안 넘기면 2단이
        좁은 칸 안에서 또 나뉘어 양쪽 다 짓눌린다 — Screen 주석이 경고하는
        바로 그 고장이고, tsc 는 잡지 못한다(폭이 좁은 것은 문법 오류가 아니다).
    */}
    <Screen scroll withTabBar wide={wide}>
      {wide
        ? <View style={styles.wideGrid}>{feedColumn}{aside}</View>
        : <>
            {feedColumn}
            {/* 폰의 지도는 목록 아래가 아니라 탭바가 늘어난 시트 안에 있다 (시안 04b).
                목록 아래에 하나 더 그리면 보러 온 기록이 그만큼 밀린다. */}
          </>}
      <ReportModal visible={reportingStoryId !== null} onClose={() => setReportingStoryId(null)} onSubmit={submitReport} />
      <SignInPromptModal
        visible={promptingSignIn}
        onClose={() => setPromptingSignIn(false)}
        onSignIn={() => { setPromptingSignIn(false); router.push({ pathname: '/sign-in', params: { returnTo: '/feed' } }); }}
      />
    </Screen>

    {/* 떠 있는 단추는 Screen 밖에 둔다. 안에 두면 스크롤과 함께
        올라가 버린다 — 탭바가 같은 이유로 받침에 담겨 떠 있다.
        `pointerEvents="box-none"` 이라 단추가 없는 자리는 손짓이 그대로 통과한다.
    */}
    {!wide
      ? <Animated.View
          // 🔴 시트가 열리면 눌리지도 않아야 한다. 투명하기만 하면 지도를 누르려던
          //    손가락이 「지도 표시하기」를 다시 누른다 — 바로 그 자리에 있다.
          pointerEvents={mapOpen ? 'none' : 'box-none'}
          style={[
            styles.fabDock,
            { paddingBottom: TAB_BAR_HEIGHT + tabBarBottomMargin(insets.bottom) + spacing[4] },
            { opacity: fade, transform: [{ translateY: fade.interpolate({ inputRange: [0, 1], outputRange: [24, 0] }) }] },
          ]}
        >
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ expanded: mapOpen }}
            accessibilityLabel={mapOpen ? tx('지도 접기', 'Hide the map') : tx('지도 표시하기', 'Show the map')}
            onPress={() => setMapOpen((open) => !open)}
            style={({ pressed }) => [styles.mapToggle, pressed && styles.pressed]}
          >
            <Text variant="body" weight="bold" color={color.text.onAction}>{mapOpen ? tx('지도 접기', 'Hide map') : tx('지도 표시하기', 'Show map')}</Text>
          </Pressable>

          {/* 글쓰기는 로그인한 사람에게만 뜬다. 비회원에게 띄우면 눌렀을 때 쫓겨난다. */}
          {signedIn
            ? <Pressable
                accessibilityRole="button"
                accessibilityLabel={tx('기록 남기기', 'Write a record')}
                onPress={() => router.push('/feed/compose')}
                style={({ pressed }) => [styles.writeFab, pressed && styles.pressed]}
              >
                {/* 「+」가 아니라 연필이다.
                    탭바 가운데에 이미 주황 + 원이 있다 — 「여행 만들기」다(TabBar 의
                    createIconWrap). 그 바로 위에 주황 + 를 또 두면 뜻이 다른 주황 + 가
                    둘 겹쳐서, 어느 것이 무엇인지 눌러 봐야 안다.
                    시안은 「orange 기록 FAB」이라고만 했지 글자를 정하지 않았다.
                */}
                <Text variant="title" weight="bold" color={color.text.onAction}>✎</Text>
              </Pressable>
            : null}
        </Animated.View>
      : null}

    {/* 폰에서는 이 막대가 지도 시트로 늘어난다 (시안 04b). 넓은 화면은 오른쪽에 지도가
        늘 떠 있으므로 늘리지 않는다. */}
    <TabBar
      active="feed"
      expanded={!wide && mapOpen}
      onCollapse={() => setMapOpen(false)}
      children={!wide
        ? <MemoryMap items={items} onOpenStory={(id) => router.push(`/feed/${id}`)} sheet onCollapse={() => setMapOpen(false)} />
        : undefined}
    />
  </View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.brand.ivory },

  // 넓은 화면: 본문 + 보조 칸. 보조 칸은 폭 고정, 본문이 남는 만큼 가져간다.
  wideGrid: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[6], marginTop: spacing[6] },
  // 🔴 기둥에 최대 폭을 주지 않는다. 지도 칸을 뺀 나머지를 다 쓴다(시안 03).
  //
  //    600 으로 묶여 있을 때는 1440 폭에서도 카드가 2열이었고 오른쪽이 텅 비었다.
  //    열 수는 아래 cardInGrid 의 「한 장의 최소 폭 280」이 정한다 — 열 수를 숫자로
  //    박지 않는다. 상한만 풀면 넓은 화면에서 저절로 3열이 된다.
  feedColumn: { flex: 1, minWidth: 0, width: '100%', alignSelf: 'center' },
  // ── 폰의 떠 있는 단추 ────────────────────────────────────
  pressed: { opacity: 0.72, transform: [{ scale: 0.97 }] },
  fabDock: { position: 'absolute', left: 0, right: 0, bottom: 0, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: spacing[3], paddingHorizontal: gutter },
  mapToggle: { minHeight: 48, justifyContent: 'center', paddingHorizontal: spacing[6], borderRadius: radius.full, backgroundColor: color.brand.navy },
  writeFab: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: 24, backgroundColor: color.action.primary },
  // 시트 안 — 위 손잡이, 머리 줄, 지도, 장소 칩 가로 줄.
  sheetHandleHit: { alignSelf: 'center', width: 44, height: 20, alignItems: 'center', justifyContent: 'center' },
  sheetHandle: { width: 36, height: 4, borderRadius: 2, backgroundColor: color.surface.field },
  sheetHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sheetClose: { minHeight: 32, justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.soft },
  sheetMap: { flex: 1, borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.md, overflow: 'hidden', backgroundColor: color.surface.soft },
  sheetRailBox: { flexGrow: 0, flexShrink: 0 },
  sheetRail: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  // 480 은 시안 값이다. 전에는 320 이라 지도가 우표만 했고, 그 뒤 520 이었다.
  // 지도를 보라고 둔 칸인데 무엇이 어디인지 안 보였다.
  //
  // 🔴 스크롤을 내려도 자리에 머문다(시안 03). 안 그러면 카드 몇 장만 내려도 지도가
  //    위로 사라져서, 「지도를 보며 기록을 훑는다」는 이 배치의 목적이 없어진다.
  //    position: 'sticky' 는 웹에만 있는 값이라 RN 의 타입에 없다 — 폰에서는 안 준다.
  aside: {
    width: 480,
    gap: spacing[3],
    ...(Platform.OS === 'web' ? ({ position: 'sticky', top: spacing[6] } as object) : null),
  },
  mapPanel: { gap: spacing[3], paddingVertical: spacing[6], paddingHorizontal: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  // 라벨 핀 — 줄바꿈된다. 장소가 몇 개든 잘리지 않는다.
  pinLabels: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  pinLabel: { minHeight: 32, justifyContent: 'center', maxWidth: '100%', paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  pinLabelActive: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  mapCard: { borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card, overflow: 'hidden' },

  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: spacing[3], flexWrap: 'wrap' },
  headerText: { gap: spacing[2], flexShrink: 1, minWidth: 0 },
  headerTitle: { marginTop: 0 },
  headerActions: {
    flexDirection: 'row', alignItems: 'center', gap: spacing[2],
    // 넘치면 내려간다. 이 줄이 없어서 오른쪽으로 삐져나갔다.
    flexWrap: 'wrap', justifyContent: 'flex-end',
  },
  // writeButton 을 지웠다. { width:'auto', paddingHorizontal } 을
  // containerStyle 로 넘겼는데 그건 껍데기의 폭일 뿐이라, 안쪽 버튼은 여전히
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
  // 넓은 화면은 2열. flexWrap 이라 폭이 모자라면 자연히 한 열이 된다
  // 열 수를 폭으로 계산해 박아 두지 않는다. minWidth 280 이 한 장의 최소 폭이다.
  listWide: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[4], marginTop: spacing[4] },
  cardInGrid: { flexGrow: 1, flexBasis: 280, minWidth: 280 },

  // ── 카드 ──────────────────────────────────────────────────
  card: { borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border, overflow: 'hidden' },
  coverWrap: { position: 'relative' },
  cover: { width: '100%', backgroundColor: color.surface.soft },
  /** 폰은 높이를 고정한다 — 사진 비율이 제각각이어도 카드 높이가 들쭉날쭉하지 않게. */
  coverPhone: { height: 300 },
  /** 넓은 화면은 정사각. 2열로 놓을 때 줄이 맞는다. */
  coverWide: { aspectRatio: 1 },
  coverImage: { width: '100%', height: '100%' },
  // 사진이 없을 때 — 회색 빈 칸 대신 tint 에 본문을 크게. 시안 「자주 틀리는 것」 4번.
  coverEmpty: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[6], backgroundColor: color.surface.tint },
  coverEmptyText: { textAlign: 'center' },
  dots: { position: 'absolute', left: 0, right: 0, bottom: spacing[3], flexDirection: 'row', justifyContent: 'center', gap: spacing[1] },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: color.surface.card, opacity: 0.5 },
  dotFirst: { opacity: 1 },
  // 사진 위가 아니라 카드 맨 위 한 줄이다. 반투명 배경이 필요 없어졌다.
  cardHead: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], paddingHorizontal: spacing[3], paddingTop: spacing[3], paddingBottom: spacing[2] },
  authorPill: {
    flexShrink: 1,
    flexDirection: 'row', alignItems: 'center', gap: spacing[2],
    minHeight: 32, paddingVertical: spacing[1], borderRadius: radius.full,
  },
  authorPillAvatar: { width: 24, height: 24, borderRadius: 12, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },
  coverActions: { flexShrink: 0, flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  cardBody: { gap: spacing[1], padding: spacing[4] },
  // flexShrink 0 — 폰 목록은 세로로 쌓이는데, 높이가 모자라면 카드가 눌려서
  // 사진이 찌그러진다. 시안 04 가 커버를 300 으로 고정하는 것과 같은 이유다.
  cardCompact: { padding: spacing[4], flexShrink: 0 },
  avatar: { width: 40, height: 40, borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' },
  avatarCompact: { width: 36, height: 36 },

  visibilityBadge: { minHeight: 28, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  pillButton: { minWidth: 72, minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  busy: { opacity: 0.6 },
  menuButton: { minWidth: 32, minHeight: 32, alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm },

  body: { lineHeight: 22 },

  // 배치는 PhotoGrid 가 정한다 — 여기서는 위아래 간격만 준다.
  images: { marginTop: spacing[2] },
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
  // — 배경색을 여기 칠하지 않는다. Button 의 variant='primary' 가
  // 이미 같은 남색을 «안쪽» 에 칠하고, 안쪽에만 모서리가 있다. 바깥 껍데기에 같은 색을
  // 덧칠하면 둥근 버튼 뒤에 네모난 판이 깔려 각져 보인다 — 두 색이 같아 네모만 보였다.
  composePost: { width: 'auto', minWidth: 96, paddingHorizontal: spacing[4] },
});
