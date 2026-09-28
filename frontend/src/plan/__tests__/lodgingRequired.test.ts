// 1박 이상 여행은 숙소를 골라야 일정을 만든다 — S15P21E201-1584.
//
// 🔴 이 시험이 지키는 것은 「앱이 숙소가 있다고 본 것 = 서버로 숙소가 실제로 가는 것」이다. 둘이 어긋나면
//    앱은 통과시켰는데 서버가 막거나(만들기를 눌러야 알게 된다), 앱이 멀쩡한 숙소를 없다고 잠근다.
import { toCreateTripPayload } from '@/api/tripApi';
import { EMPTY_PLAN, type PlanDraft } from '@/plan/PlanProvider';
import { hasLodging, isOvernight, lodgingMissing } from '@/plan/lodgingRequired';
import { lodgingSnapshotOf, RECOMMENDED_LODGING_AREAS } from '@/plan/origins';

const haeundae = RECOMMENDED_LODGING_AREAS.find((area) => area.externalId === 'lodging-haeundae')!;
const hotel = { placeId: 'hotel-1', nameKo: '해운대 호텔', nameEn: 'Haeundae Hotel', address: '부산 해운대구', lat: 35.16, lng: 129.16 };

const drafts: Record<string, PlanDraft> = {
  '숙소 없음': { ...EMPTY_PLAN },
  '숙소 동네(해운대)': { ...EMPTY_PLAN, lodging: '해운대', lodgingLat: haeundae.lat, lodgingLng: haeundae.lng },
  '검색한 호텔': { ...EMPTY_PLAN, lodging: '어느 호텔', lodgingLat: 35.1, lodgingLng: 129.1, lodgingPlace: lodgingSnapshotOf({ name: '어느 호텔', address: '부산', lat: 35.1, lng: 129.1, externalId: 'kakao-1', source: 'KAKAO_LOCAL' }) },
  '우리 표의 숙소': { ...EMPTY_PLAN, accommodation: '해운대 호텔', accommodationPlace: hotel },
  '좌표만 있고 동네도 호텔도 아님': { ...EMPTY_PLAN, lodging: '직접 쓴 이름', lodgingLat: 35.2, lodgingLng: 129.2 },
};

describe('숙소가 있나 — 서버로 가는 숙소와 같은 답', () => {
  it.each(Object.keys(drafts))('%s', (name) => {
    const draft = drafts[name];
    const payload = toCreateTripPayload(draft);
    const sent = Boolean(payload.accommodationPlaceId || payload.accommodation || payload.accommodationArea);
    expect(hasLodging(draft)).toBe(sent);
  });
});

describe('1박 이상인가', () => {
  it('오는 날이 가는 날보다 뒤면 1박 이상 — 같은 날(당일치기)이나 날짜가 비면 아니다', () => {
    expect(isOvernight('2026-10-01', '2026-10-02')).toBe(true);
    expect(isOvernight('2026-10-01', '2026-10-01')).toBe(false);
    expect(isOvernight('2026-10-01', '')).toBe(false);
    expect(isOvernight('', '')).toBe(false);
  });
});

describe('숙소를 골라야 하는데 안 골랐나', () => {
  const dates = { startDate: '2026-10-01', endDate: '2026-10-02' };

  it('🔴 1박 이상 + 숙소 없음 → 막는다', () => {
    expect(lodgingMissing({ ...drafts['숙소 없음'], ...dates })).toBe(true);
  });

  it('당일치기는 숙소 없이 된다', () => {
    expect(lodgingMissing({ ...drafts['숙소 없음'], startDate: '2026-10-01', endDate: '2026-10-01' })).toBe(false);
  });

  it('숙소를 어떤 모양으로든 골랐으면 된다', () => {
    expect(lodgingMissing({ ...drafts['숙소 동네(해운대)'], ...dates })).toBe(false);
    expect(lodgingMissing({ ...drafts['검색한 호텔'], ...dates })).toBe(false);
    expect(lodgingMissing({ ...drafts['우리 표의 숙소'], ...dates })).toBe(false);
  });
});
