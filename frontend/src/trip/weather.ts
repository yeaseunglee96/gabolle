// 여행 준비 화면의 날씨 위젯 —. GET /api/v1/weather 를 그대로 옮긴다
// (WeatherController.java 기준). 서버가 못 주면 화면은 값을 지어내지 않고 숨기거나
// 안내 문구를 보여줘야 한다(상세설계서 18.5절 가짜 데이터 금지) — 그 판단을 위해
// 실패도 종류별로 구분해 돌려준다.
import { apiRequest, ApiClientError } from '@/api/client';

export type SkyCondition = 'CLEAR' | 'PARTLY_CLOUDY' | 'CLOUDY';

export type DailyForecastDto = {
  date: string;
  minTemperature: number | null;
  maxTemperature: number | null;
  precipitationProbability: number | null;
  skyCondition: SkyCondition | null;
};

type WeatherForecastResponseDto = {
  nx: number;
  ny: number;
  cached: boolean;
  forecast: DailyForecastDto;
};

export type WeatherLoadResult =
  | { state: 'success'; forecast: DailyForecastDto }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

// 이 앱은 부산 여행 전용이라 좌표를 부산시청 기준으로 고정한다. 일정 항목에는 아직
// 장소 좌표가 화면까지 안 내려오므로(ItineraryDetailResponse.Item 에 위경도 칸이 없다)
// 장소별 정밀도는 낼 수 없다 — 도시 단위 예보로도 "하드코딩된 날씨 값 제거"라는
// 완료 기준은 충족한다. 장소 좌표가 내려오게 되면 그때 이 상수를 걷어낸다.
const BUSAN_LAT = 35.1796;
const BUSAN_LON = 129.0756;

export async function loadWeatherForecast(date: string, accessToken: string | null): Promise<WeatherLoadResult> {
  try {
    const params = new URLSearchParams({ lat: String(BUSAN_LAT), lon: String(BUSAN_LON), date });
    const response = await apiRequest<WeatherForecastResponseDto>(`/api/v1/weather?${params.toString()}`, { accessToken });
    return { state: 'success', forecast: response.forecast };
  } catch (error) {
    if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return { state: 'unavailable', message: '날씨 API가 아직 준비되지 않았어요.' };
    if (error instanceof ApiClientError && (error.status === 0 || error.code === 'NETWORK_ERROR')) return { state: 'offline', message: error.message };
    if (error instanceof ApiClientError && error.status === 502) return { state: 'error', message: '기상청 응답을 받지 못했어요.' };
    return { state: 'error', message: error instanceof Error ? error.message : '예보를 가져오지 못했어요.' };
  }
}
