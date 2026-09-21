// 챗봇이 보내도 된다고 정한 주소가 앱에 실제로 있고, 앱이 그 주소를 받아들이는가 — S15P21E201-1209.
import { ALLOWED_NAVIGATE_HREFS, isAllowedNavigateHref } from '@/assistant/assistantApi';
import type ExchangeRateScreen from '../../app/field/exchange-rate';
import type PlanBasicScreen from '../../app/plan/basic';
import type PlanScreen from '../../app/plan/index';
import type TransitScreen from '../../app/field/transit';
import type TranslateScreen from '../../app/field/translate';
import type TripsScreen from '../../app/(tabs)/trips';

/** 위 import 가 다 풀렸다는 것을 타입 수준에서 한 번 더 못박는다. */
type RouteModule = typeof PlanScreen | typeof PlanBasicScreen | typeof TripsScreen
  | typeof TranslateScreen | typeof TransitScreen | typeof ExchangeRateScreen;
type _RoutesResolve = RouteModule extends never ? never : true;

/**
 * 🔴 서버가 실제로 내주는 주소. 베낀 목록이 아니라 **실측**이다 — 2026-09-18 운영
 * (j15e201.p.ssafy.io)에서 `POST /api/v1/assistant/messages` 를 다섯 번 불러 받은 값이다.
 * 백엔드 `GeminiAssistantAdapter.ALLOWED_HREFS` 와 짝이다.
 *
 * 이 시험의 요점은 이 배열이 아니라 **앱이 실제로 쓰는 `ALLOWED_NAVIGATE_HREFS` 와
 * 대조한다는 것**이다. 예전 판은 베낀 배열을 자기 자신과 비교해서, 두 목록이 갈라진 뒤에도
 * 초록이었다 — 그동안 앱에서는 버튼 셋이 사라져 있었다 (S15P21E201-1273).
 */
const SERVER_HREFS = [
  '/plan/basic',
  '/trips',
  '/field/translate',
  '/field/transit',
  '/field/exchange-rate',
] as const;

describe('비서가 안내하는 주소는 앱이 전부 받아들여야 한다', () => {
  it.each(SERVER_HREFS)('🔴 서버가 내주는 %s 를 앱이 받는다', (href) => {
    // 못 받으면 fromDto 가 help 로 낮추고, 화면에서 이동 버튼이 사라진다.
    expect(isAllowedNavigateHref(href)).toBe(true);
  });

  it('🔴 쿼리가 붙어도 받는다 — 서버는 /plan/basic?days=2 처럼 보낸다', () => {
    expect(isAllowedNavigateHref('/plan/basic?days=2')).toBe(true);
    expect(isAllowedNavigateHref('/plan?days=3&people=4')).toBe(true);
    expect(isAllowedNavigateHref('/trips#recent')).toBe(true);
  });

  it('🔴 /plan 과 /plan/basic 을 둘 다 받는다 — 이미 깔린 앱이 안 깨지게', () => {
    // 백엔드가 /plan/basic → /plan 으로 옮겨 가는 중이다. 한쪽만 받으면 옮기는 순간
    // 서버와 앱 중 한쪽이 반드시 버튼을 잃는다.
    expect(ALLOWED_NAVIGATE_HREFS).toContain('/plan');
    expect(ALLOWED_NAVIGATE_HREFS).toContain('/plan/basic');
  });

  it('🔴 목록 밖은 안 받는다 — 모델이 지어낸 주소로 이동하지 않는다', () => {
    expect(isAllowedNavigateHref('/admin')).toBe(false);
    expect(isAllowedNavigateHref('/plan/basic/../admin')).toBe(false);
    expect(isAllowedNavigateHref('')).toBe(false);
    expect(isAllowedNavigateHref(null)).toBe(false);
  });

  it('🔴 옛 이름은 목록에 없다 — /field/bus · /field/exchange 로 되돌아가지 않는다', () => {
    expect(ALLOWED_NAVIGATE_HREFS).not.toContain('/field/bus');
    expect(ALLOWED_NAVIGATE_HREFS).not.toContain('/field/exchange');
  });
});
