import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { BrandLogo } from "../components/BrandLogo";
import { AccountMenu } from "../components/AccountMenu";
import { getCourseCategories } from "../api/client";
import type { CourseCategory } from "../types";
import { getUiLanguage, setUiLanguage, subscribeUiLanguage } from "../i18n";
import { useAppShell } from "../routes/AppShell";
import { paths } from "../routes/paths";

/**
 * / — 시작 화면
 * 서비스가 무엇을 해주는지 한 화면에 보여주고 여행 계획 입력으로 연결한다.
 */
export function HomePage() {
  const { placeCount } = useAppShell();
  const [categories, setCategories] = useState<CourseCategory[]>([]);
  const [lang, setLang] = useState<"KO" | "EN">(getUiLanguage());
  const navigate = useNavigate();
  const isEn = lang === "EN";

  useEffect(() => {
    getCourseCategories().then(setCategories).catch(() => setCategories([]));
  }, []);

  useEffect(() => subscribeUiLanguage(setLang), []);

  return (
    <div className="landing">
      <header className="landing-header">
        <Link to={paths.welcome()} className="landing-brand-link" title={isEn ? "About LOCAL ROUTE" : "서비스 소개 다시 보기"}>
          <BrandLogo />
        </Link>
        <nav className="landing-nav">
          <Link to={paths.stories()}>{isEn ? "Travel Log" : "여행 기록"}</Link>
          <Link to={paths.myTrips()}>{isEn ? "My Trips" : "내 여행"}</Link>
          <span className="landing-lang-toggle">
            <button type="button" className={lang === "KO" ? "active" : ""} onClick={() => setUiLanguage("KO")}>한국어</button>
            <span aria-hidden="true">·</span>
            <button type="button" className={lang === "EN" ? "active" : ""} onClick={() => setUiLanguage("EN")}>English</button>
          </span>
          <AccountMenu />
        </nav>
      </header>

      <section className="landing-hero">
        <span className="section-eyebrow">{isEn ? "LOCAL-RECOMMENDED TRAVEL" : "현지인 추천 기반 여행"}</span>
        <h1>{isEn ? "Enjoy Busan to the fullest" : "부산을 마음껏 즐기세요"}</h1>
        <p>{isEn
          ? "Instead of just listing places, we calculate opening hours, travel time, budget, and accessibility together."
          : "장소를 나열하는 대신 운영시간, 이동시간, 예산과 동반 조건을 함께 계산합니다."}</p>
        <button type="button" className="primary-btn" onClick={() => navigate(paths.plan())}>
          {isEn ? "Create My Itinerary" : "여행 일정 만들기"}
        </button>
        {typeof placeCount === "number" && (
          <small>{isEn ? `${placeCount.toLocaleString()} registered local places (Busan)` : `등록된 로컬 장소 ${placeCount.toLocaleString()}곳 (부산 기준)`}</small>
        )}
      </section>

      {categories.length > 0 && (
        <section className="landing-courses">
          <h2>{isEn ? "What kind of Busan do you want?" : "어떤 부산을 원하시나요?"}</h2>
          <ul>
            {categories.filter((category) => category.enabled).slice(0, 10).map((category) => (
              <li key={category.code}>
                <strong>{isEn ? category.nameEn : category.nameKo}</strong>
                {!isEn && <span>{category.summaryKo}</span>}
              </li>
            ))}
          </ul>
        </section>
      )}

      <footer className="landing-footer">
        <Link to={paths.welcome()}>{isEn ? "About LOCAL ROUTE" : "서비스 소개"}</Link>
        <Link to={paths.terms()}>{isEn ? "Terms of Service" : "이용약관"}</Link>
        <Link to={paths.privacy()}>{isEn ? "Privacy Policy" : "개인정보 처리방침"}</Link>
        <Link to={paths.openSource()}>{isEn ? "Open Source Notices" : "오픈소스 고지"}</Link>
      </footer>
    </div>
  );
}
