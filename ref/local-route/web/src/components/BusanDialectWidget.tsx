import { useState } from "react";

export interface DialectItem {
  id: string;
  dialect: string;
  standard: string;
  english: string;
  meaningEn: string;
  contextKo: string;
  audioText: string;
}

export const BUSAN_DIALECTS: DialectItem[] = [
  {
    id: "d1",
    dialect: "가가 가가?",
    standard: "그 사람이 가씨 성을 가진 그 사람이냐?",
    english: "Is that person the one named Ga?",
    meaningEn: "Used when asking if a specific person has the surname 'Ga'.",
    contextKo: "친구와 누군가에 대해 이야기할 때 신기해하며 물어볼 때 쓰는 표현입니다.",
    audioText: "가가 가가?",
  },
  {
    id: "d2",
    dialect: "맞나?",
    standard: "정말이야? / 그래?",
    english: "Is that so? / Really?",
    meaningEn: "Extremely common Busan reaction meaning 'Is that true?' or 'Oh really?'",
    contextKo: "부산 사람이 대화할 때 가장 많이 쓰는 맞장구 추임새입니다.",
    audioText: "맞나?",
  },
  {
    id: "d3",
    dialect: "단디해라",
    standard: "야무지게 잘 해라 / 확실히 해라",
    english: "Do it properly and carefully!",
    meaningEn: "An encouraging instruction to handle something thoroughly.",
    contextKo: "여행 준비나 일을 시작할 때 '꼼꼼히 챙겨라'라는 유용한 표현입니다.",
    audioText: "단디해라",
  },
  {
    id: "d4",
    dialect: "억수로 좋네!",
    standard: "엄청나게 좋네요!",
    english: "It's extremely good / awesome!",
    meaningEn: "'Eok-su-ro' means 'extremely' or 'very much'.",
    contextKo: "해운대 바다나 맛집에 감탄할 때 쓰기 좋은 표현입니다.",
    audioText: "억수로 좋네!",
  },
  {
    id: "d5",
    dialect: "살아있네!",
    standard: "멋지다 / 아주 훌륭하다",
    english: "Awesome! / Feeling great!",
    meaningEn: "Expression for praising something impressive or top-notch.",
    contextKo: "부산 음식이나 멋진 경치를 보고 신이 났을 때 감탄하는 문장입니다.",
    audioText: "살아있네!",
  },
  {
    id: "d6",
    dialect: "밥 먹었나?",
    standard: "식사는 하셨나요? / 안녕?",
    english: "Have you eaten? / How are you?",
    meaningEn: "Friendly local greeting expressing care and concern.",
    contextKo: "부산 정서를 담은 따뜻한 안부 인사입니다.",
    audioText: "밥 먹었나?",
  },
  {
    id: "d7",
    dialect: "맹키로",
    standard: "~처럼 / ~ 같이",
    english: "Like / Same as",
    meaningEn: "Used when comparing something to another (e.g. '현지인 맹키로' = 'like a local').",
    contextKo: "'부산 현지인 맹키로 여행한다'처럼 사용하는 정겨운 표현입니다.",
    audioText: "맹키로",
  },
  {
    id: "d8",
    dialect: "치워라 마!",
    standard: "됐어, 그만해!",
    english: "Forget it! / Never mind!",
    meaningEn: "Playful refusal or telling someone to stop teasing.",
    contextKo: "친근한 사이에 웃으며 손사래 칠 때 사용하는 표현입니다.",
    audioText: "치워라 마!",
  },
];

export function BusanDialectWidget({ language = "KO" }: { language?: "KO" | "EN" }) {
  const isEn = language === "EN";
  const [activeItem, setActiveItem] = useState<DialectItem | null>(null);

  const playAudio = (text: string) => {
    if (!("speechSynthesis" in window)) {
      alert("TTS audio is not supported in this browser.");
      return;
    }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = "ko-KR";
    utterance.rate = 0.8;
    window.speechSynthesis.speak(utterance);
  };

  return (
    <section className="busan-dialect-widget">
      <div className="busan-dialect-header">
        <div>
          <span className="section-eyebrow">LOCAL EXPRESSIONS · BUSAN DIALECT</span>
          <h2>🗣️ {isEn ? "Master Busan Local Dialect" : "부산 사투리 표현 익히기"}</h2>
          <p>{isEn ? "Learn authentic Busan phrases to connect with friendly locals!" : "현지인과 더 친근하게 소통할 수 있는 대표 부산 사투리를 익혀보세요."}</p>
        </div>
      </div>

      <div className="busan-dialect-grid">
        {BUSAN_DIALECTS.map((item) => (
          <div key={item.id} className="dialect-card">
            <div className="dialect-card-top">
              <span className="dialect-badge">BUSAN</span>
              <button
                type="button"
                className="audio-btn"
                onClick={() => playAudio(item.audioText)}
                title={isEn ? "Listen audio" : "음성 듣기"}
              >
                🔊 {isEn ? "Listen" : "듣기"}
              </button>
            </div>
            <strong className="dialect-title">{item.dialect}</strong>
            <p className="dialect-standard">
              <b>{isEn ? "Standard" : "표준어"}:</b> {item.standard}
            </p>
            <p className="dialect-english">
              <b>{isEn ? "English" : "영문"}:</b> {item.english}
            </p>
            <small className="dialect-context">{isEn ? item.meaningEn : item.contextKo}</small>
            <button
              type="button"
              className="view-large-btn"
              onClick={() => setActiveItem(item)}
            >
              {isEn ? "View Card 🔍" : "크게 보기 🔍"}
            </button>
          </div>
        ))}
      </div>

      {activeItem && (
        <div className="modal-backdrop" role="presentation" onClick={() => setActiveItem(null)}>
          <div className="dialect-modal" role="dialog" onClick={(e) => e.stopPropagation()}>
            <button type="button" className="close-btn" onClick={() => setActiveItem(null)}>✕</button>
            <span className="dialect-badge">BUSAN DIALECT FLASHCARD</span>
            <h2 className="large-dialect">{activeItem.dialect}</h2>
            <button
              type="button"
              className="modal-audio-btn"
              onClick={() => playAudio(activeItem.audioText)}
            >
              🔊 {isEn ? "Play Busan Pronunciation" : "부산 사투리 발음 들려주기"}
            </button>
            <div className="dialect-modal-info">
              <div>
                <dt>{isEn ? "Standard Korean" : "표준어 의미"}</dt>
                <dd>{activeItem.standard}</dd>
              </div>
              <div>
                <dt>{isEn ? "English Translation" : "영어 번역"}</dt>
                <dd>{activeItem.english}</dd>
              </div>
              <div>
                <dt>{isEn ? "Usage & Context" : "사용 상황 & 팁"}</dt>
                <dd>{isEn ? activeItem.meaningEn : activeItem.contextKo}</dd>
              </div>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
