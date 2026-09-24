// 출발일 날씨 (S15P21E201-1561).
// 옛 화면(app/(trip)/[id]/prepare.tsx)의 날씨 카드를 떼어 왔다. 여행 페이지는 이것을 창으로 띄운다
// (시안: 데스크톱 오른쪽 서랍 · 폰 아래 시트).
// 「1시간별 예보」는 서버가 시간별을 싣게 되어 그린다(S15P21E201-1582) — **서버가 준 시각만.**
// 🔴 시안의 «들를 때 날씨»·일정 시각 테두리는 아직 없다 — 이 창은 일정 항목을 안 받는다. 숫자를 지어내지 않는다.
import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { useAuth } from '@/auth/AuthProvider';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { formatMonthDay } from '@/i18n/datetime';
import { txf } from '@/i18n/format';
import { useLayout } from '@/layout/useLayout';
import { loadWeatherForecast, type HourlyForecastDto, type PrecipitationType, type SkyCondition, type WeatherLoadResult } from '@/trip/weather';

const SKY_LABEL: Record<SkyCondition, readonly [string, string]> = {
  CLEAR: ['맑음', 'Clear'],
  PARTLY_CLOUDY: ['구름 조금', 'Partly cloudy'],
  CLOUDY: ['흐림', 'Cloudy'],
};

// 시간별 칸의 그림 — 시안 그대로 글자 그림이다. 비·눈이 오면 하늘보다 그것을 먼저 그린다.
const SKY_ICON: Record<SkyCondition, string> = { CLEAR: '☀', PARTLY_CLOUDY: '⛅', CLOUDY: '☁' };
const PRECIPITATION: Record<Exclude<PrecipitationType, 'NONE'>, { icon: string; label: readonly [string, string] }> = {
  RAIN: { icon: '🌧', label: ['비', 'Rain'] },
  SHOWER: { icon: '🌧', label: ['소나기', 'Showers'] },
  SNOW: { icon: '🌨', label: ['눈', 'Snow'] },
  RAIN_SNOW: { icon: '🌨', label: ['비/눈', 'Rain and snow'] },
};
/** 강수확률이 이만큼 이상이면 파랗게 — 시안의 기준이다. */
const RAIN_HIGHLIGHT = 30;
/** 모르는 값(null)의 자리. 0 으로 그리면 「0%」·「0°」라고 단언하는 것이 된다. */
const UNKNOWN = '—';

/**
 * 시안의 「1시간별 예보」 — 폰은 옆으로 밀어 보는 한 줄, 넓은 화면(서랍)은 7칸 격자.
 * 넓은지는 여행 페이지가 폰/넓은 판을 고르는 것과 같은 값(useLayout().desktop)으로 본다.
 */
function HourlyForecast({ hourly }: { hourly: HourlyForecastDto[] }) {
  const { tx } = useI18n();
  const { desktop } = useLayout();
  const cells = hourly.map((hour) => {
    const clock = Number(hour.time.slice(0, 2));
    const label = Number.isInteger(clock) ? tx(`${clock}시`, `${clock}:00`) : hour.time;
    const precipitation = hour.precipitationType && hour.precipitationType !== 'NONE' ? PRECIPITATION[hour.precipitationType] : null;
    const sky = hour.skyCondition ? tx(...SKY_LABEL[hour.skyCondition]) : null;
    const temperature = hour.temperature != null ? `${Math.round(hour.temperature)}°` : null;
    const chance = hour.precipitationProbability;
    const spoken = [label, precipitation ? tx(...precipitation.label) : sky, temperature, chance != null ? tx(`강수확률 ${chance}%`, `${chance}% chance of rain`) : null];
    return (
      <View key={hour.time} accessible accessibilityLabel={spoken.filter(Boolean).join(', ')} style={[styles.hourCell, desktop && styles.hourCellWide]}>
        <Text variant="caption" weight="bold" color={color.text.muted}>{label}</Text>
        <Text style={styles.hourIcon}>{precipitation?.icon ?? (hour.skyCondition ? SKY_ICON[hour.skyCondition] : UNKNOWN)}</Text>
        <Text weight="bold">{temperature ?? UNKNOWN}</Text>
        <Text variant="caption" color={chance != null && chance >= RAIN_HIGHLIGHT ? color.state.info : color.text.muted}>{chance != null ? `${chance}%` : UNKNOWN}</Text>
      </View>
    );
  });
  return (
    <View style={styles.hourly}>
      <Text variant="caption" weight="bold" color={color.text.muted}>{tx('1시간별 예보', 'Hourly forecast')}</Text>
      {desktop ? (
        <View style={styles.hourGrid}>
          {cells.map((cell) => <View key={cell.key} style={styles.hourGridSlot}>{cell}</View>)}
        </View>
      ) : (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.hourRow}>{cells}</ScrollView>
      )}
    </View>
  );
}

/**
 * `date` — 출발일. `undefined` 는 아직 모른다(불러오는 중), `null` 은 일정이 없어 알 수 없다.
 * `hourly` — 카드 아래에 「1시간별 예보」를 그린다. 여행 페이지의 창만 켠다.
 */
export function TripWeatherCard({ date, hourly = false }: { date: string | null | undefined; hourly?: boolean }) {
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
    <>
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
      {/* 옛 서버(칸 없음)·시각이 하나도 없는 날은 줄 자체를 안 그린다 — 빈 줄에 「—」를 늘어놓지 않는다. */}
      {hourly && weather?.state === 'success' && weather.hourly.length > 0 ? <HourlyForecast hourly={weather.hourly} /> : null}
    </>
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
      <TripWeatherCard date={date} hourly />
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
  // ── 1시간별 예보 — 시안 TripPageMobile/Desktop.dc.html 의 hours 칸 ──
  hourly: { marginTop: spacing[4], gap: spacing[2] },
  hourRow: { gap: 6 },
  hourGrid: { flexDirection: 'row', flexWrap: 'wrap', marginHorizontal: -3 },
  // 7칸 — 칸 사이 6px 은 칸마다 양옆 3px 로 낸다(퍼센트 폭에서 gap 을 빼는 계산이 RN 에 없다).
  hourGridSlot: { width: '14.2857%', padding: 3 },
  hourCell: {
    width: 60, alignItems: 'center', gap: 6, paddingVertical: 10, paddingHorizontal: spacing[1],
    borderRadius: radius.md, backgroundColor: color.surface.tint,
  },
  hourCellWide: { width: '100%', gap: spacing[1], paddingVertical: spacing[2], paddingHorizontal: 2 },
  hourIcon: { fontSize: 20, lineHeight: 24 },
});
