import { useState } from "react";
import { parseMenuText } from "../utils/menuOcr";

export interface TranslatedMenuItem {
  id: string;
  nameKo: string;
  nameEn: string;
  price: string;
  allergensEn: string[];
  allergensKo: string[];
  dietTagsEn: string[];
  spicyLevel: number;
  /** false면 사전에 없는 문구를 원문 그대로 보여준 것 — 임의로 지어낸 번역이 아님을 표시한다. */
  matched?: boolean;
}

export const DEMO_MENUS: { title: string; image: string; items: TranslatedMenuItem[] }[] = [
  {
    title: "부산 돼지국밥 전문점 메뉴판",
    image: "https://images.unsplash.com/photo-1541544741938-0af808871cc0?auto=format&fit=crop&w=800&q=80",
    items: [
      { id: "m1", nameKo: "돼지국밥", nameEn: "Pork Rice Soup", price: "₩10,000", allergensKo: ["돼지고기"], allergensEn: ["Pork"], dietTagsEn: ["Hot Soup"], spicyLevel: 0 },
      { id: "m2", nameKo: "순대국밥", nameEn: "Korean Sausage Rice Soup", price: "₩10,500", allergensKo: ["돼지고기", "갑각류(새우젓)"], allergensEn: ["Pork", "Shellfish"], dietTagsEn: ["Hot Soup"], spicyLevel: 1 },
      { id: "m3", nameKo: "수육백반", nameEn: "Boiled Pork Slice Set", price: "₩13,000", allergensKo: ["돼지고기"], allergensEn: ["Pork"], dietTagsEn: ["Set Menu"], spicyLevel: 0 },
      { id: "m4", nameKo: "맛보기 수육", nameEn: "Appetizer Boiled Pork", price: "₩15,000", allergensKo: ["돼지고기"], allergensEn: ["Pork"], dietTagsEn: ["Side Dish"], spicyLevel: 0 },
    ],
  },
  {
    title: "해운대 밀면 & 만두 전문점 메뉴판",
    image: "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=800&q=80",
    items: [
      { id: "m5", nameKo: "물밀면", nameEn: "Chilled Wheat Noodles in Broth", price: "₩9,000", allergensKo: ["밀", "소고기"], allergensEn: ["Wheat", "Beef"], dietTagsEn: ["Cold Noodle"], spicyLevel: 1 },
      { id: "m6", nameKo: "비빔밀면", nameEn: "Spicy Mixed Wheat Noodles", price: "₩9,500", allergensKo: ["밀", "대두", "땅콩"], allergensEn: ["Wheat", "Soybean", "Peanut"], dietTagsEn: ["Spicy Noodle"], spicyLevel: 3 },
      { id: "m7", nameKo: "수제 찐만두 (8개)", nameEn: "Handmade Steamed Dumplings (8pcs)", price: "₩7,000", allergensKo: ["돼지고기", "밀", "대두"], allergensEn: ["Pork", "Wheat", "Soybean"], dietTagsEn: ["Dumpling"], spicyLevel: 0 },
    ],
  },
];

interface Props {
  isOpen: boolean;
  onClose: () => void;
  language?: "KO" | "EN";
}

export function MenuTranslatorModal({ isOpen, onClose, language = "KO" }: Props) {
  const isEn = language === "EN";
  const [selectedDemoIndex, setSelectedDemoIndex] = useState(0);
  const [customImage, setCustomImage] = useState<string | null>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [ocrItems, setOcrItems] = useState<TranslatedMenuItem[]>([]);
  const [ocrError, setOcrError] = useState<string | null>(null);

  if (!isOpen) return null;

  const currentMenu = DEMO_MENUS[selectedDemoIndex];
  const displayedItems = customImage ? ocrItems : currentMenu.items;

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setAnalyzing(true);
    setOcrError(null);
    setOcrItems([]);

    const reader = new FileReader();
    reader.onload = async (event) => {
      const dataUrl = event.target?.result as string;
      setCustomImage(dataUrl);
      try {
        const { createWorker } = await import("tesseract.js");
        const worker = await createWorker("kor+eng");
        try {
          const { data } = await worker.recognize(dataUrl);
          const items = parseMenuText(data.text, isEn);
          setOcrItems(items);
          if (items.length === 0) {
            setOcrError(isEn
              ? "Couldn't recognize any menu text. Try a clearer, well-lit, front-facing photo."
              : "메뉴 텍스트를 인식하지 못했어요. 더 밝고 정면에서 찍은 선명한 사진으로 다시 시도해주세요.");
          }
        } finally {
          await worker.terminate();
        }
      } catch {
        setOcrError(isEn ? "Text recognition failed. Please try again." : "텍스트 인식에 실패했어요. 다시 시도해주세요.");
      } finally {
        setAnalyzing(false);
      }
    };
    reader.readAsDataURL(file);
  };

  const playAudio = (text: string) => {
    if (!("speechSynthesis" in window)) return;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = "ko-KR";
    window.speechSynthesis.speak(utterance);
  };

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div className="menu-translator-modal" role="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div>
            <span className="badge-purple">AI OCR MENU TRANSLATOR</span>
            <h2>📷 {isEn ? "Menu Photo Translator" : "메뉴판 사진 번역기"}</h2>
            <p>{isEn ? "Take a photo of a Korean menu for instant English translation & allergen alerts." : "메뉴판 사진을 찍으면 영문 번역과 알레르기/돼지고기 성분을 바로 알려드려요."}</p>
          </div>
          <button type="button" className="close-btn" onClick={onClose}>✕</button>
        </div>

        <div className="menu-translator-toolbar">
          <label className="upload-btn">
            📷 {isEn ? "Take Photo / Upload Menu Image" : "메뉴판 사진 촬영 / 업로드"}
            <input type="file" accept="image/*" capture="environment" onChange={handleFileUpload} />
          </label>
          <div className="demo-selector">
            <span>{isEn ? "Sample Menus" : "샘플 메뉴판"}:</span>
            {DEMO_MENUS.map((demo, idx) => (
              <button
                key={demo.title}
                type="button"
                className={`demo-chip ${selectedDemoIndex === idx && !customImage ? "active" : ""}`}
                onClick={() => { setCustomImage(null); setOcrItems([]); setOcrError(null); setSelectedDemoIndex(idx); }}
              >
                {demo.title}
              </button>
            ))}
          </div>
        </div>

        {analyzing ? (
          <div className="analyzing-state">
            <span className="spinner" />
            <p>{isEn ? "Reading Korean text from your photo and translating menu items..." : "사진 속 한글 텍스트를 인식하고 영문으로 번역하는 중..."}</p>
          </div>
        ) : (
          <div className="menu-translator-body">
            <div className="menu-image-container">
              <img src={customImage ?? currentMenu.image} alt="Menu preview" />
              {(!customImage || ocrItems.length > 0) && (
                <span className="ocr-overlay-badge">✓ AI OCR Text Detected</span>
              )}
              {customImage && ocrItems.length === 0 && (
                <span className="ocr-overlay-badge ocr-overlay-badge-warn">⚠ {isEn ? "No text detected" : "텍스트 인식 실패"}</span>
              )}
            </div>

            <div className="translated-items-list">
              <h3>{isEn ? "Translated Menu Items" : "영문 번역 및 성분 분석 결과"}</h3>
              {ocrError && <p className="ocr-error-text">{ocrError}</p>}
              <div className="items-grid">
                {displayedItems.map((item) => (
                  <div key={item.id} className={`translated-item-card ${item.matched === false ? "unmatched" : ""}`}>
                    <div className="item-header">
                      <div>
                        <strong className="name-ko">{item.nameKo}</strong>
                        <button type="button" className="say-btn" onClick={() => playAudio(item.nameKo)}>🔊</button>
                      </div>
                      <span className="item-price">{item.price}</span>
                    </div>
                    <p className="name-en">🇬🇧 {item.nameEn}</p>
                    {item.matched === false ? (
                      <div className="item-badges">
                        <span className="unmatched-badge">{isEn ? "Not in dictionary yet" : "사전에 없는 메뉴"}</span>
                      </div>
                    ) : (
                      <div className="item-badges">
                        {item.allergensEn.map((allergy) => (
                          <span key={allergy} className="allergy-badge">
                            ⚠️ {allergy}
                          </span>
                        ))}
                        {item.spicyLevel > 0 && (
                          <span className="spicy-badge">
                            🌶️ {"🌶️".repeat(item.spicyLevel)} {isEn ? `Spicy Lvl ${item.spicyLevel}` : `맵기 ${item.spicyLevel}`}
                          </span>
                        )}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
