// 부산 사투리 한마디 — 독립 화면. S15P21E201-1422.
//
// 전에는 여행 준비물 화면 맨 아래에만 있어 여행을 고르고 들어가야 겨우 보였다. 여행 없이도 바로 열리고,
// 위에 오늘 부산 날씨 한 줄을 얹어 「현장에 있다」는 느낌을 준다(예보는 진짜 값, 없으면 줄 자체를 안 그린다).
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { useAuth } from '@/auth/AuthProvider';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { DialectFlashcards } from '@/discovery/DialectFlashcards';
import { useI18n } from '@/i18n';
import { loadWeatherForecast, type SkyCondition, type WeatherLoadResult } from '@/trip/weather';

const SKY_LABEL: Record<SkyCondition, readonly [string, string]> = {
  CLEAR: ['맑음', 'Clear'],
  PARTLY_CLOUDY: ['구름 조금', 'Partly cloudy'],
  CLOUDY: ['흐림', 'Cloudy'],
};

function today(): string { return new Date().toISOString().slice(0, 10); }

export default function Dialect() {
  const { tx } = useI18n();
  const { accessToken, ready } = useAuth();
  const [weather, setWeather] = useState<WeatherLoadResult | null>(null);
  useEffect(() => {
    if (!ready) return;
    let alive = true;
    void loadWeatherForecast(today(), accessToken).then((result) => { if (alive) setWeather(result); });
    return () => { alive = false; };
  }, [accessToken, ready]);

  const forecast = weather?.state === 'success' ? weather.forecast : null;

  return (
    <Screen scroll wide>
      <Text variant="display" weight="bold">{tx('부산 사투리 한마디', 'A word of Busan dialect')}</Text>
      <Text variant="caption" color={color.text.body} style={styles.subtitle}>
        {tx('뜻을 보고, 눌러서 진짜 부산 억양으로 들어 보세요. 식당·택시에서 한마디 하면 반응이 다릅니다.', 'See what it means, then tap to hear it in a real Busan accent. One word at a restaurant or in a taxi changes the mood.')}
      </Text>
      {forecast ? (
        <View style={styles.weatherLine}>
          <Text variant="caption" weight="bold">{tx('오늘 부산', 'Busan today')}</Text>
          <Text variant="caption" color={color.text.body}>
            {forecast.skyCondition ? tx(...SKY_LABEL[forecast.skyCondition]) : ''}
            {forecast.minTemperature != null && forecast.maxTemperature != null ? ` · ${Math.round(forecast.minTemperature)}° / ${Math.round(forecast.maxTemperature)}°` : ''}
            {forecast.precipitationProbability != null && forecast.precipitationProbability >= 60 ? ` · ${tx('☂ 우산', '☂ umbrella')}` : ''}
          </Text>
        </View>
      ) : null}
      <View style={styles.cards}><DialectFlashcards showTitle={false} /></View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  subtitle: { marginTop: spacing[2] },
  weatherLine: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], marginTop: spacing[4], paddingHorizontal: spacing[3], minHeight: 36, borderRadius: radius.full, backgroundColor: color.surface.soft, alignSelf: 'flex-start' },
  cards: { marginTop: spacing[4] },
});
