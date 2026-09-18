import { ApiClientError } from './client';

/**
 * 서버가 **「이 기능은 아직 열쇠가 없다」** 고 말했는가 — S15P21E201-1200.
 *
 * 🔴 왜 상태 숫자가 아니라 **코드**로 가르나.
 *
 * 서버는 바깥 업체(환율·버스·번역·기상청·Gemini)를 부르는 자리에서, 그 업체의 열쇠가
 * 설정돼 있지 않으면 `*_VENDOR_NOT_CONFIGURED` 를 **502** 로 돌려준다. 그런데 502 는
 * 앱에게 「서버가 죽었다」로 읽힌다 — `client.ts` 가 5xx 를 그렇게 읽기로 정해 두었고
 * (S15P21E201-1081), 그건 배포 중 nginx 502 를 놓치던 것을 고치려고 일부러 그랬다.
 *
 * 이 502 는 그 502 가 아니다. **서버는 멀쩡하다.** 같은 순간 다른 API 는 전부 200 이고,
 * 기능 하나가 준비되지 않았다는 말을 5xx 로 하고 있을 뿐이다. 그래서 상태 숫자로는
 * 이 둘을 못 가른다 — 숫자는 같기 때문이다. 가르는 것은 코드뿐이다.
 *
 * 서버가 나중에 503 으로 바꿔도 이 함수는 그대로 맞는다. 숫자를 안 보기 때문이다.
 *
 * @see S15P21E201-1186 — 열쇠 다섯 개를 실제로 넣는 일. 그건 사람이 발급해야 한다
 */
export function isVendorNotReady(error: unknown): boolean {
  return error instanceof ApiClientError && error.code.endsWith('_VENDOR_NOT_CONFIGURED');
}

/**
 * 준비되지 않은 기능 앞에서 화면이 할 말.
 *
 * 🔴 「잠시 후 다시 시도해 주세요」라고 하지 않는다. **아무리 눌러도 달라지지 않는다.**
 * 그 말은 사용자를 자기 인터넷을 의심하게 만들고, 심사자에게는 기능이 깨진 것으로 보인다.
 * 모르는 것을 아는 척하는 것보다, 준비 중이라고 말하는 쪽이 정확하다.
 */
export function vendorNotReadyMessage(tx: (ko: string, en: string) => string) {
  return tx(
    '이 기능은 아직 준비 중이에요. 준비되면 바로 쓸 수 있어요.',
    "This feature isn't ready yet. It will work as soon as it's set up.",
  );
}
