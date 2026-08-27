import { FormEvent, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { BrandLogo } from "../../components/BrandLogo";
import { getUiLanguage } from "../../i18n";
import { getStoredAccount, loginAccount, registerAccount } from "../../api/client";
import { paths } from "../../routes/paths";
import { markWelcomeSeen } from "../../utils/visitor";

type AuthMode = "login" | "signup";

export function AuthPage({ mode }: { mode: AuthMode }) {
  const navigate = useNavigate();
  const isSignup = mode === "signup";
  const isEn = getUiLanguage() === "EN";
  const savedAccount = useMemo(getStoredAccount, []);
  const [name, setName] = useState(savedAccount?.name ?? "");
  const [email, setEmail] = useState(savedAccount?.email ?? "");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const passwordRules = {
    length: password.length >= 8 && password.length <= 64,
    letter: /[A-Za-z]/.test(password),
    number: /\d/.test(password),
    special: /[^A-Za-z0-9]/.test(password),
  };
  const passwordValid = Object.values(passwordRules).every(Boolean);
  const passwordsMatch = passwordConfirm.length > 0 && password === passwordConfirm;

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");

    if (!/^\S+@\S+\.\S+$/.test(email)) {
      setError(isEn ? "Please enter a valid email address." : "이메일 주소를 확인해주세요.");
      return;
    }
    if (!passwordValid) {
      setError(isEn ? "Please meet all password requirements." : "비밀번호 조건을 모두 충족해주세요.");
      return;
    }
    if (isSignup && password !== passwordConfirm) {
      setError(isEn ? "Passwords do not match." : "비밀번호가 일치하지 않습니다.");
      return;
    }
    if (isSignup && !agreed) {
      setError(isEn ? "Please agree to the terms and privacy policy." : "이용약관과 개인정보 처리방침에 동의해주세요.");
      return;
    }
    try {
      setSubmitting(true);
      if (isSignup) await registerAccount({ name: name.trim(), email, password, locale: isEn ? "EN" : "KO" });
      else await loginAccount({ email, password });
      markWelcomeSeen();
      navigate(paths.home(), { replace: true });
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : (isEn ? "Could not connect to the server. Please try again." : "서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요."));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="auth-page">
      <section className="auth-intro" aria-label={isEn ? "LOCAL ROUTE introduction" : "LOCAL ROUTE 소개"}>
        <Link to={paths.welcome()} className="auth-logo-link"><BrandLogo /></Link>
        <div>
          <span className="section-eyebrow">LOCAL-RECOMMENDED TRAVEL</span>
          <h1>{isEn ? "Your trip continues on every device" : "여행의 계획과 기록을 안전하게 이어가세요"}</h1>
          <p>{isEn
            ? "Save itineraries, plan with companions, and revisit your travel memories wherever you sign in."
            : "만든 일정과 동행자 공동 편집, 여행 기록을 다른 기기에서도 계속 이용할 수 있어요."}</p>
        </div>
        <ul>
          <li><span>01</span>{isEn ? "Keep every itinerary together" : "여행 일정을 한곳에 보관"}</li>
          <li><span>02</span>{isEn ? "Plan together with companions" : "동행자와 함께 일정 편집"}</li>
          <li><span>03</span>{isEn ? "Build a personal memory map" : "나만의 추억 지도 완성"}</li>
        </ul>
      </section>

      <section className="auth-panel">
        <div className="auth-card">
          <header>
            <span>{isSignup ? (isEn ? "CREATE ACCOUNT" : "회원가입") : (isEn ? "WELCOME BACK" : "다시 만나서 반가워요")}</span>
            <h2>{isSignup ? (isEn ? "Start your LOCAL ROUTE" : "LOCAL ROUTE 시작하기") : (isEn ? "Sign in" : "로그인")}</h2>
            <p>{isSignup
              ? (isEn ? "Create an account to keep your trips and memories." : "계정을 만들고 여행 일정과 기록을 이어가세요.")
              : (isEn ? "Continue planning your next trip." : "저장한 여행과 다음 일정을 이어서 확인하세요.")}</p>
          </header>

          <form className="auth-form" onSubmit={submit} noValidate>
            {isSignup && <label>
              <span>{isEn ? "Name" : "이름"}</span>
              <input value={name} onChange={(event) => setName(event.target.value)} autoComplete="name" placeholder={isEn ? "Your name" : "이름을 입력해주세요"} required />
            </label>}
            <label>
              <span>{isEn ? "Email" : "이메일"}</span>
              <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" placeholder="name@example.com" required />
            </label>
            <label>
              <span>{isEn ? "Password" : "비밀번호"}</span>
              <div className="password-field">
                <input type={showPassword ? "text" : "password"} value={password} onChange={(event) => setPassword(event.target.value)} autoComplete={isSignup ? "new-password" : "current-password"} placeholder={isEn ? "8–64 characters" : "8~64자로 입력해주세요"} aria-describedby={isSignup ? "password-rules" : undefined} required />
                <button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? (isEn ? "Hide password" : "비밀번호 숨기기") : (isEn ? "Show password" : "비밀번호 보기")}>
                  {showPassword ? (isEn ? "Hide" : "숨기기") : (isEn ? "Show" : "보기")}
                </button>
              </div>
            </label>
            {isSignup && <ul className="password-rules" id="password-rules" aria-label={isEn ? "Password requirements" : "비밀번호 조건"}>
              <li className={passwordRules.length ? "valid" : ""}>{isEn ? "8–64 characters" : "8~64자"}</li>
              <li className={passwordRules.letter ? "valid" : ""}>{isEn ? "English letter" : "영문 포함"}</li>
              <li className={passwordRules.number ? "valid" : ""}>{isEn ? "Number" : "숫자 포함"}</li>
              <li className={passwordRules.special ? "valid" : ""}>{isEn ? "Special character" : "특수문자 포함"}</li>
            </ul>}
            {isSignup && <label>
              <span>{isEn ? "Confirm password" : "비밀번호 확인"}</span>
              <input type={showPassword ? "text" : "password"} value={passwordConfirm} onChange={(event) => setPasswordConfirm(event.target.value)} autoComplete="new-password" placeholder={isEn ? "Enter it once more" : "한 번 더 입력해주세요"} aria-invalid={passwordConfirm.length > 0 && !passwordsMatch} required />
              {passwordConfirm.length > 0 && <small className={`password-match ${passwordsMatch ? "valid" : ""}`}>{passwordsMatch ? (isEn ? "Passwords match." : "비밀번호가 일치합니다.") : (isEn ? "Passwords do not match." : "비밀번호가 일치하지 않습니다.")}</small>}
            </label>}

            {isSignup && <label className="auth-agreement">
              <input type="checkbox" checked={agreed} onChange={(event) => setAgreed(event.target.checked)} />
              <span>{isEn ? <>I agree to the <Link to={paths.terms()}>Terms</Link> and <Link to={paths.privacy()}>Privacy Policy</Link>.</> : <><Link to={paths.terms()}>이용약관</Link> 및 <Link to={paths.privacy()}>개인정보 처리방침</Link>에 동의합니다.</>}</span>
            </label>}

            {error && <p className="auth-error" role="alert">{error}</p>}
            <button type="submit" className="primary-btn auth-submit" disabled={submitting}>
              {submitting ? (isEn ? "Please wait…" : "처리 중…") : isSignup ? (isEn ? "Create account" : "회원가입") : (isEn ? "Sign in" : "로그인")}
            </button>
          </form>

          <p className="auth-switch">
            {isSignup ? (isEn ? "Already have an account?" : "이미 계정이 있나요?") : (isEn ? "New to LOCAL ROUTE?" : "아직 계정이 없나요?")}
            <Link to={isSignup ? paths.login() : paths.signup()}>{isSignup ? (isEn ? "Sign in" : "로그인") : (isEn ? "Create account" : "회원가입")}</Link>
          </p>

          <div className="auth-guest">
            <span>{isEn ? "No account needed · saved on this device" : "가입 없이 이용 · 이 기기에만 저장"}</span>
            <Link to={paths.home()} onClick={markWelcomeSeen}>{isEn ? "Continue as guest" : "비회원으로 계속하기"}</Link>
          </div>

          <small className="auth-demo-notice">{isEn
            ? "Demo: email verification is skipped. Your account and trips are securely stored on the server."
            : "데모 안내: 이메일 인증은 생략되며, 계정과 여행 기록은 서버 DB에 안전하게 저장됩니다."}</small>
        </div>
      </section>
    </main>
  );
}
