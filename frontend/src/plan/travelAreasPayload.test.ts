// 여행 범위가 여행 생성 요청에 실리는가 —.
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
