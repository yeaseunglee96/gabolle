// 여행 카드 사진을 서버가 주는 값으로 그린다 — S15P21E201-1435.
//
// 🔴 2026-09-21 실기(versionCode 28). 「내 여행」의 카드 사진이 어떤 카드는 나오고 어떤
//    카드는 안 나왔다. 앱을 껐다 켜면 나오는 카드가 달라졌다.
//
//    화면이 카드마다 이렇게 불렀다:
//      여행 → GET /itineraries?tripId= → GET /itineraries/{id} → 앞 정차지 여섯의 GET /places/{id}
//
//    nginx 기록(2026-09-21 08:11~08:17 UTC, 그 기기): 200 251건 · 503 59건 · 초당 최대 51건.
//    503 중 52건이 /places/{id} 였다. 서버 앞단 제한이 rate=10r/s burst=20 이라 넘친 요청이
//    거절됐고, 화면은 그 실패(null)를 세션 내내 기억해서 거절된 카드는 앱을 끌 때까지
//    사진이 없었다.
//
// 🔴 서버는 목록과 «함께» 사진을 준다(1370, 규칙은 1436 에서 화면과 맞췄다). 그것을 쓰면
//    추가 호출이 0 이다. 이 시험은 «다시 카드마다 부르는 방식으로 돌아가지 않았는가» 를 본다 —
//    돌아가도 화면에는 사진이 똑같이 나오고 요청 수만 늘어서, 눈으로는 안 잡힌다.
declare const require: (id: string) => any;
declare const __dirname: string;

const { readFileSync, existsSync } = require('fs');
const { join } = require('path');

const ROOT = join(__dirname, '..', '..', '..');
const TRIPS_SCREEN = readFileSync(join(ROOT, 'app', '(tabs)', 'trips.tsx'), 'utf8') as string;
const TRIPS_DTO = readFileSync(join(ROOT, 'src', 'trip', 'trips.ts'), 'utf8') as string;

describe('여행 카드 사진은 목록 응답이 준다', () => {
  it('🔴 카드가 서버가 준 coverImageUrl 을 그린다', () => {
    expect(TRIPS_SCREEN).toContain('<TripCover uri={trip.coverImageUrl} />');
  });

  it('🔴 카드마다 사진을 따로 부르지 않는다 — 이것이 초당 51건의 원인이었다', () => {
    expect(TRIPS_SCREEN).not.toContain('loadTripCover');
    expect(existsSync(join(ROOT, 'src', 'plan', 'tripCover.ts'))).toBe(false);
  });

  it('목록 DTO 가 표지 세 칸을 들고 있다', () => {
    expect(TRIPS_DTO).toContain('coverImageUrl: string | null;');
    expect(TRIPS_DTO).toContain('firstStopNameKo: string | null;');
    expect(TRIPS_DTO).toContain('firstStopNameEn: string | null;');
  });
});
