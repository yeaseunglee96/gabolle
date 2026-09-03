import { Platform } from 'react-native';

export const API_BASE_URL = (process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '');

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

export async function apiRequest<T>(path: string, body: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'POST',
      credentials: Platform.OS === 'web' ? 'include' : undefined,
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-Client-Platform': Platform.OS === 'web' ? 'WEB' : 'MOBILE',
      },
      body: JSON.stringify(body),
    });
  } catch {
    throw new ApiClientError('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.', 'NETWORK_ERROR', 0);
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
    );
  }
  return envelope.data;
}
