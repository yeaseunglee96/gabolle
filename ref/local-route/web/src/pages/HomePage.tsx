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
  const picks = [
    { titleKo: "영도 바다와 오래된 골목", titleEn: "Yeongdo sea & old alleys", metaKo: "로컬 산책 · 반나절", metaEn: "Local walk · Half day", reasonKo: "조용한 동네와 사진 취향에 맞아요", reasonEn: "Fits your quiet and photo tastes", tone: "ocean" },
    { titleKo: "시장 사이 부산의 한 끼", titleEn: "A meal between Busan markets", metaKo: "전통시장 · 4시간", metaEn: "Markets · 4 hours", reasonKo: "현지인 방문과 음식 취향을 함께 반영", reasonEn: "Local visits and food preferences", tone: "market" },
    { titleKo: "광안리의 느린 저녁", titleEn: "A slow evening in Gwangalli", metaKo: "야경 · 카페 · 저녁", metaEn: "Night view · Cafe · Evening", reasonKo: "여유로운 여행 스타일에 맞아요", reasonEn: "Fits a relaxed travel style", tone: "night" },
  ];

  useEffect(() => {
    getCourseCategories().then(setCategories).catch(() => setCategories([]));
  }, []);

  useEffect(() => subscribeUiLanguage(setLang), []);

  return (
    <div className="landing">
      <header className="landing-header">
        <Link to={paths.welcome()} className="landing-brand-link" title={isEn ? "About GABOLLE" : "서비스 소개 다시 보기"}>
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

      <section className="personalized-picks" aria-labelledby="pick-title">
        <div className="pick-heading"><div><span className="section-eyebrow">EDITOR'S PICK · FOR YOU</span><h1 id="pick-title">{isEn ? "Start with a Busan that feels like you" : "나와 닮은 부산부터 둘러보세요"}</h1></div><small>{isEn ? "Personalized from your explicit choices" : "내가 직접 고른 취향을 기준으로 정렬했어요"}</small></div>
        <div className="pick-grid">{picks.map((pick) => <article key={pick.titleKo} className={`pick-card ${pick.tone}`}>
          <span>{isEn ? pick.metaEn : pick.metaKo}</span>
          <h2>{isEn ? pick.titleEn : pick.titleKo}</h2>
          <p>{isEn ? pick.reasonEn : pick.reasonKo}</p>
          <button type="button" onClick={() => navigate(paths.plan("taste"))}>{isEn ? "Make this my trip" : "이 취향으로 일정 만들기"}</button>
        </article>)}</div>
      </section>

      <section className="landing-hero">
        <span className="section-eyebrow">{isEn ? "BUSAN, MADE FOR YOU" : "취향으로 만나는 부산"}</span>
        <h1>{isEn ? "Where shall we go in Busan?" : "부산, 어디부터 가볼래?"}</h1>
        <p>{isEn
          ? "Instead of just listing places, we calculate opening hours, travel time, budget, and accessibility together."
          : "유명 장소를 나열하지 않고, 취향과 제약을 이해해 지금 실행할 수 있는 로컬 일정을 만들어요."}</p>
        <div className="home-primary-actions">
          <button type="button" className="primary-btn" onClick={() => navigate(paths.plan())}>{isEn ? "Plan a trip" : "여행 계획 만들기"}</button>
          <button type="button" className="secondary-btn" onClick={() => navigate(paths.now())}>{isEn ? "Where to go now" : "지금 갈 곳 찾기"}</button>
        </div>
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
        <Link to={paths.welcome()}>{isEn ? "About GABOLLE" : "서비스 소개"}</Link>
        <Link to={paths.terms()}>{isEn ? "Terms of Service" : "이용약관"}</Link>
        <Link to={paths.privacy()}>{isEn ? "Privacy Policy" : "개인정보 처리방침"}</Link>
        <Link to={paths.openSource()}>{isEn ? "Open Source Notices" : "오픈소스 고지"}</Link>
      </footer>
    </div>
  );
}
