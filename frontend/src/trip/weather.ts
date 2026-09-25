// 여행 준비 화면의 날씨 위젯 — S15P21E201-378. GET /api/v1/weather 를 그대로 옮긴다
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

/** 기상청 PTY(강수형태). 서버 PrecipitationType 그대로 — NONE 은 「비 없음」이고, 모르면 null 이다. */
export type PrecipitationType = 'NONE' | 'RAIN' | 'RAIN_SNOW' | 'SNOW' | 'SHOWER';

/**
 * 한 시각의 예보(서버 HourlyForecastDto, S15P21E201-1534). time 은 "HH:mm" 이고 날짜는 forecast.date 다.
 * 🔴 원문에 없는 값은 null 이다 — 0 과 다르다(0 은 「강수확률 0%」, null 은 「모른다」). 채우지 않는다.
 */
export type HourlyForecastDto = {
  time: string;
  temperature: number | null;
  skyCondition: SkyCondition | null;
  precipitationProbability: number | null;
  precipitationType: PrecipitationType | null;
};

type WeatherForecastResponseDto = {
  nx: number;
  ny: number;
  cached: boolean;
  forecast: DailyForecastDto;
  /** 나중에 더해진 칸이다 — 옛 서버는 안 보낸다. */
  hourly?: HourlyForecastDto[] | null;
};

export type WeatherLoadResult =
  /**
   * hourly 는 **서버가 준 시각만** 담는다(S15P21E201-1582). 오늘 날짜면 발표 이후 시각 몇 칸만 오고
   * (2026-09-24 운영 실측: 21~23시 셋), 옛 서버면 빈 목록이다. 빈 시각을 지어서 채우지 않는다.
   */
  | { state: 'success'; forecast: DailyForecastDto; hourly: HourlyForecastDto[] }
  /** 기상청 단기예보는 발표 시점부터 사흘 남짓만 준다 — 그 밖의 날짜는 «실패»가 아니라 «아직»이다(S15P21E201-1376). */
  | { state: 'out-of-range'; message: string }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

// 이 앱은 부산 여행 전용이라 좌표를 부산시청 기준으로 고정한다. 일정 항목에는 아직
// 장소 좌표가 화면까지 안 내려오므로(ItineraryDetailResponse.Item 에 위경도 칸이 없다)
// 장소별 정밀도는 낼 수 없다 — 도시 단위 예보로도 "하드코딩된 날씨 값 제거"라는
// 완료 기준은 충족한다. 장소 좌표가 내려오게 되면 그때 이 상수를 걷어낸다.
const BUSAN_LAT = 35.1796;
const BUSAN_LON = 129.0756;

/** 오늘(이 기기의 날짜)에서 며칠 뒤인가 — 날짜 글자(YYYY-MM-DD)를 못 읽으면 null. UTC 로 세면 한국 아침엔 하루가 어긋난다. */
function daysFromToday(date: string, now: Date): number | null {
  const [y, m, d] = date.split('-').map(Number);
  if (!y || !m || !d) return null;
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((new Date(y, m - 1, d).getTime() - today.getTime()) / 86_400_000);
}
/** 기상청 단기예보가 주는 범위 — 오늘부터 사흘 뒤까지. */
const FORECAST_DAYS = 3;

export async function loadWeatherForecast(date: string, accessToken: string | null, now: Date = new Date()): Promise<WeatherLoadResult> {
  // 🔴 범위 밖 날짜는 부르지 않는다(S15P21E201-1641). 전에는 불러서 400 을 받은 뒤에야 「출발 3일 전부터」를 그렸다 —
  //    여행 준비 화면이 2~4주 뒤 날짜로 4일간 12번. 서버 규칙과 같게 앱이 먼저 가른다. 못 읽는 날짜는 서버에 맡긴다.
  const ahead = daysFromToday(date, now);
  if (ahead !== null && (ahead < 0 || ahead > FORECAST_DAYS)) return { state: 'out-of-range', message: '출발일 예보는 출발 3일 전부터 보여드려요.' };
  try {
    const params = new URLSearchParams({ lat: String(BUSAN_LAT), lon: String(BUSAN_LON), date });
    const response = await apiRequest<WeatherForecastResponseDto>(`/api/v1/weather?${params.toString()}`, { accessToken });
    return { state: 'success', forecast: response.forecast, hourly: response.hourly ?? [] };
  } catch (error) {
    if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return { state: 'unavailable', message: '날씨 API가 아직 준비되지 않았어요.' };
    // 서버 원문: 「date 가 이 발표 회차의 단기예보 범위를 벗어났습니다」(2026-09-21 실서버 실기, 출발 6일 전 여행).
    // 🔴 말투를 보지 않는다 — 이 화면이 보내는 값 가운데 사람이 바꾸는 것은 날짜뿐이라(좌표는 상수),
    //    이 코드의 400 은 곧 「날짜가 범위 밖」이다. 서버 문구가 바뀌어도 안 흔들린다.
    if (error instanceof ApiClientError && error.status === 400 && error.code === 'WEATHER_INVALID_REQUEST') return { state: 'out-of-range', message: error.message };
    if (error instanceof ApiClientError && (error.status === 0 || error.code === 'NETWORK_ERROR')) return { state: 'offline', message: error.message };
    if (error instanceof ApiClientError && error.status === 502) return { state: 'error', message: '기상청 응답을 받지 못했어요.' };
    return { state: 'error', message: error instanceof Error ? error.message : '예보를 가져오지 못했어요.' };
  }
}
