import AsyncStorage from '@react-native-async-storage/async-storage';

import type { OAuthProvider } from './authApi';

// S15P21E201-830 — 웹에서는 소셜 로그인 팝업(window.open)이 모바일 브라우저의 팝업
// 차단에 막힌다. 그래서 웹에서는 새 창 대신 현재 페이지를 그대로 제공자 인증 화면으로
// 넘긴다(oauth.ts). code_verifier·state·nonce는 원래 창의 메모리에만 있었는데
// 페이지를 통째로 넘기면 사라지므로, 넘어가기 전에 여기 잠깐 저장하고 돌아와서
// (oauth/[provider]/callback.tsx) 한 번 꺼내 쓴 뒤 지운다 — pendingReturnTo.ts와 같은 모양이다.
const STORAGE_KEY = '@gabolle/pending-oauth';

export type PendingOAuth = {
  provider: OAuthProvider;
  redirectUri: string;
  codeVerifier: string;
  state: string;
  nonce: string;
  returnTo?: string | null;
};

export async function savePendingOAuth(value: PendingOAuth) {
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(value));
}

// 한 번 쓰면 지운다 — 이 값은 진행 중인 시도 하나에만 쓰인다. 남겨 두면 다음 시도가
// state 불일치로 실패했을 때도 옛 값을 계속 붙잡아 헷갈리는 오류를 낸다.
export async function consumePendingOAuth(): Promise<PendingOAuth | null> {
  const raw = await AsyncStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  await AsyncStorage.removeItem(STORAGE_KEY);
  try {
    const parsed = JSON.parse(raw) as PendingOAuth;
    if (!parsed?.provider || !parsed.codeVerifier || !parsed.state) return null;
    return parsed;
  } catch {
    return null;
  }
}
