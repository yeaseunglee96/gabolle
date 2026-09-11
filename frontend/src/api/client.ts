import { Platform } from 'react-native';

export const API_BASE_URL = (process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const configuredTimeout = Number(process.env.EXPO_PUBLIC_API_TIMEOUT_MS ?? 12000);
const API_TIMEOUT_MS = Number.isFinite(configuredTimeout) && configuredTimeout > 0 ? configuredTimeout : 12000;
let apiLanguage: 'ko' | 'en' = 'ko';
export function setApiLanguage(language: 'ko' | 'en') { apiLanguage = language; }
export function getApiLanguage() { return apiLanguage; }

type ApiEnvelope<T> = {
  data: T | null;
  error: { code: string; message: string; fields?: string[] } | null;
  meta: { requestId: string };
};

export class ApiClientError extends Error {
  constructor(
    message: string,
    public readonly code: string,
    public readonly status: number,
    public readonly fields: string[] = [],
    // 서버가 에러 응답에도 data 를 함께 실어 보내는 경우(예: 409 OAUTH_ACCOUNT_LINK_REQUIRED)를 위한 것.
    public readonly data: unknown = null,
  ) {
    super(message);
    this.name = 'ApiClientError';
  }
}

export class ApiUnavailableError extends ApiClientError {
  constructor(
    message = apiLanguage === 'en'
      ? 'The server is unavailable. Please try again shortly.'
      : '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.',
  ) {
    super(message, 'NETWORK_ERROR', 0);
    this.name = 'ApiUnavailableError';
  }
}

type ApiAvailabilityListener = (unavailable: boolean) => void;
const availabilityListeners = new Set<ApiAvailabilityListener>();
let apiUnavailable = false;

function setApiUnavailable(next: boolean) {
  if (apiUnavailable === next) return;
  apiUnavailable = next;
  availabilityListeners.forEach((listener) => listener(next));
}

export function subscribeApiAvailability(listener: ApiAvailabilityListener) {
  availabilityListeners.add(listener);
  listener(apiUnavailable);
  return () => {
    availabilityListeners.delete(listener);
  };
}

type RequestOptions = Omit<RequestInit, 'body'> & { body?: unknown; accessToken?: string | null; skipUnauthorizedHandling?: boolean };
let unauthorizedHandler: (() => void) | null = null;
export function setUnauthorizedHandler(handler: (() => void) | null) { unauthorizedHandler = handler; }

// 액세스 토큰이 만료돼 401 을 받으면, 로그아웃시키기 전에 이 핸들러로 한 번 갱신을 시도한다.
// 여러 요청이 동시에 401 을 받아도 갱신은 한 번만 나가도록 진행 중인 시도를 공유한다.
type RefreshHandler = () => Promise<string | null>;
let refreshHandler: RefreshHandler | null = null;
export function setRefreshHandler(handler: RefreshHandler | null) { refreshHandler = handler; }
let refreshInFlight: Promise<string | null> | null = null;
function refreshAccessToken(): Promise<string | null> {
  if (!refreshHandler) return Promise.resolve(null);
  if (!refreshInFlight) {
    refreshInFlight = refreshHandler().catch(() => null).finally(() => { refreshInFlight = null; });
  }
  return refreshInFlight;
}

export function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return performRequest<T>(path, options, false);
}

async function performRequest<T>(path: string, options: RequestOptions, isRetry: boolean): Promise<T> {
  const { body, accessToken, headers, skipUnauthorizedHandling, ...requestOptions } = options;
  const controller = new AbortController();
  let timedOut = false;
  const abortFromCaller = () => controller.abort();
  if (requestOptions.signal?.aborted) controller.abort();
  else requestOptions.signal?.addEventListener('abort', abortFromCaller, { once: true });
  const timeout = setTimeout(() => { timedOut = true; controller.abort(); }, API_TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...requestOptions,
      signal: controller.signal,
      method: requestOptions.method ?? 'GET',
      credentials: Platform.OS === 'web' ? 'include' : undefined,
      headers: {
        Accept: 'application/json',
        'Accept-Language': apiLanguage,
        ...(body === undefined || body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
        'X-Client-Platform': Platform.OS === 'web' ? 'WEB' : 'MOBILE',
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...headers,
      },
      body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
    });
  } catch {
    if (timedOut) throw new ApiClientError('서버 응답이 늦어 요청을 마쳤어요. 잠시 후 다시 시도해 주세요.', 'REQUEST_TIMEOUT', 0);
    setApiUnavailable(true);
    throw new ApiUnavailableError();
  } finally {
    clearTimeout(timeout);
    requestOptions.signal?.removeEventListener('abort', abortFromCaller);
  }

  // HTTP 오류여도 서버 자체에는 다시 연결된 상태다.
  setApiUnavailable(false);

  if (response.status === 401 && !skipUnauthorizedHandling) {
    if (!isRetry) {
      const refreshedToken = await refreshAccessToken();
      if (refreshedToken) return performRequest<T>(path, { ...options, accessToken: refreshedToken }, true);
    }
    unauthorizedHandler?.();
  }
  if (response.status === 204) return undefined as T;

  const isJson = (response.headers.get('content-type') ?? '').includes('application/json');
  if (response.ok && !isJson) return undefined as T;
  if (!isJson) {
    throw new ApiClientError(`예상하지 못한 서버 응답이에요. (HTTP ${response.status})`, 'INVALID_RESPONSE', response.status);
  }

  const envelope = (await response.json()) as ApiEnvelope<T>;
  if (!response.ok || envelope.error || envelope.data === null) {
    throw new ApiClientError(
      envelope.error?.message ?? '요청을 처리하지 못했어요.',
      envelope.error?.code ?? 'REQUEST_FAILED',
      response.status,
      envelope.error?.fields ?? [],
      envelope.data,
    );
  }
  return envelope.data;
}
