import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';

export const API_BASE_URL = (process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '');
// 서버가 초대 응답에 주는 건 token·acceptPath(서버 API 경로)뿐이다 — 앱 화면 주소는 앱이
// 스스로 조립해야 한다(TripInviteResponse·StoryInviteResponse 공통 계약). oauth.ts의
// CALLBACK_BASE_URL과 같은 값이라 같은 환경변수를 그대로 쓴다 — 이 웹앱이 실제로 떠 있는 자리다.
export const APP_WEB_BASE_URL = (process.env.EXPO_PUBLIC_OAUTH_CALLBACK_BASE_URL ?? 'https://j15e201.p.ssafy.io').replace(/\/$/, '');
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

// 🔴 S15P21E201-1081 — 「서버가 잠깐 못 받는다」와 「이 기능이 아직 없다」를 가르는 자리.
//
// 배포할 때마다 백엔드가 잠깐 끊기고 그동안 nginx 가 502·503·504 를 준다. 그 응답의 본문은
// JSON 이 아니라 HTML 이라, 예전에는 전부 INVALID_RESPONSE 로 떨어졌다. 그것을 보고 화면들이
// "이 API 는 아직 준비되지 않았어요" 라고 말했고, 사용자는 **아직 만들지 않은 기능**으로 읽고
// 나갔다. 실제로는 몇십 초 뒤면 되는 것이었다.
//
// 그래서 상태 코드로 가른다 — 404·501 만 "아직 없다" 이고, 5xx 는 "잠시 후 다시" 다.
// 같은 저장소의 routeDirections.ts·itinerary.ts 가 이미 쓰던 방식이다.
export const SERVER_ERROR_CODE = 'SERVER_ERROR';

/**
 * 5xx 인가 — 서버가 이번 요청을 처리하지 못한 것이지, 그 기능이 없는 것이 아니다.
 *
 * 🔴 **501 은 뺀다.** 숫자로는 5xx 지만 이 저장소에서 501(Not Implemented)은 404 와 한 짝으로
 * "아직 안 만들었다" 를 뜻한다(routeDirections.ts·itinerary.ts 가 `404 || 501` 로 함께 본다).
 * 여기 넣으면 "잠시 후 다시" 라고 말하게 되는데, 기다려도 생기지 않는 것이라 거짓말이 된다.
 */
export function isServerErrorStatus(status: number): boolean {
  return status >= 500 && status <= 599 && status !== 501;
}

/** 이 오류가 5xx 때문인가. 화면이 "잠시 후 다시" 로 말해야 하는 경우다. */
export function isServerError(error: unknown): boolean {
  return error instanceof ApiClientError && isServerErrorStatus(error.status);
}

function serverErrorMessage(status: number): string {
  return apiLanguage === 'en'
    ? `The server could not handle this just now. Please try again shortly. (HTTP ${status})`
    : `서버가 잠시 응답하지 못했어요. 잠시 후 다시 시도해 주세요. (HTTP ${status})`;
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

// 익명 출입증(S15P21E201-303 이 발급하는 X-Session-Token) — 가입 안 한 사람의 요청도
// 같은 세션으로 묶이도록 여기서 한 번만 발급받아 모든 요청에 자동으로 붙인다(S15P21E201-311).
const ANONYMOUS_SESSION_STORAGE_KEY = 'gabolle.anonymous-session-token';
let anonymousSessionToken: string | null = null;
let anonymousSessionPromise: Promise<string | null> | null = null;

async function readStoredAnonymousSessionToken(): Promise<string | null> {
  try {
    if (Platform.OS === 'web') {
      return typeof localStorage === 'undefined' ? null : localStorage.getItem(ANONYMOUS_SESSION_STORAGE_KEY);
    }
    return await SecureStore.getItemAsync(ANONYMOUS_SESSION_STORAGE_KEY);
  } catch {
    return null;
  }
}

async function writeStoredAnonymousSessionToken(token: string): Promise<void> {
  try {
    if (Platform.OS === 'web') {
      if (typeof localStorage !== 'undefined') localStorage.setItem(ANONYMOUS_SESSION_STORAGE_KEY, token);
      return;
    }
    await SecureStore.setItemAsync(ANONYMOUS_SESSION_STORAGE_KEY, token);
  } catch {
    // 저장에 실패해도 메모리의 토큰은 남아 있어 이번 실행 동안은 계속 쓸 수 있다.
  }
}

async function requestAnonymousSessionToken(): Promise<string | null> {
  // 이 fetch 에 타임아웃이 없으면 응답이 안 오는 동안 영영 안 끝나고, ensureAnonymousSessionToken()의
  // anonymousSessionPromise 도 settle 되지 않아 그대로 남는다 — 이후 모든 API 요청이 이 프라미스를
  // 그대로 돌려받아 앱 전체가 멈춘다(S15P21E201-929). performRequest() 의 타임아웃은 이 fetch 뒤에
  // 시작하므로 여기를 보호하지 못한다.
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), API_TIMEOUT_MS);
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/auth/anonymous`, {
      method: 'POST',
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    });
    if (!response.ok) return null;
    const envelope = (await response.json()) as ApiEnvelope<{ sessionId: string; sessionToken: string; issuedAt: string }>;
    return envelope.data?.sessionToken ?? null;
  } catch {
    return null;
  } finally {
    clearTimeout(timeout);
  }
}

async function ensureAnonymousSessionToken(): Promise<string | null> {
  if (anonymousSessionToken) return anonymousSessionToken;
  if (!anonymousSessionPromise) {
    anonymousSessionPromise = (async () => {
      const stored = await readStoredAnonymousSessionToken();
      if (stored) {
        anonymousSessionToken = stored;
        return stored;
      }
      // 발급이 실패하면 한 번 더 시도하고, 그래도 실패하면 연결 실패로 처리한다.
      const issued = (await requestAnonymousSessionToken()) ?? (await requestAnonymousSessionToken());
      if (issued) {
        anonymousSessionToken = issued;
        setApiUnavailable(false);
        await writeStoredAnonymousSessionToken(issued);
      } else {
        setApiUnavailable(true);
      }
      return issued;
    })().finally(() => {
      anonymousSessionPromise = null;
    });
  }
  return anonymousSessionPromise;
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
  const sessionToken = await ensureAnonymousSessionToken();
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
        ...(sessionToken ? { 'X-Session-Token': sessionToken } : {}),
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

  // HTTP 오류여도 서버 자체에는 다시 연결된 상태다 — 🔴 5xx 는 빼고(S15P21E201-1081).
  //
  // 예전에는 응답이 오기만 하면 무조건 setApiUnavailable(false) 였다. 그런데 배포 중에
  // nginx 가 502 를 주는 동안에도 "응답은 온" 것이라, 앱은 서버가 멀쩡하다고 판단했고
  // "서버에 연결할 수 없어요" 배너가 끝내 안 떴다. 사용자는 화면마다 다른 말을 들었다.
  const serverSideFailure = isServerErrorStatus(response.status);
  setApiUnavailable(serverSideFailure);

  if (response.status === 401 && !skipUnauthorizedHandling) {
    if (!isRetry) {
      const refreshedToken = await refreshAccessToken();
      if (refreshedToken) return performRequest<T>(path, { ...options, accessToken: refreshedToken }, true);
    }
    // S15P21E201-997 — 회원 토큰 없이 부른 요청의 401 은 "세션이 끊겼다"가 아니라 "이 경로는
    // 로그인이 필요하다"는 뜻이다. 서버가 익명 출입증에 401 을 주는 자리가 여럿인데, 그것을
    // 전부 세션 만료로 읽어 로그인 화면으로 튕기면 로그인한 적 없는 사람이 화면을 보기도 전에
    // 쫓겨난다. 튕기는 것은 회원 토큰을 들고 갔는데도 거절당했을 때(=갱신까지 실패)뿐이다.
    if (accessToken) unauthorizedHandler?.();
  }
  if (response.status === 204) return undefined as T;

  const isJson = (response.headers.get('content-type') ?? '').includes('application/json');
  if (response.ok && !isJson) return undefined as T;
  if (!isJson) {
    // 🔴 5xx 의 HTML 본문(nginx 의 502 안내 쪽)을 "예상하지 못한 응답" 으로 부르지 않는다.
    //    그 이름이 화면에서 "아직 만들지 않은 기능" 으로 번역되는 것이 이 버그였다.
    if (serverSideFailure) {
      throw new ApiClientError(serverErrorMessage(response.status), SERVER_ERROR_CODE, response.status);
    }
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
