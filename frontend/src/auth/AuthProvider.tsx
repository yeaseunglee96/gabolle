import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import { useRouter } from 'expo-router';
import { getApiLanguage, setRefreshHandler, setUnauthorizedHandler } from '@/api/client';
import { deleteMe, getMe, login, logoutMobileSession, logoutWebSession, refreshMobileSession, refreshWebSession, updateMe, type AuthTokens, type AuthUser, type SignupLanguage } from './authApi';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { clearSavedTrips } from '@/trip/tripLibrary';
import { restoreMobileAuth } from './restoreMobileAuth';

const tx = (ko: string, en: string) => (getApiLanguage() === 'en' ? en : ko);

const REFRESH_TOKEN_KEY = 'gabolle.refresh-token';
type AuthContextValue = { accessToken: string | null; user: AuthUser | null; ready: boolean; signIn: (email: string, password: string) => Promise<void>; acceptTokens: (tokens: AuthTokens) => Promise<void>; updateProfile: (input: { displayName?: string; language?: SignupLanguage; avatarUrl?: string | null }) => Promise<void>; deleteAccount: (confirmation: string) => Promise<void>; clearSession: () => void; signOut: () => Promise<void> };
const AuthContext = createContext<AuthContextValue | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const preferences = useOnboardingPreferences();
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [refreshToken, setRefreshTokenState] = useState<string | null>(null);
  // — 갱신표를 ref 로도 들고 있는다.
  const refreshTokenRef = useRef<string | null>(null);
  const setRefreshToken = (value: string | null) => { refreshTokenRef.current = value; setRefreshTokenState(value); };
  const [user, setUser] = useState<AuthUser | null>(null);
  const [ready, setReady] = useState(false);
  const clearSession = () => {
    setAccessToken(null);
    setRefreshToken(null);
    setUser(null);
    if (Platform.OS !== 'web') void SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
  };
  // — 여기서 계정 언어로 화면 언어를 되돌리지 않는다.
  const applyUser = (currentUser: AuthUser) => {
    setUser(currentUser);
  };
  useEffect(() => {
    setUnauthorizedHandler(() => { clearSession(); router.replace('/sign-in'); });
    setRefreshHandler(async () => {
      try {
        if (Platform.OS === 'web') {
          const tokens = await refreshWebSession();
          setAccessToken(tokens.accessToken);
          applyUser(tokens.user);
          return tokens.accessToken;
        }
        // 가둔 값이 아니라 지금 값을 본다. 앱을 다시 켠 직후처럼 ref 가 아직 비어 있을
        // 수 있으니, 그때는 저장소를 한 번 읽어 본다 — 있는 표를 없다고 답하지 않기 위해서다.
        // (여기는 web 이 위에서 이미 돌아간 뒤라 기기 저장소만 본다)
        const current = refreshTokenRef.current ?? await SecureStore.getItemAsync(REFRESH_TOKEN_KEY);
        if (!current) return null;
        const tokens = await refreshMobileSession(current);
        setAccessToken(tokens.accessToken);
        setRefreshToken(tokens.refreshToken);
        applyUser(tokens.user);
        if (tokens.refreshToken) await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, tokens.refreshToken);
        return tokens.accessToken;
      } catch {
        return null;
      }
    });
    return () => { setUnauthorizedHandler(null); setRefreshHandler(null); };
    // refreshToken 을 의존성에 두지 않는다 — 값이 바뀔 때마다 등록을 풀었다 다시 거는
    // 사이에 틈이 생기고, 1118 이 그 틈에서 났다. 지금 값은 ref 가 알려 준다.
  }, [router]);
  useEffect(() => {
    let active = true;
    const restore = async () => {
      try {
        if (Platform.OS === 'web') {
          const tokens = await refreshWebSession();
          const currentUser = await getMe(tokens.accessToken);
          if (active) { setAccessToken(tokens.accessToken); applyUser(currentUser); }
          return;
        }
        const tokens = await restoreMobileAuth({
          read: () => SecureStore.getItemAsync(REFRESH_TOKEN_KEY),
          write: (token) => SecureStore.setItemAsync(REFRESH_TOKEN_KEY, token),
          remove: () => SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY),
        }, refreshMobileSession);
        if (!tokens) return;
        if (active) {
          setAccessToken(tokens.accessToken);
          setRefreshToken(tokens.refreshToken);
          applyUser(tokens.user);
        }
      } catch {
        // 일시적인 통신·저장소 오류로 로그인 정보를 지우지 않는다.
        // 명시적인 세션 거부(401)는 restoreMobileAuth에서 처리한다.
      } finally {
        if (active) setReady(true);
      }
    };
    void restore();
    return () => { active = false; };
  }, []);
  const value = useMemo<AuthContextValue>(() => ({ accessToken, user, ready, clearSession,
    signIn: async (email, password) => { const tokens = await login(email, password); const currentUser = await getMe(tokens.accessToken); setAccessToken(tokens.accessToken); setRefreshToken(tokens.refreshToken); applyUser(currentUser); if (Platform.OS !== 'web' && tokens.refreshToken) await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, tokens.refreshToken); },
    acceptTokens: async (tokens) => { const currentUser = await getMe(tokens.accessToken); setAccessToken(tokens.accessToken); setRefreshToken(tokens.refreshToken); applyUser(currentUser); if (Platform.OS !== 'web' && tokens.refreshToken) await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, tokens.refreshToken); },
    updateProfile: async (input) => { if (!accessToken) throw new Error(tx('로그인이 필요합니다.', 'Please sign in.')); const currentUser = await updateMe(accessToken, input); applyUser(currentUser); },
    deleteAccount: async (confirmation) => { if (!accessToken) throw new Error(tx('로그인이 필요합니다.', 'Please sign in.')); await deleteMe(accessToken, confirmation); await clearSavedTrips(); preferences.reset(); clearSession(); router.replace('/'); },
    // 로그아웃해도 이 기기에 남는 것들을 정리한다 — 안 그러면 같은 기기에서 다음 사람이
    // 로그인했을 때 앞사람의 여행 목록·언어·이동 성향이 그대로 보인다.
    signOut: async () => { try { if (Platform.OS === 'web') await logoutWebSession(); else if (refreshToken) await logoutMobileSession(refreshToken); } finally { await clearSavedTrips(); preferences.reset(); clearSession(); router.replace('/sign-in'); } },
  }), [accessToken, preferences, ready, refreshToken, router, user]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() { const value = useContext(AuthContext); if (!value) throw new Error(tx('useAuth는 AuthProvider 안에서 사용해야 합니다.', 'useAuth must be used inside AuthProvider.')); return value; }
