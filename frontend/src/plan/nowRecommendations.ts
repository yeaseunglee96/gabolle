import { apiRequest, ApiClientError, getApiLanguage } from '@/api/client';
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

const t = (ko: string, en: string) => (getApiLanguage() === 'en' ? en : ko);

const NOW_REASON: Record<string, [string, string]> = {
  SHORT_TRAVEL: ['이동 시간이 짧아요', 'A short trip away'],
  STILL_OPEN_LONG: ['영업 종료까지 여유 있어요', 'Open for a while yet'],
  MATCHES_TASTE: ['취향 조건에 맞아요', 'Matches your taste'],
  WEATHER_FRIENDLY: ['지금 날씨에 어울려요', 'Good fit for the weather now'],
  NEARBY_POPULAR: ['근처에서 인기 있어요', 'Popular nearby'],
};
export const nowReasonLabel = (code: string) => t(...(NOW_REASON[code] ?? ['추천 조건 반영', 'Reflects your conditions']));

export const idleNowResult = (): NowViewModel => ({ state: 'idle', candidates: [], weatherApplied: false, message: '' });

// 🔴 API가 붙기 전까지 화면이 항상 비어 보인다는 지적(2026-09-10)에 따라, "연결 전" 상태
// 대신 예시 후보를 보여준다. placeId는 다른 화면(home.tsx 추천 카드)에서도 쓰는 실재
// 장소라 "자세히 보기"를 눌러도 깨지지 않는다. 이동 시간·마감까지 남은 시간은 실제 계산이
// 아니라 요청한 남는 시간 안에서 그럴듯하게 맞춘 값일 뿐이다 — isSample이 true인 항목은
// 화면에서 반드시 "샘플" 배지를 붙인다.
export type SampleNowCandidate = NowCandidate & { isSample: true };

export function buildSampleNowResult(remainingMinutes: number): NowViewModel {
  const SAMPLE_PLACES: Array<[string, string, string[]]> = [
    ['haeundae', '해운대 해수욕장', ['NEARBY_POPULAR']],
    ['gwangalli', '광안리 해수욕장', ['WEATHER_FRIENDLY']],
    ['gamcheon', '감천문화마을', ['SHORT_TRAVEL']],
  ];
  const candidates: SampleNowCandidate[] = SAMPLE_PLACES.map(([placeId, name, reasonCodes], index) => {
    const travelMinutes = Math.max(5, Math.round((remainingMinutes / (SAMPLE_PLACES.length + 1)) * (index + 1)));
    return {
      placeId,
      name,
      travelMinutes,
      minutesUntilClose: Math.max(30, remainingMinutes - travelMinutes),
      reasonCodes,
      reasons: reasonCodes.map(nowReasonLabel),
      dataStatus: 'ESTIMATED',
      isSample: true,
    };
  });
  return {
    state: 'partial',
    candidates,
    weatherApplied: false,
    message: t('지금 갈 곳 추천은 아직 서버와 연결되지 않았어요 — 예시 후보를 보여드려요.', "The nearby-now recommendation isn't connected to the server yet — here are example candidates."),
  };
}

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
      return { state: 'empty', candidates: [], weatherApplied: dto.weatherApplied, message: t('남은 시간 안에 갈 만한 곳을 찾지 못했어요. 시간을 늘려 다시 찾아보세요.', 'We couldn’t find a place within your remaining time. Try a longer time budget.') };
    }
    return {
      state: dto.weatherApplied ? 'success' : 'partial',
      candidates,
      weatherApplied: dto.weatherApplied,
      message: dto.weatherApplied ? t('지금 조건에 맞는 곳을 찾았어요.', 'We found places that fit your conditions now.') : t('날씨 정보 없이 계산했어요.', 'Calculated without weather data.'),
    };
  } catch (error) {
    if (error instanceof ApiClientError && (error.status === 0 || error.code === 'NETWORK_ERROR')) {
      return { state: 'offline', candidates: [], weatherApplied: false, message: error.message };
    }
    if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return buildSampleNowResult(input.remainingMinutes);
    return { state: 'error', candidates: [], weatherApplied: false, message: error instanceof Error ? error.message : t('지금 갈 곳을 찾지 못했어요.', 'Could not find a place to go right now.') };
  }
}
