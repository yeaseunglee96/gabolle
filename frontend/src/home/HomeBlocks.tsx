// 데스크톱 홈의 블록들 (S15P21E201-970). 시안 `design_handoff_home` 1a.
//
// 🔴 시안에 있지만 **그리지 않은 것 둘** — 서버에 그 값이 없다.
//   · 여행 카드의 **제목** — TripSummaryDto 에 제목 칸이 없다(날짜·일수·인원·상태뿐)
//   · 장소 카드의 **사진** — photoUrl 은 상세의 선택 필드이고 늘 비어 있다. 목록엔 칸도 없다.
//     채우는 작업(S15P21E201-146)이 머지되고 목록 API 에 실리면 그때 넣는다.
//
// 🔴 정정 (2026-09-16, S15P21E201-1023) — 위 둘 중 **제목은 이제 그린다.** 서버가
// TripSummaryDto 에 `title` 을 주기 시작했다. 사진은 여전히 위 문단 그대로다.
import { useState } from 'react';
import { Image, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { GabolleMascot } from '@/components/DongbaekMascot';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
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
  // 최고기온을 쓰고, 없으면 최저라도 보여준다. 둘 다 없으면 줄 자체를 안 그린다 —
  // 「—도」 라고 적으면 값이 있는 것처럼 보인다.
  const temp = forecast.maxTemperature ?? forecast.minTemperature;
  if (temp === null) return null;
  const hint = weatherHint(forecast, tx);
  return (
    <View style={styles.weatherRow}>
      <Text variant="display" weight="bold" color={color.text.onAction}>{`${Math.round(temp)}°`}</Text>
      <Text color={color.text.onDarkMuted}>{`${skyLabel(forecast.skyCondition, tx)} · ${tx('부산 지금', 'Busan now')}`}</Text>
      {hint ? <Text variant="caption" color={color.text.onDarkMuted} style={styles.weatherHint}>{hint}</Text> : null}
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
        : <View style={styles.storyImage} />}
      <View style={styles.storyBody}>
        <Text variant="caption" numberOfLines={1}>{where ? `${relativeStoryTime(story.createdAt, tx)} · ${where}` : relativeStoryTime(story.createdAt, tx)}</Text>
        <Text numberOfLines={2} color={color.text.heading}>{story.body}</Text>
        <View style={styles.storyAuthor}>
          <View style={styles.storyAvatar}><Text variant="caption" weight="bold" color={color.text.onAction}>{initial}</Text></View>
          <Text variant="caption" weight="bold" color={color.text.body} numberOfLines={1}>{story.author.displayName}</Text>
        </View>
      </View>
    </Pressable>
  );
}

export function HeroStories({ stories, chips, signedIn }: { stories: StoryDto[] | null; chips: FacetKeyEntry[]; signedIn: boolean }) {
  const router = useRouter();
  const { tx, language } = useI18n();
  return (
    <View style={styles.heroRight}>
      <View style={styles.heroRightHead}>
        <Text variant="eyebrow" weight="bold">{tx('지금 부산에서 남긴 기록', 'Just shared in Busan')}</Text>
        {signedIn ? (
          <Pressable accessibilityRole="link" onPress={() => router.push('/feed')} style={styles.feedAll}>
            <Text weight="bold" color={color.text.onAction}>{tx('피드 전체 →', 'See all →')}</Text>
          </Pressable>
        ) : null}
      </View>

      {/* 🔴 로그인 안 한 사람에게 「아직 기록이 없어요」라고 하면 거짓말이다 — 기록은 있는데
          서버가 익명에게는 안 준다(스토리 조회가 401). 못 보는 이유를 그대로 적는다. */}
      {!signedIn ? (
        <View style={styles.signInPrompt}>
          <Text color={color.text.onDarkMuted}>{tx('다른 여행자들이 남긴 기록은 로그인하면 볼 수 있어요.', 'Sign in to see what other travelers shared.')}</Text>
          <Pressable accessibilityRole="button" onPress={() => router.push('/sign-in')} style={styles.signInButton}>
            <Text weight="bold" color={color.brand.navy}>{tx('로그인하고 보기', 'Sign in to view')}</Text>
          </Pressable>
        </View>
      ) : (
        <>
          <View style={styles.storyGrid}>
            {stories === null
              // 로딩 중에는 같은 크기의 회색 칸을 둔다 — 카드가 늦게 들어오며 아래가 밀리지 않게.
              ? [0, 1, 2].map((slot) => <View key={slot} style={[styles.storyCard, styles.storySkeleton]} />)
              : stories.map((story) => <StoryCard key={story.id} story={story} />)}
          </View>
          {stories !== null && stories.length === 0 ? (
            <Text variant="caption" color={color.text.onDarkMuted}>{tx('아직 남겨진 기록이 없어요. 첫 기록을 남겨 보세요.', 'No records yet — be the first to share one.')}</Text>
          ) : null}
        </>
      )}

      {/* 🔴 칩은 「장소 태그」가 아니라 places/facets 의 갈래다. 장소에 태그 칸이 없어서
          시안대로는 못 만든다. 눌렀을 때 가는 곳도 피드 필터가 아니라 이미 있는 로컬 탐색이다
          — 피드에는 태그로 거르는 조회가 없다(그건 S15P21E201-971).

          🔴 2026-09-15 에 제목을 붙였다(-989). 그전에는 알약 몇 개만 떠 있어서 그것이 로컬
          탐색으로 가는 입구라는 것을 알 수 없었다 — 이 앱에서 로컬 탐색은 하단 탭에도 상단
          바에도 없고 이 칩이 거의 유일한 길이다. 위 기록 구역과 같은 머리 모양을 쓴다.

          🔴 갈래를 하나도 못 받으면 **머리까지 통째로 접는다.** 제목만 남고 아래가 비면
          고장난 화면으로 보인다 — 아래 장소 블록이 같은 이유로 같은 판단을 한다. 갈래 조회가
          비는 것은 드문 일이 아니다(서버가 안 붙은 개발 환경에서 실제로 그랬다). */}
      {chips.length ? (
        <>
          <View style={styles.heroRightHead}>
            <Text variant="eyebrow" weight="bold">{tx('로컬 탐색', 'Explore locally')}</Text>
            <Pressable accessibilityRole="link" onPress={() => router.push('/explore')} style={styles.feedAll}>
              <Text weight="bold" color={color.text.onAction}>{tx('전체 →', 'See all →')}</Text>
            </Pressable>
          </View>
          <View style={styles.chipRow}>
            {chips.map((chip) => (
              <Pressable key={chip.featureKey} accessibilityRole="link" onPress={() => router.push({ pathname: '/explore', params: { facet: chip.featureKey } })} style={({ pressed }) => [styles.chip, pressed && styles.chipPressed]}>
                <Text weight="medium" color={color.text.onAction}>{localFacetLabel(chip, language)}</Text>
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
        {/* 🔴 시안은 「{닉네임}님 취향에 가까운 곳」이었다. 취향 축(로컬성·조용함)과 장소 갈래는
            서로 다른 체계라 이어 줄 값이 없어서, 취향을 반영한 척하지 않고 제목을 낮춘다.
            2026-09-15 에 한 번 더 낮췄다 — 「부산 대표 장소」라고 적었는데 고르는 방법이
            **부산 중심에서 가까운 순 넷**이라 대표를 판정하는 자리가 어디에도 없었다. 대표 장소를
            실제로 채우는 것은 자료 쪽 일이다(S15P21E201-920). */}
        <Text variant="display" weight="bold">{tx('부산 둘러보기', 'Browse Busan')}</Text>
        <Pressable accessibilityRole="link" onPress={() => router.push('/explore')}>
          <Text color={color.text.body}>{tx('로컬 탐색에서 더 보기 →', 'Explore more →')}</Text>
        </Pressable>
      </View>
      <View style={styles.placeGrid}>
        {places.map((place) => (
          <Pressable key={place.placeId} accessibilityRole="button" onPress={() => router.push(`/place/${place.placeId}`)} style={({ pressed }) => [styles.placeCard, pressed && styles.pressed]}>
            {/* 장소 사진이 아직 안 온다 — 빈 칸을 두되 「사진 없음」이라고 적지는 않는다. */}
            <View style={styles.placeThumb} />
            <Text weight="bold" numberOfLines={1}>{place.nameKo}</Text>
            {/* 🔴 `category` 를 그대로 그리면 화면에 「FOOD」 같은 **코드**가 뜬다. 그 코드를 한글로
                바꿀 표가 없다 — assistant/intent.ts 에 여섯 개짜리가 있지만 챗봇 입력을 코드로
                바꾸는 용도라 값이 더 늘면 그대로 새어 나온다. 주소는 늘 오고 사람이 읽을 수 있다. */}
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
          {/* 🔴 이름이 있으면 이름, 없으면 날짜 (S15P21E201-1023). 없는 이름을 지어내지 않는
              것은 그대로다 — 서버도 이름이 없을 때 날짜를 대신 채워 보내지 않는다. */}
          <Text variant="title" weight="bold">
            {tripDisplayTitle(trip, trip.startDate && trip.endDate ? `${formatDay(trip.startDate, tx)} ~ ${formatDay(trip.endDate, tx)}` : formatDay(trip.startDate, tx))}
          </Text>
          <Text color={color.text.body}>
            {tx(`${trip.dayCount}일 · ${trip.partySize}명`, `${trip.dayCount} days · ${trip.partySize} travelers`)}
          </Text>
          <Text weight="bold" color={color.brand.navy} style={styles.tripGo}>{opening ? tx('일정 찾는 중…', 'Finding itinerary…') : tx('일정 보기 →', 'View itinerary →')}</Text>
        </Pressable>
      ) : (
        <View style={styles.tripEmpty}>
          <GabolleMascot state="idle" style={styles.tripMascot} />
          <View style={styles.tripEmptyCopy}>
            <Text weight="bold">{tx('아직 만든 여행이 없어요', 'No trips yet')}</Text>
            <Pressable accessibilityRole="button" onPress={() => router.push('/plan/basic')}>
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

  weatherRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginTop: spacing[6], paddingTop: spacing[6], borderTopWidth: 1, borderTopColor: 'rgba(255,255,255,0.14)' },
  weatherHint: { marginLeft: 'auto' },

  // 시안 1a 의 오른쪽 칸 패딩(위 40 · 오른 80 · 아래 40 · 왼 24). 이게 없어서 카드와
  // 「피드 전체 →」가 화면 오른쪽 끝에 붙었고, 칩 아래가 네이비 판 바닥에 닿아 다음 구역과
  // 딱 붙어 보였다.
  heroRight: { flex: 1, gap: spacing[3], minWidth: 0, paddingTop: 40, paddingRight: 80, paddingBottom: 40, paddingLeft: spacing[6] },
  heroRightHead: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', gap: spacing[3] },
  feedAll: { minHeight: 44, justifyContent: 'center' },
  // 🔴 flex:1 만 주면 카드가 남은 칸을 전부 나눠 먹는다. 기록이 둘뿐이면 한 장이 500px 을
  //    넘어가 사진이 화면 절반을 차지했다. 시안 폭(1440 에서 약 250)으로 상한을 둔다.
  storyGrid: { flexDirection: 'row', gap: spacing[3], alignItems: 'flex-start' },
  signInPrompt: { gap: spacing[3], alignItems: 'flex-start', padding: spacing[4], borderRadius: radius.lg, backgroundColor: 'rgba(255,255,255,0.08)' },
  signInButton: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card },
  storyCard: { flex: 1, minWidth: 0, maxWidth: 260, borderRadius: radius.lg, overflow: 'hidden', backgroundColor: color.surface.card },
  storySkeleton: { height: 260, maxWidth: 260, backgroundColor: 'rgba(255,255,255,0.12)' },
  storyImage: { width: '100%', aspectRatio: 1, backgroundColor: color.surface.soft },
  storyBody: { gap: spacing[1], paddingHorizontal: spacing[4], paddingTop: 14, paddingBottom: spacing[4] },
  storyAuthor: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], marginTop: spacing[1] },
  storyAvatar: { width: 20, height: 20, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },

  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], marginTop: spacing[1] },
  chip: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.1)' },
  chipPressed: { backgroundColor: color.brand.orange },

  placesBlock: { flex: 1, gap: spacing[4], minWidth: 0 },
  blockHead: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', gap: spacing[3] },
  placeGrid: { flexDirection: 'row', gap: spacing[3] },
  placeCard: { flex: 1, minWidth: 0, gap: spacing[2] },
  placeThumb: { width: '100%', aspectRatio: 4 / 3, borderRadius: radius.md, backgroundColor: color.surface.soft },

  tripBlock: { width: 360, gap: spacing[3] },
  // 시안은 패딩 20 인데 간격 토큰에 20 이 없다(4·8·12·16·24·32). 16 으로 내린다 —
  // 토큰 밖 숫자를 화면에 직접 쓰지 않는 것이 이 저장소 규칙이다.
  tripCard: { gap: spacing[2], padding: spacing[4], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card },
  tripCardPressed: { borderColor: color.brand.orange },
  tripSkeleton: { height: 160 },
  tripGo: { marginTop: spacing[2] },
  tripEmpty: { flexDirection: 'row', alignItems: 'center', gap: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card },
  tripMascot: { width: 64, height: 64 },
  tripEmptyCopy: { flex: 1, gap: spacing[1] },
});
