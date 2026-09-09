import { useEffect, useState } from "react";
import { getUiLanguage, setUiLanguage, subscribeUiLanguage } from "../i18n";
import { AccountMenu } from "./AccountMenu";

export function LanguageSwitcher() {
  const [lang, setLang] = useState<"KO" | "EN">(getUiLanguage());

  useEffect(() => {
    return subscribeUiLanguage((newLang) => setLang(newLang));
  }, []);

  const toggleLanguage = (targetLang: "KO" | "EN") => {
    setUiLanguage(targetLang);
  };

  return (
    <>
      <div className="top-right-lang-switcher" aria-label="Language selection">
        <button
          type="button"
          className={`lang-btn ${lang === "KO" ? "active" : ""}`}
          onClick={() => toggleLanguage("KO")}
        >
          <span className="lang-label-long">한국어</span><span className="lang-label-short">KO</span>
        </button>
        <span className="lang-divider">|</span>
        <button
          type="button"
          className={`lang-btn ${lang === "EN" ? "active" : ""}`}
          onClick={() => toggleLanguage("EN")}
        >
          <span className="lang-label-long">English</span><span className="lang-label-short">EN</span>
        </button>
        <span className="lang-divider">|</span>
        <AccountMenu />
      </div>

    </>
  );
}
