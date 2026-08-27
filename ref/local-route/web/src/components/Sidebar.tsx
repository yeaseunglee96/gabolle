import { Link } from "react-router-dom";
import { BrandLogo } from "./BrandLogo";
import { paths } from "../routes/paths";

export type DashboardTab = "home" | "schedule" | "discover" | "together" | "prep";

const NAV_ITEMS: { id: DashboardTab; label: string; labelEn: string }[] = [
  { id: "home", label: "홈", labelEn: "Home" },
  { id: "schedule", label: "일정", labelEn: "Plan" },
  { id: "discover", label: "로컬", labelEn: "Local" },
  { id: "together", label: "함께", labelEn: "Together" },
  { id: "prep", label: "여행 준비", labelEn: "Prepare" },
];

interface Props {
  placeCount: number | null;
  language?: "KO" | "EN";
  activeTab: DashboardTab;
  onTabChange: (tab: DashboardTab) => void;
  showNavigation?: boolean;
}

export function Sidebar({ placeCount, language = "KO", activeTab, onTabChange, showNavigation = true }: Props) {
  const en = language === "EN";
  return (
    <aside className="sidebar">
      <Link to={paths.welcome()} className="sidebar-brand" title={en ? "About LOCAL ROUTE" : "서비스 소개 다시 보기"}>
        <BrandLogo />
      </Link>
      <p className="brand-tagline">
        {en ? "Places locals return to," : "현지인이 다시 가는 곳으로,"}
        <br />
        {en ? "even where Google Maps can't find the way" : "구글맵이 못 찾는 길도 걱정 없이"}
      </p>

      {showNavigation && <nav className="sidebar-nav" aria-label={en ? "Main service navigation" : "주요 서비스"}>
        {NAV_ITEMS.map((item) => (
          <button
            key={item.id}
            type="button"
            className={`nav-item ${activeTab === item.id ? "active" : ""}`}
            aria-current={activeTab === item.id ? "page" : undefined}
            onClick={() => onTabChange(item.id)}
          >
            <span>{en ? item.labelEn : item.label}</span>
          </button>
        ))}
      </nav>}

      <div className="sidebar-card stat-card">
        <div className="stat-number">{placeCount ?? "-"}{en ? " places" : "개"}</div>
        <div className="stat-label">{en ? "Registered local places (Busan)" : "등록된 로컬 장소 (부산 기준)"}</div>
      </div>
    </aside>
  );
}
