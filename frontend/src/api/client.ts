import { Platform } from 'react-native';

export const API_BASE_URL = (process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const configuredTimeout = Number(process.env.EXPO_PUBLIC_API_TIMEOUT_MS ?? 12000);
const API_TIMEOUT_MS = Number.isFinite(configuredTimeout) && configuredTimeout > 0 ? configuredTimeout : 12000;
let apiLanguage: 'ko' | 'en' = 'ko';
export function setApiLanguage(language: 'ko' | 'en') { apiLanguage = language; }

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
  ) {
    super(message);
    this.name = 'ApiClientError';
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & { body?: unknown; accessToken?: string | null; skipUnauthorizedHandling?: boolean };
let unauthorizedHandler: (() => void) | null = null;
export function setUnauthorizedHandler(handler: (() => void) | null) { unauthorizedHandler = handler; }

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
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
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        'X-Client-Platform': Platform.OS === 'web' ? 'WEB' : 'MOBILE',
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    if (timedOut) throw new ApiClientError('서버 응답이 늦어 요청을 마쳤어요. 잠시 후 다시 시도해 주세요.', 'REQUEST_TIMEOUT', 0);
    throw new ApiClientError('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.', 'NETWORK_ERROR', 0);
  } finally {
    clearTimeout(timeout);
    requestOptions.signal?.removeEventListener('abort', abortFromCaller);
  }

  if (response.status === 401 && !skipUnauthorizedHandling) unauthorizedHandler?.();
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
    );
  }
  return envelope.data;
}
