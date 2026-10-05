// 폴드 바깥 화면(약 369dp)·글자 크게에서 꺾이고 가리던 것 — S15P21E201-1986.
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

const read = (p: string) => readFileSync(join(__dirname, '..', '..', p), 'utf8') as string;

describe('좁은 화면', () => {
  it('🔴 여행 화면 도구 줄은 좁으면 세 칸씩 두 줄, 이름은 한 줄', () => {
    const src = read('src/trip/page/TripPageMobile.tsx');
    expect(src).toContain('narrowTools && styles.actionsGrid');
    expect(src).toContain('numberOfLines={narrow ? 1 : 2}');
    expect(src).toMatch(/width \/ PixelRatio\.getFontScale\(\) < TOOL_ROW_MIN_WIDTH/);
    expect((src.match(/<ActionTile narrow=\{narrowTools\}/g) ?? []).length).toBe(6);
  });
  it('🔴 홈 맨 아래는 떠 있는 AI 단추 높이만큼 비워 둔다', () => {
    expect(read('app/(tabs)/home.tsx')).toContain('testID="home-assistant-clearance"');
  });
  it('🔴 머리말 날씨 숫자는 줄지 않는다 — 이름표만 준다', () => {
    const src = read('src/home/HomeBlocks.tsx');
    expect(src).toContain('weatherValue: { flexShrink: 0 }');
    expect(src).toContain('weatherLabel: { flexShrink: 1 }');
  });
  it('🔴 버스 검색칸은 좁으면 예시를 뺀 짧은 안내', () => {
    expect(read('src/field/DestinationPicker.tsx')).toContain("shortHint ? tx('장소 이름으로 찾기', 'Search by place name')");
  });
});
