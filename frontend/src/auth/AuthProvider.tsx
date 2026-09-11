import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import { useRouter } from 'expo-router';
import { getApiLanguage, setRefreshHandler, setUnauthorizedHandler } from '@/api/client';
import { deleteMe, getMe, login, logoutMobileSession, logoutWebSession, refreshMobileSession, refreshWebSession, updateMe, type AuthTokens, type AuthUser, type SignupLanguage } from './authApi';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { clearSavedTrips } from '@/trip/tripLibrary';

const tx = (ko: string, en: string) => (getApiLanguage() === 'en' ? en : ko);

const REFRESH_TOKEN_KEY = 'gabolle.refresh-token';
type AuthContextValue = { accessToken: string | null; user: AuthUser | null; ready: boolean; signIn: (email: string, password: string) => Promise<void>; acceptTokens: (tokens: AuthTokens) => Promise<void>; updateProfile: (input: { displayName: string; language: SignupLanguage }) => Promise<void>; deleteAccount: (confirmation: string) => Promise<void>; clearSession: () => void; signOut: () => Promise<void> };
const AuthContext = createContext<AuthContextValue | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const preferences = useOnboardingPreferences();
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [refreshToken, setRefreshToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [ready, setReady] = useState(false);
  const clearSession = () => {
    setAccessToken(null);
    setRefreshToken(null);
    setUser(null);
    if (Platform.OS !== 'web') void SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
  };
  const applyUser = (currentUser: AuthUser) => {
    setUser(currentUser);
    const profileLanguage = currentUser.language?.toUpperCase() === 'EN' ? 'en' : currentUser.language?.toUpperCase() === 'KO' ? 'ko' : null;
    if (profileLanguage && profileLanguage !== preferences.language) preferences.setLanguage(profileLanguage);
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
        if (!refreshToken) return null;
        const tokens = await refreshMobileSession(refreshToken);
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
  }, [router, refreshToken]);
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
        const storedRefreshToken = await SecureStore.getItemAsync(REFRESH_TOKEN_KEY);
        if (!storedRefreshToken) return;
        const tokens = await refreshMobileSession(storedRefreshToken);
        const currentUser = await getMe(tokens.accessToken);
        if (active) {
          setAccessToken(tokens.accessToken);
          setRefreshToken(tokens.refreshToken);
          applyUser(currentUser);
          if (tokens.refreshToken) await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, tokens.refreshToken);
        }
      } catch {
        if (Platform.OS !== 'web') await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
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
    // 로그인했을 때 앞사람의 여행 목록·언어·이동 성향이 그대로 보인다(S15P21E201-740).
    signOut: async () => { try { if (Platform.OS === 'web') await logoutWebSession(); else if (refreshToken) await logoutMobileSession(refreshToken); } finally { await clearSavedTrips(); preferences.reset(); clearSession(); router.replace('/sign-in'); } },
  }), [accessToken, preferences, ready, refreshToken, router, user]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() { const value = useContext(AuthContext); if (!value) throw new Error(tx('useAuth는 AuthProvider 안에서 사용해야 합니다.', 'useAuth must be used inside AuthProvider.')); return value; }
