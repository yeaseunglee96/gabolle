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

/**
 * 커버 — 카드 맨 위의 사진 자리 (S15P21E201-1177).
 *
 * 🔴 사진이 없으면 **회색 빈 칸을 두지 않는다.** tint 카드에 본문을 크게 넣는다. 시안이
 * 「자주 틀리는 것」 4번으로 못박은 자리다 — 빈 회색은 「사진을 못 불러왔다」로 읽힌다.
 *
 * 사진이 여러 장이면 아래 가운데에 점을 찍는다. 넘기는 기능은 아직 없다 — 점은 **몇 장인지
 * 알리는 표시**일 뿐이고, 없는 기능을 있는 것처럼 보이게 하지 않으려고 첫 점만 진하게 둔다.
 */
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
  /** 내가 저장한 기록인가 — S15P21E201-1221. StoryDto엔 없는 칸이라 화면이 따로 들고 다닌다. */
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
    <View style={styles.coverWrap}>
      <StoryCover story={story} compact={compact} onOpen={onOpen} />

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

    <Pressable accessibilityRole="link" accessibilityLabel={tx('기록 자세히 보기', 'View record details')} onPress={onOpen} style={styles.cardBody}>
      <Text variant="body" weight="bold" color={color.text.heading} numberOfLines={1}>{title}</Text>

      {/* 🔴 사진이 없는 글은 본문을 커버에 이미 크게 그렸다. 여기서 또 그리면 같은 글이 두 번
          나온다 — 9/16 에 「N곳」이 두 번 나온 것과 같은 종류다. 시안도 "아래 본문 미리보기는
          생략" 이라고 적었다. */}
      {hasPhoto
        ? <Text variant="body" color={color.text.body} numberOfLines={2} style={styles.body}>{markdownToPlain(story.body)}</Text>
        : null}

      {/* 메타 한 줄.
          🔴 시안은 여기에 「답글 N · 조회 N」을 넣으라고 한다. 그 칸이 **서버에 아직 없다** —
          story 표에 parent 칸도, 조회 표도 없다(마이그레이션 전수 확인, 2026-09-17).
          0 을 하드코딩해 그리지 않는다. 모르는 것을 아는 척하는 것이라, 서버가 칸을 주는 날
          자연히 나타나게 둔다(S15P21E201-1177 의 백엔드 몫). 지금은 시각과 지역만 말한다. */}
      <Text variant="caption" color={color.text.muted}>
        {relativeStoryTime(story.createdAt, tx)}{story.region ? ` · ${story.region}` : ''}
      </Text>
    </Pressable>

    {/* 좋아요·싫어요·저장 — S15P21E201-1174(반응 카운트, kojh0124 님)·-1221(저장).
        🔴 myReaction은 세 값이다(null·LIKE·DISLIKE) — !myReaction으로 묶지 않는다. */}
    <View style={styles.reactionRow}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={story.myReaction === 'LIKE' ? tx('좋아요 취소', 'Remove like') : tx('좋아요', 'Like')}
        accessibilityState={{ selected: story.myReaction === 'LIKE', busy: reacting }}
        disabled={reacting}
        onPress={() => onReact('LIKE')}
        style={[styles.reactionButton, reacting && styles.busy]}
      >
        <Text variant="caption" weight="bold" color={story.myReaction === 'LIKE' ? color.brand.orange : color.text.muted}>
          {tx('👍', '👍')}{typeof story.likeCount === 'number' ? ` ${story.likeCount}` : ''}
        </Text>
      </Pressable>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={story.myReaction === 'DISLIKE' ? tx('싫어요 취소', 'Remove dislike') : tx('싫어요', 'Dislike')}
        accessibilityState={{ selected: story.myReaction === 'DISLIKE', busy: reacting }}
        disabled={reacting}
        onPress={() => onReact('DISLIKE')}
        style={[styles.reactionButton, reacting && styles.busy]}
      >
        <Text variant="caption" weight="bold" color={story.myReaction === 'DISLIKE' ? color.brand.navy : color.text.muted}>
          {tx('👎', '👎')}{typeof story.dislikeCount === 'number' ? ` ${story.dislikeCount}` : ''}
        </Text>
      </Pressable>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={saved ? tx('저장 취소', 'Remove from saved') : tx('저장', 'Save')}
        accessibilityState={{ selected: saved, busy: savingStar }}
        disabled={savingStar}
        onPress={onToggleSave}
        style={[styles.reactionButton, savingStar && styles.busy]}
      >
        <Text variant="caption" weight="bold" color={saved ? color.brand.orange : color.text.muted}>
          {saved ? tx('★ 저장됨', 'Saved') : tx('☆ 저장', 'Save')}
        </Text>
      </Pressable>
    </View>
  </View>;
}

/*
 * 🔴 placesInFeed() 를 지웠다 (S15P21E201-1177).
 *
 * 「이 피드에 나온 장소」 목록이 지도 패널의 라벨 핀과 겹쳐서 목록을 뺐고, 그 목록만
 * 쓰던 함수라 같이 지운다. 세던 숫자는 「전체에서 몇 번」이 아니라 「지금 보이는 목록에서
 * 몇 번」이었다 — 집계 API 가 없어 범위를 좁게 잡은 값이다. 집계 API 가 생기면 그때
 * 제대로 만든다. 안 쓰는 계산을 화면마다 돌려 두지 않는다.
 */

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

  // 🔴 고른 것을 기억한다 (S15P21E201-1177). 시안이 「선택 = navy 배경/흰 글자, 나머지
  // 흰 배경」이라고 한 그 상태다. 전에는 언제나 첫 번째가 골라진 채였다 — 지도가 있는데
  // 목록에서 어디를 보는지 고를 수가 없었다.
  const [selected, setSelected] = useState<string | null>(null);
  const current = selected && stops.some((stop) => stop.id === selected) ? selected : stops[0]?.id ?? null;

  if (!stops.length) return null;

  return <View style={styles.mapPanel}>
    <Eyebrow>{tx('추억 지도', 'Memory map')}</Eyebrow>
    <View style={styles.mapCard}>
      <RouteMap stops={stops} selectedId={current ?? stops[0].id} onSelect={onOpenStory} height={360} />
    </View>

    {/* 장소 라벨 — 누르면 지도에서 그 핀이 골라지고, 한 번 더 누르면 그 기록으로 간다.
        🔴 「누르면 곧바로 이동」이 아니다. 지도를 보며 고르는 자리라, 첫 누름은 **지도에서
        찾아 주는 것**이어야 한다. 이동은 이미 골라진 것을 다시 누를 때다. */}
    <View style={styles.pinLabels}>
      {stops.map((stop) => {
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
      })}
    </View>

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

  // 🔴 1024 이상에서만 보조 칸을 붙인다. 저장소 반응형 표가 「1024~ 사이드바 + 본문」
  // 이라고 정해 두었다(layout/breakpoints.ts). 인계 문서는 'md' 라고 적었지만 이
  // 저장소의 md 는 600 이라, 거기서 320 보조 칸을 붙이면 본문이 짓눌린다.
  const wide = isAtLeast(width, 'lg');
  const compact = !isAtLeast(width, 'md');

  const [scope, setScope] = useState<FeedScope>('ALL');
  const [loadingMore, setLoadingMore] = useState(false);
  const [unfollowingId, setUnfollowingId] = useState<string | null>(null);
  // 폰에서 지도를 폈나 (S15P21E201-1177). 넓은 화면은 늘 떠 있어 이 값을 안 본다.
  const insets = useSafeAreaInsets();
  const [mapOpen, setMapOpen] = useState(false);
  const [reportingStoryId, setReportingStoryId] = useState<string | null>(null);
  const [savingStoryId, setSavingStoryId] = useState<string | null>(null);
  const [reactingStoryId, setReactingStoryId] = useState<string | null>(null);
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

  // 저장 여부 — S15P21E201-1221. StoryDto엔 없는 칸이라 저장 id 집합을 따로 받아 대조한다.
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
   * 좋아요·싫어요 토글 — S15P21E201-1174. 같은 것을 다시 누르면 끄고(DELETE), 다른 것을
   * 누르면 바꾼다(PUT). 서버가 세는 수를 낙관적으로 미리 맞춰 그린다 — 매번 목록을 다시
   * 불러오면 스크롤 위치가 튄다.
   */
  const react = async (story: StoryDto, reaction: 'LIKE' | 'DISLIKE') => {
    if (!signedIn) { setPromptingSignIn(true); return; }
    if (reactingStoryId) return;
    const was = story.myReaction ?? null;
    const next = was === reaction ? null : reaction;
    setReactingStoryId(story.id);
    const outcome = await setStoryReaction(story.id, next, accessToken);
    setReactingStoryId(null);
    if (outcome.state !== 'success') return;
    replaceItems((current) => current.map((item) => {
      if (item.id !== story.id) return item;
      let likeCount = item.likeCount ?? 0;
      let dislikeCount = item.dislikeCount ?? 0;
      if (was === 'LIKE') likeCount -= 1;
      if (was === 'DISLIKE') dislikeCount -= 1;
      if (next === 'LIKE') likeCount += 1;
      if (next === 'DISLIKE') dislikeCount += 1;
      return { ...item, myReaction: next, likeCount, dislikeCount };
    }));
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
    <View>
      <Eyebrow>{tx('여행 기록 피드', 'Travel story feed')}</Eyebrow>
      <Text variant="display" weight="bold" style={styles.headerTitle}>{tx('여행 이야기', 'Travel stories')}</Text>
    </View>
    <View style={styles.headerActions}>
      <View accessibilityRole="tablist" style={compact ? styles.scopeChips : styles.scopeSegments}>
        {scopeButton('ALL', tx('전체', 'All'))}
        {scopeButton('FOLLOWING', tx('팔로잉', 'Following'))}
      </View>
      {/* 🔴 폰의 글쓰기 진입은 아래 떠 있는 단추(FAB)로 옮겼다 (S15P21E201-1177, 시안 5번).
          여기 남겨 두면 같은 행동이 한 화면에 두 자리에 있게 된다. composeEntryFor() 의
          'headerButton' 은 이제 「폰이다」를 뜻하고, 그 자리를 FAB 가 맡는다. */}
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
      ? <Button label={loadingMore ? tx('불러오는 중…', 'Loading…') : tx('더 보기', 'Load more')} variant="ghost" disabled={loadingMore} onPress={() => void loadMore()} containerStyle={styles.loadMore} />
      : null}
  </View>;

  // 🔴 「이 피드에 나온 장소」 목록을 뺐다 (S15P21E201-1177).
  //
  //    지도 패널 안의 라벨 핀이 같은 장소를 이미 보여준다. 둘을 같이 두면 같은 정보가
  //    한 화면에 두 번 나온다 — 9/16 에 「N곳」이 두 번 나온 것과 같은 종류다.
  //
  //    잃는 것이 하나 있다: 장소마다 몇 번 나왔는지(count). 그건 「지금 보이는 목록에서
  //    몇 번」이라 집계 API 가 없어 만든 값이었고(아래 countPlaces 주석), 라벨을 눌러
  //    지도에서 찾는 것이 그 숫자보다 쓸모 있다고 봤다.
  const aside = <View style={styles.aside}>
    <MemoryMap items={items} onOpenStory={(id) => router.push(`/feed/${id}`)} />
  </View>;

  return <View style={styles.shell}>
    {/* 🔴 wide 를 넘겨야 최대 폭이 720 → 1180 으로 열린다. 안 넘기면 2단이
        좁은 칸 안에서 또 나뉘어 양쪽 다 짓눌린다 — Screen 주석이 경고하는
        바로 그 고장이고, tsc 는 잡지 못한다(폭이 좁은 것은 문법 오류가 아니다). */}
    <Screen scroll withTabBar wide={wide}>
      {wide
        ? <View style={styles.wideGrid}>{feedColumn}{aside}</View>
        : <>
            {feedColumn}
            {/* 🔴 폰에서 지도를 접었다 편다 (S15P21E201-1177, 시안 5번).
                넓은 화면은 오른쪽에 지도 패널이 늘 떠 있지만 폰에는 그 자리가 없다.
                그렇다고 목록 위에 지도를 항상 깔면 정작 보러 온 기록이 밀린다.
                그래서 **부를 때만** 편다.

                🔴 다른 화면으로 보내지 않는다. 피드 전체를 담는 지도 화면이 저장소에
                없다 — 없는 곳으로 가는 단추를 만들지 않는다. 「표시하기」라는 말 그대로
                이 자리에서 보여준다. */}
            {mapOpen ? <View style={styles.phoneMap}><MemoryMap items={items} onOpenStory={(id) => router.push(`/feed/${id}`)} /></View> : null}
          </>}
      <ReportModal visible={reportingStoryId !== null} onClose={() => setReportingStoryId(null)} onSubmit={submitReport} />
      <SignInPromptModal
        visible={promptingSignIn}
        onClose={() => setPromptingSignIn(false)}
        onSignIn={() => { setPromptingSignIn(false); router.push({ pathname: '/sign-in', params: { returnTo: '/feed' } }); }}
      />
    </Screen>

    {/* 🔴 떠 있는 단추는 Screen **밖**에 둔다 (S15P21E201-1177). 안에 두면 스크롤과 함께
        올라가 버린다 — 탭바가 같은 이유로 받침에 담겨 떠 있다(S15P21E201-1155).
        `pointerEvents="box-none"` 이라 단추가 없는 자리는 손짓이 그대로 통과한다. */}
    {!wide
      ? <View pointerEvents="box-none" style={[styles.fabDock, { paddingBottom: TAB_BAR_HEIGHT + tabBarBottomMargin(insets.bottom) + spacing[4] }]}>
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
                {/* 🔴 「+」가 아니라 연필이다 (S15P21E201-1177).
                    탭바 가운데에 이미 **주황 + 원**이 있다 — 「여행 만들기」다(TabBar 의
                    createIconWrap). 그 바로 위에 주황 + 를 또 두면 **뜻이 다른 주황 + 가
                    둘** 겹쳐서, 어느 것이 무엇인지 눌러 봐야 안다.
                    시안은 「orange 기록 FAB」이라고만 했지 글자를 정하지 않았다. */}
                <Text variant="title" weight="bold" color={color.text.onAction}>✎</Text>
              </Pressable>
            : null}
        </View>
      : null}

    <TabBar active="feed" />
  </View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.brand.ivory },

  // 넓은 화면: 본문 + 보조 칸. 보조 칸은 폭 고정, 본문이 남는 만큼 가져간다.
  wideGrid: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[6], marginTop: spacing[6] },
  // 🔴 피드 기둥에 최대 폭을 준다 (S15P21E201-1169).
  //
  // 전에는 flex:1 만 있어서 넓은 화면에서 카드가 1,200px 넘게 벌어졌다. 그러면 두 가지가
  // 같이 나빠진다 — 글이 한 줄에 너무 길어 읽기 어렵고, 사진의 **비율 제한이 무력해진다**
  // (0.6 비율이 폭 1,200 에서 높이 2,000 이 된다). 사용자가 올린 화면이 그 상태였다.
  //
  // 600 은 이런 세로 피드의 통상 폭이다(X 가 598). 폰에서는 화면이 그보다 좁으므로
  // 아무것도 안 바뀐다.
  feedColumn: { flex: 1, minWidth: 0, maxWidth: 600, width: '100%', alignSelf: 'center' },
  // ── 폰의 떠 있는 단추 (S15P21E201-1177) ────────────────────────────────────
  //
  // 탭바 위에 뜬다. 탭바 높이와 안전영역을 TabBar 가 내보낸 값으로 계산한다 —
  // 같은 숫자를 여기 또 적으면 저쪽에서 64 를 바꾸는 날 조용히 겹친다.
  pressed: { opacity: 0.72, transform: [{ scale: 0.97 }] },
  fabDock: { position: 'absolute', left: 0, right: 0, bottom: 0, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: spacing[3], paddingHorizontal: gutter },
  mapToggle: { minHeight: 48, justifyContent: 'center', paddingHorizontal: spacing[6], borderRadius: radius.full, backgroundColor: color.brand.navy },
  writeFab: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: 24, backgroundColor: color.brand.orange },
  phoneMap: { marginTop: spacing[4] },
  // 🔴 520 은 시안 값이다 (S15P21E201-1177). 전에는 320 이라 지도가 우표만 했다 —
  //    지도를 보라고 둔 칸인데 무엇이 어디인지 안 보였다.
  aside: { width: 520, gap: spacing[3] },
  mapPanel: { gap: spacing[3], paddingVertical: spacing[6], paddingHorizontal: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  // 라벨 핀 — 줄바꿈된다. 장소가 몇 개든 잘리지 않는다.
  pinLabels: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  pinLabel: { minHeight: 32, justifyContent: 'center', maxWidth: '100%', paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  pinLabelActive: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  mapCard: { borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card, overflow: 'hidden' },

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
  // 🔴 넓은 화면은 2열 (S15P21E201-1177). flexWrap 이라 폭이 모자라면 자연히 한 열이 된다 —
  //    열 수를 폭으로 계산해 박아 두지 않는다. minWidth 280 이 한 장의 최소 폭이다.
  listWide: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[4], marginTop: spacing[4] },
  cardInGrid: { flexGrow: 1, flexBasis: 280, minWidth: 280 },

  // ── 카드 (S15P21E201-1177) ──────────────────────────────────────────────────
  //
  // 커버 사진이 맨 위에 오고 그 위에 작성자·동작이 얹힌다. 카드 자체의 여백은 없앴다 —
  // 사진이 카드 끝까지 닿아야 시안의 인상이 난다. 글 부분만 안쪽 여백을 갖는다.
  card: { borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border, overflow: 'hidden' },
  coverWrap: { position: 'relative' },
  cover: { width: '100%', backgroundColor: color.surface.soft },
  /** 폰은 높이를 고정한다 — 사진 비율이 제각각이어도 카드 높이가 들쭉날쭉하지 않게. */
  coverPhone: { height: 300 },
  /** 넓은 화면은 정사각. 2열로 놓을 때 줄이 맞는다. */
  coverWide: { aspectRatio: 1 },
  coverImage: { width: '100%', height: '100%' },
  // 🔴 사진이 없을 때 — 회색 빈 칸 대신 tint 에 본문을 크게. 시안 「자주 틀리는 것」 4번.
  coverEmpty: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[6], backgroundColor: color.surface.tint },
  coverEmptyText: { textAlign: 'center' },
  dots: { position: 'absolute', left: 0, right: 0, bottom: spacing[3], flexDirection: 'row', justifyContent: 'center', gap: spacing[1] },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: color.surface.card, opacity: 0.5 },
  dotFirst: { opacity: 1 },
  authorPill: {
    position: 'absolute', top: spacing[3], left: spacing[3],
    flexDirection: 'row', alignItems: 'center', gap: spacing[2],
    maxWidth: '70%', minHeight: 32, paddingVertical: spacing[1], paddingHorizontal: spacing[2],
    borderRadius: radius.full,
    // 사진 위에 얹히므로 반투명 배경을 깐다. 안 깔면 밝은 사진에서 이름이 사라진다 —
    // 오늘 상태바에서 겪은 것과 같은 종류다.
    backgroundColor: 'rgba(255,253,248,0.92)',
  },
  authorPillAvatar: { width: 24, height: 24, borderRadius: 12, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },
  coverActions: { position: 'absolute', top: spacing[3], right: spacing[3], flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  cardBody: { gap: spacing[1], padding: spacing[4] },
  reactionRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[4], paddingHorizontal: spacing[4], paddingBottom: spacing[3], marginTop: -spacing[2] },
  reactionButton: { minHeight: 44, justifyContent: 'center' },
  cardCompact: { padding: spacing[4] },
  avatar: { width: 40, height: 40, borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' },
  avatarCompact: { width: 36, height: 36 },

  visibilityBadge: { minHeight: 28, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  pillButton: { minWidth: 72, minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  busy: { opacity: 0.6 },
  menuButton: { minWidth: 32, minHeight: 32, alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm },

  body: { lineHeight: 22 },

  // 배치는 PhotoGrid 가 정한다 (S15P21E201-1135) — 여기서는 위아래 간격만 준다.
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
  // 🔴 S15P21E201-1144 — 배경색을 여기 칠하지 않는다. Button 의 variant='primary' 가
  // 이미 같은 남색을 «안쪽» 에 칠하고, 안쪽에만 모서리가 있다. 바깥 껍데기에 같은 색을
  // 덧칠하면 둥근 버튼 뒤에 네모난 판이 깔려 각져 보인다 — 두 색이 같아 네모만 보였다.
  composePost: { width: 'auto', minWidth: 96, paddingHorizontal: spacing[4] },
});
