// 시안에 있지만 그리지 않은 것 둘 — 서버에 그 값이 없다.
// · 여행 카드의 제목 — TripSummaryDto 에 제목 칸이 없다(날짜·일수·인원·상태뿐)
// · 장소 카드의 사진 — photoUrl 은 상세의 선택 필드이고 늘 비어 있다. 목록엔 칸도 없다.
// 채우는 작업이 머지되고 목록 API 에 실리면 그때 넣는다.
import { useState } from 'react';
import { Image, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { GabolleMascot } from '@/components/DongbaekMascot';
import { PlaceVisual } from '@/components/PlaceVisual';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { markdownToPlain } from '@/social/markdown';
import { useAuth } from '@/auth/AuthProvider';
import { resolveHomeTripDestination } from './tripNavigation';
import { localFacetLabel, type FacetKeyEntry } from '@/discovery/localExplore';
import type { HomePlaceItem } from './useHomeData';
import { relativeStoryTime, type StoryDto } from '@/social/stories';
import { tripDisplayTitle, type TripSummaryDto } from '@/trip/trips';
import type { DailyForecastDto } from '@/trip/weather';

type Tx = (ko: string, en: string) => string;

// ── 날씨 한 줄 (히어로 왼쪽 아래) ──────────────────────────────────────────────

function skyLabel(sky: DailyForecastDto['skyCondition'], tx: Tx) {
  if (sky === 'CLEAR') return tx('맑음', 'Clear');
  if (sky === 'PARTLY_CLOUDY') return tx('구름 조금', 'Partly cloudy');
  if (sky === 'CLOUDY') return tx('흐림', 'Cloudy');
  return tx('날씨 정보 없음', 'No weather data');
}

/** 힌트 문구는 서버가 주지 않는다 — 받은 값으로 여기서 만든다(인계 문서도 클라이언트 매핑). */
function weatherHint(forecast: DailyForecastDto, tx: Tx) {
  const rain = forecast.precipitationProbability;
  if (rain !== null && rain >= 60) return tx('우산을 챙기세요', 'Take an umbrella');
  if (forecast.skyCondition === 'CLEAR') return tx('걷기 좋은 날이에요', 'A good day to walk');
  if (forecast.skyCondition === 'CLOUDY') return tx('실내 코스도 하나 챙겨두세요', 'Keep an indoor stop in mind');
  return '';
}

export function WeatherLine({ forecast }: { forecast: DailyForecastDto | null }) {
  const { tx } = useI18n();
  if (!forecast) return null;
  // 최고기온을 쓰고, 없으면 최저라도 보여준다. 둘 다 없으면 줄 자체를 안 그린다
  // 「—도」 라고 적으면 값이 있는 것처럼 보인다.
  const temp = forecast.maxTemperature ?? forecast.minTemperature;
  if (temp === null) return null;
  const hint = weatherHint(forecast, tx);
  return (
    <View style={styles.weatherRow}>
      <Text variant="display" weight="bold">{`${Math.round(temp)}°`}</Text>
      <Text color={color.text.body}>{`${skyLabel(forecast.skyCondition, tx)} · ${tx('부산 지금', 'Busan now')}`}</Text>
      {hint ? <Text variant="caption" color={color.text.body} style={styles.weatherHint}>{hint}</Text> : null}
    </View>
  );
}

// ── 최근 기록 3장 + 갈래 칩 (히어로 오른쪽) ────────────────────────────────────

function StoryCard({ story }: { story: StoryDto }) {
  const router = useRouter();
  const { tx } = useI18n();
  const where = story.place?.name ?? story.region ?? '';
  const initial = story.author.displayName.slice(0, 1);
  return (
    <Pressable accessibilityRole="button" onPress={() => router.push(`/feed/${story.id}`)} style={({ pressed }) => [styles.storyCard, pressed && styles.pressed]}>
      {story.images.length
        ? <Image source={{ uri: story.images[0].url }} resizeMode="cover" accessibilityLabel={tx('여행 기록 사진', 'Trip record photo')} style={styles.storyImage} />
        : <View style={[styles.storyImage, styles.storyCoverEmpty]}>
            <Text variant="title" weight="bold" color={color.text.heading} numberOfLines={5} style={styles.storyCoverEmptyText}>{markdownToPlain(story.body)}</Text>
          </View>}
      <View style={styles.storyBody}>
        <Text variant="caption" numberOfLines={1}>{where ? `${relativeStoryTime(story.createdAt, tx)} · ${where}` : relativeStoryTime(story.createdAt, tx)}</Text>
        {/* 사진이 없는 글은 본문을 커버에 이미 크게 그렸다. 여기서 또 그리면 같은 글이 두 번
            나온다 — 피드 카드(app/(tabs)/feed.tsx)가 같은 이유로 생략하는 자리다.
        */}
        {story.images.length ? <Text numberOfLines={2} color={color.text.heading}>{markdownToPlain(story.body)}</Text> : null}
        <View style={styles.storyAuthor}>
          <View style={styles.storyAvatar}><Text variant="caption" weight="bold" color={color.text.onAction}>{initial}</Text></View>
          <Text variant="caption" weight="bold" color={color.text.body} numberOfLines={1}>{story.author.displayName}</Text>
        </View>
      </View>
    </Pressable>
  );
}

export function HeroStories({ stories, chips }: { stories: StoryDto[] | null; chips: FacetKeyEntry[] }) {
  const router = useRouter();
  const { tx, language } = useI18n();
  return (
    <View style={styles.heroRight}>
      <View style={styles.heroRightHead}>
        <Text variant="eyebrow" weight="bold">{tx('지금 부산에서 남긴 기록', 'Just shared in Busan')}</Text>
 {/* 로그인 여부로 가리지 않는다, 진미리). 스토리 조회가 익명
            출입증에 열렸다 — 2026-09-18 운영에서 실측했다(X-Session-Token 으로 200).
            옛 주석은 「익명에게는 401」이라고 적혀 있었는데 그건 -974·995 전의 이야기다. */}
        <Pressable accessibilityRole="link" onPress={() => router.push('/feed')} style={styles.feedAll}>
          <Text weight="bold" color={color.text.eyebrow}>{tx('피드 전체 →', 'See all →')}</Text>
        </Pressable>
      </View>

      <View style={styles.storyGrid}>
        {stories === null
          // 로딩 중에는 같은 크기의 회색 칸을 둔다 — 카드가 늦게 들어오며 아래가 밀리지 않게.
          ? [0, 1, 2].map((slot) => <View key={slot} style={[styles.storyCard, styles.storySkeleton]} />)
          : stories.map((story) => <StoryCard key={story.id} story={story} />)}
      </View>
      {stories !== null && stories.length === 0 ? (
        <Text variant="caption" color={color.text.body}>{tx('아직 남겨진 기록이 없어요. 첫 기록을 남겨 보세요.', 'No records yet — be the first to share one.')}</Text>
      ) : null}

      {/* 칩은 「장소 태그」가 아니라 places/facets 의 갈래다. 장소에 태그 칸이 없어서
          시안대로는 못 만든다. 눌렀을 때 가는 곳도 피드 필터가 아니라 이미 있는 로컬 탐색이다
          — 피드에는 태그로 거르는 조회가 없다(그건.
      */}
      {chips.length ? (
        <>
          <View style={styles.heroRightHead}>
            <Text variant="eyebrow" weight="bold">{tx('로컬 탐색', 'Explore locally')}</Text>
            <Pressable accessibilityRole="link" onPress={() => router.push('/explore')} style={styles.feedAll}>
              <Text weight="bold" color={color.text.eyebrow}>{tx('전체 →', 'See all →')}</Text>
            </Pressable>
          </View>
          <View style={styles.chipRow}>
            {chips.map((chip) => (
              <Pressable key={chip.featureKey} accessibilityRole="link" onPress={() => router.push({ pathname: '/explore', params: { facet: chip.featureKey } })} style={({ pressed }) => [styles.chip, pressed && styles.chipPressed]}>
                <Text weight="medium">{localFacetLabel(chip, language)}</Text>
              </Pressable>
            ))}
          </View>
        </>
      ) : null}
    </View>
  );
}

// ── 장소 넷 ───────────────────────────────────────────────────────────────────

export function PlacePicks({ places }: { places: HomePlaceItem[] }) {
  const router = useRouter();
  const { tx } = useI18n();
  // 한 곳도 못 받았으면 블록을 통째로 접는다 — 제목만 남고 아래가 비면 고장난 화면으로 보인다.
  if (!places.length) return null;
  return (
    <View style={styles.placesBlock}>
      <View style={styles.blockHead}>
 {/* 시안은 「{닉네임}님 취향에 가까운 곳」이었다. 취향 축(로컬성·조용함)과 장소 갈래는
            서로 다른 체계라 이어 줄 값이 없어서, 취향을 반영한 척하지 않고 제목을 낮춘다.
            2026-09-15 에 한 번 더 낮췄다 — 「부산 대표 장소」라고 적었는데 고르는 방법이
            **부산 중심에서 가까운 순 넷**이라 대표를 판정하는 자리가 어디에도 없었다. 대표 장소를
 실제로 채우는 것은 자료 쪽 일이다. */}
        <Text variant="display" weight="bold">{tx('부산 둘러보기', 'Browse Busan')}</Text>
        <Pressable accessibilityRole="link" onPress={() => router.push('/explore')}>
          <Text color={color.text.body}>{tx('로컬 탐색에서 더 보기 →', 'Explore more →')}</Text>
        </Pressable>
      </View>
      <View style={styles.placeGrid}>
        {places.map((place) => (
          <Pressable key={place.placeId} accessibilityRole="button" onPress={() => router.push(`/place/${place.placeId}`)} style={({ pressed }) => [styles.placeCard, pressed && styles.pressed]}>
            <PlaceVisual name={place.nameKo} address={place.address} photoUrl={place.photoUrl} photoSource={place.photoSource} />
            <Text weight="bold" numberOfLines={1}>{place.nameKo}</Text>
            {/* `category` 를 그대로 그리면 화면에 「FOOD」 같은 코드가 뜬다. 그 코드를 한글로
                바꿀 표가 없다 — assistant/intent.ts 에 여섯 개짜리가 있지만 챗봇 입력을 코드로
                바꾸는 용도라 값이 더 늘면 그대로 새어 나온다. 주소는 늘 오고 사람이 읽을 수 있다.
            */}
            <Text variant="caption" numberOfLines={1}>{place.address ?? ''}</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

// ── 내 여행 ───────────────────────────────────────────────────────────────────

function formatDay(iso: string | null, tx: Tx) {
  if (!iso) return tx('날짜 미정', 'Dates TBD');
  const [, month, day] = iso.split('-');
  return tx(`${Number(month)}월 ${Number(day)}일`, `${month}/${day}`);
}

function statusLabel(trip: TripSummaryDto, tx: Tx) {
  if (trip.status === 'IN_PROGRESS') return tx('진행 중', 'In progress');
  if (trip.status === 'READY') return tx('준비 완료', 'Ready');
  return tx('예정', 'Upcoming');
}

/** 카드에 적을 날짜 범위. 시작일이 없으면 부르는 쪽이 안 쓰게 되어 있다. */
function dateRange(trip: TripSummaryDto, tx: Tx) {
  return trip.startDate && trip.endDate
    ? `${formatDay(trip.startDate, tx)} ~ ${formatDay(trip.endDate, tx)}`
    : formatDay(trip.startDate, tx);
}

export function MyTripCard({ trip, signedIn, loaded }: { trip: TripSummaryDto | null; signedIn: boolean; loaded: boolean }) {
  const router = useRouter();
  const { tx } = useI18n();
  const { accessToken } = useAuth();
  const [opening, setOpening] = useState(false);
  const openTrip = async () => {
    if (!trip || opening) return;
    setOpening(true);
    const destination = await resolveHomeTripDestination(trip.tripId, accessToken);
    setOpening(false);
    router.push(destination as never);
  };
  if (!signedIn) return null;

  return (
    <View style={styles.tripBlock}>
      <Text variant="eyebrow" weight="bold">{tx('내 여행', 'My trip')}</Text>
      {!loaded ? <View style={[styles.tripCard, styles.tripSkeleton]} /> : trip ? (
        <Pressable accessibilityRole="button" accessibilityState={{ busy: opening, disabled: opening }} disabled={opening} onPress={() => void openTrip()} style={({ pressed }) => [styles.tripCard, pressed && styles.tripCardPressed]}>
          <Text variant="caption" weight="bold" color={color.state.success}>{statusLabel(trip, tx)}</Text>
          {/* 이름이 있으면 이름, 없으면 날짜. 없는 이름을 지어내지 않는
              것은 그대로다 — 서버도 이름이 없을 때 날짜를 대신 채워 보내지 않는다.
          */}
          <Text variant="title" weight="bold">
            {tripDisplayTitle(trip, dateRange(trip, tx))}
          </Text>
          {/* 이름을 제목에 올리면 날짜가 화면에서 사라진다 — 그러면 같은 이름의 여행
              둘을 날짜로 가릴 수 없다. 이름이 있을 때만 이 줄에 날짜를 같이 적는다.
              이름이 없으면 제목이 이미 날짜라 두 번 적지 않는다 (시안 design_handoff_trip_name_flow).
          */}
          <Text color={color.text.body}>
            {trip.title?.trim() && trip.startDate
              ? tx(`${dateRange(trip, tx)} · ${trip.dayCount}일 · ${trip.partySize}명`, `${dateRange(trip, tx)} · ${trip.dayCount} days · ${trip.partySize} travelers`)
              : tx(`${trip.dayCount}일 · ${trip.partySize}명`, `${trip.dayCount} days · ${trip.partySize} travelers`)}
          </Text>
          <Text weight="bold" color={color.brand.navy} style={styles.tripGo}>{opening ? tx('일정 찾는 중…', 'Finding itinerary…') : tx('일정 보기 →', 'View itinerary →')}</Text>
        </Pressable>
      ) : (
        <View style={styles.tripEmpty}>
          <GabolleMascot state="idle" style={styles.tripMascot} />
          <View style={styles.tripEmptyCopy}>
            <Text weight="bold">{tx('아직 만든 여행이 없어요', 'No trips yet')}</Text>
            <Pressable accessibilityRole="button" onPress={() => router.push('/plan')}>
              <Text weight="bold" color={color.brand.navy}>{tx('첫 여행 만들기 →', 'Plan your first trip →')}</Text>
            </Pressable>
          </View>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  pressed: { opacity: 0.85 },

  weatherRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginTop: spacing[6], paddingTop: spacing[6], borderTopWidth: 1, borderTopColor: color.surface.border },
  weatherHint: { marginLeft: 'auto' },

  // 남는 공간이 없을 때는 아무 일도 안 한다 — 그래서 로그인 화면은 지금과 똑같다.
  heroRight: { flex: 1, gap: spacing[3], minWidth: 0, paddingTop: 40, paddingRight: 80, paddingBottom: 40, paddingLeft: spacing[6], justifyContent: 'center' },
  heroRightHead: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', gap: spacing[3] },
  feedAll: { minHeight: 44, justifyContent: 'center' },
  // flex:1 만 주면 카드가 남은 칸을 전부 나눠 먹는다. 기록이 둘뿐이면 한 장이 500px 을
  // 넘어가 사진이 화면 절반을 차지했다. 시안 폭(1440 에서 약 250)으로 상한을 둔다.
  storyGrid: { flexDirection: 'row', gap: spacing[3], alignItems: 'flex-start' },
  storyCard: { flex: 1, minWidth: 0, maxWidth: 260, borderRadius: radius.lg, overflow: 'hidden', backgroundColor: color.surface.card },
  storySkeleton: { height: 260, maxWidth: 260, backgroundColor: color.surface.soft },
  storyImage: { width: '100%', aspectRatio: 1, backgroundColor: color.surface.soft },
  // 사진이 없을 때 — 회색 빈 칸 대신 tint 에 본문을 크게. 시안 「자주 틀리는 것」 4번.
  // 빈 회색은 「사진을 못 불러왔다」로 읽힌다. feed.tsx 의 coverEmpty 와 같은 규칙이다.
  storyCoverEmpty: { alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: color.surface.tint },
  storyCoverEmptyText: { textAlign: 'center' },
  storyBody: { gap: spacing[1], paddingHorizontal: spacing[4], paddingTop: 14, paddingBottom: spacing[4] },
  storyAuthor: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], marginTop: spacing[1] },
  storyAvatar: { width: 20, height: 20, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },

  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], marginTop: spacing[1] },
  chip: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.soft },
  chipPressed: { backgroundColor: color.brand.orange },

  placesBlock: { flex: 1, gap: spacing[4], minWidth: 0 },
  blockHead: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', gap: spacing[3] },
  placeGrid: { flexDirection: 'row', gap: spacing[3] },
  placeCard: { flex: 1, minWidth: 0, gap: spacing[2] },

  tripBlock: { width: 360, gap: spacing[3] },
  // 시안은 패딩 20 인데 간격 토큰에 20 이 없다(4·8·12·16·24·32). 16 으로 내린다
  // 토큰 밖 숫자를 화면에 직접 쓰지 않는 것이 이 저장소 규칙이다.
  tripCard: { gap: spacing[2], padding: spacing[4], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card },
  tripCardPressed: { borderColor: color.brand.orange },
  tripSkeleton: { height: 160 },
  tripGo: { marginTop: spacing[2] },
  tripEmpty: { flexDirection: 'row', alignItems: 'center', gap: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card },
  tripMascot: { width: 64, height: 64 },
  tripEmptyCopy: { flex: 1, gap: spacing[1] },
});
