// 출발일 날씨 (S15P21E201-1561).
// 옛 화면(app/(trip)/[id]/prepare.tsx)의 날씨 카드를 떼어 왔다. 여행 페이지는 이것을 창으로 띄운다
// (시안: 데스크톱 오른쪽 서랍 · 폰 아래 시트).
// 「1시간별 예보」는 서버가 시간별을 싣게 되어 그린다(S15P21E201-1582) — **서버가 준 시각만.**
// 「들를 때 날씨」·일정 시각 테두리는 여행 페이지가 출발일 일정 항목을 넘겨서 그린다(S15P21E201-1586).
// 🔴 정차 시각의 칸이 서버에 없으면 옆 칸으로 대신하지 않는다 — 「—」. 숫자를 지어내지 않는다.
import { useEffect, useRef, useState } from 'react';
import { localToday } from '@/plan/tripBasics';
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
/** 폰 줄의 칸 폭과 칸 사이 — 첫 일정 칸까지 밀어 둘 거리를 이것으로 잰다. */
const HOUR_CELL_WIDTH = 60;
const HOUR_GAP = 6;

type Tx = (ko: string, en: string) => string;

/** 그날 들르는 곳 — 시각("HH:mm")과 이름. */
export type WeatherStop = { time: string; name: string };

/** 일정 항목 → 들르는 곳. 시각은 startsAt 글자 그대로(서버가 보낸 현지 시각)에서 읽는다. 시각이 없는 항목은 뺀다. */
export function weatherStops(items: ReadonlyArray<{ startsAt: string; title: string }> | null | undefined): WeatherStop[] {
  return (items ?? []).flatMap((item) => {
    const clock = /T(\d{2}:\d{2})/.exec(item.startsAt)?.[1];
    return clock ? [{ time: clock, name: item.title }] : [];
  });
}

/**
 * 정차 시각에 쓸 시간별 칸 — 가장 가까운 정시(09:46 → 10시, 09:29 → 9시)의 칸이다.
 * 기상청 시간별 값은 「그 정시의 예보」라서 내림보다 반올림이 그 시각에 가깝다.
 * 🔴 그 칸이 서버 응답에 없으면 null — 옆 칸으로 대신하지 않는다(오늘은 발표 이후 시각만 오고, 23:40 은 없는 「24시」다).
 */
export function hourForStop(stopTime: string, hourly: ReadonlyArray<HourlyForecastDto>): HourlyForecastDto | null {
  const [hour, minute] = stopTime.split(':').map(Number);
  if (!Number.isInteger(hour) || !Number.isInteger(minute)) return null;
  const key = `${String(hour + (minute >= 30 ? 1 : 0)).padStart(2, '0')}:00`;
  return hourly.find((cell) => cell.time === key) ?? null;
}

/** 폰 줄을 처음 열 때 밀어 둘 거리 — 일정이 있는 가장 이른 칸이 맨 앞에 오게. 일정 칸이 없으면 0(맨 앞부터). */
export function firstStopScrollX(hourly: ReadonlyArray<HourlyForecastDto>, stops: ReadonlyArray<WeatherStop>): number {
  const marked = new Set(stops.map((stop) => hourForStop(stop.time, hourly)).filter(Boolean));
  const index = hourly.findIndex((cell) => marked.has(cell));
  return index > 0 ? index * (HOUR_CELL_WIDTH + HOUR_GAP) : 0;
}

/** 한 칸을 사람이 읽는 조각으로 — 칸과 「들를 때 날씨」 줄이 같은 말을 하게 한 곳에서 만든다. */
function describeHour(hour: HourlyForecastDto, tx: Tx) {
  const precipitation = hour.precipitationType && hour.precipitationType !== 'NONE' ? PRECIPITATION[hour.precipitationType] : null;
  const chance = hour.precipitationProbability;
  return {
    icon: precipitation?.icon ?? (hour.skyCondition ? SKY_ICON[hour.skyCondition] : null),
    condition: precipitation ? tx(...precipitation.label) : hour.skyCondition ? tx(...SKY_LABEL[hour.skyCondition]) : null,
    temperature: hour.temperature != null ? `${Math.round(hour.temperature)}°` : null,
    chance,
    spokenChance: chance != null ? tx(`강수확률 ${chance}%`, `${chance}% chance of rain`) : null,
  };
}

/**
 * 시안의 「1시간별 예보」 — 폰은 옆으로 밀어 보는 한 줄, 넓은 화면(서랍)은 7칸 격자.
 * 넓은지는 여행 페이지가 폰/넓은 판을 고르는 것과 같은 값(useLayout().desktop)으로 본다.
 * 일정이 있는 시각의 칸은 테두리를 두르고 정차 번호를 붙인다(시안 「9시 · 1」).
 */
function HourlyForecast({ hourly, stops }: { hourly: HourlyForecastDto[]; stops: WeatherStop[] }) {
  const { tx } = useI18n();
  const { desktop } = useLayout();
  const stopNumbers = new Map<HourlyForecastDto, number[]>();
  stops.forEach((stop, index) => {
    const cell = hourForStop(stop.time, hourly);
    if (cell) stopNumbers.set(cell, [...(stopNumbers.get(cell) ?? []), index + 1]);
  });
  // 🔴 폰은 처음 열 때 첫 일정 칸으로 한 번만 밀어 둔다. 앞으로 밀면 새벽도 보인다 — 줄을 자르지 않는다.
  const scrollRef = useRef<ScrollView>(null);
  const scrolled = useRef(false);
  const startX = firstStopScrollX(hourly, stops);
  const cells = hourly.map((hour) => {
    const clock = Number(hour.time.slice(0, 2));
    const numbers = stopNumbers.get(hour);
    // 정차 번호 — 좁은 격자 칸은 시안처럼 붙여 쓴다(「9시·1」), 폰 칸은 띄운다(「9시 · 1」).
    const label = (Number.isInteger(clock) ? tx(`${clock}시`, `${clock}:00`) : hour.time) + (numbers ? `${desktop ? '·' : ' · '}${numbers.join(',')}` : '');
    const said = describeHour(hour, tx);
    return (
      <View key={hour.time} accessible accessibilityLabel={[label, said.condition, said.temperature, said.spokenChance].filter(Boolean).join(', ')} style={[styles.hourCell, desktop && styles.hourCellWide, numbers && styles.hourCellStop]}>
        <Text variant="caption" weight="bold" color={numbers ? color.text.heading : color.text.muted} numberOfLines={1}>{label}</Text>
        <Text style={styles.hourIcon}>{said.icon ?? UNKNOWN}</Text>
        <Text weight="bold">{said.temperature ?? UNKNOWN}</Text>
        <Text variant="caption" color={said.chance != null && said.chance >= RAIN_HIGHLIGHT ? color.state.info : color.text.muted}>{said.chance != null ? `${said.chance}%` : UNKNOWN}</Text>
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
        <ScrollView
          ref={scrollRef}
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.hourRow}
          onContentSizeChange={() => {
            if (scrolled.current || startX === 0) return;
            scrolled.current = true;
            scrollRef.current?.scrollTo({ x: startX, animated: false });
          }}
        >
          {cells}
        </ScrollView>
      )}
    </View>
  );
}

/** 시안의 「들를 때 날씨」 — 정차지마다 그 시각의 칸. 칸이 없으면 「—」. */
function StopWeather({ stops, hourly }: { stops: WeatherStop[]; hourly: HourlyForecastDto[] }) {
  const { tx } = useI18n();
  return (
    <View style={styles.hourly}>
      <Text variant="caption" weight="bold" color={color.text.muted}>{tx('들를 때 날씨', 'Weather at each stop')}</Text>
      <View style={styles.stopList}>
        {stops.map((stop, index) => {
          const cell = hourForStop(stop.time, hourly);
          const said = cell ? describeHour(cell, tx) : null;
          const rain = said?.chance != null ? tx(`비 ${said.chance}%`, `Rain ${said.chance}%`) : null;
          const summary = said ? [said.icon, said.temperature ?? UNKNOWN].filter(Boolean).join(' ') + (rain ? ` · ${rain}` : '') : UNKNOWN;
          return (
            <View
              key={`${stop.time}-${index}`}
              accessible
              accessibilityLabel={[stop.time, stop.name, ...(said ? [said.condition, said.temperature, said.spokenChance] : [tx('예보 없음', 'No forecast')])].filter(Boolean).join(', ')}
              style={[styles.stopRow, index > 0 && styles.stopRowDivided]}
            >
              <Text variant="caption" weight="bold" color={color.text.muted} style={styles.stopTime}>{stop.time}</Text>
              <Text weight="bold" numberOfLines={1} style={styles.stopName}>{stop.name}</Text>
              <Text variant="caption" color={color.text.body}>{summary}</Text>
            </View>
          );
        })}
      </View>
    </View>
  );
}

/**
 * `date` — 출발일. `undefined` 는 아직 모른다(불러오는 중), `null` 은 일정이 없어 알 수 없다.
 * `hourly` — 카드 아래에 「1시간별 예보」를 그린다. 여행 페이지의 창만 켠다.
 * `stops` — 그날 들르는 곳. 있으면 시간별 칸에 테두리를 두르고 「들를 때 날씨」를 그린다.
 */
export function TripWeatherCard({ date, hourly = false, stops = [] }: { date: string | null | undefined; hourly?: boolean; stops?: WeatherStop[] }) {
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
        ) : weather?.state === 'out-of-range' && weather.past ? (
          // 🔴 지난 날짜에 「그때 다시 열어 주세요」라고 하지 않는다 — 그때는 오지 않는다(S15P21E201-1773).
          <Text variant="body" color={color.text.muted}>{`${tx('지난 여행', 'Past trip')} · ${tx('예보 없음', 'No forecast')}`}</Text>
        ) : weather?.state === 'out-of-range' ? (
          <Text variant="body" color={color.text.muted}>{tx('출발일 예보는 출발 3일 전부터 보여드려요. 그때 다시 열어 주세요.', 'The departure-day forecast opens 3 days before you leave. Check back then.')}</Text>
        ) : weather ? (
          <Text variant="body" color={color.text.muted}>{tx('예보를 가져오지 못했습니다.', 'Could not load the forecast.')}</Text>
        ) : (
          <Text variant="body" color={color.text.muted}>{tx('예보를 불러오는 중…', 'Loading the forecast…')}</Text>
        )}
      </View>
      {/* 옛 서버(칸 없음)·시각이 하나도 없는 날은 줄 자체를 안 그린다 — 빈 줄에 「—」를 늘어놓지 않는다.
          「들를 때 날씨」도 같다 — 시간별이 하나도 없으면 모든 줄이 「—」라 말할 것이 없다. */}
      {hourly && weather?.state === 'success' && weather.hourly.length > 0 ? (
        <>
          <HourlyForecast hourly={weather.hourly} stops={stops} />
          {stops.length > 0 ? (
            <>
              <StopWeather stops={stops} hourly={weather.hourly} />
              <Text variant="caption" color={color.text.muted} style={styles.hourly}>{tx('테두리는 일정이 있는 시간이에요. 예보는 부산 전체 기준이에요.', 'Outlined hours have a stop. The forecast covers all of Busan.')}</Text>
            </>
          ) : null}
        </>
      ) : null}
    </>
  );
}

/**
 * 창에 띄우는 꼴 — 머리(여행 전 · 출발일) + 카드.
 * `items` — 출발일의 일정 항목. 여행 페이지가 그날 일정을 넘긴다(없으면 시간별 줄만).
 */
/**
 * 머리 글의 때 — S15P21E201-1764. 전에는 날짜와 무관하게 늘 「여행 전 · 출발일 날씨」였다.
 * Play 35 실기기에서 당일 여행(진행 중) 밤 9시에 열어도 「여행 전」이라고 적었다.
 * 날짜는 YYYY-MM-DD 글자끼리 비교한다(같은 꼴이라 글자 순서가 곧 날짜 순서다).
 */
export function weatherPhase(date: string | null | undefined, today: string): 'before' | 'today' | 'after' {
  const day = date?.slice(0, 10);
  if (!day) return 'before';
  if (day === today) return 'today';
  return day > today ? 'before' : 'after';
}

/**
 * 머리 글(작은 머리 + 제목)을 날짜에 맞춰 고른다 — 여행 페이지 창과 여행 준비 화면(prepare)이 같이 쓴다.
 * 🔴 S15P21E201-1773: 준비 화면은 이 판정을 안 거치고 늘 「여행 전 · … 출발」이라 적었다(지난 여행에도).
 */
export function weatherHeading(
  tx: (ko: string, en: string) => string,
  date: string | null | undefined,
  departure: string | null,
  today: string,
): { eyebrow: string; title: string } {
  const phase = weatherPhase(date, today);
  const eyebrow = phase === 'today'
    ? tx('여행 중 · 오늘', 'On the trip · Today')
    : phase === 'after'
      ? (departure ? txf(tx, '여행 중 · %s 출발', 'On the trip · Departed %s', departure) : tx('여행 중', 'On the trip'))
      : (departure ? txf(tx, '여행 전 · %s 출발', 'Before the trip · Departing %s', departure) : tx('여행 전', 'Before the trip'));
  const title = phase === 'today' ? tx('오늘 날씨', "Today's weather") : tx('출발일 날씨', 'Departure-day weather');
  return { eyebrow, title };
}

export function TripWeatherPanel({ date, items, today = localToday() }: { date: string | null | undefined; items?: ReadonlyArray<{ startsAt: string; title: string }> | null; today?: string }) {
  const { tx, locale } = useI18n();
  const departure = date ? formatMonthDay(date, locale) : null;
  const { eyebrow, title } = weatherHeading(tx, date, departure, today);
  return (
    <View>
      <Eyebrow>{eyebrow}</Eyebrow>
      <Text variant="display" weight="bold" style={styles.title}>{title}</Text>
      <TripWeatherCard date={date} hourly stops={weatherStops(items)} />
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
  hourRow: { gap: HOUR_GAP },
  hourGrid: { flexDirection: 'row', flexWrap: 'wrap', marginHorizontal: -3 },
  // 7칸 — 칸 사이 6px 은 칸마다 양옆 3px 로 낸다(퍼센트 폭에서 gap 을 빼는 계산이 RN 에 없다).
  hourGridSlot: { width: '14.2857%', padding: 3 },
  // 테두리 자리는 모든 칸에 비워 둔다 — 일정 칸만 굵어지면 그 칸만 커져서 줄이 들쭉날쭉해진다.
  hourCell: {
    width: HOUR_CELL_WIDTH, alignItems: 'center', gap: 6, paddingVertical: 10, paddingHorizontal: 2,
    borderRadius: radius.md, borderWidth: 2, borderColor: 'transparent', backgroundColor: color.surface.tint,
  },
  hourCellWide: { width: '100%', gap: spacing[1], paddingVertical: spacing[2], paddingHorizontal: 0 },
  hourCellStop: { borderColor: color.text.heading },
  hourIcon: { fontSize: 20, lineHeight: 24 },
  // ── 들를 때 날씨 ──
  stopList: { borderRadius: radius.lg, backgroundColor: color.surface.tint, paddingHorizontal: spacing[4], paddingVertical: spacing[1] },
  stopRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 48 },
  stopRowDivided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.surface.border },
  stopTime: { width: 44 },
  stopName: { flex: 1, minWidth: 0 },
});
