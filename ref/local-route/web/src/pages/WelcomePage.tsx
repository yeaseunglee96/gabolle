import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { BrandLogo } from "../components/BrandLogo";
import { getUiLanguage, setUiLanguage, subscribeUiLanguage } from "../i18n";
import { markWelcomeSeen } from "../utils/visitor";
import { paths } from "../routes/paths";
import { getStoredAccount, logoutAccount, type AccountUser } from "../api/client";

const FEATURES = [
  {
    titleKo: "맞춤 일정 생성",
    titleEn: "Custom Itinerary",
    bodyKo: "예산 · 이동시간 · 영업시간",
    bodyEn: "Budget · Travel Time · Hours",
  },
  {
    titleKo: "로컬 추천 코스",
    titleEn: "Local Recommendations",
    bodyKo: "관광 코스 · 로컬 코스",
    bodyEn: "Essential Sights · Hidden Local",
  },
  {
    titleKo: "외국인 여행 도구",
    titleEn: "Traveler Tools",
    bodyKo: "메뉴 번역 · 음성 통역 · 사투리",
    bodyEn: "Menu Translation · Voice · Dialect",
  },
  {
    titleKo: "실시간 내비게이션",
    titleEn: "Live Navigation",
    bodyKo: "GPS 안내 · 택시 카드",
    bodyEn: "GPS Guide · Taxi Card",
  },
  {
    titleKo: "동행 & 기록",
    titleEn: "Share & Record",
    bodyKo: "일정 공유 · 공동 편집",
    bodyEn: "Share · Co-edit",
  },
  {
    titleKo: "여행 준비 지원",
    titleEn: "Trip Prep",
    bodyKo: "날씨 · 경비 · 예약",
    bodyEn: "Weather · Costs · Booking",
  },
];

/**
 * /welcome — 웹 첫 방문 화면
 * 서비스 목적·핵심 기능을 소개하고, 다른 무엇보다 먼저 화면 언어를 고르게 한다.
 * 재방문 사용자(진행 중인 일정이 있음)는 AppRouter 의 진입 분기에서 이 화면을 건너뛰고
 * 자신의 일정으로 바로 들어간다. 앱(Expo)의 위치·알림 권한 요청 화면(/onboarding)과는 별개다.
 */
export function WelcomePage() {
  const navigate = useNavigate();
  const [lang, setLang] = useState<"KO" | "EN">(getUiLanguage());
  const [account, setAccount] = useState<AccountUser | null>(getStoredAccount());
  const isEn = lang === "EN";

  useEffect(() => subscribeUiLanguage(setLang), []);

  const chooseLanguage = (next: "KO" | "EN") => setUiLanguage(next);

  const start = () => {
    markWelcomeSeen();
    navigate(paths.home(), { replace: true });
  };

  return (
    <div className="landing onboarding-view">
      <header className="landing-header">
        <BrandLogo />
        <nav className="welcome-auth-nav" aria-label={isEn ? "Account" : "계정"}>
          {account ? (
            <>
              <span>{account.name}</span>
              <button type="button" onClick={async () => { await logoutAccount(); setAccount(null); }}>
                {isEn ? "Sign out" : "로그아웃"}
              </button>
            </>
          ) : (
            <>
              <button type="button" onClick={() => navigate(paths.login())}>{isEn ? "Sign in" : "로그인"}</button>
              <button type="button" className="welcome-signup-btn" onClick={() => navigate(paths.signup())}>{isEn ? "Create account" : "회원가입"}</button>
            </>
          )}
        </nav>
      </header>

      <section className="onboarding-lang-picker" aria-label={isEn ? "Choose your language" : "언어를 선택하세요"}>
        <span className="section-eyebrow">{isEn ? "Choose your language" : "언어를 선택하세요"}</span>
        <div className="onboarding-lang-options">
          <button
            type="button"
            className={`tag-chip onboarding-lang-chip ${lang === "KO" ? "selected" : ""}`}
            aria-pressed={lang === "KO"}
            onClick={() => chooseLanguage("KO")}
          >
            한국어
          </button>
          <button
            type="button"
            className={`tag-chip onboarding-lang-chip ${lang === "EN" ? "selected" : ""}`}
            aria-pressed={lang === "EN"}
            onClick={() => chooseLanguage("EN")}
          >
            English
          </button>
        </div>
      </section>

      <section className="landing-hero onboarding-hero">
        <span className="section-eyebrow">{isEn ? "LOCAL-RECOMMENDED TRAVEL" : "현지인 추천 기반 여행"}</span>
        <h1>{isEn ? "Enjoy Busan, recommended by locals" : "현지인이 추천해주는 부산을 즐기세요"}</h1>
        <p>{isEn
          ? "From planning to communicating on the ground — all in one place."
          : "일정 계산부터 현지 소통까지, 한 번에 도와드려요."}</p>
      </section>

      <section className="onboarding-features">
        {FEATURES.map((feature) => (
          <article key={feature.titleKo} className="onboarding-feature-card">
            <strong>{isEn ? feature.titleEn : feature.titleKo}</strong>
            <p>{isEn ? feature.bodyEn : feature.bodyKo}</p>
          </article>
        ))}
      </section>

      <div className="onboarding-cta">
        <button type="button" className="primary-btn" onClick={start}>
          {isEn ? "Get Started" : "시작하기"}
        </button>
        <small>{isEn ? "No sign-up required · Your trip is saved on this device" : "회원가입 없이 이용할 수 있으며, 여행은 현재 기기에 저장됩니다."}</small>
      </div>
    </div>
  );
}
