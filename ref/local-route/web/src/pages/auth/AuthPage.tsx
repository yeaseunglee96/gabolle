import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { BrandLogo } from "../../components/BrandLogo";
import { getUiLanguage } from "../../i18n";
import { loginWithDemoProvider } from "../../api/client";
import { paths } from "../../routes/paths";
import { markWelcomeSeen } from "../../utils/visitor";

type AuthMode = "login" | "signup";
type OAuthProvider = "GOOGLE" | "NAVER" | "KAKAO";

const providers: Array<{ id: OAuthProvider; ko: string; en: string; className: string }> = [
  { id: "KAKAO", ko: "카카오로 계속하기", en: "Continue with Kakao", className: "kakao" },
  { id: "NAVER", ko: "네이버로 계속하기", en: "Continue with Naver", className: "naver" },
  { id: "GOOGLE", ko: "Google로 계속하기", en: "Continue with Google", className: "google" },
];

export function AuthPage({ mode }: { mode: AuthMode }) {
  const navigate = useNavigate();
  const isSignup = mode === "signup";
  const isEn = getUiLanguage() === "EN";
  const [agreed, setAgreed] = useState(false);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState<OAuthProvider | null>(null);

  const continueWith = async (provider: OAuthProvider) => {
    setError("");
    if (!agreed) {
      setError(isEn ? "Please agree to the terms and privacy policy." : "이용약관과 개인정보 처리방침에 동의해주세요.");
      return;
    }
    try {
      setSubmitting(provider);
      await loginWithDemoProvider(provider, isEn ? "EN" : "KO");
      markWelcomeSeen();
      navigate(paths.onboarding(), { replace: true });
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : (isEn ? "Could not connect to the server. Please try again." : "서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요."));
    } finally {
      setSubmitting(null);
    }
  };

  return (
    <main className="auth-page">
      <section className="auth-intro" aria-label={isEn ? "GABOLLE introduction" : "GABOLLE 소개"}>
        <Link to={paths.welcome()} className="auth-logo-link"><BrandLogo /></Link>
        <div>
          <span className="section-eyebrow">LOCAL TRAVEL, MADE FOR YOU</span>
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
            <span>{isSignup ? (isEn ? "GET STARTED" : "처음 오셨나요?") : (isEn ? "WELCOME BACK" : "다시 만나서 반가워요")}</span>
            <h2>{isSignup ? (isEn ? "Start GABOLLE" : "GABOLLE 시작하기") : (isEn ? "Continue your trip" : "여행을 이어가세요")}</h2>
            <p>{isEn ? "Use the account you already have. No new password is needed." : "이미 사용하는 계정으로 간편하게 시작하세요. 새 비밀번호를 만들 필요가 없어요."}</p>
          </header>

          <div className="oauth-provider-list" aria-label={isEn ? "Social sign in providers" : "소셜 로그인 제공자"}>
            {providers.map((provider) => <button key={provider.id} type="button" className={provider.className} disabled={submitting !== null} onClick={() => void continueWith(provider.id)}><i aria-hidden="true">{provider.id === "GOOGLE" ? "G" : provider.id === "NAVER" ? "N" : "K"}</i><span>{submitting === provider.id ? (isEn ? "Connecting…" : "연결 중…") : isEn ? provider.en : provider.ko}</span></button>)}
          </div>

          <label className="oauth-agreement"><input type="checkbox" checked={agreed} onChange={(event) => setAgreed(event.target.checked)} /><span>{isEn ? <>I agree to the <Link to={paths.terms()}>Terms</Link> and <Link to={paths.privacy()}>Privacy Policy</Link>.</> : <><Link to={paths.terms()}>이용약관</Link> 및 <Link to={paths.privacy()}>개인정보 처리방침</Link>에 동의합니다.</>}</span></label>
          {error && <p className="auth-error" role="alert">{error}</p>}

          <p className="auth-switch">
            {isSignup ? (isEn ? "Already started?" : "이미 시작하셨나요?") : (isEn ? "First time here?" : "처음 방문하셨나요?")}
            <Link to={isSignup ? paths.login() : paths.signup()}>{isSignup ? (isEn ? "Sign in" : "로그인") : (isEn ? "Get started" : "시작하기")}</Link>
          </p>

          <small className="auth-demo-notice">{isEn
            ? "Demo mode uses a provider-shaped test account. Production connects through the provider's secure sign-in page."
            : "데모에서는 제공자별 테스트 계정으로 연결됩니다. 실제 서비스에서는 각 제공자의 안전한 로그인 화면을 사용합니다."}</small>
        </div>
      </section>
    </main>
  );
}
