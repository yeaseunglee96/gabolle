// 여행 범위가 여행 생성 요청에 실리는가 — S15P21E201-980.
//
// 이 칸이 없던 동안 지역 칩은 화면에서만 받고 서버로 가지 않았다. 해운대를 골라도 추천
// 스무 곳이 전부 출발지 근처였고, 고른 사람은 반영됐다고 믿었다.
import { toCreateTripPayload } from '@/api/tripApi';
import { EMPTY_PLAN } from '@/plan/PlanProvider';

const base = { ...EMPTY_PLAN, startDate: '2026-10-12', endDate: '2026-10-13' };

describe('여행 생성 요청의 여행 범위', () => {
  it('고른 순서 그대로 코드를 싣는다', () => {
    const payload = toCreateTripPayload({ ...base, travelAreas: ['SONGJEONG', 'HAEUNDAE'] });

    expect(payload.travelAreas).toEqual(['SONGJEONG', 'HAEUNDAE']);
  });

  // 범위를 안 고르고 넘어가는 것이 보통이다. 그때 서버가 받는 모양이 달라지면 안 된다.
  it('안 고르면 빈 배열이다', () => {
    const payload = toCreateTripPayload(base);

    expect(payload.travelAreas).toEqual([]);
  });
});
