// 시안에 있지만 그리지 않은 것 둘 — 서버에 그 값이 없다.
// · 여행 카드의 제목 — TripSummaryDto 에 제목 칸이 없다(날짜·일수·인원·상태뿐)
// · 장소 카드의 사진 — photoUrl 은 상세의 선택 필드이고 늘 비어 있다. 목록엔 칸도 없다.
// 채우는 작업이 머지되고 목록 API 에 실리면 그때 넣는다.
import { storyBodyText } from '@/social/courseLink';
import { enCount, enPlural, txf } from '@/i18n/format';
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { AppImage } from '@/components/AppImage';
import { useRouter, type Href } from 'expo-router';

import { GabolleMascot } from '@/components/DongbaekMascot';
import { PlaceVisual } from '@/components/PlaceVisual';
import { Text } from '@/components/Text';
import { HeartIcon } from '@/components/HeartIcon';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { markdownToPlain } from '@/social/markdown';
import { AuthorAvatar } from '@/social/AuthorAvatar';
import { useAuth } from '@/auth/AuthProvider';
import { resolveHomeTripDestination } from './tripNavigation';
import { HomeRow, homeCardWidth } from './HomeRow';
import type { HomeCardPlace, HomeFacetRow } from './useHomeData';
import { addressForLanguage } from '@/discovery/localAddress';
import { otherNameFor } from '@/discovery/localNames';
import { placeNameForLanguage } from '@/discovery/romanize';
import { relativeStoryTime, type StoryDto, storyPlaceName } from '@/social/stories';
import type { TripSummaryDto } from '@/trip/trips';
import { humanTripTitle, tripDatesLabel, tripNameOrDates } from '@/trip/tripNaming';
// 🔴 상태 글자는 여행 목록 카드와 같은 함수다 — 날짜가 서버 상태를 이긴다(S15P21E201-1595). 전에는 서버 status 를
//    그대로 읽는 홈 전용 함수가 따로 있어서, 오늘 진행 중인 여행에도 「준비 완료」가 붙었다.
import { effectiveTripStatus, tripStatusLabel } from '@/trip/tripStatus';
import { headerWeather, localTimeText, type CurrentWeather, type DailyForecastDto, type HeaderWeather, type HourlyForecastDto } from '@/trip/weather';
import { regionText } from '@/social/districtNames';


type Tx = (ko: string, en: string) => string;

// ── 날씨 (상단 바 오른쪽, 「여행 만들기」 왼쪽) ───────────────────────────────

function skyLabel(sky: DailyForecastDto['skyCondition'], tx: Tx) {
  if (sky === 'CLEAR') return tx('맑음', 'Clear');
  if (sky === 'PARTLY_CLOUDY') return tx('구름 조금', 'Partly cloudy');
  if (sky === 'CLOUDY') return tx('흐림', 'Cloudy');
  return tx('날씨 정보 없음', 'No weather data');
}

/**
 * 상단 바 오른쪽에 서는 날씨. 전에는 히어로 아래 한 줄(WeatherLine)이었다.
 *
 * <p>그 줄이 시작 바와 첫 기록 줄 사이를 가로로 갈라놓아서, 홈에 들어온 사람의 눈이
 * 시작 바에서 한 번 끊겼다. 날씨는 «지금 부산이 어떤가»를 곁들이는 정보지 길을
 * 막을 것이 아니라, 상단 바 구석으로 옮긴다.
 *
 * <p>🔴 힌트 문구(「걷기 좋은 날이에요」)는 같이 안 옮겼다 — 상단 바에 자리가 없다.
 * 그 문구를 만들던 weatherHint 도 쓰는 곳이 없어져 걷어냈다. 되살릴 일이 생기면
 * 이 커밋을 보면 된다.
 */
/** 머리말 날씨의 이름표 — 값의 뜻을 따른다(S15P21E201-1981). 전에는 최고기온에 「부산 지금」을 붙였다. */
export function headerWeatherLabel(kind: HeaderWeather['kind'], tx: Tx): string {
  return kind === 'now' ? tx('지금', 'Now') : kind === 'high' ? tx('오늘 최고', "Today's high") : tx('오늘 최저', "Today's low");
}

type HeaderForecast = DailyForecastDto & { current?: CurrentWeather; hourly?: HourlyForecastDto[] };

/**
 * 머리말 날씨 `[ 하늘 지금 N° ]` — 넓은 화면 상단 바와 폰 홈 칩이 같이 쓴다(S15P21E201-1981).
 * 지금 기온이 없으면 `[ 하늘 오늘 최고 N° ]`. 값이 하나도 없으면 아무것도 안 그린다.
 * compact 면 하늘과 기온만 — 좁은 상단 바에서 「晴れ · 今の釜山」이 메뉴 「マイ旅行」에 붙어 버렸다(768 세로 탭).
 */
export function HeaderWeatherText({ forecast, compact = false, now = new Date() }: { forecast: HeaderForecast | null; compact?: boolean; now?: Date }) {
  const { tx } = useI18n();
  if (!forecast) return null;
  const shown = headerWeather(forecast, localTimeText(now));
  if (shown === null) return null;
  return (
    <View style={styles.weatherRow} accessible accessibilityLabel={`${skyLabel(shown.sky, tx)} · ${headerWeatherLabel(shown.kind, tx)} ${Math.round(shown.value)}°`}>
      {shown.icon ? <Text variant="body" accessibilityElementsHidden importantForAccessibility="no">{shown.icon}</Text> : null}
      {compact ? null : <Text variant="caption" color={color.text.body}>{headerWeatherLabel(shown.kind, tx)}</Text>}
      <Text variant="title" weight="bold">{`${Math.round(shown.value)}°`}</Text>
    </View>
  );
}

/** 상단 바 오른쪽에 서는 날씨. 전에는 히어로 아래 한 줄(WeatherLine)이었다 — 시작 바와 첫 기록 줄 사이를 갈라놓았다. */
export function TopNavWeather({ forecast, compact = false }: { forecast: HeaderForecast | null; compact?: boolean }) {
  return <HeaderWeatherText forecast={forecast} compact={compact} />;
}

// ── 최근 기록 3장 + 갈래 칩 (히어로 오른쪽) ────────────────────────────────────

function StoryCard({ story, cardWidth }: { story: StoryDto; cardWidth: number }) {
  const router = useRouter();
  const { tx, language } = useI18n();
  const where = (story.place ? storyPlaceName(story.place, tx, language) : null) ?? (story.region ? regionText(story.region, tx) : null) ?? '';
  const body = markdownToPlain(storyBodyText(story.body)).trim();
  const hasImage = story.images.length > 0;
  // 🔴 같은 글자를 두 번 그리지 않는다 — S15P21E201-1372. 예전에는 제목이 장소, 부제가
  //    「2일 전 · 장소」라서 장소가 두 줄에 나란히 두 번 나왔다(2026-09-21 배포본 실측).
  //    사진이 있으면 본문 첫 줄이 제목이고 장소는 부제에만 간다(시안 5 Home). 사진이 없으면
  //    본문이 이미 커버에 크게 있으니 제목은 장소(없으면 「{작성자}의 기록」), 부제는 시간만.
  const heading = hasImage && body ? body : where || txf(tx, '%s의 기록', "%s's record", story.author.displayName);
  const when = relativeStoryTime(story.createdAt, tx);
  const sub = where && heading !== where ? `${when} · ${where}` : when;
  const square = { width: cardWidth, height: cardWidth };
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={txf(tx, '%s 기록 보기', 'Open record: %s', heading)}
      onPress={() => router.push(`/feed/${story.id}`)}
      style={({ pressed }) => [{ width: cardWidth }, pressed && styles.pressed]}
    >
      {story.images.length
        ? <AppImage source={{ uri: story.images[0].url }} resizeMode="cover" accessibilityLabel={tx('여행 기록 사진', 'Trip record photo')} style={[styles.storyImage, square]} />
        // 사진이 없을 때 회색 빈 칸을 두지 않는다 — tint 바탕에 본문을 크게. 빈 회색은
        // 「사진을 못 불러왔다」로 읽힌다. feed.tsx 의 coverEmpty 와 같은 규칙이다.
        : <View style={[styles.storyImage, styles.storyCoverEmpty, square]}>
            <Text variant="title" weight="bold" color={color.text.heading} numberOfLines={4} style={styles.storyCoverEmptyText}>{body}</Text>
          </View>}
      <View style={styles.storyBody}>
        <Text variant="body" weight="bold" color={color.text.heading} numberOfLines={1}>{heading}</Text>
        <Text variant="caption" color={color.text.body} numberOfLines={1}>{sub}</Text>
        <View style={styles.storyAuthor}>
          <AuthorAvatar name={story.author.displayName} uri={story.author.avatarUrl} style={styles.storyAvatar} />
          <Text variant="caption" weight="bold" color={color.text.body} numberOfLines={1}>{story.author.displayName}</Text>
        </View>
      </View>
    </Pressable>
  );
}

export function StoryRow({
  stories,
  width,
  cardWidth: cardWidthOverride,
  gutter,
  arrows,
}: {
  stories: StoryDto[] | null;
  width: number;
  /** 폰은 160 정사각이다. 안 주면 데스크톱 계산(한 줄에 일곱 장)을 쓴다. */
  cardWidth?: number;
  gutter?: number;
  arrows?: boolean;
}) {
  const router = useRouter();
  const { tx } = useI18n();
  const cardWidth = cardWidthOverride ?? homeCardWidth(width);
  return (
    <HomeRow
      title={tx('지금 부산에서 남긴 기록', 'Just shared in Busan')}
      openLabel={tx('기록 전체 보기', 'See all records')}
      onOpen={() => router.push('/feed')}
      width={width}
      gutter={gutter}
      arrows={arrows}
    >
      {/* 로그인 여부로 가리지 않는다(진미리). 스토리 조회가 익명 출입증에 열렸다 —
          2026-09-18 운영에서 실측했다(X-Session-Token 으로 200). */}
      {stories === null
        // 불러오는 중에는 같은 크기의 빈 칸을 둔다 — 카드가 늦게 들어오며 아래가 밀리지 않게.
        ? [0, 1, 2, 3, 4, 5, 6].map((slot) => (
            <View key={slot} style={[styles.storySkeleton, { width: cardWidth, height: cardWidth }]} />
          ))
        : stories.map((story) => <StoryCard key={story.id} story={story} cardWidth={cardWidth} />)}

      {stories !== null && stories.length === 0 ? (
        // 🔴 가로로 미는 줄 안이라 폭을 주지 않으면 줄을 안 바꾸고 화면 밖으로 나갔다(일본어 실측, S15P21E201-1867). 줄 폭만큼으로 묶는다.
        <Text variant="caption" color={color.text.body} style={{ width: Math.max(160, width - (gutter ?? 24) * 2) }}>
          {tx('아직 남겨진 기록이 없어요. 첫 기록을 남겨 보세요.', 'No records yet — be the first to share one.')}
        </Text>
      ) : null}
    </HomeRow>
  );
}

// ── 로컬 탐색 줄 (축제 · 전통시장) ────────────────────────────────────────────

function PlaceCard({
  place,
  cardWidth,
  liked,
  onToggleLike,
}: {
  place: HomeCardPlace;
  cardWidth: number;
  liked: boolean;
  onToggleLike: () => void;
}) {
  const router = useRouter();
  const { tx, language } = useI18n();
  // 🔴 이름·주소가 언어와 상관없이 한국어였다(S15P21E201-1877). 한국어 화면은 그대로 둔다.
  const shownName = language === 'ko' ? place.nameKo : placeNameForLanguage(place.nameKo, otherNameFor(place.nameEn, place.localNames, language), language);
  return (
    <View style={{ width: cardWidth }}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={txf(tx, '%s 상세 보기', 'View details for %s', place.nameKo)}
        onPress={() => router.push(`/place/${place.placeId}`)}
        style={({ pressed }) => [styles.placeCard, pressed && styles.pressed]}
      >
        {/* PlaceVisual 의 기본 비율은 4:3 이다. style 이 뒤에 붙으므로 여기서 정사각으로 덮는다
            — 부품을 고치지 않는다. 사진이 없으면 BUSAN 자리표시가 그대로 나온다(회색 판 금지).
            출처 띠도 PlaceVisual 이 그린다 — 공공누리 표기는 이용 조건이라 빼면 안 된다. */}
        <PlaceVisual
          name={place.nameKo}
          address={place.address}
          photoUrl={place.photoUrl}
          photoSource={place.photoSource}
          photoSubject={place.photoSubject}
          photoLicense={place.photoLicense}
          style={styles.placePhoto}
        />
      </Pressable>

      {/* 하트 — 누르면 빨갛게 찬다(HeartIcon). 전에는 켜지면 흰 원이 생겨 좋아요로 안 읽혔다(사용자 의견 2026-10-02).
          색만으로 상태를 말하지 않는다 — 꺼짐은 빈 하트(옅은 어둠), 켜짐은 찬 하트로 모양부터 다르다. */}
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ selected: liked }}
        accessibilityLabel={liked
          ? txf(tx, '%s 저장 해제', 'Unsave %s', place.nameKo)
          : txf(tx, '%s 저장', 'Save %s', place.nameKo)}
        onPress={onToggleLike}
        style={styles.heartButton}
      >
        <HeartIcon filled={liked} />
      </Pressable>

      <Text variant="body" weight="bold" color={color.text.heading} numberOfLines={1} style={styles.placeName}>{shownName}</Text>
      <Text variant="caption" color={color.text.body} numberOfLines={1}>{addressForLanguage(place, language)}</Text>
    </View>
  );
}

export function PlaceRow({
  row,
  width,
  likedIds,
  onToggleLike,
  cardWidth: cardWidthOverride,
  gutter,
  arrows,
}: {
  row: HomeFacetRow;
  width: number;
  likedIds: Set<string>;
  onToggleLike: (placeId: string) => void;
  cardWidth?: number;
  gutter?: number;
  arrows?: boolean;
}) {
  const router = useRouter();
  const { tx, language } = useI18n();
  const cardWidth = cardWidthOverride ?? homeCardWidth(width);
  const places = row.places;

  // 한 곳도 못 받았으면 줄을 통째로 접는다 — 제목만 남고 아래가 비면 고장난 화면으로 보인다.
  if (places !== null && places.length === 0) return null;

  return (
    <HomeRow
      eyebrow={tx('로컬 탐색', 'Explore locally')}
      title={tx(row.titleKo, row.titleEn)}
      openLabel={tx('이 갈래 전체 보기', 'See all in this category')}
      // 줄마다 여는 곳이 다르다 — 축제는 날짜로 거르는 축제 화면, 나머지는 로컬 탐색(S15P21E201-1594).
      onOpen={() => router.push(row.href as Href)}
      width={width}
      gutter={gutter}
      arrows={arrows}
    >
      {places === null
        ? [0, 1, 2, 3, 4, 5, 6].map((slot) => (
            <View key={slot} style={[styles.storySkeleton, { width: cardWidth, height: cardWidth }]} />
          ))
        : places.map((place) => (
            <PlaceCard
              key={place.placeId}
              place={place}
              cardWidth={cardWidth}
              liked={likedIds.has(place.placeId)}
              onToggleLike={() => onToggleLike(place.placeId)}
            />
          ))}
    </HomeRow>
  );
}

// ── 내 여행 ───────────────────────────────────────────────────────────────────

/** 카드에 적을 날짜 범위 — 여행 목록과 같은 표기(tripDatesLabel, S15P21E201-1738). */
function dateRange(trip: TripSummaryDto, tx: Tx, locale: string) {
  return tripDatesLabel(trip.startDate, trip.endDate, locale) ?? tx('날짜 미정', 'Dates TBD');
}

/**
 * 내 여행 카드.
 *
 * @param layout `'column'`(기본) — 홈의 폭 360 세로 카드. `'row'` — 마이페이지 넓은 화면의 가로형:
 *     칸 폭을 다 쓰고, 왼쪽에 글자·오른쪽에 「일정 보기 →」, 선 없는 흰 카드
 *     (시안 design_handoff_mypage_v2 변경점 3, S15P21E201-1526). 🔴 홈은 안 바뀐다 — 기본값이 그대로다.
 */
export function MyTripCard({ trip, signedIn, loaded, layout = 'column', hasTrips = false }: { trip: TripSummaryDto | null; signedIn: boolean; loaded: boolean; layout?: 'column' | 'row'; /** 지난 여행이라도 있나 — 빈 칸 문구를 가른다(S15P21E201-1770). */ hasTrips?: boolean }) {
  const router = useRouter();
  const { tx, locale } = useI18n();
  const { accessToken } = useAuth();
  const [opening, setOpening] = useState(false);
  const openTrip = async () => {
    if (!trip || opening) return;
    setOpening(true);
    const destination = await resolveHomeTripDestination(trip, accessToken);
    setOpening(false);
    router.push(destination as never);
  };
  if (!signedIn) return null;
  const row = layout === 'row';

  // 🔴 함수로 둔다 — 여행이 없을 때(trip === null) 미리 만들면 상태 글자(tripStatusLabel)가 null 을 읽다 터진다.
  const tripCopy = (t: TripSummaryDto) => <>
    <Text variant="caption" weight="bold" color={color.state.success}>{tripStatusLabel(effectiveTripStatus(t), tx)}</Text>
    {/* 이름이 있으면 이름, 없으면 날짜. 없는 이름을 지어내지 않는
        것은 그대로다 — 서버도 이름이 없을 때 날짜를 대신 채워 보내지 않는다.
    */}
    <Text variant="title" weight="bold">
      {tripNameOrDates(t, tx, locale)}
    </Text>
    {/* 이름을 제목에 올리면 날짜가 화면에서 사라진다 — 그러면 같은 이름의 여행
        둘을 날짜로 가릴 수 없다. 이름이 있을 때만 이 줄에 날짜를 같이 적는다.
        이름이 없으면 제목이 이미 날짜라 두 번 적지 않는다 (시안 design_handoff_trip_name_flow).
    */}
    <Text color={color.text.body}>
      {humanTripTitle(t.title) && t.startDate
        ? txf(tx, '%s · %s일 · %s명', `%s · %s ${enPlural(t.dayCount, 'day', 'days')} · %s ${enPlural(t.partySize, 'traveler', 'travelers')}`, dateRange(t, tx, locale), t.dayCount, t.partySize)
        : tx(`${t.dayCount}일 · ${t.partySize}명`, `${enCount(t.dayCount, 'day', 'days')} · ${enCount(t.partySize, 'traveler', 'travelers')}`)}
    </Text>
  </>;

  return (
    <View style={[styles.tripBlock, row && styles.tripBlockRow]}>
      <Text variant="eyebrow" weight="bold">{tx('내 여행', 'My trip')}</Text>
      {!loaded ? <View style={[styles.tripCard, styles.tripSkeleton, row && styles.tripSkeletonRow]} /> : trip ? (
        <Pressable accessibilityRole="button" accessibilityState={{ busy: opening, disabled: opening }} disabled={opening} onPress={() => void openTrip()} style={({ pressed }) => [styles.tripCard, row && styles.tripCardRow, pressed && (row ? styles.pressed : styles.tripCardPressed)]}>
          {row ? <View style={styles.tripRowCopy}>{tripCopy(trip)}</View> : tripCopy(trip)}
          <Text weight="bold" color={color.brand.navy} style={row ? styles.tripGoRow : styles.tripGo}>{opening ? tx('일정 찾는 중…', 'Finding itinerary…') : tx('일정 보기 →', 'View itinerary →')}</Text>
        </Pressable>
      ) : (
        <View style={[styles.tripEmpty, row && styles.tripEmptyRow]}>
          <GabolleMascot state="idle" style={styles.tripMascot} />
          <View style={styles.tripEmptyCopy}>
            <Text weight="bold">{hasTrips ? tx('다가오는 여행이 없어요', 'No upcoming trips') : tx('아직 만든 여행이 없어요', 'No trips yet')}</Text>
            <Pressable accessibilityRole="button" onPress={() => router.push('/plan')}>
              <Text weight="bold" color={color.brand.navy}>{hasTrips ? tx('새 여행 만들기 →', 'Plan a new trip →') : tx('첫 여행 만들기 →', 'Plan your first trip →')}</Text>
            </Pressable>
          </View>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  pressed: { opacity: 0.85 },

  // 상단 바 안이라 위쪽 구분선도 여백도 없다. 바가 이미 자기 높이를 가진다.
  weatherRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },

  // 줄 배치에서는 카드 폭을 줄 부품(HomeRow)이 정해서 내려준다 — 여기서 상한을 두면
  // 한 줄에 몇 장이 보이는지가 두 곳에서 정해지고, 둘이 어긋나면 줄마다 장 수가 달라진다.
  storySkeleton: { borderRadius: radius.md, backgroundColor: color.surface.soft },
  storyImage: { borderRadius: radius.md, backgroundColor: color.surface.soft },
  // 사진이 없을 때 — 회색 빈 칸 대신 tint 에 본문을 크게. 시안 「자주 틀리는 것」 1번.
  // 빈 회색은 「사진을 못 불러왔다」로 읽힌다. feed.tsx 의 coverEmpty 와 같은 규칙이다.
  storyCoverEmpty: { alignItems: 'center', justifyContent: 'center', padding: spacing[3] },
  storyCoverEmptyText: { textAlign: 'center' },
  // 카드에 테두리와 배경을 두지 않는다 — 사진이 곧 카드다(시안 1·2). 글은 사진 아래에 붙는다.
  storyBody: { gap: spacing[1], paddingTop: spacing[3] },
  storyAuthor: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], marginTop: spacing[1] },
  storyAvatar: { width: 18, height: 18, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },

  placeCard: { position: 'relative' },
  placePhoto: { aspectRatio: 1 },
  placeName: { marginTop: spacing[3] },
  // 누르는 자리는 44 로 두고 보이는 원은 28 이다 — 손가락은 44 를 필요로 하는데
  // 28 보다 큰 동그라미를 사진 위에 얹으면 사진을 가린다.
  heartButton: { position: 'absolute', top: 0, right: 0, width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },

  tripBlock: { width: 360, gap: spacing[3] },
  // 시안은 패딩 20 인데 간격 토큰에 20 이 없다(4·8·12·16·24·32). 16 으로 내린다
  // 토큰 밖 숫자를 화면에 직접 쓰지 않는 것이 이 저장소 규칙이다.
  tripCard: { gap: spacing[2], padding: spacing[4], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card },
  tripCardPressed: { borderColor: color.action.secondary },
  tripSkeleton: { height: 160 },
  tripGo: { marginTop: spacing[2] },
  tripEmpty: { flexDirection: 'row', alignItems: 'center', gap: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card },
  tripMascot: { width: 64, height: 64 },
  tripEmptyCopy: { flex: 1, gap: spacing[1] },

  // ── layout="row" — 마이페이지 넓은 화면 (시안 design_handoff_mypage_v2) ──
  // 칸 폭을 다 쓴다. 눈썹과 카드 사이 8, 카드 아래 16(다음 눈썹 「내 계정」까지).
  tripBlockRow: { width: '100%', gap: spacing[2], marginBottom: spacing[4] },
  // 🔴 선 없는 흰 카드 — tokens 규칙 3(카드에 선·그림자 없음). 세로형은 홈 시안이 선을 둬서 그대로 둔다.
  tripCardRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[4], borderWidth: 0 },
  tripRowCopy: { flex: 1, minWidth: 0, gap: spacing[1] },
  tripGoRow: { flexShrink: 0 },
  tripSkeletonRow: { height: 96, borderWidth: 0 },
  tripEmptyRow: { borderWidth: 0 },
});
