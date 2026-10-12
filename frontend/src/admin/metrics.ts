// 운영 지표(운영자) API — S15P21E201-2010. 백엔드 계약은
// backend/.../metrics/AdminMetricsController.java · MetricsQueryService.java 그대로다. 정의는 docs/metrics/METRICS.md.
import { API_BASE_URL, apiRequest, ApiClientError } from '@/api/client';

export type DailyRow = {
  day: string;
  dau: number;
  wau: number;
  mau: number;
  anonymousDau: number;
  anonymousMau: number;
  signups: number;
  tripsAi: number;
  tripsSelf: number;
  tripsNoItinerary: number;
  routePosts: number;
  placeSuggestions: number;
};

export type CohortRow = { signupDay: string; size: number; d1: number | null; d7: number | null; d30: number | null };

export type RetentionRate = { cohortUsers: number; returned: number; rate: number | null };

export type MetricsReport = {
  today: string;
  from: string;
  totals: { dau: number; wau: number; mau: number; anonymousMau: number; users: number; trips: number; routePosts: number; placeSuggestions: number };
  daily: DailyRow[];
  cohorts: CohortRow[];
  retention: { d1: RetentionRate; d7: RetentionRate; d30: RetentionRate };
  notes: { requestTrackingSince: string | null; timezone: string };
};

export type MetricsResult =
  | { state: 'success'; report: MetricsReport }
  | { state: 'forbidden' }
  | { state: 'error'; message: string };

export async function fetchMetrics(accessToken: string | null, days = 90): Promise<MetricsResult> {
  try {
    const report = await apiRequest<MetricsReport>(`/api/v1/admin/metrics?days=${days}`, { accessToken });
    return { state: 'success', report };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 403) return { state: 'forbidden' };
    return { state: 'error', message: error instanceof Error ? error.message : '지표를 불러오지 못했어요.' };
  }
}

/** 하루 한 줄 CSV 를 받아 온다(운영자 토큰이 필요해 링크로 못 연다). */
export async function fetchMetricsCsv(accessToken: string | null, days = 90): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/metrics/daily.csv?days=${days}`, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });
  if (!response.ok) throw new Error(`CSV 를 받지 못했어요 (${response.status})`);
  return response.text();
}

/** SVG polyline 의 points — 왼쪽→오른쪽 시간순, 최대값이 맨 위(y=0). */
export function linePoints(values: number[], width: number, height: number): string {
  const max = Math.max(0, ...values);
  const step = values.length > 1 ? width / (values.length - 1) : 0;
  return values
    .map((v, i) => {
      const x = values.length > 1 ? i * step : width;
      const y = max === 0 ? height : height - (v / max) * height;
      return `${round(x)},${round(y)}`;
    })
    .join(' ');
}

function round(n: number) {
  return Math.round(n * 10) / 10;
}

export function formatRate(rate: number | null | undefined): string {
  return rate == null ? '—' : `${(rate * 100).toFixed(1)}%`;
}
