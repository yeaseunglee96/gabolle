// 16 여행 준비·날씨 — Figma 16_여행 준비·날씨 실측 그대로.
//
// 날씨는 GET /api/v1/weather 로 실제 값을 받는다(S15P21E201-378) — 준비물 목록은
// 아직 하드코딩 목업이다(별도 티켓 범위).
import { useEffect, useState } from 'react';
import { Image, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Speech from 'expo-speech';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { Button } from '@/components/Button';
import { useAuth } from '@/auth/AuthProvider';
import { useI18n } from '@/i18n';
import { DIALECT_PHRASES } from '@/discovery/dialectPhrases';
import { RouteMap } from '@/map/RouteMap';
import type { MapStop } from '@/map/types';
import { loadItinerary } from '@/plan/itinerary';
import { getTripStories } from '@/social/stories';
import { loadTrips } from '@/trip/trips';
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

export default function Prepare() {
  const router = useRouter();
  const { tx } = useI18n();
  const { accessToken } = useAuth();
  const { id } = useLocalSearchParams<{ id: string }>();
  const tripId = id ?? 'demo-trip';
  const [firstDayDate, setFirstDayDate] = useState<string | null>(null);
  const [weather, setWeather] = useState<WeatherLoadResult | null>(null);
  const [memoryMap, setMemoryMap] = useState<MemoryMapState>({ status: 'loading' });

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
    });
    return () => { cancelled = true; };
  }, [tripId, accessToken]);

  useEffect(() => {
    let cancelled = false;
    void loadItinerary(tripId, accessToken).then((result) => {
      if (cancelled) return;
      const date = result.state === 'success' ? (result.itinerary.days[0]?.date ?? null) : null;
      setFirstDayDate(date);
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

      <View style={styles.prepCard}>
        <Text variant="title" weight="bold" style={styles.prepTitle}>
          {tx('가볼래가 챙긴 준비물', 'What GABOLLE packed for you')}
        </Text>
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

      <View style={styles.rainCard}>
        <Text variant="caption" weight="bold" color={color.text.accent}>
          {tx('비 예보 대응', 'Responding to the rain forecast')}
        </Text>
        <Text variant="title" weight="bold" style={styles.rainTitle}>
          {tx('야외 1곳을 실내 코스로 바꿀까요?', 'Swap 1 outdoor stop for an indoor one?')}
        </Text>
        <Text variant="caption" style={styles.rainDesc}>
          {tx('흰여울 → 국립해양박물관 · 이동 12분 감소', 'Huinnyeoul → National Maritime Museum · 12 min less travel')}
        </Text>
      </View>

      <DialectFlashcards />

      <Button
        label={tx('대체 일정 미리보기', 'Preview the alternative plan')}
        variant="field"
        containerStyle={styles.cta}
        onPress={() => router.push(`/${tripId}/result`)}
      />
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
  rainCard: {
    marginTop: spacing[4],
    backgroundColor: color.surface.soft,
    borderRadius: radius.md,
    padding: spacing[4],
    gap: spacing[1],
  },
  rainTitle: {
    marginTop: spacing[1],
  },
  rainDesc: {
    color: color.text.body,
  },
  cta: {
    marginTop: spacing[6],
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
