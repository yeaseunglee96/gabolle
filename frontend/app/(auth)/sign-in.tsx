import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { ScenicVideo } from '@/components/ScenicVideo';

// 왼쪽 판 사진 — 검은 판에 글자 두 줄뿐이라 허전했다(2026-09-21 실기, S15P21E201-1381). 부산 사진 위에 동백이와 세 줄.
const introPhoto = require('../../assets/home/web-hero.png');
// 그 위에 첫 화면(00a)과 같은 바다 전차 영상(가로) — 웹 첫인상이 폰과 같은 톤이 된다(S15P21E201-1398). 사진은 영상이
// 늦거나 못 뜨거나 동작 줄이기가 켜져 있을 때의 대체본으로 그 밑에 그대로 둔다.
const introVideo = require('../../assets/video/busan-tram-landscape.mp4');
import { useFocusEffect, useLocalSearchParams, useRouter, type Href } from 'expo-router';
import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { loginWithOAuth } from '@/auth/oauth';
import { navigateAfterOAuthComplete } from '@/auth/oauthNavigation';
import type { OAuthProvider } from '@/auth/authApi';
import { guestDestination, resolveDestination, savePendingReturnTo, signedInDestination } from '@/auth/pendingReturnTo';
import { enterApp } from '@/auth/enterApp';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { SocialProviderIcon } from '@/components/SocialProviderIcon';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
// 웹의 검은 포커스 외곽선을 끈다 — 대신 행의 붉은 선(inputFocused)이 포커스를 보여 준다(S15P21E201-1518).
import { webInputNoOutline } from '@/design/webGlobalStyles';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';
import { txf } from '@/i18n/format';

function errorMessage(cause: unknown, tx: (ko: string, en: string) => string, context: 'password' | 'social') {
  if (cause instanceof ApiClientError && cause.status === 429) return tx('요청이 너무 많아요. 잠시 후 다시 시도해 주세요.', 'Too many attempts. Please try again shortly.');
  if (cause instanceof ApiClientError && cause.code === 'EMAIL_NOT_VERIFIED') return tx('이메일 인증을 마친 뒤 로그인해 주세요.', 'Verify your email before signing in.');
  if (cause instanceof ApiClientError && cause.status === 401) {
    return context === 'password'
      ? tx('이메일 또는 비밀번호가 올바르지 않아요.', 'The email or password is incorrect.')
      : tx('소셜 로그인을 완료하지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not complete social sign-in. Please try again shortly.');
  }
  return cause instanceof ApiClientError ? cause.message : tx('로그인하지 못했어요.', 'Unable to sign in.');
}
export default function SignIn() {
  const router = useRouter(); const { returnTo, passwordReset, gated } = useLocalSearchParams<{ returnTo?: string; passwordReset?: string; gated?: string }>(); const { signIn, acceptTokens, user, ready } = useAuth();
  const { tx } = useI18n();
  const { kind } = useLayout();
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [show, setShow] = useState(false);
  const emailRef = useRef<TextInput>(null);
  const passwordRef = useRef<TextInput>(null);
  // 포커스 = 붉은 2px 선 — 웹의 검은 기본 외곽선을 끈 대신이다(회원가입 시안과 같게, S15P21E201-1518).
  const [focused, setFocused] = useState<'email' | 'password' | null>(null);
  const [busy, setBusy] = useState(false); const [provider, setProvider] = useState<OAuthProvider | null>(null); const [feedback, setFeedback] = useState<{ danger: boolean; text: string } | null>(passwordReset === 'success' ? { danger: false, text: tx('비밀번호가 변경됐어요. 새 비밀번호로 로그인해 주세요.', 'Your password was changed. Sign in with your new password.') } : null);
  const eligible = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) && password.length > 0;
  useEffect(() => { void savePendingReturnTo(returnTo); }, [returnTo]);

  // — 로그인한 사람에게 로그인 화면을 보여주지 않는다.
  const signedInHere = useRef(false);
  const leaving = useRef(false);
  // 이 화면이 한 번이라도 가려졌다가 다시 보이는 것인가.
  const cameBack = useRef(false);
  // 🔴 `user`·`ready` 를 useFocusEffect 콜백의 deps 에 그대로 두면 안 된다 — expo-router 의
  // useFocusEffect(실은 react-navigation 원본)는 내부 useEffect 가 `[effect, navigation]` 에
  // 걸려 있어서, 화면이 그대로 떠 있는 채로(포커스를 잃지 않고) `effect` 참조만 바뀌어도
  // cleanup 을 부르고 곧장 다시 부른다. submit() 이 로그인에 성공해 `user` 가 채워지는
  // 바로 그 렌더에서 이게 걸리면, cleanup 이 cameBack.current 를 미리 true 로 만들어
  // "여기서 방금 로그인했다" 가드(mine)를 무너뜨리고 홈으로 비키는 코드가 끼어든다 —
  // submit() 자신의 returnTo 이동과 경합해서 이긴 쪽이 남는다(S15P21E201-1541 재발,
  // 이번엔 이메일 로그인·네이티브에서). 그래서 `user`·`ready` 는 ref 로 최신값만 들고
  // 콜백 자체는 라우터가 바뀔 때만(사실상 거의 안 바뀐다) 다시 만든다.
  const userRef = useRef(user);
  const readyRef = useRef(ready);
  const returnToRef = useRef(returnTo);
  useEffect(() => { userRef.current = user; readyRef.current = ready; returnToRef.current = returnTo; });
  // 이 화면이 지금 보이고 있나 — 아래 「로그인 복구가 늦게 끝났을 때」 판정이 가려진 화면에서 돌지 않게.
  const screenFocused = useRef(false);
  // 🔴 이미 로그인한 사람은 returnTo 로, 없으면 홈으로 비킨다(S15P21E201-1594). 전에는 언제나 홈이었다.
  const leaveIfSignedIn = useCallback(() => {
    // 이 화면에서 로그인 절차를 시작했고 아직 떠난 적이 없으면 그대로 둔다
    // 그쪽은 submit·social 이 직접 목적지로 보낸다. 둘이 같이 움직이면 한 번 갈 길을 두 번 간다.
    const mine = signedInHere.current && !cameBack.current;
    if (screenFocused.current && readyRef.current && userRef.current && !mine && !leaving.current) {
      leaving.current = true;
      enterApp(router, signedInDestination(returnToRef.current) as Href);
    }
  }, [router]);
  useFocusEffect(
    useCallback(() => {
      screenFocused.current = true;
      leaveIfSignedIn();
      // 포커스를 잃으면 「다음엔 돌아온 것」으로 친다.
      return () => { screenFocused.current = false; cameBack.current = true; leaving.current = false; };
    }, [leaveIfSignedIn]),
  );
  // 🔴 로그인 복구가 화면보다 늦게 끝나도 비킨다(S15P21E201-1594). 웹에서 주소로 /sign-in 에 바로 오면 화면이 먼저 뜨고
  //    로그인 복구(쿠키 → 토큰)는 뒤에 끝난다. 위 판정은 화면이 보이는 순간 한 번뿐이라 그때는 아직 모르고 지나갔다.
  //    이것은 포커스 효과가 아니라 cleanup 이 cameBack 을 건드리지 않는다 — 위 1541 의 경합을 다시 만들지 않는다.
  useEffect(() => { leaveIfSignedIn(); }, [ready, user, leaveIfSignedIn]);
  async function submit() { if (!eligible || busy || provider) return; setBusy(true); setFeedback(null); signedInHere.current = true; try { await signIn(email, password); enterApp(router, (await resolveDestination(returnTo)) as Href); } catch (e) { setFeedback({ danger: true, text: errorMessage(e, tx, 'password') }); } finally { setBusy(false); } }
  async function social(next: OAuthProvider) {
    if (busy || provider) return;
    setProvider(next);
    setFeedback(null);
    signedInHere.current = true;
    try {
      // 웹에서는 loginWithOAuth가 현재 페이지를 제공자 화면으로 그대로 넘긴다
      // — 이 아래는 실행되지 않고, 완료 뒤 분기는
      // oauth/[provider]/callback.tsx가 같은 navigateAfterOAuthComplete로 이어받는다.
      const result = await loginWithOAuth(next, returnTo);
      await navigateAfterOAuthComplete({ result, provider: next, returnTo, router, acceptTokens });
    } catch (e) {
      setFeedback({ danger: true, text: errorMessage(e, tx, 'social') });
    } finally {
      setProvider(null);
    }
  }
  return <Screen scroll wide style={styles.screen}><View style={[styles.loginLayout, kind === 'tablet' && styles.loginLayoutWide]}>
      {kind === 'tablet' && <View style={styles.webIntro}>
        <Image source={introPhoto} resizeMode="cover" accessibilityLabel="" style={styles.webIntroPhoto} />
        <ScenicVideo source={introVideo} style={styles.webIntroPhoto} />
        <LinearGradient colors={['rgba(25,25,25,0.15)', 'rgba(25,25,25,0.55)', 'rgba(25,25,25,0.88)']} locations={[0, 0.55, 1]} style={StyleSheet.absoluteFill} />
        <View style={styles.webIntroBody}>
          <GabolleMascot state="open" still style={styles.webIntroMascot} />
          <Eyebrow>{tx('가볼래 계정', 'GABOLLE Account')}</Eyebrow>
          <Text variant="display" weight="bold" color={color.text.onAction} style={styles.webIntroTitle}>{tx('여행의 설렘은 그대로,\n일정은 안전하게', 'Keep the excitement,\nsave every plan.')}</Text>
          <Text variant="body" color={color.text.onDarkMuted}>{tx('저장한 부산 여행과 동행자 일정을 어디서든 이어보세요.', 'Continue your saved Busan trips and shared plans anywhere.')}</Text>
          <View style={styles.webIntroPoints}>
            {[
              tx('취향·예산·이동 조건으로 만드는 부산 일정', 'Busan plans built from your taste, budget and mobility'),
              tx('동행자와 함께 고치고, 어디서든 이어 보기', 'Edit with companions and pick up anywhere'),
              tx('메뉴판 읽기 · 현장 말하기 · 환율 — 여행 중 바로', 'Menu reading, phrases and exchange rates on the spot'),
            ].map((line) => (
              <View key={line} style={styles.webIntroPoint}><View style={styles.webIntroDot} /><Text variant="caption" color={color.text.onAction}>{line}</Text></View>
            ))}
          </View>
        </View>
      </View>}
    <View style={styles.panel}>
    <Pressable accessibilityRole="link" accessibilityLabel={tx('GABOLLE 홈으로 이동', 'Go to the GABOLLE home')} onPress={() => router.replace(kind === 'phone' ? '/home' : '/')} style={({ pressed }) => [styles.logoLink, pressed && styles.pressed]}>
      <Image source={require('../../assets/brand/gabolle-logo-hd.png')} resizeMode="contain" accessibilityIgnoresInvertColors style={styles.logo} />
    </Pressable>
    <Text variant="display" weight="bold" style={styles.title}>{tx('로그인', 'Sign in')}</Text>
    <Text color={color.text.body} style={styles.subtitle}>{tx('부산 여행을 시작해볼까요?', 'Ready to explore Busan?')}</Text>
    <View style={styles.form}>
      <View style={styles.field}><Text variant="caption" weight="bold">{tx('이메일', 'Email')}</Text><View style={[styles.inputRow, focused === 'email' && styles.inputFocused]}><TextInput testID="sign-in-email" onFocus={() => setFocused('email')} onBlur={() => setFocused(null)} accessibilityLabel={tx('이메일', 'Email')} autoCapitalize="none" autoComplete="email" keyboardType="email-address" textContentType="username" returnKeyType="next" onSubmitEditing={() => passwordRef.current?.focus()} submitBehavior="submit" ref={emailRef} value={email} onChangeText={setEmail} placeholder="name@example.com" placeholderTextColor={color.text.muted} style={styles.inputWithClear} />{email.length > 0 && <Pressable accessibilityRole="button" accessibilityLabel={tx('이메일 지우기', 'Clear email')} onPress={() => setEmail('')} tabIndex={-1} style={styles.clear}><Text variant="body" color={color.text.muted}>✕</Text></Pressable>}</View></View>
      <View style={styles.field}><Text variant="caption" weight="bold">{tx('비밀번호', 'Password')}</Text><View style={[styles.passwordRow, focused === 'password' && styles.inputFocused]}><TextInput testID="sign-in-password" onFocus={() => setFocused('password')} onBlur={() => setFocused(null)} accessibilityLabel={tx('비밀번호', 'Password')} autoCapitalize="none" autoComplete="current-password" textContentType="password" returnKeyType="go" ref={passwordRef} secureTextEntry={!show} value={password} onChangeText={setPassword} onSubmitEditing={() => void submit()} placeholder={tx('비밀번호', 'Password')} placeholderTextColor={color.text.muted} style={styles.passwordInput} />{password.length > 0 && <Pressable accessibilityRole="button" accessibilityLabel={tx('비밀번호 지우기', 'Clear password')} onPress={() => setPassword('')} tabIndex={-1} style={styles.clear}><Text variant="body" color={color.text.muted}>✕</Text></Pressable>}<Pressable accessibilityRole="button" accessibilityLabel={show ? tx('비밀번호 숨기기', 'Hide password') : tx('비밀번호 보이기', 'Show password')} accessibilityState={{ selected: show }} onPress={() => setShow(!show)} style={styles.eye}><Text variant="caption">{show ? tx('숨김', 'Hide') : tx('보기', 'Show')}</Text></Pressable></View></View>
      <Pressable accessibilityRole="link" style={styles.forgot} onPress={() => router.push('/forgot-password')}><Text variant="caption" color={color.action.primary}>{tx('비밀번호를 잊으셨나요?', 'Forgot your password?')}</Text></Pressable>
      {feedback && <Card><Text accessibilityRole="alert" variant="caption" color={feedback.danger ? color.state.danger : color.text.body}>{feedback.text}</Text></Card>}
      <Button testID="sign-in-submit" accessibilityRole="button" accessibilityState={{ disabled: !eligible || busy || !!provider, busy }} label={busy ? tx('로그인 중…', 'Signing in…') : tx('로그인', 'Sign in')} disabled={!eligible || busy || !!provider} onPress={() => void submit()} />
      {/* — 보호 화면에서 튕겨 온 것이면 returnTo 로 되돌아가지 않는다.
      */}
      <Pressable accessibilityRole="button" accessibilityHint={tx('로그인 없이 홈과 주요 기능을 둘러봅니다.', 'Browse the home screen and core features without signing in.')} style={styles.guest} onPress={() => enterApp(router, guestDestination(returnTo, gated) as Href)}><Text variant="body" weight="bold" color={color.action.primary}>{tx('비회원으로 둘러보기', 'Browse as guest')}</Text></Pressable>
    </View>
    {/* — 제공자마다 글자까지 있는 전체 폭 버튼 넷을 세로로 쌓았더니
        화면이 버튼으로 빽빽해 보인다는 신고가 있었다. 토스 등 참고 화면처럼 동그란
        아이콘만 가로로 늘어놓는 방식으로 바꾼다 — 글자는 화면에서 지우되
        accessibilityLabel 에는 그대로 남겨 스크린리더는 이전과 똑같이 "Google로
        계속하기" 처럼 읽는다.
    */}
    <View style={styles.divider}><View style={styles.line} /></View>
    <Text variant="caption" color={color.text.muted} style={styles.socialsLabel}>{tx('SNS 계정으로 로그인', 'Or continue with')}</Text>
    <View style={styles.socials}>{([
      // 외국인 관광객이 주 사용자라 계정 보유 가능성이 높은 순서로 둔다 — 구글·애플은
      // 외국에서도 흔한 글로벌 계정, 카카오·네이버는 한국 전용 계정이라 관광객은
      // 어차피 새로 만들어야 한다(둘 사이 순서는 무의미하니 그대로 카카오·네이버 순).
      { item: 'google', name: 'Google', backgroundColor: '#ffffff', borderColor: '#dadce0' },
      { item: 'apple', name: 'Apple', backgroundColor: '#000000', borderColor: '#000000' },
      { item: 'kakao', name: 'Kakao', backgroundColor: '#fee500', borderColor: '#fee500' },
      { item: 'naver', name: 'Naver', backgroundColor: '#03c75a', borderColor: '#03c75a' },
    ] as const).map(({ item, name, backgroundColor, borderColor }) => { const action = txf(tx, '%s로 계속하기', 'Continue with %s', name); return <Pressable key={item} accessibilityRole="button" accessibilityLabel={action} accessibilityState={{ disabled: busy || !!provider, busy: provider === item }} disabled={busy || !!provider} onPress={() => void social(item)} style={({ pressed }) => [styles.social, { backgroundColor, borderColor }, pressed && styles.pressed]}><SocialProviderIcon provider={item} /></Pressable>; })}</View>
    {provider && <ActivityIndicator accessibilityLabel={tx('소셜 로그인 처리 중', 'Processing social sign-in')} color={color.action.primary} style={styles.socialsSpinner} />}
    <Pressable accessibilityRole="link" style={[styles.signup, styles.signupTop]} onPress={() => router.push({ pathname: '/sign-up', params: returnTo ? { returnTo } : {} })}><Text variant="caption">{tx('처음이신가요? ', 'New here? ')}<Text variant="caption" weight="bold" color={color.action.secondary}>{tx('회원가입', 'Create an account')}</Text></Text></Pressable>
  </View></View></Screen>;
}
const styles = StyleSheet.create({ screen: { alignItems: 'center', justifyContent: 'center' }, loginLayout: { width: '100%', alignItems: 'center' }, loginLayoutWide: { flexDirection: 'row', alignItems: 'stretch', gap: spacing[8] }, // 🔴 zIndex: 0 — react-native-web 의 Image 는 그림을 z-index -1 로 그린다. 판이 쌓임 문맥이 아니면 그 그림이 판의 검은 바탕 «뒤»로 가서 안 보인다(2026-09-21 실측).
  webIntro: { flex: 1, minHeight: 680, justifyContent: 'flex-end', borderRadius: radius.lg, overflow: 'hidden', backgroundColor: color.brand.navy, zIndex: 0 }, webIntroPhoto: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, width: '100%', height: '100%' }, webIntroBody: { gap: spacing[4], padding: spacing[8] }, webIntroMascot: { width: 84, height: 84, marginBottom: spacing[2] }, webIntroPoints: { gap: spacing[2], marginTop: spacing[2] }, webIntroPoint: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, webIntroDot: { width: 6, height: 6, borderRadius: radius.full, backgroundColor: color.brand.ivory }, webIntroTitle: { fontSize: 38, lineHeight: 48 }, panel: { flex: 1, width: '100%', maxWidth: 420, justifyContent: 'center', paddingVertical: spacing[3] }, logoLink: { minWidth: 120, minHeight: 44, alignSelf: 'center', alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm }, logo: { width: 242, height: 44 }, title: { textAlign: 'center', marginTop: spacing[3] }, subtitle: { textAlign: 'center', marginTop: spacing[2], marginBottom: spacing[6] }, form: { gap: spacing[3] }, field: { gap: spacing[2] }, input: { minHeight: 52, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4] }, inputRow: { minHeight: 52, flexDirection: 'row', alignItems: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card }, inputWithClear: { flex: 1, minWidth: 0, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4], paddingVertical: spacing[3], ...webInputNoOutline }, passwordRow: { minHeight: 52, flexDirection: 'row', alignItems: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card }, passwordInput: { flex: 1, minWidth: 0, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4], paddingVertical: spacing[3], ...webInputNoOutline }, inputFocused: { borderColor: color.action.outline, borderWidth: 2 }, // ✕ 는 tabIndex={-1} — 키보드 탭이 ✕ 에 걸려 이메일 → 비밀번호로 바로 못 가던 것(S15P21E201-1518 피드백).
  clear: { minWidth: 36, minHeight: 44, alignItems: 'center', justifyContent: 'center' }, eye: { minWidth: 52, minHeight: 44, alignItems: 'center', justifyContent: 'center' }, forgot: { minHeight: 44, alignSelf: 'flex-end', justifyContent: 'center' }, guest: { minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.action.primary, backgroundColor: color.surface.card }, divider: { marginTop: spacing[6] }, line: { height: 1, backgroundColor: color.surface.field }, socialsLabel: { textAlign: 'center', marginTop: spacing[3], marginBottom: spacing[4] }, socials: { flexDirection: 'row', justifyContent: 'center', gap: spacing[4] }, social: { width: 56, height: 56, borderRadius: 999, borderWidth: 1, alignItems: 'center', justifyContent: 'center' }, socialsSpinner: { marginTop: spacing[3] }, pressed: { opacity: .75 }, signup: { minHeight: 44, alignItems: 'center', justifyContent: 'center' }, signupTop: { marginTop: spacing[6] } });
