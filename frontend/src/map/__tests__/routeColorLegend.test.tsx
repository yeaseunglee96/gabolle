// 지도 위 경로 색의 범례 — S15P21E201-1896.
//
// 🔴 이 시험이 지키는 것:
//    ① 초록·노랑·빨강·모름 회색 넷과, 어떤 규칙으로 칠했는지 한 줄이 나온다 — 고른 조건(경사 · 그늘 · 둘 다)마다 그 조건의 말로.
//    ② 그늘을 아는 조각이 하나도 없는데 그늘을 골랐으면 «자료가 아직 없다» 고 말한다 — 모르는 것을 아는 척하지 않는다.
//    ③ 칠한 선이 없으면(legend 가 null) 아무것도 안 그린다.
//    ④ 문구가 일본어·중국어(간체·번체) 번역표에 다 있다 — 없으면 그 화면에서만 영어로 떨어진다(check-translations 와 같은 뜻).
import { StyleSheet, View } from 'react-native';
import { render, screen } from '@testing-library/react-native';

import { color } from '@/design/tokens';
import { getTranslation } from '@/i18n/translations';
import { legendCopy, RouteColorLegend } from '@/map/RouteColorLegend';
import type { RouteLegend } from '@/map/routeGrading';
import { GRADE_COLOR } from '@/map/slopeGrades';

const ko = (text: string) => text;
const en = (_ko: string, text: string) => text;

const SLOPE: RouteLegend = { grading: { slope: true, shade: false }, hasShade: false };
const SHADE: RouteLegend = { grading: { slope: false, shade: true }, hasShade: true };
const BOTH: RouteLegend = { grading: { slope: true, shade: true }, hasShade: true };
const BOTH_NO_SHADE: RouteLegend = { grading: { slope: true, shade: true }, hasShade: false };
const SHADE_NO_SHADE: RouteLegend = { grading: { slope: false, shade: true }, hasShade: false };

describe('범례 문구', () => {
  it('🔴 넷의 이름 — 잘 맞아요 · 보통 · 안 맞아요 · 모름', () => {
    expect(legendCopy(SLOPE, ko).labels).toEqual({ good: '잘 맞아요', fair: '보통', bad: '안 맞아요', unknown: '모름' });
    expect(legendCopy(SLOPE, en).labels).toEqual({ good: 'Good fit', fair: 'Fair', bad: 'Poor fit', unknown: 'Unknown' });
  });

  it('🔴 경사만 고르면 경사 기준 — 5% · 8.33% 문턱과 계단', () => {
    const { rule, note } = legendCopy(SLOPE, ko);
    expect(rule).toContain('가파른 경사 피하기');
    expect(rule).toContain('5% 미만');
    expect(rule).toContain('8.33%');
    expect(rule).toContain('계단');
    expect(rule).not.toContain('그늘');
    expect(note).toBeNull();
  });

  it('🔴 그늘만 고르면 그늘 기준', () => {
    const { rule, note } = legendCopy(SHADE, ko);
    expect(rule).toContain('그늘 많은 곳 우선');
    expect(rule).not.toContain('경사');
    expect(note).toBeNull();
  });

  it('🔴 둘 다 고르면 합친 점수 — 경사 60% · 그늘 40%, 그늘 자료가 없는 곳은 경사만', () => {
    const { rule, note } = legendCopy(BOTH, ko);
    expect(rule).toContain('60%');
    expect(rule).toContain('40%');
    expect(rule).toContain('그늘 자료가 없는 곳은 경사만');
    expect(note).toBeNull();
  });

  it('🔴 그늘을 아는 조각이 하나도 없으면 그렇다고 적는다', () => {
    expect(legendCopy(BOTH_NO_SHADE, ko).note).toBe('지금 보이는 길은 그늘 자료가 아직 없어 경사로만 칠했어요');
    expect(legendCopy(SHADE_NO_SHADE, ko).note).toBe('지금 보이는 길은 그늘 자료가 아직 없어 회색으로 보여요');
    // 경사만 고른 여행은 그늘 자료가 없어도 할 말이 없다
    expect(legendCopy(SLOPE, ko).note).toBeNull();
  });

  it('영어 화면에 한글이 섞이지 않는다', () => {
    for (const legend of [SLOPE, SHADE, BOTH, BOTH_NO_SHADE, SHADE_NO_SHADE]) {
      const { labels, rule, note } = legendCopy(legend, en);
      expect([...Object.values(labels), rule, note ?? ''].join(' ')).not.toMatch(/[가-힣]/);
    }
  });

  it('🔴 모든 문구가 일본어·중국어(간체·번체) 번역표에 있다', () => {
    for (const legend of [SLOPE, SHADE, BOTH, BOTH_NO_SHADE, SHADE_NO_SHADE]) {
      const { labels, rule, note } = legendCopy(legend, ko);
      for (const text of [...Object.values(labels), rule, ...(note ? [note] : [])]) {
        for (const field of ['ja', 'zhHans', 'zhHant'] as const) expect({ text, field, translated: getTranslation(text, field) }).toEqual({ text, field, translated: expect.any(String) });
      }
    }
  });
});

describe('범례 그리기', () => {
  it('🔴 칠한 선이 없으면(legend null) 아무것도 안 그린다', () => {
    const view = render(<RouteColorLegend legend={null} tx={ko} />);
    expect(view.toJSON()).toBeNull();
    expect(render(<RouteColorLegend legend={undefined} tx={ko} />).toJSON()).toBeNull();
  });

  it('🔴 넷의 이름과 규칙 한 줄이 나온다', () => {
    render(<RouteColorLegend legend={BOTH_NO_SHADE} tx={ko} />);
    for (const label of ['잘 맞아요', '보통', '안 맞아요', '모름']) expect(screen.getByText(label)).toBeTruthy();
    expect(screen.getByText(legendCopy(BOTH_NO_SHADE, ko).rule)).toBeTruthy();
    expect(screen.getByText('지금 보이는 길은 그늘 자료가 아직 없어 경사로만 칠했어요')).toBeTruthy();
  });

  it('🔴 견본 색이 선에 칠하는 색과 같다 — 초록·노랑·빨강·회색', () => {
    const view = render(<RouteColorLegend legend={SLOPE} tx={ko} />);
    const backgrounds = view.UNSAFE_getAllByType(View)
      .map((node: { props: { style?: unknown } }) => (StyleSheet.flatten(node.props.style as never) as { backgroundColor?: string } | undefined)?.backgroundColor)
      .filter((value: string | undefined): value is string => Boolean(value));
    expect(backgrounds).toEqual(expect.arrayContaining([GRADE_COLOR.good, GRADE_COLOR.fair, GRADE_COLOR.bad, GRADE_COLOR.unknown]));
    expect(GRADE_COLOR).toEqual({ good: color.state.success, fair: color.state.warning, bad: color.state.danger, unknown: color.text.muted });
  });

  it('지도를 누르거나 끄는 것을 막지 않는다 — 눌리지 않는 안내다', () => {
    const view = render(<RouteColorLegend legend={SLOPE} tx={ko} />);
    expect(view.toJSON()).toMatchObject({ props: { pointerEvents: 'none' } });
  });
});
