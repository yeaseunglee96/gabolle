// 출발일 날씨 (S15P21E201-1561).
// 옛 화면(app/(trip)/[id]/prepare.tsx)의 날씨 카드를 떼어 왔다. 여행 페이지는 이것을 창으로 띄운다
// (시안: 데스크톱 오른쪽 서랍 · 폰 아래 시트).
// 🔴 시안의 «1시간별 예보»·«들를 때 날씨» 는 없다 — 서버에 출발일 하루치 예보만 있다. 숫자를 지어내지 않는다.
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { useAuth } from '@/auth/AuthProvider';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { formatMonthDay } from '@/i18n/datetime';
import { txf } from '@/i18n/format';
import { loadWeatherForecast, type SkyCondition, type WeatherLoadResult } from '@/trip/weather';

const SKY_LABEL: Record<SkyCondition, readonly [string, string]> = {
  CLEAR: ['맑음', 'Clear'],
  PARTLY_CLOUDY: ['구름 조금', 'Partly cloudy'],
  CLOUDY: ['흐림', 'Cloudy'],
};

/**
 * `date` — 출발일. `undefined` 는 아직 모른다(불러오는 중), `null` 은 일정이 없어 알 수 없다.
 */
export function TripWeatherCard({ date }: { date: string | null | undefined }) {
  const { tx } = useI18n();
  const { accessToken } = useAuth();
  const [weather, setWeather] = useState<WeatherLoadResult | null>(null);

  useEffect(() => {
    if (date === undefined) { setWeather(null); return; }
    if (date === null) { setWeather({ state: 'unavailable', message: '일정을 아직 못 불러왔어요.' }); return; }
    let cancelled = false;
    setWeather(null);
    void loadWeatherForecast(date, accessToken).then((result) => { if (!cancelled) setWeather(result); });
    return () => { cancelled = true; };
  }, [date, accessToken]);

  return (
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
            <Text variant="caption" weight="bold" color={color.state.info}>{tx('☂ 우산을 챙기세요', '☂ Bring an umbrella')}</Text>
          ) : null}
        </>
      ) : weather?.state === 'out-of-range' ? (
        <Text variant="body" color={color.text.muted}>{tx('출발일 예보는 출발 3일 전부터 보여드려요. 그때 다시 열어 주세요.', 'The departure-day forecast opens 3 days before you leave. Check back then.')}</Text>
      ) : weather ? (
        <Text variant="body" color={color.text.muted}>{tx('예보를 가져오지 못했습니다.', 'Could not load the forecast.')}</Text>
      ) : (
        <Text variant="body" color={color.text.muted}>{tx('예보를 불러오는 중…', 'Loading the forecast…')}</Text>
      )}
    </View>
  );
}

/** 창에 띄우는 꼴 — 머리(여행 전 · 출발일) + 카드. */
export function TripWeatherPanel({ date }: { date: string | null | undefined }) {
  const { tx, locale } = useI18n();
  const departure = date ? formatMonthDay(date, locale) : null;
  return (
    <View>
      <Eyebrow>{departure ? txf(tx, '여행 전 · %s 출발', 'Before the trip · Departing %s', departure) : tx('여행 전', 'Before the trip')}</Eyebrow>
      <Text variant="display" weight="bold" style={styles.title}>{tx('출발일 날씨', 'Departure-day weather')}</Text>
      <TripWeatherCard date={date} />
    </View>
  );
}

const styles = StyleSheet.create({
  title: { marginTop: spacing[1] },
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
});
