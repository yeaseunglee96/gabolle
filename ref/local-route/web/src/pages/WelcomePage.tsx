import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { BrandLogo } from "../components/BrandLogo";
import { getUiLanguage, setUiLanguage, subscribeUiLanguage } from "../i18n";
import { markWelcomeSeen } from "../utils/visitor";
import { paths } from "../routes/paths";
import { getStoredAccount, logoutAccount, type AccountUser } from "../api/client";

const FEATURES = [
  {
    titleKo: "나에게 맞는 부산",
    titleEn: "Busan, made for you",
    bodyKo: "취향 · 제약 · 여행 맥락을 함께 반영",
    bodyEn: "Taste · Needs · Travel context",
  },
  {
    titleKo: "실행 가능한 일정",
    titleEn: "A trip you can follow",
    bodyKo: "영업시간 · 예산 · 이동 동선을 한 번에",
    bodyEn: "Hours · Budget · Routes",
  },
  {
    titleKo: "지금 갈 곳",
    titleEn: "Where to go now",
    bodyKo: "현재 위치 · 시간 · 날씨에 맞춰 추천",
    bodyEn: "Location · Time · Weather",
  },
  {
    titleKo: "이유가 보이는 추천",
    titleEn: "Recommendations with reasons",
    bodyKo: "로컬성 · 새로움 · 주의사항을 투명하게",
    bodyEn: "Local fit · Novelty · Warnings",
  },
  {
    titleKo: "함께 만드는 여행",
    titleEn: "Plan together",
    bodyKo: "초대 · 공동 편집 · 버전 충돌 보호",
    bodyEn: "Invite · Co-edit · Version safety",
  },
  {
    titleKo: "여행 뒤에도 이어지는 취향",
    titleEn: "A taste profile that grows",
    bodyKo: "저장 · 교체 · 방문을 다음 추천에 반영",
    bodyEn: "Save · Replace · Visit",
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
    navigate(account ? paths.home() : paths.signup(), { replace: true });
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
        <span className="section-eyebrow">{isEn ? "HYPER-PERSONALIZED BUSAN" : "나만의 부산을 만나는 방법"}</span>
        <h1>{isEn ? "Busan, shall we go?" : "부산, 가볼래?"}</h1>
        <p>{isEn
          ? "Find local places that fit you and turn them into a trip you can actually follow."
          : "내 취향과 조건에 맞는 로컬 장소를 찾고, 실제로 움직일 수 있는 일정으로 완성해요."}</p>
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
          {account ? (isEn ? "Continue" : "내 여행 계속하기") : (isEn ? "Create an account" : "가입하고 시작하기")}
        </button>
        <small>{isEn ? "For travelers aged 14 and over · Korean and English supported" : "만 14세 이상 이용 가능 · 한국어와 영어를 지원합니다."}</small>
      </div>
    </div>
  );
}
