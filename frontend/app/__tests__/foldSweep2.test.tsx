// 폴드 점검 2차(10/5) — 화면이 아니라 소스에서 지키는 것들 — S15P21E201-1985.
jest.mock('react-native-webview', () => ({ WebView: () => null }));
// node 타입이 없어 import 대신 require — teamReportFixes.test.ts 와 같은 방식.
declare const require: (id: string) => any;
declare const __dirname: string;

import { keepTripFields } from '../trips/[id]/itinerary';

const read = (p: string): string => require('fs').readFileSync(require('path').join(__dirname, '..', '..', p), 'utf8');

describe('폴드 점검 2차', () => {
  it('🔴 「내가 한 변경 N건」(change) 아이콘은 빈 네모가 아니라 그림이 있다', () => {
    const src = read('src/components/NoticeIcon.tsx');
    const branch = src.slice(src.indexOf("kind === 'change'"));
    const end = branch.indexOf(': null}');
    expect(branch.slice(0, end)).toMatch(/<Path/);
  });

  it('🔴 편집 결과에 여행 번호·제목이 빠져 와도 이전 값을 지킨다 — 고정 뒤 「이름 바꾸기·동행 초대·날씨」가 사라졌다', () => {
    const prev = { id: 'i1', tripId: 't1', title: '부산', version: 1 } as never;
    const next = { id: 'i1', version: 2 } as never;
    const merged = keepTripFields(next, prev) as unknown as { tripId: string; title: string; version: number };
    expect(merged.tripId).toBe('t1');
    expect(merged.title).toBe('부산');
    expect(merged.version).toBe(2);
    const own = keepTripFields({ id: 'i1', tripId: 't2', title: '새 이름', version: 3 } as never, prev) as unknown as { tripId: string; title: string };
    expect(own.tripId).toBe('t2');
    expect(own.title).toBe('새 이름');
  });

  it('🔴 404 화면(폰) — 위쪽 안전 영역도 남색이라 밝은 상태표시줄 글자가 보인다', () => {
    const src = read('app/+not-found.tsx');
    expect(src).toMatch(/screen:\s*\{[^}]*backgroundColor:\s*color\.brand\.navy/);
  });
});
