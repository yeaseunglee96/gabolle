import { apiRequest, ApiClientError } from '@/api/client';
import type { DataStatus } from '@/plan/recommendations';

export type NowViewState = 'idle' | 'loading' | 'success' | 'partial' | 'empty' | 'error' | 'offline' | 'unavailable';

export type NowCandidateDto = {
  placeId: string;
  name: string;
  travelMinutes: number;
  minutesUntilClose: number | null;
  reasonCodes: string[];
  dataStatus: DataStatus;
};
export type NowRecommendationResultDto = { candidates: NowCandidateDto[]; weatherApplied: boolean };

export type NowCandidate = NowCandidateDto & { reasons: string[] };
export type NowViewModel = { state: NowViewState; candidates: NowCandidate[]; weatherApplied: boolean; message: string };

const NOW_REASON: Record<string, string> = {
  SHORT_TRAVEL: '이동 시간이 짧아요',
  STILL_OPEN_LONG: '영업 종료까지 여유 있어요',
  MATCHES_TASTE: '취향 조건에 맞아요',
  WEATHER_FRIENDLY: '지금 날씨에 어울려요',
  NEARBY_POPULAR: '근처에서 인기 있어요',
};
export const nowReasonLabel = (code: string) => NOW_REASON[code] ?? '추천 조건 반영';

export const idleNowResult = (): NowViewModel => ({ state: 'idle', candidates: [], weatherApplied: false, message: '' });

// FR-REC-10: API가 아직 없는 동안에는 이 상태를 보여주고 가짜 결과를 만들지 않는다.
export const unavailableNowResult = (): NowViewModel => ({
  state: 'unavailable',
  candidates: [],
  weatherApplied: false,
  message: '지금 갈 곳 추천은 아직 서버와 연결되지 않았어요. 곧 제공될 예정이에요.',
});

export type NowRequestInput = {
  latitude: number | null;
  longitude: number | null;
  manualLocation: string | null;
  remainingMinutes: number;
};

export async function requestNowRecommendations(input: NowRequestInput, accessToken: string | null): Promise<NowViewModel> {
  try {
    const dto = await apiRequest<NowRecommendationResultDto>('/api/v1/recommendations/now', {
      method: 'POST',
      accessToken,
      body: {
        latitude: input.latitude,
        longitude: input.longitude,
        manualLocation: input.manualLocation,
        remainingMinutes: input.remainingMinutes,
      },
    });
    const candidates = dto.candidates.map((item) => ({ ...item, reasons: item.reasonCodes.map(nowReasonLabel) }));
    if (!candidates.length) {
      return { state: 'empty', candidates: [], weatherApplied: dto.weatherApplied, message: '남은 시간 안에 갈 만한 곳을 찾지 못했어요. 시간을 늘려 다시 찾아보세요.' };
    }
    return {
      state: dto.weatherApplied ? 'success' : 'partial',
      candidates,
      weatherApplied: dto.weatherApplied,
      message: dto.weatherApplied ? '지금 조건에 맞는 곳을 찾았어요.' : '날씨 정보 없이 계산했어요.',
    };
  } catch (error) {
    if (error instanceof ApiClientError && (error.status === 0 || error.code === 'NETWORK_ERROR')) {
      return { state: 'offline', candidates: [], weatherApplied: false, message: error.message };
    }
    if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return unavailableNowResult();
    return { state: 'error', candidates: [], weatherApplied: false, message: error instanceof Error ? error.message : '지금 갈 곳을 찾지 못했어요.' };
  }
}
