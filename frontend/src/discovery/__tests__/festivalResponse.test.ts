import { getFestivals } from '../festivals';

// 서버(FestivalResponse)는 { items, count } 로 준다. 프론트가 festivals 라는 없는 칸을
// 읽던 동안에는 200 이 와도 조용히 빈 목록이었고, 오류가 아니라 견본 폴백도 안 걸렸다
// .
const serverItem = {
  placeId: '11111111-1111-1111-1111-111111111111',
  nameKo: '광안리어방축제',
  nameEn: 'Gwangalli Eobang Festival',
  address: '부산 수영구',
  startDate: '2026-10-01',
  endDate: '2026-10-03',
  overlapDates: ['2026-10-02'],
  priceLevel: { value: '무료', evidenceStatus: 'ESTIMATED' },
};

function respondWith(payload: unknown, status = 200) {
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL) => {
    if (String(input).includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    return new Response(JSON.stringify(payload), { status, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

describe('축제 목록 응답 읽기', () => {
  it('서버가 items 로 준 축제를 그대로 싣는다', async () => {
    respondWith({ data: { items: [serverItem], count: 1 }, error: null, meta: { requestId: 'r1' } });
    const festivals = await getFestivals('2026-10-01', '2026-10-05');
    expect(festivals).toHaveLength(1);
    expect(festivals[0].nameKo).toBe('광안리어방축제');
    expect(festivals[0].nameEn).toBe('Gwangalli Eobang Festival');
    expect(festivals[0].priceLevel?.evidenceStatus).toBe('ESTIMATED');
  });

  it('서버가 빈 목록을 주면 견본을 지어내지 않는다', async () => {
    respondWith({ data: { items: [], count: 0 }, error: null, meta: { requestId: 'r1' } });
    await expect(getFestivals('2026-10-01', '2026-10-05')).resolves.toEqual([]);
  });

  // 🔴 예전에는 이 자리에서 «견본으로 물러섰다» — 부산불꽃축제·광안리어방축제·BIFF 를
  //    지어내서 돌려줬다. 이름과 주소는 실재하는데 날짜만 조회 기간을 나눠 만든 값이라,
  //    12월로 조회하면 「부산불꽃축제 12/08~12/10」 같은 없는 일정이 그럴듯하게 나왔다.
  //    여행 계획을 거기 맞추면 헛걸음한다 — S15P21E201-1346.
  it('🔴 서버가 실패하면 «지어내지 않고» 실패를 올려보낸다', async () => {
    respondWith({ data: null, error: { code: 'INTERNAL_ERROR', message: '오류' }, meta: { requestId: 'r1' } }, 500);
    await expect(getFestivals('2026-10-01', '2026-10-05')).rejects.toBeDefined();
  });

  it('🔴 지어낸 축제 이름이 어디서도 안 나온다', async () => {
    respondWith({ data: null, error: { code: 'INTERNAL_ERROR', message: '오류' }, meta: { requestId: 'r1' } }, 500);
    const invented = ['부산불꽃축제', '광안리어방축제', '부산국제영화제(BIFF)'];
    await getFestivals('2026-12-01', '2026-12-31').then(
      (items) => {
        // 여기 오면 안 된다. 왔다면 적어도 지어낸 이름은 없어야 한다.
        for (const name of invented) expect(items.some((f) => f.nameKo === name)).toBe(false);
      },
      () => undefined,   // 던지는 것이 올바른 동작이다
    );
  });
});
