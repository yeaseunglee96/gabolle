// 여행 화면(폰) 펼친 일정 카드에서 장소 상세로 — S15P21E201-1733.
//
// 🔴 2026-09-26 발표 시연 점검. 일정 카드를 누르면 펼쳐지기만 하고(예상 도착 · 도착 찍기 · 제외) 장소 상세로 가는
//    길이 없었다. 장소 상세(영업시간·경사도·계단·택시 기사에게 보여주기)는 홈 카드·로컬 탐색·피드·저장에서만
//    열려, 시연 흐름 「일정 → 장소 상세」가 끊겼다. 조율 세션 결정: 펼친 카드에 이동 단추 하나.
//    카드 부품(TimelineStop)은 화면 안 함수라 소스로 본다 — 이 저장소의 다른 화면 시험과 같은 방식이다.
declare const require: (id: string) => any;
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');
const read = (...parts: string[]) => readFileSync(join(__dirname, ...parts), 'utf8') as string;

const PAGE = read('..', 'TripPageMobile.tsx');

describe('펼친 일정 카드의 「자세히 보기」', () => {
  it('🔴 카드마다 그 장소의 상세로 가는 길을 넘긴다', () => {
    expect(PAGE).toContain('onOpenPlace={() => router.push(`/place/${item.placeId}`)}');
  });

  it('🔴 펼친 칸에 단추로 그린다 — 누르면 그 길로 간다', () => {
    const detail = PAGE.slice(PAGE.indexOf('<View style={styles.stopDetail}>'));
    expect(detail).toContain("tx('자세히 보기 ›', 'Place details ›')");
    expect(detail).toContain('onPress={onOpenPlace}');
  });

  it('쓰는 문구는 번역표에 이미 있다 — 일·중에서 영어로 떨어지지 않는다', () => {
    const table = read('..', '..', '..', 'i18n', 'translations.ts');
    expect(table).toContain("'자세히 보기 ›': {");
    expect(table).toContain("'%s 상세 보기': {");
  });

  it('🔴 읽어 주기 이름이 카드 펼치기(「%s 자세히」)와 겹치지 않는다 — 두 동작이 같은 이름이면 화면 읽기로 못 가른다', () => {
    const detail = PAGE.slice(PAGE.indexOf('<View style={styles.stopDetail}>'));
    expect(detail).toContain("accessibilityLabel={txf(tx, '%s 상세 보기', 'Open details for %s', item.title)} onPress={onOpenPlace}");
  });
});
