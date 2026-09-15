// 16 여행 준비·날씨 — Figma 16_여행 준비·날씨 실측 그대로.
//
// 날씨는 GET /api/v1/weather 로 실제 값을 받는다(S15P21E201-378) — 준비물 목록은
// 아직 하드코딩 목업이다(별도 티켓 범위).
import { useEffect, useState } from 'react';
import { Image, Pressable, Share as NativeShare, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Speech from 'expo-speech';

import { ApiClientError } from '@/api/client';
import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { Button } from '@/components/Button';
import { SampleNotice } from '@/components/SampleNotice';
import { useAuth } from '@/auth/AuthProvider';
import { useI18n } from '@/i18n';
import { DIALECT_PHRASES } from '@/discovery/dialectPhrases';
import { RouteMap } from '@/map/RouteMap';
import type { MapStop } from '@/map/types';
import { loadItinerary } from '@/plan/itinerary';
import { issueShareLink } from '@/share/sharedItinerary';
import { getTripStories } from '@/social/stories';
import { SelectTripFirst } from '@/trip/SelectTripFirst';
import { loadTripItineraries, loadTrips } from '@/trip/trips';
import { loadWeatherForecast, type SkyCondition, type WeatherLoadResult } from '@/trip/weather';

const SKY_LABEL: Record<SkyCondition, readonly [string, string]> = {
  CLEAR: ['맑음', 'Clear'],
  PARTLY_CLOUDY: ['구름 조금', 'Partly cloudy'],
  CLOUDY: ['흐림', 'Cloudy'],
};

function formatDepartureDate(value: string) {
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return null;
  return { ko: `${date.getMonth() + 1}월 ${date.getDate()}일`, en: date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) };
}

const PREP_ITEMS = [
  { icon: require('../../../assets/icons/common/umbrella.png'), nameKo: '접이식 우산', nameEn: 'Folding umbrella', descKo: '오후 비 예보', descEn: 'Rain forecast in the afternoon' },
  { icon: require('../../../assets/icons/common/shoes.png'), nameKo: '미끄럼 적은 신발', nameEn: 'Non-slip shoes', descKo: '흰여울 경사 구간', descEn: 'Huinnyeoul has a slope section' },
  { icon: require('../../../assets/icons/common/idcard.png'), nameKo: '해외카드·여권 사본', nameEn: 'Overseas card · passport copy', descKo: '현장 결제 대비', descEn: 'In case you need to pay on site' },
];

function DialectFlashcards() {
  const { tx } = useI18n();
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [speakingId, setSpeakingId] = useState<string | null>(null);

  function listen(phrase: (typeof DIALECT_PHRASES)[number]) {
    try {
      Speech.stop();
      setSpeakingId(phrase.id);
      Speech.speak(phrase.dialect, { language: 'ko-KR', rate: 0.9, onDone: () => setSpeakingId(null), onStopped: () => setSpeakingId(null), onError: () => setSpeakingId(null) });
    } catch {
      // 소리 기능이 없는 브라우저(Web Speech API 미지원 등)에서도 카드는 그대로 둔다.
      setSpeakingId(null);
    }
  }

  return (
    <View style={styles.dialectSection}>
      <Text variant="title" weight="bold" style={styles.prepTitle}>{tx('부산 사투리 한마디', 'A word of Busan dialect')}</Text>
      <View style={styles.dialectList}>
        {DIALECT_PHRASES.map((phrase) => {
          const expanded = expandedId === phrase.id;
          return (
            <View key={phrase.id} style={[styles.dialectCard, expanded && styles.dialectCardExpanded]}>
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ expanded }}
                onPress={() => setExpandedId(expanded ? null : phrase.id)}
                style={styles.dialectCardHeader}
              >
                <Text variant={expanded ? 'display' : 'title'} weight="bold">{phrase.dialect}</Text>
                {!expanded ? <Text variant="caption" color={color.text.muted}>{tx('눌러서 뜻 보기', 'Tap to see meaning')}</Text> : null}
              </Pressable>
              {expanded ? (
                <>
                  <View style={styles.dialectMeaningRow}>
                    <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('표준어', 'Standard Korean')}</Text>
                    <Text variant="body" color={color.text.body}>{phrase.standard}</Text>
                  </View>
                  <View style={styles.dialectMeaningRow}>
                    <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('영문 뜻', 'English meaning')}</Text>
                    <Text variant="body" color={color.text.body}>{phrase.en}</Text>
                  </View>
                  <Text variant="caption" color={color.text.muted}>{tx(phrase.situationKo, phrase.situationEn)}</Text>
                  <Pressable accessibilityRole="button" accessibilityLabel={tx(`${phrase.dialect} 발음 듣기`, `Listen to ${phrase.dialect}`)} onPress={() => listen(phrase)} style={styles.listenButton}>
                    <Text variant="caption" weight="bold" color={color.text.onAction}>{speakingId === phrase.id ? tx('재생 중', 'Playing') : tx('▶ 듣기', '▶ Listen')}</Text>
                  </Pressable>
                </>
              ) : null}
            </View>
          );
        })}
      </View>
    </View>
  );
}

// S15P21E201-248 — 여행 종료일이 지나면 이 탭에 추억 지도 카드를 띄운다. 계획한 경로가
// 아니라 실제로 쓴 기록(story)의 장소를 방문 순서(created_at)대로 이어 그린다 — 서버가
// 그 순서를 보장한다(S15P21E201-829). 좌표 없는 기록은 선에서 빠진다(지어내지 않는다).
type MemoryMapState =
  | { status: 'not-ended' }
  | { status: 'loading' }
  | { status: 'ready'; stops: MapStop[] }
  | { status: 'unavailable' };

function isTripEnded(endDate: string | null): boolean {
  if (!endDate) return false;
  const today = new Date().toISOString().slice(0, 10);
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

// S15P21E201-251 — 여행이 끝난 뒤 "부산에서의 N일" 한 장으로 되돌아보게 한다.
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
      await NativeShare.share({ title, message: tx(`${title} 일정을 공유해요.\n${issued.shareUrl}`, `Sharing my ${title} itinerary.\n${issued.shareUrl}`), url: issued.shareUrl });
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
        <Text variant="display" weight="bold" color={color.brand.orange}>{visitCount}</Text>
        <Text variant="caption" color={color.text.muted}>{tx('방문지', 'Places visited')}</Text>
      </View>
      <Button label={sharing ? tx('공유 링크 만드는 중…', 'Creating share link…') : tx('여행 공유하기', 'Share this trip')} variant="ghost" disabled={sharing} onPress={() => void share()} />
      {shareError ? <Text variant="caption" color={color.state.danger}>{shareError}</Text> : null}
    </View>
  );
}

// 여행 식별자가 없으면 서버를 아예 안 부른다 (S15P21E201-1000).
export default function Prepare() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return id ? <PrepareForTrip tripId={id} /> : <SelectTripFirst />;
}

function PrepareForTrip({ tripId }: { tripId: string }) {
  const { tx } = useI18n();
  const router = useRouter();
  const { accessToken } = useAuth();
  const [firstDayDate, setFirstDayDate] = useState<string | null>(null);
  const [tripTitle, setTripTitle] = useState<string | null>(null);
  const [weather, setWeather] = useState<WeatherLoadResult | null>(null);
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
          name: story.place?.name ?? story.body.slice(0, 20),
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
    // S15P21E201-912: loadItinerary는 GET /api/v1/itineraries/{id}를 부르므로 일정 식별자가
    // 필요하다 — 이 화면의 tripId(여행 식별자)를 그대로 넘기면 서버에 없는 자원을 찾아
    // 404가 나고, 날씨·제목이 영영 안 뜬다. trips.ts의 다른 화면들과 같은 방식으로 먼저
    // 일정 목록을 받아 그 첫 항목의 itineraryId를 쓴다.
    void loadTripItineraries(tripId, accessToken).then(async (refsResult) => {
      if (cancelled) return;
      const itineraryId = refsResult.state === 'success' ? refsResult.itineraries[0]?.itineraryId : undefined;
      if (!itineraryId) {
        setFirstDayDate(null);
        setTripTitle(null);
        setWeather({ state: 'unavailable', message: '일정을 아직 못 불러왔어요.' });
        return;
      }
      const result = await loadItinerary(itineraryId, accessToken);
      if (cancelled) return;
      const date = result.state === 'success' ? (result.itinerary.days[0]?.date ?? null) : null;
      setFirstDayDate(date);
      setTripTitle(result.state === 'success' ? result.itinerary.title : null);
      if (!date) { setWeather({ state: 'unavailable', message: '일정을 아직 못 불러왔어요.' }); return; }
      void loadWeatherForecast(date, accessToken).then((weatherResult) => { if (!cancelled) setWeather(weatherResult); });
    });
    return () => { cancelled = true; };
  }, [tripId, accessToken]);

  const departure = firstDayDate ? formatDepartureDate(firstDayDate) : null;

  return (
    <Screen scroll>
      <View style={styles.headerRow}>
        <View style={styles.headerCopy}>
          <Eyebrow>
            {departure ? tx(`여행 전 · ${departure.ko} 출발`, `Before the trip · Departing ${departure.en}`) : tx('여행 전', 'Before the trip')}
          </Eyebrow>
          <Text variant="display" weight="bold" style={styles.title}>
            {tx('부산 여행 준비', 'Getting ready for Busan')}
          </Text>
        </View>
      </View>

      {memoryMap.status === 'ready' && tripSummary && tripTitle && (
        <TripSummaryCard tripId={tripId} title={tripTitle} visitCount={tripSummary.visitCount} photoUrl={tripSummary.photoUrl} />
      )}

      {memoryMap.status === 'ready' && <MemoryMapCard tripId={tripId} stops={memoryMap.stops} />}

      <View style={styles.weatherCard}>
        {weather?.state === 'success' ? (
          <>
            <View style={styles.weatherTopRow}>
              <Text variant="hero" weight="bold" color={color.text.heading}>
                {weather.forecast.maxTemperature != null ? `${Math.round(weather.forecast.maxTemperature)}°` : tx('미확인', 'N/A')}
              </Text>
              <View style={styles.weatherStatus}>
                <Text variant="body" weight="bold">
                  {weather.forecast.skyCondition ? tx(...SKY_LABEL[weather.forecast.skyCondition]) : tx('하늘 상태 미확인', 'Sky condition unknown')}
                  {weather.forecast.minTemperature != null && weather.forecast.maxTemperature != null ? ` · ${Math.round(weather.forecast.minTemperature)}~${Math.round(weather.forecast.maxTemperature)}°` : ''}
                </Text>
              </View>
            </View>
            <Text variant="body" weight="medium" style={styles.weatherRain}>
              {weather.forecast.precipitationProbability != null
                ? tx(`강수확률 ${weather.forecast.precipitationProbability}%`, `${weather.forecast.precipitationProbability}% chance of rain`)
                : tx('강수확률 미확인', 'Rain chance unknown')}
            </Text>
            {weather.forecast.precipitationProbability != null && weather.forecast.precipitationProbability >= 60 ? (
              <Text variant="caption" weight="bold" color={color.brand.orange}>{tx('☂ 우산을 챙기세요', '☂ Bring an umbrella')}</Text>
            ) : null}
          </>
        ) : weather ? (
          <Text variant="body" color={color.text.muted}>{tx('예보를 가져오지 못했습니다.', 'Could not load the forecast.')}</Text>
        ) : (
          <Text variant="body" color={color.text.muted}>{tx('예보를 불러오는 중…', 'Loading the forecast…')}</Text>
        )}
      </View>

      {/* S15P21E201-900: 기념품샵 진입 카드는 최초 배포에서 뺐다 — 기념품샵 갈래 장소가
          0곳이라 눌러도 항상 빈 목록만 나온다. /{tripId}/souvenirs 라우트는 그대로 있다. */}

      <View style={styles.prepCard}>
        <Text variant="title" weight="bold" style={styles.prepTitle}>
          {tx('가볼래가 챙긴 준비물', 'What GABOLLE packed for you')}
        </Text>
        {/* S15P21E201-1009 — 이 목록만 고정 목업이다. 같은 화면의 날씨는 실제 값이라
            화면 전체에 표시를 달면 진짜인 것까지 가짜라고 말하게 된다. */}
        <SampleNotice
          badge={tx('샘플', 'Sample')}
          description={tx('준비물 목록은 아직 고정된 예시예요. 위의 날씨는 실제 예보예요.', 'This packing list is still a fixed example. The weather above is a real forecast.')}
        />
        {PREP_ITEMS.map((item) => (
          <View key={item.nameKo} style={styles.prepRow}>
            <Image source={item.icon} resizeMode="contain" style={styles.prepIcon} />
            <View style={styles.prepBody}>
              <Text variant="body" weight="bold">
                {tx(item.nameKo, item.nameEn)}
              </Text>
            </View>
            <Text variant="caption" style={styles.prepDesc}>
              {tx(item.descKo, item.descEn)}
            </Text>
          </View>
        ))}
      </View>

      <DialectFlashcards />
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
    // 전역 배지로 옮기면서(S15P21E201-261) 대신 남겨 둔 여백이다.
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
  weatherCard: {
    marginTop: spacing[6],
    backgroundColor: color.surface.tint,
    borderRadius: radius.lg,
    padding: spacing[4],
    gap: spacing[2],
  },
  weatherTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
  },
  weatherStatus: {
    gap: spacing[1],
  },
  weatherRain: {
    color: color.text.heading,
  },
  prepCard: {
    marginTop: spacing[4],
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    padding: spacing[4],
    gap: spacing[3],
  },
  prepTitle: {
    marginBottom: spacing[1],
  },
  prepRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
  },
  prepBody: {
    flex: 1,
  },
  prepIcon: {
    width: 26,
    height: 26,
  },
  prepDesc: {
    color: color.text.body,
  },
  dialectSection: {
    marginTop: spacing[4],
  },
  dialectList: {
    gap: spacing[3],
    marginTop: spacing[1],
  },
  dialectCard: {
    minHeight: 84,
    justifyContent: 'center',
    gap: spacing[2],
    padding: spacing[4],
    borderRadius: radius.lg,
    backgroundColor: color.surface.card,
    borderWidth: 1,
    borderColor: color.surface.field,
  },
  dialectCardHeader: {
    gap: spacing[2],
  },
  dialectCardExpanded: {
    borderColor: color.brand.orange,
    borderWidth: 2,
    backgroundColor: color.surface.warm,
  },
  dialectMeaningRow: {
    gap: spacing[1],
  },
  listenButton: {
    marginTop: spacing[1],
    minHeight: 36,
    paddingHorizontal: spacing[3],
    borderRadius: radius.full,
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'flex-start',
    backgroundColor: color.brand.navy,
  },
});
