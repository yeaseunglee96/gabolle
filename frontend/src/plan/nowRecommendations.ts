import { apiRequest, ApiClientError } from '@/api/client';
import { getCurrentLanguage } from '@/i18n/languages';
import { pickLanguage } from '@/i18n/pick';
import { getNearbyPlaces } from '@/discovery/localExplore';
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

// 🔴 화면 문구는 고른 언어의 번역표로 — S15P21E201-1767. 전에는 서버용 언어(ko|en 뿐)로 골라서 일본어·중국어
//    화면에 추천 이유가 「Matches your interests」처럼 영어로 떴다(Play 35 실기기). 표에 줄은 이미 있었다.
//    intent.ts(S15P21E201-1517)와 같은 방식이다. 표에 없는 문구는 전과 같이 영어로 떨어진다.
const t = (ko: string, en: string) => pickLanguage(getCurrentLanguage(), { ko, en });

const NOW_REASON: Record<string, [string, string]> = {
  SHORT_TRAVEL: ['이동 시간이 짧아요', 'A short trip away'],
  STILL_OPEN_LONG: ['영업 종료까지 여유 있어요', 'Open for a while yet'],
  MATCHES_TASTE: ['취향 조건에 맞아요', 'Matches your taste'],
  WEATHER_FRIENDLY: ['지금 날씨에 어울려요', 'Good fit for the weather now'],
  NEARBY_POPULAR: ['근처에서 인기 있어요', 'Popular nearby'],
};
export const nowReasonLabel = (code: string) => t(...(NOW_REASON[code] ?? ['추천 조건 반영', 'Reflects your conditions']));

export const idleNowResult = (): NowViewModel => ({ state: 'idle', candidates: [], weatherApplied: false, message: '' });

/**
 * 🔴 **지어낸 장소를 추천하지 않는다** — S15P21E201-1345.
 *
 * 추천 API(`POST /api/v1/recommendations/now`, 명세의 REC-04)는 **만들어진 적이 없다.**
 * 운영에서 404 다. 그동안 이 자리는 404 를 받으면 해운대·광안리·감천문화마을을
 * **지어내서** 그렸다(2026-09-10). 그게 왜 나쁜가:
 *
 * <ul>
 *   <li>「이동 15분」·「영업 종료까지 45분」이 <b>남은 시간을 나눠서 만든 숫자</b>였다.
 *       아무것도 재지 않았다. 「45분 남았다」를 믿고 간 사람은 틀린 것을 믿은 것이다</li>
 *   <li>{@code placeId} 가 {@code 'haeundae'} 같은 <b>가짜</b>라서 「자세히 보기」가 깨졌다</li>
 *   <li>이 저장소는 다른 자리에서 일관되게 «확신이 없으면 안 그린다»를 지킨다 —
 *       메뉴판은 「지어내는 것이 없다」, 예시 사진은 「확신이 없으면 null 을 낸다」.
 *       여기만 반대로 하고 있었다</li>
 * </ul>
 *
 * <p>그래서 **있는 자료로 정직하게** 답한다. `GET /api/v1/places/nearby` 는 이미 있고
 * **진짜 장소**와 **진짜 거리**({@code distanceM})를 준다. 가까운 순서로 보여주고,
 * 이동 시간은 걷는 속도로 어림해 「추정」이라고 적는다. **영업 종료 시각은 이 응답에
 * 없으므로 지어내지 않고 「미확인」으로 둔다** — 화면이 그 경우를 이미 그릴 줄 안다.
 */

/** 걷는 속도 — 시속 4km ≈ 분당 67m. 어림값이라 화면에 «추정»으로 적는다. */
const WALK_METRES_PER_MINUTE = 67;

/** 남은 시간의 절반까지만 가는 것으로 잡는다 — 가서 머물 시간도 있어야 한다. */
const TRAVEL_SHARE_OF_REMAINING = 0.5;

/** 반경이 너무 커지면 「지금 갈 곳」이 아니게 된다. */
const MAX_RADIUS_M = 8000;

export function nearbyRadiusMetres(remainingMinutes: number): number {
  const reachable = remainingMinutes * TRAVEL_SHARE_OF_REMAINING * WALK_METRES_PER_MINUTE;
  return Math.max(500, Math.min(MAX_RADIUS_M, Math.round(reachable)));
}

export function walkMinutes(distanceM: number): number {
  return Math.max(1, Math.round(distanceM / WALK_METRES_PER_MINUTE));
}

/**
 * 추천 API 가 없을 때 쓰는 길 — **진짜 주변 장소**를 가까운 순서로 보여준다.
 *
 * 좌표가 없으면 아무것도 지어내지 않고 위치가 필요하다고 말한다. 직접 입력한
 * 출발지만 있는 경우가 그렇다 — 그 글자를 좌표로 바꾸는 일은 이 화면이 못 한다.
 */
export async function buildNearbyNowResult(
  input: NowRequestInput,
  signal?: AbortSignal,
): Promise<NowViewModel> {
  if (input.latitude == null || input.longitude == null) {
    return {
      state: 'unavailable',
      candidates: [],
      weatherApplied: false,
      message: t('지금 갈 곳 추천은 아직 준비 중이라, 그동안은 현재 위치로 가까운 곳을 찾아드려요. 위치를 켜 주세요.',
        'Nearby-now recommendations are still being built. For now we can list places near you — please turn on location.'),
    };
  }

  const nearby = await getNearbyPlaces({
    lat: input.latitude,
    lng: input.longitude,
    radiusMeters: nearbyRadiusMetres(input.remainingMinutes),
    limit: 10,
  }, signal);

  if (nearby.state !== 'success') {
    return {
      state: nearby.state === 'offline' ? 'offline' : 'error',
      candidates: [],
      weatherApplied: false,
      message: nearby.message,
    };
  }

  const candidates: NowCandidate[] = nearby.items
    .map((item) => {
      const travelMinutes = walkMinutes(item.distanceM);
      // 「가까워요」는 거리를 실제로 재서 하는 말이라 붙여도 된다. 인기·날씨·취향은
      // 이 응답이 말해 주지 않으므로 붙이지 않는다.
      const reasonCodes = ['SHORT_TRAVEL'];
      return {
        placeId: item.placeId,
        name: item.nameKo,
        travelMinutes,
        // 🔴 영업 시간 자료가 이 응답에 없다. 모르면 모른다고 한다.
        minutesUntilClose: null,
        reasonCodes,
        reasons: reasonCodes.map(nowReasonLabel),
        dataStatus: 'ESTIMATED' as DataStatus,
      };
    })
    .filter((candidate) => candidate.travelMinutes <= input.remainingMinutes);

  if (!candidates.length) {
    return {
      state: 'empty',
      candidates: [],
      weatherApplied: false,
      message: t('남은 시간 안에 걸어갈 만한 곳을 찾지 못했어요. 시간을 늘려 다시 찾아보세요.',
        'We couldn’t find a place within walking distance in your remaining time. Try a longer time budget.'),
    };
  }

  return {
    state: 'partial',
    candidates,
    weatherApplied: false,
    message: t('지금 갈 곳 추천은 아직 준비 중이에요. 그동안 현재 위치에서 가까운 순서로 보여드려요 — 이동 시간은 걷는 속도로 어림한 값이고, 영업 시간은 확인하지 못했어요.',
      'Nearby-now recommendations are still being built. Meanwhile, here are real places sorted by distance — walking times are estimates, and opening hours are unconfirmed.'),
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
    // 🔴 404·501 은 「이 API 가 아직 없다」는 뜻이다 — 지어내지 않고 진짜 주변 장소로 답한다.
    if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return buildNearbyNowResult(input);
    return { state: 'error', candidates: [], weatherApplied: false, message: error instanceof Error ? error.message : t('지금 갈 곳을 찾지 못했어요.', 'Could not find a place to go right now.') };
  }
}
