// 여행 페이지가 경로 선을 무엇으로 칠하나 — 고른 조건을 어디서 읽는가. S15P21E201-1896.
//
// 🔴 이 시험이 지키는 것:
//    ① 서버가 알려 준 값(일정 응답의 slopeAvoid · shadePrefer)이 먼저다. 서버가 false 라고 답하면 기기 초안이 무엇이든 false.
//    ② 칸이 없을 때만(옛 서버) 기기 초안으로 대신한다 — slopeConstraint === 'AVOID' · shadePreference === 'PREFER'.
//       ALLOW · NO_PREFERENCE · null 은 «고르지 않음» 이다.
//    ③ 둘 다 없으면 조건 없음(둘 다 false).
//    ④ 선(dayRoutes)이 그 조건과 조각을 그대로 지도에 넘긴다 — grading 을 안 넘기면 키 자체가 없다(전과 같은 모양).
import { legKey } from '@/map/courseRoutePaths';
import { dayMap, dayRoutes, routeGradingOf } from '@/trip/page/tripPageModel';

describe('고른 조건 읽기 — routeGradingOf', () => {
  it('🔴 서버가 알려 준 값이 먼저다 — 서버가 false 면 초안이 AVOID·PREFER 여도 false', () => {
    const draft = { slopeConstraint: 'AVOID' as const, shadePreference: 'PREFER' as const };
    expect(routeGradingOf({ slopeAvoid: false, shadePrefer: false }, draft)).toEqual({ slope: false, shade: false });
    expect(routeGradingOf({ slopeAvoid: true, shadePrefer: false }, null)).toEqual({ slope: true, shade: false });
    expect(routeGradingOf({ slopeAvoid: false, shadePrefer: true }, null)).toEqual({ slope: false, shade: true });
    expect(routeGradingOf({ slopeAvoid: true, shadePrefer: true }, null)).toEqual({ slope: true, shade: true });
  });

  it('🔴 칸이 없는 옛 서버에서는 초안으로 대신한다', () => {
    expect(routeGradingOf({}, { slopeConstraint: 'AVOID', shadePreference: 'PREFER' })).toEqual({ slope: true, shade: true });
    expect(routeGradingOf(null, { slopeConstraint: 'AVOID', shadePreference: null })).toEqual({ slope: true, shade: false });
    expect(routeGradingOf(undefined, { slopeConstraint: null, shadePreference: 'PREFER' })).toEqual({ slope: false, shade: true });
  });

  it('한 칸만 오면 그 칸은 서버 값, 없는 칸만 초안으로', () => {
    expect(routeGradingOf({ slopeAvoid: false }, { slopeConstraint: 'AVOID', shadePreference: 'PREFER' })).toEqual({ slope: false, shade: true });
    expect(routeGradingOf({ shadePrefer: false }, { slopeConstraint: 'AVOID', shadePreference: 'PREFER' })).toEqual({ slope: true, shade: false });
  });

  it('🔴 ALLOW · NO_PREFERENCE · 비어 있음은 «고르지 않음» 이다', () => {
    expect(routeGradingOf({}, { slopeConstraint: 'ALLOW', shadePreference: 'NO_PREFERENCE' })).toEqual({ slope: false, shade: false });
    expect(routeGradingOf({}, { slopeConstraint: null, shadePreference: null })).toEqual({ slope: false, shade: false });
  });

  it('🔴 둘 다 없으면 조건 없음 — 예전처럼 늘 경사 색으로 칠하지 않는다', () => {
    expect(routeGradingOf(null, null)).toEqual({ slope: false, shade: false });
    expect(routeGradingOf({}, null)).toEqual({ slope: false, shade: false });
  });
});

describe('선에 실어 보내기 — dayRoutes', () => {
  const stop = (id: string, number: number, latitude: number) => ({ id, number, name: id, latitude, longitude: 129.1 });
  const map = { stops: [stop('a', 1, 35.1), stop('b', 2, 35.104)], days: [] };
  const pieces = [{ from: 0, to: 1, slopePercent: 9, stairs: false, shade: 0.2 }];
  const legs = { [legKey(1, 0)]: { path: [{ latitude: 35.1, longitude: 129.1 }, { latitude: 35.104, longitude: 129.1 }], estimated: false, pieces } };

  it('🔴 고른 조건과 그늘까지 실린 조각이 그대로 지도에 간다', () => {
    const [route] = dayRoutes(map, 1, '#123456', legs, { slope: true, shade: true });
    expect(route.grading).toEqual({ slope: true, shade: true });
    expect(route.pieces).toEqual(pieces);
    expect(route.color).toBe('#123456');
  });

  it('조건을 안 골랐어도 grading 은 «둘 다 false» 로 간다 — 지도가 한 색으로 그리게', () => {
    const [route] = dayRoutes(map, 1, '#123456', legs, { slope: false, shade: false });
    expect(route.grading).toEqual({ slope: false, shade: false });
  });

  it('grading 을 안 주면 키 자체가 없다 — 전과 같은 모양', () => {
    const [route] = dayRoutes(map, 1, '#123456', legs);
    expect('grading' in route).toBe(false);
  });
});
