import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Platform } from 'react-native';
import { useRouter } from 'expo-router';
import { setUnauthorizedHandler } from '@/api/client';
import { getMe, login, logoutMobileSession, logoutWebSession, refreshWebSession, type AuthTokens, type AuthUser } from './authApi';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';

type AuthContextValue = { accessToken: string | null; user: AuthUser | null; ready: boolean; signIn: (email: string, password: string) => Promise<void>; acceptTokens: (tokens: AuthTokens) => Promise<void>; clearSession: () => void; signOut: () => Promise<void> };
const AuthContext = createContext<AuthContextValue | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const preferences = useOnboardingPreferences();
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [refreshToken, setRefreshToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [ready, setReady] = useState(Platform.OS !== 'web');
  const clearSession = () => { setAccessToken(null); setRefreshToken(null); setUser(null); };
  const applyUser = (currentUser: AuthUser) => {
    setUser(currentUser);
    const profileLanguage = currentUser.language?.toUpperCase() === 'EN' ? 'en' : currentUser.language?.toUpperCase() === 'KO' ? 'ko' : null;
    if (profileLanguage && profileLanguage !== preferences.language) preferences.setLanguage(profileLanguage);
  };
  useEffect(() => { setUnauthorizedHandler(() => { clearSession(); router.replace('/sign-in'); }); return () => setUnauthorizedHandler(null); }, [router]);
  useEffect(() => {
    if (Platform.OS !== 'web') return;
    let active = true;
    void refreshWebSession().then(async (tokens) => ({ tokens, currentUser: await getMe(tokens.accessToken) })).then(({ tokens, currentUser }) => { if (active) { setAccessToken(tokens.accessToken); applyUser(currentUser); } }).catch(() => {}).finally(() => { if (active) setReady(true); });
    return () => { active = false; };
  }, []);
  const value = useMemo<AuthContextValue>(() => ({ accessToken, user, ready, clearSession,
    signIn: async (email, password) => { const tokens = await login(email, password); const currentUser = await getMe(tokens.accessToken); setAccessToken(tokens.accessToken); setRefreshToken(tokens.refreshToken); applyUser(currentUser); },
    acceptTokens: async (tokens) => { const currentUser = await getMe(tokens.accessToken); setAccessToken(tokens.accessToken); setRefreshToken(tokens.refreshToken); applyUser(currentUser); },
    signOut: async () => { try { if (Platform.OS === 'web') await logoutWebSession(); else if (refreshToken) await logoutMobileSession(refreshToken); } finally { clearSession(); router.replace('/sign-in'); } },
  }), [accessToken, preferences, ready, refreshToken, router, user]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() { const value = useContext(AuthContext); if (!value) throw new Error('useAuth는 AuthProvider 안에서 사용해야 합니다.'); return value; }
