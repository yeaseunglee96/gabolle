import { ApiClientError } from './client';

/** 서버가 「이 기능은 아직 열쇠가 없다」 고 말했는가 — S15P21E201-1200. */
export function isVendorNotReady(error: unknown): boolean {
  return error instanceof ApiClientError && error.code.endsWith('_VENDOR_NOT_CONFIGURED');
}

/** 준비되지 않은 기능 앞에서 화면이 할 말. */
export function vendorNotReadyMessage(tx: (ko: string, en: string) => string) {
  return tx(
    '이 기능은 아직 준비 중이에요. 준비되면 바로 쓸 수 있어요.',
    "This feature isn't ready yet. It will work as soon as it's set up.",
  );
}
