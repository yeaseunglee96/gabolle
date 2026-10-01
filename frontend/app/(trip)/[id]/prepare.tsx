// 16 여행 준비·날씨 — Figma 16_여행 준비·날씨 실측 그대로.
import { storyBodyText } from '@/social/courseLink';
import { txf } from '@/i18n/format';
import { localDateKey } from '@/plan/tripProgress';
import { formatMonthDay } from '@/i18n/datetime';
import { useEffect, useState } from 'react';
import { Image, Share as NativeShare, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { Button } from '@/components/Button';
import { useAuth } from '@/auth/AuthProvider';
import { useI18n } from '@/i18n';
import { DialectFlashcards } from '@/discovery/DialectFlashcards';
import { RouteMap } from '@/map/RouteMap';
import type { MapStop } from '@/map/types';
import { loadItinerary } from '@/plan/itinerary';
import { issueShareLink } from '@/share/sharedItinerary';
import { getTripStories, storyPlaceName } from '@/social/stories';
import { SelectTripFirst } from '@/trip/SelectTripFirst';
import { loadTripItineraries, loadTrips } from '@/trip/trips';
import { TripWeatherCard, weatherDateFor, weatherHeading } from '@/trip/TripWeatherPanel';
import { localToday } from '@/plan/tripBasics';

// 🔴 한국어면 손으로, 그 밖이면 «무조건 en-US» 였다 (S15P21E201-1355).
//    이제는 고른 언어에 맞는 꼴로 운영체제가 만든다.
function formatDepartureDate(value: string, locale: string) {
  return formatMonthDay(value, locale);
}

// 🔴 「가볼래가 챙긴 준비물」은 뺐다(S15P21E201-1422) — 고정 샘플(우산·신발·카드 사본, 「오후 비 예보」)이라 진짜가 아니었고,
//    같은 화면의 날씨(진짜)까지 가짜로 보이게 했다. 사투리 카드는 src/discovery/DialectFlashcards.tsx 로 갔고 여기서도 그대로 쓴다.

// — 여행 종료일이 지나면 이 탭에 추억 지도 카드를 띄운다. 계획한 경로가
// 아니라 실제로 쓴 기록(story)의 장소를 방문 순서(created_at)대로 이어 그린다 — 서버가
// 그 순서를 보장한다. 좌표 없는 기록은 선에서 빠진다(지어내지 않는다).
type MemoryMapState =
  | { status: 'not-ended' }
  | { status: 'loading' }
  | { status: 'ready'; stops: MapStop[] }
  | { status: 'unavailable' };

function isTripEnded(endDate: string | null): boolean {
  if (!endDate) return false;
  const today = localDateKey(new Date());
  return endDate < today;
}

function MemoryMapCard({ tripId, stops }: { tripId: string; stops: MapStop[] }) {
  const { tx } = useI18n();
  const router = useRouter();
  return (
    <View style={styles.memoryCard}>
      <Text variant="title" weight="bold" style={styles.prepTitle}>{tx('추억 지도', 'Memory map')}</Text>
      {stops.length > 0 ? (
        <>
          <RouteMap stops={stops} selectedId="" onSelect={(id) => router.push(`/feed/${id}`)} height={220} />
          <Text variant="caption" color={color.text.muted}>{tx('마커를 누르면 그 기록으로 이동해요.', 'Tap a marker to open that record.')}</Text>
        </>
      ) : (
        <Text variant="body" color={color.text.muted}>{tx('이 여행에는 위치가 있는 기록이 아직 없어요.', 'This trip has no records with a location yet.')}</Text>
      )}
    </View>
  );
}

// — 여행이 끝난 뒤 "부산에서의 N일" 한 장으로 되돌아보게 한다.
// 총 이동 거리는 넣지 않는다 — 실제 이동 경로 길이인지 장소 간 직선 거리 합인지
// 상세설계서에 정해져 있지 않아서다(같은 이유로 "먹은 음식"도 없다 — story.place에
// category가 없어 식당/카페인지 구분할 근거 자체가 없다). 근거 없는 숫자는 안 보여준다.
function TripSummaryCard({ tripId, title, visitCount, photoUrl }: { tripId: string; title: string; visitCount: number; photoUrl: string | null }) {
  const { tx } = useI18n();
  const { accessToken } = useAuth();
  const [sharing, setSharing] = useState(false);
  const [shareError, setShareError] = useState('');

  async function share() {
    if (!accessToken || sharing) return;
    setSharing(true);
    setShareError('');
    try {
      const issued = await issueShareLink(tripId, accessToken);
      await NativeShare.share({ title, message: txf(tx, '%s 일정을 공유해요.\n%s', 'Sharing my %s itinerary.\n%s', title, issued.shareUrl), url: issued.shareUrl });
    } catch (cause) {
      setShareError(cause instanceof ApiClientError ? cause.message : tx('공유 링크를 만들지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not create the share link. Please try again shortly.'));
    } finally {
      setSharing(false);
    }
  }

  return (
    <View style={styles.summaryCard}>
      {photoUrl ? <Image source={{ uri: photoUrl }} style={styles.summaryPhoto} /> : null}
      <Text variant="title" weight="bold">{title}</Text>
      <View style={styles.summaryStat}>
        <Text variant="display" weight="bold" color={color.action.primary}>{visitCount}</Text>
        <Text variant="caption" color={color.text.muted}>{tx('방문지', 'Places visited')}</Text>
      </View>
      <Button label={sharing ? tx('공유 링크 만드는 중…', 'Creating share link…') : tx('여행 공유하기', 'Share this trip')} variant="tertiary" disabled={sharing} onPress={() => void share()} />
      {shareError ? <Text variant="caption" color={color.state.danger}>{shareError}</Text> : null}
    </View>
  );
}

// 여행 식별자가 없으면 서버를 아예 안 부른다.
export default function Prepare() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return id ? <PrepareForTrip tripId={id} /> : <SelectTripFirst />;
}

function PrepareForTrip({ tripId }: { tripId: string }) {
  const { tx, locale, language } = useI18n();
  const router = useRouter();
  const { accessToken } = useAuth();
  // undefined = 아직 모름(불러오는 중). null = 일정이 없어 알 수 없음.
  const [firstDayDate, setFirstDayDate] = useState<string | null | undefined>(undefined);
  const [lastDayDate, setLastDayDate] = useState<string | null>(null);
  const [tripTitle, setTripTitle] = useState<string | null>(null);
  const [memoryMap, setMemoryMap] = useState<MemoryMapState>({ status: 'loading' });
  const [tripSummary, setTripSummary] = useState<{ visitCount: number; photoUrl: string | null } | null>(null);

  useEffect(() => {
    let cancelled = false;
    void loadTrips(accessToken).then(async (result) => {
      if (cancelled) return;
      const trip = result.state === 'success' ? result.trips.find((item) => item.tripId === tripId) : null;
      if (!trip || !isTripEnded(trip.endDate)) { setMemoryMap({ status: 'not-ended' }); return; }
      const storiesResult = await getTripStories(tripId, accessToken);
      if (cancelled) return;
      if (storiesResult.state !== 'success') { setMemoryMap({ status: 'unavailable' }); return; }
      const stops: MapStop[] = storiesResult.items
        .filter((story) => story.place?.lat != null && story.place?.lng != null)
        .map((story, index) => ({
          id: story.id,
          number: index + 1,
          name: (story.place ? storyPlaceName(story.place, tx, language) : null) ?? storyBodyText(story.body).slice(0, 20),
          latitude: story.place!.lat as number,
          longitude: story.place!.lng as number,
          imageUrl: story.images[0]?.url,
        }));
      setMemoryMap({ status: 'ready', stops });
      setTripSummary({
        visitCount: storiesResult.items.filter((story) => story.place != null).length,
        photoUrl: storiesResult.items.find((story) => story.images[0]?.url)?.images[0]?.url ?? null,
      });
    });
    return () => { cancelled = true; };
  }, [tripId, accessToken]);

  useEffect(() => {
    let cancelled = false;
    // : loadItinerary는 GET /api/v1/itineraries/{id}를 부르므로 일정 식별자가
    // 필요하다 — 이 화면의 tripId(여행 식별자)를 그대로 넘기면 서버에 없는 자원을 찾아
    // 404가 나고, 날씨·제목이 영영 안 뜬다. trips.ts의 다른 화면들과 같은 방식으로 먼저
    // 일정 목록을 받아 그 첫 항목의 itineraryId를 쓴다.
    void loadTripItineraries(tripId, accessToken).then(async (refsResult) => {
      if (cancelled) return;
      const itineraryId = refsResult.state === 'success' ? refsResult.itineraries[0]?.itineraryId : undefined;
      if (!itineraryId) {
        setFirstDayDate(null);
        setTripTitle(null);
        return;
      }
      const result = await loadItinerary(itineraryId, accessToken);
      if (cancelled) return;
      const date = result.state === 'success' ? (result.itinerary.days[0]?.date ?? null) : null;
      setFirstDayDate(date);
      setLastDayDate(result.state === 'success' ? (result.itinerary.days[result.itinerary.days.length - 1]?.date ?? null) : null);
      setTripTitle(result.state === 'success' ? result.itinerary.title : null);
    });
    return () => { cancelled = true; };
  }, [tripId, accessToken]);

  const departure = firstDayDate ? formatDepartureDate(firstDayDate, locale) : null;
  // 여행 중이면 오늘 날씨를 보인다(S15P21E201-1911). 머리 글도 그 날짜에 맞춘다(S15P21E201-1773).
  const weatherDate = weatherDateFor(firstDayDate, lastDayDate, localToday());
  const heading = weatherHeading(tx, weatherDate, departure, localToday());

  return (
    <Screen scroll>
      <View style={styles.headerRow}>
        <View style={styles.headerCopy}>
          <Eyebrow>
            {heading.eyebrow}
          </Eyebrow>
          <Text variant="display" weight="bold" style={styles.title}>
            {heading.title}
          </Text>
        </View>
      </View>

      {memoryMap.status === 'ready' && tripSummary && tripTitle && (
        <TripSummaryCard tripId={tripId} title={tripTitle} visitCount={tripSummary.visitCount} photoUrl={tripSummary.photoUrl} />
      )}

      {memoryMap.status === 'ready' && <MemoryMapCard tripId={tripId} stops={memoryMap.stops} />}

      <TripWeatherCard date={weatherDate} />

      {/* : 기념품샵 진입 카드는 최초 배포에서 뺐다 — 기념품샵 갈래 장소가
          0곳이라 눌러도 항상 빈 목록만 나온다. /{tripId}/souvenirs 라우트는 그대로 있다.
      */}

      <View style={styles.dialectSection}><DialectFlashcards /></View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  headerCopy: {
    flex: 1,
    gap: spacing[1],
    // 우측 상단에 상시 떠 있는 언어 배지(GlobalLanguageBadge)와 겹치지 않게
    // 제목 영역 오른쪽에 여백을 둔다 — 이 자리에 배지가 인라인으로 있던 것을
    // 전역 배지로 옮기면서 대신 남겨 둔 여백이다.
    paddingRight: 140,
  },
  title: {
    marginTop: spacing[1],
  },
  memoryCard: {
    marginTop: spacing[6],
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    padding: spacing[4],
    gap: spacing[3],
  },
  summaryCard: {
    marginTop: spacing[6],
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    padding: spacing[4],
    gap: spacing[2],
  },
  summaryPhoto: {
    width: '100%',
    height: 160,
    borderRadius: radius.md,
    marginBottom: spacing[2],
    backgroundColor: color.surface.soft,
  },
  summaryStat: {
    alignItems: 'flex-start',
  },
  prepTitle: {
    marginBottom: spacing[2],
  },
  dialectSection: {
    marginTop: spacing[4],
  },
});
