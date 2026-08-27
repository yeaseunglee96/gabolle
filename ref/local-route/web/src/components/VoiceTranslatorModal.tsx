import { useEffect, useRef, useState } from "react";

export interface ChatMessage {
  id: string;
  speaker: "TOURIST" | "LOCAL";
  originalText: string;
  translatedText: string;
  timestamp: string;
}

export const PRESET_VOICE_QA = [
  {
    speaker: "TOURIST" as const,
    en: "Excuse me, does this soup contain pork?",
    ko: "실례지만, 이 국밥에 돼지고기가 들어가나요?",
  },
  {
    speaker: "LOCAL" as const,
    ko: "네, 돼지고기가 들어갑니다. 혹시 못 드시면 순대나 내장으로 바꿔드릴 수 있어요.",
    en: "Yes, it contains pork. If you cannot eat it, we can substitute it with Korean sausage or tripe.",
  },
  {
    speaker: "TOURIST" as const,
    en: "That sounds great! I'll have the sausage rice soup, please.",
    ko: "좋습니다! 순대국밥으로 부탁드릴게요.",
  },
];

interface Props {
  isOpen: boolean;
  onClose: () => void;
  language?: "KO" | "EN";
}

export function VoiceTranslatorModal({ isOpen, onClose, language = "KO" }: Props) {
  const isEn = language === "EN";
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [listeningSpeaker, setListeningSpeaker] = useState<"TOURIST" | "LOCAL" | null>(null);
  const [transcriptInput, setTranscriptInput] = useState("");
  const recognitionRef = useRef<any>(null);
  const transcriptRef = useRef("");

  useEffect(() => {
    if (isOpen && messages.length === 0) {
      // Seed default welcome message
      setMessages([
        {
          id: "m0",
          speaker: "LOCAL",
          originalText: "어서오세요! 무엇을 도와드릴까요?",
          translatedText: "Welcome! How can I help you today?",
          timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
        },
      ]);
    }
  }, [isOpen, messages.length]);

  if (!isOpen) return null;

  const playSpeech = (text: string, lang: "ko-KR" | "en-US") => {
    if (!("speechSynthesis" in window)) return;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = lang;
    utterance.rate = 0.9;
    window.speechSynthesis.speak(utterance);
  };

  const startListening = (speaker: "TOURIST" | "LOCAL") => {
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRecognition) {
      // Fallback for browsers without STT support
      alert(isEn ? "Web Speech STT is not supported in this browser. Please type or use quick presets." : "이 브라우저는 음성 인식을 지원하지 않습니다. 텍스트 입력이나 예시 문장을 활용해주세요.");
      return;
    }

    if (listeningSpeaker) {
      stopListening();
      return;
    }

    try {
      const recognition = new SpeechRecognition();
      recognition.lang = speaker === "TOURIST" ? "en-US" : "ko-KR";
      recognition.interimResults = true;
      recognition.continuous = false;

      recognition.onstart = () => {
        setListeningSpeaker(speaker);
        transcriptRef.current = "";
        setTranscriptInput("");
      };

      recognition.onresult = (event: any) => {
        const current = event.resultIndex;
        const transcript = event.results[current][0].transcript;
        transcriptRef.current = transcript;
        setTranscriptInput(transcript);
      };

      recognition.onend = () => {
        setListeningSpeaker(null);
        const finalTranscript = transcriptRef.current;
        if (finalTranscript.trim()) {
          sendVoiceMessage(speaker, finalTranscript);
        }
      };

      recognition.onerror = () => {
        setListeningSpeaker(null);
      };

      recognitionRef.current = recognition;
      recognition.start();
    } catch {
      setListeningSpeaker(null);
    }
  };

  const stopListening = () => {
    if (recognitionRef.current) {
      recognitionRef.current.stop();
      setListeningSpeaker(null);
    }
  };

  const sendVoiceMessage = (speaker: "TOURIST" | "LOCAL", text: string) => {
    if (!text.trim()) return;

    // Simple fast translation logic for demo
    let translated = "";
    if (speaker === "TOURIST") {
      translated = text.includes("pork")
        ? "돼지고기가 포함되어 있나요?"
        : text.includes("check")
        ? "체크인하고 싶어요."
        : text.includes("bill") || text.includes("pay") || text.includes("card")
        ? "계산할게요."
        : `[영-한 번역]: ${text}`;
    } else {
      translated = text.includes("네") || text.includes("예")
        ? "Yes, that is correct."
        : text.includes("얼마")
        ? "How much is it?"
        : `[한-영 Translation]: ${text}`;
    }

    const newMessage: ChatMessage = {
      id: String(Date.now()),
      speaker,
      originalText: text,
      translatedText: translated,
      timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
    };

    setMessages((prev) => [...prev, newMessage]);
    setTranscriptInput("");

    // Auto-play translated speech
    const targetLang = speaker === "TOURIST" ? "ko-KR" : "en-US";
    playSpeech(translated, targetLang);
  };

  const addPreset = (qa: typeof PRESET_VOICE_QA[0]) => {
    const isTourist = qa.speaker === "TOURIST";
    const newMessage: ChatMessage = {
      id: String(Date.now()),
      speaker: qa.speaker,
      originalText: isTourist ? qa.en : qa.ko,
      translatedText: isTourist ? qa.ko : qa.en,
      timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
    };
    setMessages((prev) => [...prev, newMessage]);
    playSpeech(newMessage.translatedText, isTourist ? "ko-KR" : "en-US");
  };

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div className="voice-translator-modal" role="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div>
            <span className="badge-blue">REAL-TIME VOICE INTERPRETER</span>
            <h2>🎙️ {isEn ? "Bi-directional Voice Translator" : "양방향 실시간 음성 번역기"}</h2>
            <p>{isEn ? "Speak in English or Korean for instant simultaneous voice & text translation." : "외국인과 현지인이 서로의 언어로 말하면 동시에 통역해드립니다."}</p>
          </div>
          <button type="button" className="close-btn" onClick={onClose}>✕</button>
        </div>

        <div className="voice-chat-history">
          {messages.map((msg) => {
            const isTourist = msg.speaker === "TOURIST";
            return (
              <div key={msg.id} className={`chat-bubble-row ${isTourist ? "tourist" : "local"}`}>
                <div className="avatar">{isTourist ? "🇺🇸" : "🇰🇷"}</div>
                <div className="chat-bubble">
                  <div className="bubble-speaker-tag">
                    {isTourist ? "Tourist (English)" : "Local (한국어)"} · <small>{msg.timestamp}</small>
                  </div>
                  <p className="original-text">{msg.originalText}</p>
                  <div className="translation-box">
                    <span>{isTourist ? "🇰🇷" : "🇺🇸"} {msg.translatedText}</span>
                    <button
                      type="button"
                      className="tts-btn"
                      onClick={() => playSpeech(msg.translatedText, isTourist ? "ko-KR" : "en-US")}
                    >
                      🔊
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {listeningSpeaker && (
          <div className="listening-indicator">
            <span className="pulsing-mic">🎙️</span>
            <p>{isEn ? `Listening (${listeningSpeaker === "TOURIST" ? "English" : "Korean"})...` : `음성 인식 중 (${listeningSpeaker === "TOURIST" ? "영어" : "한국어"})...`}</p>
            {transcriptInput && <small>"{transcriptInput}"</small>}
          </div>
        )}

        <div className="voice-presets-row">
          <span>{isEn ? "Quick Presets" : "대화 예시"}:</span>
          {PRESET_VOICE_QA.map((qa, idx) => (
            <button key={idx} type="button" onClick={() => addPreset(qa)}>
              {qa.speaker === "TOURIST" ? "🇺🇸" : "🇰🇷"} {qa.en}
            </button>
          ))}
        </div>

        <div className="voice-controls">
          <button
            type="button"
            className={`mic-btn tourist-mic ${listeningSpeaker === "TOURIST" ? "listening" : ""}`}
            onClick={() => startListening("TOURIST")}
          >
            <span>🇺🇸</span>
            <div>
              <strong>{listeningSpeaker === "TOURIST" ? (isEn ? "Stop Listening" : "음성 입력 중...") : (isEn ? "Speak English" : "외국인 (영어 말하기)")}</strong>
              <small>Tourist (EN)</small>
            </div>
          </button>

          <button
            type="button"
            className={`mic-btn local-mic ${listeningSpeaker === "LOCAL" ? "listening" : ""}`}
            onClick={() => startListening("LOCAL")}
          >
            <span>🇰🇷</span>
            <div>
              <strong>{listeningSpeaker === "LOCAL" ? (isEn ? "Stop Listening" : "음성 입력 중...") : (isEn ? "Speak Korean" : "한국인 (한국어 말하기)")}</strong>
              <small>Local (KO)</small>
            </div>
          </button>
        </div>
      </div>
    </div>
  );
}
