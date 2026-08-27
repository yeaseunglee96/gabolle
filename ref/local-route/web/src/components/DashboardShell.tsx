import type { ReactNode } from "react";
import { Sidebar } from "./Sidebar";
import type { DashboardTab } from "./Sidebar";
import { LanguageSwitcher } from "./LanguageSwitcher";

interface Props {
  placeCount: number | null;
  children: ReactNode;
  language?: "KO" | "EN";
  activeTab?: DashboardTab;
  onTabChange?: (tab: DashboardTab) => void;
}

export function DashboardShell({ placeCount, children, language = "KO", activeTab = "home", onTabChange }: Props) {
  return (
    <div className="dashboard-shell">
      <a className="skip-link" href="#main-content">본문으로 건너뛰기</a>
      <LanguageSwitcher />
      <Sidebar placeCount={placeCount} language={language} activeTab={activeTab} onTabChange={onTabChange ?? (() => undefined)} showNavigation={!!onTabChange} />
      <main className="dashboard-main" id="main-content" tabIndex={-1}>{children}</main>
    </div>
  );
}

