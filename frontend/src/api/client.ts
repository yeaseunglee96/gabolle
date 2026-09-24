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

/** 요청이 서버에 닿지도 못했을 때 던진다. */
export class ApiUnavailableError extends ApiClientError {
  constructor(
    message = apiLanguage === 'en'
      ? 'The server is unavailable. Please try again shortly.'
      : '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.',
    /** `fetch` 가 던진 것을 짧게 줄인 말. 원인을 못 알아냈으면 null. */
    public readonly cause: string | null = null,
  ) {
    super(message, 'NETWORK_ERROR', 0);
    this.name = 'ApiUnavailableError';
  }
}

/** 던져진 것에서 사람이 읽을 수 있는 한 줄을 뽑는다. 길면 자른다. */
export function describeThrown(error: unknown): string | null {
  if (error instanceof Error && error.message) return error.message.slice(0, 160);
  if (typeof error === 'string' && error) return error.slice(0, 160);
  return null;
}

// — 「서버가 잠깐 못 받는다」와 「이 기능이 아직 없다」를 가르는 자리.
export const SERVER_ERROR_CODE = 'SERVER_ERROR';

/** 5xx 인가 — 서버가 이번 요청을 처리하지 못한 것이지, 그 기능이 없는 것이 아니다. */
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

/**
 * 호출자가 스스로 끊은 요청. 화면을 떠났거나 다음 검색어가 앞 요청을 대신한 것이라,
 * 사람에게 보여 줄 오류가 아니다 — 대개 그냥 무시하면 된다.
 */
export const REQUEST_CANCELLED_CODE = 'REQUEST_CANCELLED';

type ApiAvailabilityListener = (unavailable: boolean) => void;
const availabilityListeners = new Set<ApiAvailabilityListener>();
let apiUnavailable = false;

/**
 * 🔴 끊겼다고 판단한 뒤 스스로 되묻는 간격. 붙을 때까지 늘려 가며 물어본다 —
 * 서버가 배포로 잠깐 죽은 것이면 첫 번째나 두 번째에 붙고, 오래 죽어 있으면
 * 30초마다 한 번씩만 두드린다.
 */
export const RECOVERY_PROBE_DELAYS_MS = [3000, 5000, 10000, 20000, 30000];

/**
 * 살아 있는지 되물을 때 부르는 경로. **응답 내용은 안 본다** — 서버가 5xx 가 아닌
 * 무엇이든 돌려주면(401 이어도) API 까지 길이 뚫린 것이다.
 */
const RECOVERY_PROBE_PATH = '/api/v1/places/facets';

/** 되묻기가 너무 오래 매달려 있지 않게 하는 시간. 어차피 다음 차례가 또 온다. */
const RECOVERY_PROBE_TIMEOUT_MS = 5000;

let recoveryTimer: ReturnType<typeof setTimeout> | null = null;
let recoveryAttempt = 0;

function stopRecoveryProbe() {
  if (recoveryTimer) { clearTimeout(recoveryTimer); recoveryTimer = null; }
  recoveryAttempt = 0;
}

/**
 * 🔴 배너는 "연결되면 자동으로 사라집니다" 라고 약속한다. 그 약속을 지키는 것이 이 함수다.
 *
 * 예전에는 되묻는 코드가 아예 없어서, 다시 붙었다는 사실을 **다른 요청이 우연히 성공할 때만**
 * 알 수 있었다. 그런데 요청을 하나도 안 하는 화면이 여럿이다(현장 도구·장소별 한국어 등).
 * 그런 화면에 머무는 동안에는 서버가 멀쩡해져도 배너가 영영 남았다 (S15P21E201-1281).
 */
function scheduleRecoveryProbe() {
  if (recoveryTimer) return;
  const delay = RECOVERY_PROBE_DELAYS_MS[Math.min(recoveryAttempt, RECOVERY_PROBE_DELAYS_MS.length - 1)];
  recoveryAttempt += 1;
  recoveryTimer = setTimeout(() => {
    recoveryTimer = null;
    void probeApiReachable().then((reachable) => {
      if (!apiUnavailable) return;           // 그새 다른 요청이 성공해 이미 꺼졌다
      if (reachable) setApiUnavailable(false);
      else scheduleRecoveryProbe();
    });
  }, delay);
  // 되묻기가 기다리는 중이라고 해서 프로세스가 안 끝나면 안 된다 — 시험이 안 끝난다.
  // 폰에는 unref 가 없으므로 있을 때만 부른다.
  (recoveryTimer as { unref?: () => void }).unref?.();
}

async function probeApiReachable(): Promise<boolean> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), RECOVERY_PROBE_TIMEOUT_MS);
  try {
    const response = await fetch(`${API_BASE_URL}${RECOVERY_PROBE_PATH}`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    });
    // 401·404 도 "서버가 대답했다" 는 뜻이다. 5xx 만 아직 죽은 것으로 본다.
    return !isServerErrorStatus(response.status);
  } catch {
    return false;
  } finally {
    clearTimeout(timer);
  }
}

function setApiUnavailable(next: boolean) {
  if (apiUnavailable === next) return;
  apiUnavailable = next;
  availabilityListeners.forEach((listener) => listener(next));
  if (next) scheduleRecoveryProbe();
  else stopRecoveryProbe();
}

/** 시험에서 타이머를 남기지 않기 위한 손잡이. 화면 코드는 부를 일이 없다. */
export function __resetApiAvailabilityForTests() {
  stopRecoveryProbe();
  apiUnavailable = false;
}

export function subscribeApiAvailability(listener: ApiAvailabilityListener) {
  availabilityListeners.add(listener);
  listener(apiUnavailable);
  return () => {
    availabilityListeners.delete(listener);
  };
}

// 익명 출입증이 발급하는 X-Session-Token) — 가입 안 한 사람의 요청도
// 같은 세션으로 묶이도록 여기서 한 번만 발급받아 모든 요청에 자동으로 붙인다.
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
  // 이 fetch 에 타임아웃이 없으면 응답이 안 오는 동안 영영 안 끝나고, ensureAnonymousSessionToken의
  // anonymousSessionPromise 도 settle 되지 않아 그대로 남는다 — 이후 모든 API 요청이 이 프라미스를
  // 그대로 돌려받아 앱 전체가 멈춘다. performRequest 의 타임아웃은 이 fetch 뒤에
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

/**
 * `timeoutMs` — 이 요청에만 쓰는 제한. 안 주면 앱 기본값(12초).
 *
 * 🔴 아무 데나 쓰라고 만든 것이 아니다. 늘리면 그만큼 사용자가 빈 화면을 본다.
 * 지금 쓰는 곳은 메뉴판 읽기 하나뿐이고, 그 근거는 `src/field/menuScan.ts` 에 있다 —
 * 실제 메뉴판이 11~13초가 걸려서 12초로는 못 읽는다(S15P21E201-1315).
 *
 * 🔴 늘릴 때는 **서버 쪽과 함께** 늘린다. 서버가 앱보다 먼저 포기해야 한다 —
 * 앱이 먼저 끊으면 읽기가 성공해도 사용자는 못 받고, 값은 나가고 하루 한도도 깎인다.
 * 규칙은 context/decisions.md 의 DEC-LATENCY-001.
 */
type RequestOptions = Omit<RequestInit, 'body'> & {
  body?: unknown; accessToken?: string | null; skipUnauthorizedHandling?: boolean; timeoutMs?: number;
  /** 본문 말고 응답 머리도 봐야 할 때 — 피드가 «실제로 적용된 정렬»을 `X-Feed-Applied` 로 받는다(S15P21E201-1411). 성공·실패 가리지 않고 한 번 부른다. */
  onResponse?: (response: Response) => void;
};
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

/**
 * 🔴 서버 앞 nginx 는 IP 당 요청 수를 제한하고(limit_req 10r/s, burst 20), 넘친 요청을 **503** 으로
 * 돌려준다. 로그인 뒤 홈은 한꺼번에 API 를 십수 번 부르는데, 그 가운데 절반이 이 503 으로 잘려
 * 「내 여행」·저장·취향 칸이 빈 채로 떴다(S15P21E201-1574). 서버가 죽은 것이 아니라 «잠깐 몰린 것»이라,
 * 토큰이 다시 차오르는 시간(초당 10개)만큼만 기다렸다 GET 을 두 번까지 다시 보낸다.
 * 무작위 지터(jitter — 재시도 시각을 조금씩 흩뜨리는 것)를 섞는 이유는, 잘린 아홉 개가 같은 순간에
 * 다시 몰려 또 잘리지 않게 하려는 것이다. POST 등은 두 번 실행되면 안 되므로 재시도하지 않는다.
 */
export const RATE_LIMIT_RETRY_DELAYS_MS = [500, 1200];
const RATE_LIMIT_RETRY_JITTER_MS = 500;

function isSafeToRetryMethod(method: string | undefined): boolean {
  const upper = (method ?? 'GET').toUpperCase();
  return upper === 'GET' || upper === 'HEAD';
}

export function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return performRequest<T>(path, options, false);
}

async function performRequest<T>(path: string, options: RequestOptions, isRetry: boolean, rateLimitAttempt = 0): Promise<T> {
  const { body, accessToken, headers, skipUnauthorizedHandling, timeoutMs, onResponse, ...requestOptions } = options;
  const controller = new AbortController();
  let timedOut = false;
  const abortFromCaller = () => controller.abort();
  if (requestOptions.signal?.aborted) controller.abort();
  else requestOptions.signal?.addEventListener('abort', abortFromCaller, { once: true });
  // 🔴 timeoutMs 를 requestOptions 에서 빼낸 이유 — 안 빼면 fetch 의 옵션으로 흘러든다.
  const timeout = setTimeout(() => { timedOut = true; controller.abort(); },
    timeoutMs && timeoutMs > 0 ? timeoutMs : API_TIMEOUT_MS);
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
  } catch (error) {
    if (timedOut) throw new ApiClientError('서버 응답이 늦어 요청을 마쳤어요. 잠시 후 다시 시도해 주세요.', 'REQUEST_TIMEOUT', 0);
    // 🔴 호출자가 끊은 것은 서버가 죽은 것이 아니다 (S15P21E201-1281).
    // 화면을 떠날 때 cleanup 이 요청을 끊고(app/place/[id].tsx 등 아홉 곳), 출발지
    // 검색창은 글자를 칠 때마다 앞 요청을 끊는다. 그것까지 끊김으로 세면 평범하게
    // 쓰는 것만으로 "서버 연결을 확인하고 있어요" 가 떴다.
    if (requestOptions.signal?.aborted) {
      throw new ApiClientError('요청을 취소했어요.', REQUEST_CANCELLED_CODE, 0);
    }
    setApiUnavailable(true);
    // 원인을 버리지 않는다. 자세한 이유는 ApiUnavailableError 참고.
    throw new ApiUnavailableError(undefined, describeThrown(error));
  } finally {
    clearTimeout(timeout);
    requestOptions.signal?.removeEventListener('abort', abortFromCaller);
  }

  // 깃발(setApiUnavailable)을 올리기 «전에» 재시도한다 — 잠깐 몰린 503 이 「서버 연결 불가」 배너를 깜빡이게 하면 안 된다.
  if (
    response.status === 503
    && isSafeToRetryMethod(requestOptions.method)
    && rateLimitAttempt < RATE_LIMIT_RETRY_DELAYS_MS.length
    && !requestOptions.signal?.aborted
  ) {
    await new Promise<void>((resolve) => {
      setTimeout(resolve, RATE_LIMIT_RETRY_DELAYS_MS[rateLimitAttempt] + Math.random() * RATE_LIMIT_RETRY_JITTER_MS);
    });
    if (!requestOptions.signal?.aborted) {
      return performRequest<T>(path, options, isRetry, rateLimitAttempt + 1);
    }
    throw new ApiClientError('요청을 취소했어요.', REQUEST_CANCELLED_CODE, 0);
  }

  // HTTP 오류여도 서버 자체에는 다시 연결된 상태다 — 5xx 는 빼고.
  const serverSideFailure = isServerErrorStatus(response.status);
  setApiUnavailable(serverSideFailure);
  try { onResponse?.(response); } catch { /* 머리를 읽다 던져도 요청 자체는 살린다 */ }

  if (response.status === 401 && !skipUnauthorizedHandling) {
    if (!isRetry) {
      const refreshedToken = await refreshAccessToken();
      if (refreshedToken) return performRequest<T>(path, { ...options, accessToken: refreshedToken }, true);
    }
    // — 회원 토큰 없이 부른 요청의 401 은 "세션이 끊겼다"가 아니라 "이 경로는
    // 로그인이 필요하다"는 뜻이다. 서버가 익명 출입증에 401 을 주는 자리가 여럿인데, 그것을
    // 전부 세션 만료로 읽어 로그인 화면으로 튕기면 로그인한 적 없는 사람이 화면을 보기도 전에
    // 쫓겨난다. 튕기는 것은 회원 토큰을 들고 갔는데도 거절당했을 때(=갱신까지 실패)뿐이다.
    if (accessToken) unauthorizedHandler?.();
  }
  if (response.status === 204) return undefined as T;

  const isJson = (response.headers.get('content-type') ?? '').includes('application/json');
  if (response.ok && !isJson) return undefined as T;
  if (!isJson) {
    // 5xx 의 HTML 본문(nginx 의 502 안내 쪽)을 "예상하지 못한 응답" 으로 부르지 않는다.
    // 그 이름이 화면에서 "아직 만들지 않은 기능" 으로 번역되는 것이 이 버그였다.
    if (serverSideFailure) {
      throw new ApiClientError(serverErrorMessage(response.status), SERVER_ERROR_CODE, response.status);
    }
    throw new ApiClientError(`예상하지 못한 서버 응답이에요. (HTTP ${response.status})`, 'INVALID_RESPONSE', response.status);
  }

  const envelope = (await response.json()) as ApiEnvelope<T>;
  if (!response.ok || envelope.error || envelope.data === null) {
    // — 「이 기능은 열쇠가 없다」는 5xx 는 서버가 죽은 것이 아니다.
    if ((envelope.error?.code ?? '').endsWith('_VENDOR_NOT_CONFIGURED')) setApiUnavailable(false);
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
