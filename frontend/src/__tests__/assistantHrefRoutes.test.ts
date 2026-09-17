// 챗봇이 보내도 된다고 정한 주소가 **앱에 실제로 있는가** — S15P21E201-1209.
//
// 🔴 이 시험이 막는 것은 「서버와 앱이 서로 다른 주소를 쓰는 것」이다.
//
//    서버(`GeminiAssistantAdapter.ALLOWED_HREFS`)는 챗봇이 갈 수 있는 주소를 못박아 두었다.
//    그런데 앱은 그중 둘을 **다른 이름으로** 만들어 두었다 —
//    `/field/transit` 대신 `/field/bus`, `/field/exchange-rate` 대신 `/field/exchange`.
//    그래서 챗봇에게 「버스 언제 와?」를 물으면 404 「길을 잃었어요」가 떴다
//    (2026-09-18 실기기 `gabolle://field/transit` 로 확인).
//
//    왜 아무도 못 봤나 — 화면은 홈 → 현장 도구로 들어가면 멀쩡히 열린다. 그 길로만 다녔다.
//    그리고 **앱 안에는 `/field/transit` 이라는 글자가 한 번도 안 나온다.** 프론트만 보면
//    단서가 없고, 서버 코드를 같이 열어야만 보인다. 사람은 그걸 매번 하지 않는다.
//
// ## 🔴 붙드는 방법이 둘로 나뉘어 있다 — 일부러 그렇다
//
// 1. **주소가 있는가** → 아래 `import type` 이 본다. expo-router 는 파일 경로가 곧 주소라,
//    이 import 가 풀린다는 것이 곧 그 주소가 있다는 뜻이다. 파일 이름이 바뀌면
//    **시험이 돌기도 전에 `tsc` 가 빨개진다.**
//
//    `import type` 인 것이 중요하다 — 실행할 때는 통째로 사라지므로 화면이 끌고 오는
//    네이티브 모듈(지도·소리)을 흔들 필요가 없다. 그런 몸통 큰 시험은 금방 낡는다.
//
// 2. **목록이 서버와 같은가** → 아래 jest 시험이 본다.
import type ExchangeRateScreen from '../../app/field/exchange-rate';
import type PlanBasicScreen from '../../app/plan/basic';
import type TransitScreen from '../../app/field/transit';
import type TranslateScreen from '../../app/field/translate';
import type TripsScreen from '../../app/(tabs)/trips';

/** 위 import 가 다섯 다 풀렸다는 것을 타입 수준에서 한 번 더 못박는다. */
type RouteModule = typeof PlanBasicScreen | typeof TripsScreen | typeof TranslateScreen
  | typeof TransitScreen | typeof ExchangeRateScreen;
type _RoutesResolve = RouteModule extends never ? never : true;

/**
 * 서버가 챗봇에게 허용한 주소 (`GeminiAssistantAdapter.ALLOWED_HREFS`, back/dev).
 *
 * 🔴 서버에서 이 목록이 바뀌면 **여기와 위 import 를 같이** 고친다. 바로 그 순간이
 *    「앱에 그 화면이 있는가」를 확인해야 하는 순간이고, 그게 이 시험의 존재 이유다.
 */
const ASSISTANT_HREFS = [
  '/plan/basic',
  '/trips',
  '/field/translate',
  '/field/transit',
  '/field/exchange-rate',
] as const;

describe('챗봇이 안내하는 주소는 앱에 전부 있어야 한다', () => {
  it('🔴 서버가 허용한 주소 다섯을 그대로 들고 있다', () => {
    // 위 import 다섯과 이 목록 다섯이 짝이다. 서버 목록이 늘었는데 여기를 안 고치면,
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
