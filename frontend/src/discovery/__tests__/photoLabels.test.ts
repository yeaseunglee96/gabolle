import { getFestivals } from '../festivals';
import { photoLabels } from '../places';

// — 관광사진갤러리에서 받은 축제 사진 35건 중 그 축제를 실제로 찍은 것은
// 1건이고, 나머지는 그 축제가 열리는 곳을 찍은 사진이다. 그냥 띄우면 「이 축제가 이렇게
// 생겼구나」로 읽힌다 — 알레르기에서 고친 것과 같은 종류의 거짓말이다.
const tx = (ko: string) => ko;
const txEn = (_ko: string, en: string) => en;

describe('사진에 무슨 말을 붙이나', () => {
  it('🔴 행사장 사진에는 배지를 단다 — 축제 사진으로 읽히면 안 된다', () => {
    const labels = photoLabels({ photoSubject: 'VENUE', photoSource: '한국관광공사 관광사진갤러리' }, tx);
    expect(labels.badge).toBe('행사장 사진');
    expect(labels.credit).toBe('사진 제공: 한국관광공사 관광사진갤러리');
  });

  it('그 장소를 찍은 사진에는 배지를 안 단다 — 기대한 대로인 것은 말할 게 없다', () => {
    expect(photoLabels({ photoSubject: 'SELF', photoSource: '한국관광공사 관광사진갤러리' }, tx).badge).toBeNull();
  });

  it('🔴 칸이 안 왔으면 아무 말도 안 한다 — 모르는 것을 아는 척하지 않는다', () => {
    expect(photoLabels({}, tx)).toEqual({ badge: null, credit: null, licenseUrl: null });
  });

  it('출처만 있고 피사체를 모르면 출처만 적는다', () => {
    expect(photoLabels({ photoSource: '한국관광공사 관광사진갤러리' }, tx)).toEqual({
      badge: null,
      credit: '사진 제공: 한국관광공사 관광사진갤러리',
      // 라이선스가 없는 사진(공공누리)은 링크도 없다 — S15P21E201-1610.
      licenseUrl: null,
    });
  });

  it('영어에서도 둘 다 나온다', () => {
    const labels = photoLabels({ photoSubject: 'VENUE', photoSource: 'Korea Tourism Organization' }, txEn);
    expect(labels.badge).toBe('Venue photo');
    expect(labels.credit).toBe('Photo: Korea Tourism Organization');
  });
});

// 서버가 두 칸을 보내기 시작해도 프론트가 그냥 버리면 아무 소용이 없다. 반대로 아직 안 보낼
// 때도 지금과 똑같이 그려야 한다 — 그래야 서로의 배포를 안 기다린다.
function respondWith(item: Record<string, unknown>) {
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL) => {
    if (String(input).includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    return new Response(JSON.stringify({ data: { items: [item], count: 1 }, error: null, meta: { requestId: 'r1' } }), { status: 200, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

const baseItem = {
  placeId: '11111111-1111-1111-1111-111111111111',
  nameKo: '광안리 M 드론쇼',
  address: '부산 수영구',
  startDate: '2026-10-01',
  endDate: '2026-10-03',
  overlapDates: [],
};

describe('축제 응답이 사진 두 칸을 실어 온다', () => {
  it('서버가 보낸 출처와 피사체가 그대로 온다', async () => {
    respondWith({ ...baseItem, photoUrl: 'https://example.test/a.jpg', photoSource: '한국관광공사 관광사진갤러리', photoSubject: 'VENUE' });
    const [festival] = await getFestivals('2026-10-01', '2026-10-05');
    expect(festival.photoSubject).toBe('VENUE');
    expect(photoLabels(festival, tx).badge).toBe('행사장 사진');
  });

  it('🔴 서버가 아직 두 칸을 안 보내도 지금처럼 그린다 — 배포 순서를 안 탄다', async () => {
    respondWith({ ...baseItem, photoUrl: 'https://example.test/a.jpg' });
    const [festival] = await getFestivals('2026-10-01', '2026-10-05');
    expect(festival.photoUrl).toBe('https://example.test/a.jpg');
    expect(photoLabels(festival, tx)).toEqual({ badge: null, credit: null, licenseUrl: null });
  });
});
