// 챗봇이 보내도 된다고 정한 주소가 앱에 실제로 있는가 —.
import type ExchangeRateScreen from '../../app/field/exchange-rate';
import type PlanBasicScreen from '../../app/plan/basic';
import type TransitScreen from '../../app/field/transit';
import type TranslateScreen from '../../app/field/translate';
import type TripsScreen from '../../app/(tabs)/trips';

/** 위 import 가 다섯 다 풀렸다는 것을 타입 수준에서 한 번 더 못박는다. */
type RouteModule = typeof PlanBasicScreen | typeof TripsScreen | typeof TranslateScreen
  | typeof TransitScreen | typeof ExchangeRateScreen;
type _RoutesResolve = RouteModule extends never ? never : true;

/** 서버가 챗봇에게 허용한 주소 (`GeminiAssistantAdapter.ALLOWED_HREFS`, back/dev). */
const ASSISTANT_HREFS = [
  '/plan/basic',
  '/trips',
  '/field/translate',
  '/field/transit',
  '/field/exchange-rate',
] as const;

describe('챗봇이 안내하는 주소는 앱에 전부 있어야 한다', () => {
  it('🔴 서버가 허용한 주소 다섯을 그대로 들고 있다', () => {
    // 위 import 다섯과 이 목록 다섯이 짝이다. 서버 목록이 늘었는데 여기를 안 고치면
    // 늘어난 주소는 앱에 화면이 있는지 아무도 확인하지 않게 된다.
    expect(ASSISTANT_HREFS).toHaveLength(5);
    expect(ASSISTANT_HREFS).toEqual([
      '/plan/basic',
      '/trips',
      '/field/translate',
      '/field/transit',
      '/field/exchange-rate',
    ]);
  });

  it('🔴 옛 이름은 목록에 없다 — /field/bus · /field/exchange 로 되돌아가지 않는다', () => {
    expect(ASSISTANT_HREFS).not.toContain('/field/bus');
    expect(ASSISTANT_HREFS).not.toContain('/field/exchange');
  });
});
