// 지도 위 경로 색의 범례 — S15P21E201-1896.
//
// 🔴 이 시험이 지키는 것:
//    ① 초록·노랑·빨강·모름 회색 넷이 고른 조건(경사 · 그늘 · 둘 다)의 말로 나온다 — % 숫자·규칙 줄은 없다(S15P21E201-2023).
//    ② 그늘을 아는 조각이 하나도 없는데 그늘을 골랐으면 «자료가 아직 없다» 고 말한다 — 모르는 것을 아는 척하지 않는다.
//    ③ 칠한 선이 없으면(legend 가 null) 아무것도 안 그린다.
//    ④ 문구가 일본어·중국어(간체·번체)·베트남어 번역표에 다 있다 — 없으면 그 화면에서만 영어로 떨어진다(check-translations 와 같은 뜻).
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
  it('🔴 경사만 — 평탄 · 조금 가파름 · 가파름·계단 · 모름 (S15P21E201-2023)', () => {
    expect(legendCopy(SLOPE, ko).labels).toEqual({ good: '평탄', fair: '조금 가파름', bad: '가파름·계단', unknown: '모름' });
    expect(legendCopy(SLOPE, en).labels).toEqual({ good: 'Flat', fair: 'A bit steep', bad: 'Steep or stairs', unknown: 'Unknown' });
    expect(legendCopy(SLOPE, ko).note).toBeNull();
  });

  it('🔴 그늘만 — 그늘 많음 · 보통 · 그늘 적음', () => {
    expect(legendCopy(SHADE, ko).labels).toEqual({ good: '그늘 많음', fair: '보통', bad: '그늘 적음', unknown: '모름' });
    expect(legendCopy(SHADE, ko).note).toBeNull();
  });

  it('🔴 둘 다 — 걷기 편함 · 보통 · 걷기 힘듦', () => {
    expect(legendCopy(BOTH, ko).labels).toEqual({ good: '걷기 편함', fair: '보통', bad: '걷기 힘듦', unknown: '모름' });
    expect(legendCopy(BOTH, ko).note).toBeNull();
  });

  it('🔴 어떤 문구에도 % 숫자가 없다 — 경사 % 는 ±3~5%p 오차라 숫자로 안 보인다 (S15P21E201-2023)', () => {
    for (const legend of [SLOPE, SHADE, BOTH, BOTH_NO_SHADE, SHADE_NO_SHADE]) {
      for (const tx of [ko, en]) {
        const { labels, note } = legendCopy(legend, tx);
        expect([...Object.values(labels), note ?? ''].join(' ')).not.toMatch(/[0-9]|%/);
      }
    }
  });

  it('🔴 그늘을 아는 조각이 하나도 없으면 그렇다고 적는다', () => {
    expect(legendCopy(BOTH_NO_SHADE, ko).note).toBe('그늘 자료가 없어 경사로만 칠했어요');
    expect(legendCopy(SHADE_NO_SHADE, ko).note).toBe('그늘 자료가 아직 없어요');
    // 경사만 고른 여행은 그늘 자료가 없어도 할 말이 없다
    expect(legendCopy(SLOPE, ko).note).toBeNull();
  });

  it('영어 화면에 한글이 섞이지 않는다', () => {
    for (const legend of [SLOPE, SHADE, BOTH, BOTH_NO_SHADE, SHADE_NO_SHADE]) {
      const { labels, note } = legendCopy(legend, en);
      expect([...Object.values(labels), note ?? ''].join(' ')).not.toMatch(/[가-힣]/);
    }
  });

  it('🔴 모든 문구가 일본어·중국어(간체·번체)·베트남어 번역표에 있다', () => {
    for (const legend of [SLOPE, SHADE, BOTH, BOTH_NO_SHADE, SHADE_NO_SHADE]) {
      const { labels, note } = legendCopy(legend, ko);
      for (const text of [...Object.values(labels), ...(note ? [note] : [])]) {
        for (const field of ['ja', 'zhHans', 'zhHant', 'vi'] as const) expect({ text, field, translated: getTranslation(text, field) }).toEqual({ text, field, translated: expect.any(String) });
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

  it('🔴 넷의 이름 한 줄과, 있으면 덧붙임 한 마디만 나온다 — 규칙 줄·숫자 없음', () => {
    render(<RouteColorLegend legend={BOTH_NO_SHADE} tx={ko} />);
    for (const label of ['걷기 편함', '보통', '걷기 힘듦', '모름']) expect(screen.getByText(label)).toBeTruthy();
    expect(screen.getByText('그늘 자료가 없어 경사로만 칠했어요')).toBeTruthy();
    expect(screen.queryByText(/%/)).toBeNull();
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
