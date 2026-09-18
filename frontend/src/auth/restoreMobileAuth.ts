import { ApiClientError } from '@/api/client';
import type { AuthTokens } from './authApi';

type SessionStorage = {
  read: () => Promise<string | null>;
  write: (token: string) => Promise<void>;
  remove: () => Promise<void>;
};

/** 통신 실패는 세션 거부가 아니다. 서버의 401 응답만 저장된 자격을 폐기한다. */
export async function restoreMobileAuth(storage: SessionStorage, refresh: (token: string) => Promise<AuthTokens>): Promise<AuthTokens | null> {
  const stored = await storage.read();
  if (!stored) return null;
  let tokens: AuthTokens;
  try {
    tokens = await refresh(stored);
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 401) await storage.remove();
    throw error;
  }
  // 갱신 응답에는 사용자 정보도 있다. 추가 프로필 조회가 실패해서 회전된 토큰을 잃지 않는다.
  if (tokens.refreshToken) await storage.write(tokens.refreshToken);
  return tokens;
}
