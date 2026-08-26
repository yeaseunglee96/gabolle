import { useState } from "react";

export interface SpeakSentence {
  id: string;
  category: "TOURIST" | "RESTAURANT" | "TAXI" | "LODGING" | "GENERAL";
  ko: string;
  en: string;
  romanization: string;
}

export const PRESET_SENTENCES: SpeakSentence[] = [
  // TOURIST
  { id: "s1", category: "TOURIST", ko: "입장권 얼마인가요?", en: "How much is the admission ticket?", romanization: "Ipjanggwon eolma-ingayo?" },
  { id: "s2", category: "TOURIST", ko: "사진 한 장 찍어주시겠어요?", en: "Could you take a photo of us, please?", romanization: "Sajin han jang jjigeojusigesseoyo?" },
  { id: "s3", category: "TOURIST", ko: "화장실이 어디에 있나요?", en: "Where is the restroom?", romanization: "Hwajangsil-i eodie innayo?" },
  { id: "s4", category: "TOURIST", ko: "마감 시간이 몇 시인가요?", en: "What time do you close?", romanization: "Magam sigan-i myeot si-ingayo?" },

  // RESTAURANT & CAFE
  { id: "r1", category: "RESTAURANT", ko: "이 메뉴 하나 주세요.", en: "One of this menu item, please.", romanization: "I menu hana juseyo." },
  { id: "r2", category: "RESTAURANT", ko: "안 맵게 해주세요.", en: "Please make it not spicy.", romanization: "An maepge haejuseyo." },
  { id: "r3", category: "RESTAURANT", ko: "물 좀 주시겠어요?", en: "Could I have some water, please?", romanization: "Mul jom jusigesseoyo?" },
  { id: "r4", category: "RESTAURANT", ko: "카드로 결제할게요.", en: "I would like to pay by card.", romanization: "Kadeuro gyeoljehalgeyo." },
  { id: "r5", category: "RESTAURANT", ko: "돼지고기/갑각류가 들어가나요?", en: "Does this contain pork/shellfish?", romanization: "Dwaejigogi/Gapgakryuga deuroganayo?" },
  { id: "r6", category: "RESTAURANT", ko: "아이스 아메리카노 한 잔 주세요.", en: "One Iced Americano, please.", romanization: "Aiseu Amrikano han jan juseyo." },
  { id: "r7", category: "RESTAURANT", ko: "포장/테이크아웃 할게요.", en: "To go / Takeout, please.", romanization: "Pojang/Teikeu-a-ut halgeyo." },

  // TAXI
  { id: "t1", category: "TAXI", ko: "이 주소로 가주세요.", en: "Please take me to this address.", romanization: "I jusoro gajuseyo." },
  { id: "t2", category: "TAXI", ko: "여기서 세워주세요.", en: "Please stop here.", romanization: "Yeogiseo seowojuseyo." },
  { id: "t3", category: "TAXI", ko: "트렁크 좀 열어주시겠어요?", en: "Could you open the trunk, please?", romanization: "Teureongkeu jom yeorojusigesseoyo?" },

  // LODGING
  { id: "l1", category: "LODGING", ko: "체크인하고 싶어요.", en: "I would like to check in.", romanization: "Chekeuinhago sipeoyo." },
  { id: "l2", category: "LODGING", ko: "짐을 맡길 수 있나요?", en: "Can I leave my luggage here?", romanization: "Jimeul matgil su innayo?" },
];

interface Props {
  isOpen: boolean;
  onClose: () => void;
  targetCategory?: string;
  targetAddress?: string;
  targetName?: string;
  targetNameEn?: string | null;
  language?: "KO" | "EN";
}

export function SpeakModal({ isOpen, onClose, targetCategory = "RESTAURANT", targetAddress, targetName, targetNameEn, language = "KO" }: Props) {
  const isEn = language === "EN";
  const initialCategory = targetCategory === "CAFE" ? "RESTAURANT" : (["TOURIST", "RESTAURANT", "TAXI", "LODGING"].includes(targetCategory) ? targetCategory : "GENERAL");
  const [selectedCategory, setSelectedCategory] = useState<string>(initialCategory);
  const [activeSentence, setActiveSentence] = useState<SpeakSentence | null>(null);
  const [isFullScreen, setIsFullScreen] = useState(false);

  if (!isOpen) return null;

  const displayName = isEn && targetNameEn ? `${targetName} (${targetNameEn})` : targetName;
  const sentences = PRESET_SENTENCES.filter((s) => s.category === selectedCategory || (selectedCategory === "GENERAL" && true));
  const currentSentence = activeSentence ?? sentences[0] ?? PRESET_SENTENCES[0];

  const speakText = (text: string, slow = false) => {
    if (!("speechSynthesis" in window)) {
      alert("TTS is not supported in this browser.");
      return;
    }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = "ko-KR";
    utterance.rate = slow ? 0.6 : 0.9;
    window.speechSynthesis.speak(utterance);
  };

  return (
    <div className="speak-modal-overlay" onClick={onClose}>
      <div className="speak-modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="speak-modal-header">
          <div>
            <span className="speak-badge">AUDIO & SHOW KOREAN</span>
            <h3>{isEn ? "Speak Korean & Show Screen" : "현장 한국어 말하기 & 화면 보여주기"}</h3>
            {displayName && <p className="speak-target">📍 {displayName} ({targetAddress ?? ""})</p>}
          </div>
          <button type="button" className="close-btn" onClick={onClose}>✕</button>
        </div>

        <div className="category-tabs">
          <button
            type="button"
            className={selectedCategory === "TOURIST" ? "active" : ""}
            onClick={() => setSelectedCategory("TOURIST")}
          >
            🏛️ {isEn ? "Sights" : "관광지"}
          </button>
          <button
            type="button"
            className={selectedCategory === "RESTAURANT" ? "active" : ""}
            onClick={() => setSelectedCategory("RESTAURANT")}
          >
            🍽️ {isEn ? "Dining" : "식당·카페"}
          </button>
          <button
            type="button"
            className={selectedCategory === "TAXI" ? "active" : ""}
            onClick={() => setSelectedCategory("TAXI")}
          >
            🚕 {isEn ? "Taxi / Transport" : "택시/이동"}
          </button>
          <button
            type="button"
            className={selectedCategory === "LODGING" ? "active" : ""}
            onClick={() => setSelectedCategory("LODGING")}
          >
            🏨 {isEn ? "Hotel" : "숙소"}
          </button>
        </div>

        <div className="speak-body">
          {targetAddress && selectedCategory === "TAXI" && (
            <div className="taxi-address-card">
              <span className="card-label">{isEn ? "Show this to the taxi driver (Destination)" : "기사님께 보여주세요 (Destination)"}</span>
              <h2 className="taxi-destination">{displayName}</h2>
              <p className="taxi-address">{targetAddress}</p>
              <div className="card-actions">
                <button type="button" className="speak-btn" onClick={() => speakText(`${targetName} (으)로 가주세요.`)}>
                  🔊 {isEn ? "Play Address Audio" : "주소 음성 들려주기"}
                </button>
              </div>
            </div>
          )}

          <div className="sentence-list">
            {sentences.map((sentence) => (
              <div
                key={sentence.id}
                className={`sentence-item ${activeSentence?.id === sentence.id ? "selected" : ""}`}
                onClick={() => setActiveSentence(sentence)}
              >
                <div className="sentence-text">
                  <strong className="ko-text">{sentence.ko}</strong>
                  <span className="roman-text">{sentence.romanization}</span>
                  <small className="en-text">{sentence.en}</small>
                </div>
                <div className="sentence-actions">
                  <button type="button" className="action-btn" onClick={() => speakText(sentence.ko, false)}>
                    🔊 {isEn ? "Listen" : "재생"}
                  </button>
                  <button type="button" className="action-btn slow" onClick={() => speakText(sentence.ko, true)}>
                    🐢 {isEn ? "Slow" : "천천히"}
                  </button>
                </div>
              </div>
            ))}
          </div>

          {activeSentence && (
            <div className="display-card">
              <span className="display-hint">{isEn ? "Tap to view full screen for staff" : "직원에게 화면을 크게 보여줄 수 있어요"}</span>
              <div className="display-box" onClick={() => setIsFullScreen(true)}>
                <h2>{activeSentence.ko}</h2>
                <p>{activeSentence.en}</p>
              </div>
            </div>
          )}
        </div>
      </div>

      {isFullScreen && activeSentence && (
        <div className="fullscreen-speak-overlay" onClick={() => setIsFullScreen(false)}>
          <div className="fullscreen-content">
            <span className="fullscreen-tip">터치하면 닫힙니다 (Tap anywhere to close)</span>
            <h1 className="giant-text">{activeSentence.ko}</h1>
            <p className="giant-sub">{activeSentence.en}</p>
            <div className="fullscreen-buttons">
              <button
                type="button"
                className="giant-speak-btn"
                onClick={(e) => {
                  e.stopPropagation();
                  speakText(activeSentence.ko, false);
                }}
              >
                🔊 {isEn ? "Play Sound" : "음성 재생"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
