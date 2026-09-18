import AsyncStorage from '@react-native-async-storage/async-storage';

// 로그인 전에 있던 자리로 돌아가기 위한 값을 잠깐 저장해 둔다.
//
// sign-in.tsx 는 원래 URL 의 returnTo 쿼리 파라미터만 믿고 돌아갔는데, 회원가입 뒤
// 이메일 인증처럼 앱을 벗어났다가 메일의 링크로 다시 들어오는 경로에서는 그 쿼리가
// 살아남지 않는다(그 링크는 백엔드가 만든 것이라 returnTo 를 모른다). 그러면 로그인
// 뒤 기본값인 /me 로 떨어져, 여행 조건 4단계를 다 채우고도 다시 처음부터 눌러야 했다.
// 그래서 URL 에 returnTo 가 보일 때마다 여기 저장해 두고, 없을 때는 이걸로 대신한다.
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

// sign-in.tsx와 웹 소셜 로그인 착지 화면(oauth/[provider]/callback.tsx, S15P21E201-830)이
// 로그인 뒤 돌아갈 곳을 같은 규칙으로 정하도록 한 곳에 둔다 — 두 곳에 각자 적으면
// 한쪽만 고쳐지는 날이 온다.
export async function resolveDestination(returnTo?: string | null): Promise<string> {
  if (isSafeReturnPath(returnTo)) return returnTo;
  const pending = await consumePendingReturnTo();
  return pending ?? '/home';
}
