import AsyncStorage from '@react-native-async-storage/async-storage';

// 로그인 전에 있던 자리로 돌아가기 위한 값을 잠깐 저장해 둔다.
const STORAGE_KEY = '@gabolle/pending-return-to';

export function isSafeReturnPath(value?: string | null): value is string {
  return !!value && value.startsWith('/') && !value.startsWith('//') && !value.includes('://') && !value.startsWith('/sign-in') && !value.startsWith('/sign-up');
}

export async function savePendingReturnTo(value?: string | null) {
  if (isSafeReturnPath(value)) await AsyncStorage.setItem(STORAGE_KEY, value);
}

// 한 번 쓰면 지운다 — 다음 로그인까지 엉뚱한 곳으로 계속 보내지 않기 위해서다.
export async function consumePendingReturnTo(): Promise<string | null> {
  const value = await AsyncStorage.getItem(STORAGE_KEY);
  if (value) await AsyncStorage.removeItem(STORAGE_KEY);
  return isSafeReturnPath(value) ? value : null;
}

// sign-in.tsx와 웹 소셜 로그인 착지 화면(oauth/[provider]/callback.tsx,이
// 로그인 뒤 돌아갈 곳을 같은 규칙으로 정하도록 한 곳에 둔다 — 두 곳에 각자 적으면
// 한쪽만 고쳐지는 날이 온다.
export async function resolveDestination(returnTo?: string | null): Promise<string> {
  if (isSafeReturnPath(returnTo)) return returnTo;
  const pending = await consumePendingReturnTo();
  return pending ?? '/home';
}

/** 「비회원으로 둘러보기」를 눌렀을 때 갈 곳 — S15P21E201-1116. */
export function guestDestination(returnTo?: string | null, gated?: string | null): string {
  if (gated === '1') return '/home';
  return isSafeReturnPath(returnTo) ? returnTo : '/home';
}