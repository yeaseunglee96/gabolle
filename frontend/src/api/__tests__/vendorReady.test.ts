// 「아직 열쇠가 없다」와 「서버가 죽었다」를 가른다 —.
import { ApiClientError } from '../client';
import { isVendorNotReady, vendorNotReadyMessage } from '../vendorReady';

const tx = (ko: string) => ko;

describe('isVendorNotReady — 코드로 가른다, 숫자로 가르지 않는다', () => {
  // 서버(back/dev)가 실제로 던지는 다섯. 여기 적어 두면 늘거나 바뀔 때 이 시험이 먼저 안다.
  const CODES = [
    'ASSISTANT_VENDOR_NOT_CONFIGURED',
    'EXCHANGE_RATE_VENDOR_NOT_CONFIGURED',
    'TRANSLATE_VENDOR_NOT_CONFIGURED',
    'TRANSIT_VENDOR_NOT_CONFIGURED',
    'WEATHER_VENDOR_NOT_CONFIGURED',
  ];

  it('다섯 코드를 전부 알아본다', () => {
    for (const code of CODES) {
      expect(isVendorNotReady(new ApiClientError('설정되지 않았습니다.', code, 502))).toBe(true);
    }
  });

  it('🔴 상태 숫자가 바뀌어도 판단이 안 흔들린다 — 서버가 503 으로 옮겨도 그대로다', () => {
    for (const status of [500, 502, 503, 400]) {
      expect(isVendorNotReady(new ApiClientError('x', 'TRANSIT_VENDOR_NOT_CONFIGURED', status))).toBe(true);
    }
  });

  it('🔴 진짜 서버 고장은 같은 502 여도 아니라고 한다', () => {
    expect(isVendorNotReady(new ApiClientError('서버 오류', 'SERVER_ERROR', 502))).toBe(false);
    expect(isVendorNotReady(new ApiClientError('요청을 처리하지 못했어요.', 'REQUEST_FAILED', 500))).toBe(false);
  });

  it('ApiClientError 가 아니면 아니다', () => {
    expect(isVendorNotReady(new Error('boom'))).toBe(false);
    expect(isVendorNotReady(null)).toBe(false);
    expect(isVendorNotReady(undefined)).toBe(false);
  });
});

describe('vendorNotReadyMessage — 「다시 시도」를 권하지 않는다', () => {
  it('🔴 다시 해 보라고 하지 않는다 — 눌러도 달라지지 않기 때문이다', () => {
    const message = vendorNotReadyMessage(tx);
    expect(message).not.toContain('다시 시도');
    expect(message).not.toContain('잠시 후');
    expect(message).toContain('준비');
  });
});
