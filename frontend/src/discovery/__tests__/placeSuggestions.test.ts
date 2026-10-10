// 현지인 장소 제안 — 화면이 서버 답을 어떻게 읽는가(S15P21E201-2031).
jest.mock('@/api/client', () => {
  const actual = jest.requireActual('@/api/client');
  return { ...actual, apiRequest: jest.fn() };
});

import { apiRequest, ApiClientError } from '@/api/client';
import { buildLocationPickHtml } from '@/discovery/locationPickHtml';
import { draftProblems, fetchLocalPick, submitPlaceSuggestion } from '@/discovery/placeSuggestions';
import { absolutePhotoUrl } from '@/discovery/places';
import { suggestHref } from '@/discovery/SuggestPlaceLink';

const mocked = apiRequest as jest.MockedFunction<typeof apiRequest>;
const draft = { name: '민락 국밥', category: 'FOOD', lat: 35.15, lng: 129.11, reason: '아침 7시에 문을 여는 국밥집', photoUrl: '/api/v1/uploads/images/a.jpg' };

beforeEach(() => mocked.mockReset());

describe('draftProblems', () => {
  it('다 채우면 문제가 없다', () => {
    expect(draftProblems(draft)).toEqual([]);
  });
  it('이름·갈래·위치·이유가 비면 각각 알려 준다', () => {
    expect(draftProblems({ name: ' ', reason: '' })).toEqual(['name', 'category', 'location', 'reason', 'photo']);
  });
  it('숙소처럼 고를 수 없는 갈래는 막는다', () => {
    expect(draftProblems({ ...draft, category: 'LODGING' })).toEqual(['category']);
  });
  it('추천 이유는 80자까지', () => {
    expect(draftProblems({ ...draft, reason: '가'.repeat(81) })).toEqual(['reason']);
  });
});

describe('🔴 장소·사진·추천 이유 모두 필수 (사용자 지시 2026-10-10)', () => {
  it('사진이 없으면 보낼 수 없다', () => {
    expect(draftProblems({ ...draft, photoUrl: null })).toEqual(['photo']);
  });
  it('추천 이유는 10자 이상', () => {
    expect(draftProblems({ ...draft, reason: '맛있어요' })).toEqual(['reason']);
  });
  it('서버의 사진 필수 거절을 읽는다', async () => {
    mocked.mockRejectedValueOnce(new ApiClientError('', 'PLACE_SUGGESTION_PHOTO_REQUIRED', 400, ['photoUrl']));
    await expect(submitPlaceSuggestion(draft, 't')).resolves.toEqual({ state: 'photoRequired' });
  });
});

describe('submitPlaceSuggestion', () => {
  it('🔴 중복이면 이미 있는 장소 번호를 돌려준다 — 「그 장소 보기」로 보낸다', async () => {
    mocked.mockRejectedValueOnce(new ApiClientError('이미 있는 장소예요', 'PLACE_SUGGESTION_DUPLICATE', 409, ['place-1']));
    await expect(submitPlaceSuggestion(draft, 'token')).resolves.toEqual({ state: 'duplicate', placeId: 'place-1' });
  });
  it('부산 밖·하루 상한·막힌 글은 각자 다른 상태다', async () => {
    mocked.mockRejectedValueOnce(new ApiClientError('', 'PLACE_SUGGESTION_OUTSIDE_BUSAN', 400));
    await expect(submitPlaceSuggestion(draft, 't')).resolves.toEqual({ state: 'outside' });
    mocked.mockRejectedValueOnce(new ApiClientError('', 'PLACE_SUGGESTION_RATE_LIMITED', 429));
    await expect(submitPlaceSuggestion(draft, 't')).resolves.toEqual({ state: 'limited' });
    mocked.mockRejectedValueOnce(new ApiClientError('', 'PLACE_SUGGESTION_TEXT_BLOCKED', 400));
    await expect(submitPlaceSuggestion(draft, 't')).resolves.toEqual({ state: 'blocked' });
  });
  it('빈 선택 칸은 null 로 보내고 글자는 다듬는다', async () => {
    mocked.mockResolvedValueOnce({ suggestionId: 's1', status: 'PENDING' });
    await submitPlaceSuggestion({ ...draft, name: '  민락 국밥 ', openingHours: '  ', phone: '' }, 't');
    expect(mocked).toHaveBeenCalledWith('/api/v1/place-suggestions', expect.objectContaining({
      method: 'POST',
      body: expect.objectContaining({ name: '민락 국밥', openingHours: null, phone: null, photoUrl: '/api/v1/uploads/images/a.jpg' }),
    }));
  });
});

it('제안으로 생긴 장소가 아니면(404) 「현지인 추천」을 안 그린다', async () => {
  mocked.mockRejectedValueOnce(new ApiClientError('', 'PLACE_SUGGESTION_NOT_FOUND', 404));
  await expect(fetchLocalPick('p1', 't')).resolves.toBeNull();
});

it('검색에서 친 이름을 제안 화면으로 들고 간다', () => {
  expect(suggestHref(' 할매 국밥 ')).toBe(`/place-suggest?name=${encodeURIComponent('할매 국밥')}`);
  expect(suggestHref('')).toBe('/place-suggest');
});

it('🔴 우리 서버에 올라간 사진(상대 주소)은 서버 주소를 붙이고, 바깥 주소는 그대로 둔다', () => {
  expect(absolutePhotoUrl('/api/v1/uploads/images/story/a.jpg')).toMatch(/^https?:\/\/.+\/api\/v1\/uploads\/images\/story\/a\.jpg$/);
  expect(absolutePhotoUrl('https://tong.visitkorea.or.kr/a.jpg')).toBe('https://tong.visitkorea.or.kr/a.jpg');
});

it('위치 고르기 지도는 멈출 때마다 가운데를 보낸다', () => {
  const html = buildLocationPickHtml('k&ey', 35.1, 129.1);
  expect(html).toContain("addListener(map, 'idle'");
  expect(html).toContain('appkey=k%26ey');
  expect(html).toContain('new kakao.maps.LatLng(35.1, 129.1)');
});
