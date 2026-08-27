import { useEffect, useState } from "react";
import { getUiLanguage, setUiLanguage, subscribeUiLanguage } from "../i18n";
import { MenuTranslatorModal } from "./MenuTranslatorModal";
import { VoiceTranslatorModal } from "./VoiceTranslatorModal";
import { AccountMenu } from "./AccountMenu";

export function LanguageSwitcher() {
  const [lang, setLang] = useState<"KO" | "EN">(getUiLanguage());
  const [showMenuTranslator, setShowMenuTranslator] = useState(false);
  const [showVoiceTranslator, setShowVoiceTranslator] = useState(false);
  const isEn = lang === "EN";

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
          className="header-tool-btn"
          onClick={() => setShowMenuTranslator(true)}
          title={isEn ? "Menu Photo Translator" : "메뉴판 사진 번역기"}
        >
          📷 {isEn ? "Menu Translator" : "메뉴판 번역"}
        </button>
        <button
          type="button"
          className="header-tool-btn"
          onClick={() => setShowVoiceTranslator(true)}
          title={isEn ? "Bi-directional Voice Interpreter" : "양방향 실시간 음성 번역기"}
        >
          🎙️ {isEn ? "Voice Interpreter" : "음성 통역"}
        </button>
        <span className="lang-divider">|</span>
        <button
          type="button"
          className={`lang-btn ${lang === "KO" ? "active" : ""}`}
          onClick={() => toggleLanguage("KO")}
        >
          한국어
        </button>
        <span className="lang-divider">|</span>
        <button
          type="button"
          className={`lang-btn ${lang === "EN" ? "active" : ""}`}
          onClick={() => toggleLanguage("EN")}
        >
          English
        </button>
        <span className="lang-divider">|</span>
        <AccountMenu />
      </div>

      <MenuTranslatorModal
        isOpen={showMenuTranslator}
        onClose={() => setShowMenuTranslator(false)}
        language={lang}
      />

      <VoiceTranslatorModal
        isOpen={showVoiceTranslator}
        onClose={() => setShowVoiceTranslator(false)}
        language={lang}
      />
    </>
  );
}
