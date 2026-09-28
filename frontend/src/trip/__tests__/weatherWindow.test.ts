// 예보 범위 밖 날짜로 날씨를 부르지 않는다 — S15P21E201-1641.
//
// 🔴 이 시험이 지키는 것: 기상청 단기예보는 발표 시점부터 사흘 남짓만 준다. 여행 준비 화면이 2~4주 뒤 날짜로 4일간
//    12번 불렀고, 서버는 설계대로 400 을 돌려줬다. 앱은 400 을 받은 뒤에야 「출발 3일 전부터 보여드려요」를 그렸다.
//    날짜를 먼저 보고, 범위 밖이면 부르지 않는다 — 그 문구는 그대로 그린다(state: out-of-range).
import { apiRequest } from '@/api/client';
import { loadWeatherForecast } from '@/trip/weather';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn() }));
const request = jest.mocked(apiRequest);

// 이 기기의 날짜로 센다 — UTC 로 세면 한국 아침엔 하루가 어긋난다.
const dayKey = (offset: number) => { const d = new Date(); d.setDate(d.getDate() + offset); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };

beforeEach(() => {
  request.mockReset();
  request.mockResolvedValue({ forecast: { date: dayKey(0) }, hourly: [] });
});

describe('날씨 예보 범위', () => {
  it.each([14, 28, 4])('🔴 %s일 뒤는 부르지 않는다 — 요청 0번, 「아직」으로 그린다', async (offset) => {
    await expect(loadWeatherForecast(dayKey(offset), 'token')).resolves.toMatchObject({ state: 'out-of-range' });
    expect(request).not.toHaveBeenCalled();
  });

  it('지난 날짜도 부르지 않는다 — 예보가 없다', async () => {
    await expect(loadWeatherForecast(dayKey(-1), 'token')).resolves.toMatchObject({ state: 'out-of-range' });
    expect(request).not.toHaveBeenCalled();
  });

  it.each([0, 1, 3])('%s일 뒤는 부른다', async (offset) => {
    await expect(loadWeatherForecast(dayKey(offset), 'token')).resolves.toMatchObject({ state: 'success' });
    expect(request).toHaveBeenCalledTimes(1);
  });
});

// 🔴 지난 여행(S15P21E201-1773) — 운영 웹에서 어제 출발한 여행의 준비 화면이 「여행 전 · 9월 26일 출발」과
//    「출발 3일 전부터 보여드려요. 그때 다시 열어 주세요」를 적었다. 「그때」는 오지 않는다.
describe('지난 여행의 날씨 창', () => {
  it('🔴 지난 날짜는 「지났다」고 알린다 — 「아직」과 가른다', async () => {
    await expect(loadWeatherForecast(dayKey(-1), 'token')).resolves.toMatchObject({ state: 'out-of-range', past: true });
    await expect(loadWeatherForecast(dayKey(14), 'token')).resolves.not.toHaveProperty('past', true);
  });

  it('🔴 준비 화면 머리 글 — 지난 여행에 「여행 전」이라 적지 않는다', () => {
    const { weatherHeading } = jest.requireActual<typeof import('@/trip/TripWeatherPanel')>('@/trip/TripWeatherPanel');
    const ko = (k: string) => k;
    expect(weatherHeading(ko, '2026-09-26', '9월 26일', '2026-09-27').eyebrow).toBe('여행 중 · 9월 26일 출발');
    expect(weatherHeading(ko, '2026-09-27', '9월 27일', '2026-09-27').title).toBe('오늘 날씨');
    expect(weatherHeading(ko, '2026-09-30', '9월 30일', '2026-09-27').eyebrow).toBe('여행 전 · 9월 30일 출발');
  });
});
