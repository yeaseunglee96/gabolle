import AsyncStorage from '@react-native-async-storage/async-storage';

import type { OAuthProvider } from './authApi';

// S15P21E201-830 — 웹에서는 소셜 로그인 팝업(window.open)이 모바일 브라우저의 팝업
// 차단에 막힌다. 그래서 웹에서는 새 창 대신 현재 페이지를 그대로 제공자 인증 화면으로
// 넘긴다(oauth.ts). code_verifier·state·nonce는 원래 창의 메모리에만 있었는데
// 페이지를 통째로 넘기면 사라지므로, 넘어가기 전에 여기 잠깐 저장하고 돌아와서
// (oauth/[provider]/callback.tsx) 한 번 꺼내 쓴 뒤 지운다 — pendingReturnTo.ts와 같은 모양이다.
const STORAGE_KEY = '@gabolle/pending-oauth';

// S15P21E201-832 — 같은 왕복(제공자 인증 → 착지 화면)을 로그인과 "이미 로그인한 계정에
// 소셜 연결하기" 둘 다에 쓴다. 착지 화면이 completeOAuth(로그인)를 부를지 linkOAuthAccount
// (연결)를 부를지는 이 칸으로 가른다 — 없으면 로그인으로 본다(기존 저장값과 호환).
export type PendingOAuthIntent = 'login' | 'link';

export type PendingOAuth = {
  intent: PendingOAuthIntent;
  provider: OAuthProvider;
  redirectUri: string;
  codeVerifier: string;
  state: string;
  nonce: string;
  // login: 완료 뒤 돌아갈 곳(resolveDestination이 읽는다).
  // link: 완료 뒤 돌아갈 설정 화면 경로 — 지금은 /me 하나뿐이라 이 칸을 그대로 쓴다.
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
    return { ...parsed, intent: parsed.intent ?? 'login' };
  } catch {
    return null;
  }
}
