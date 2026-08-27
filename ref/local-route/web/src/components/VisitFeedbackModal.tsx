import { useState } from "react";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  placeName: string;
  language?: "KO" | "EN";
  onSubmitFeedback?: (feedback: { rating: string; foodMatch: boolean; foreignEase: boolean; comment: string }) => void;
}

export function VisitFeedbackModal({ isOpen, onClose, placeName, language = "KO", onSubmitFeedback }: Props) {
  const isEn = language === "EN";
  const [rating, setRating] = useState<"GREAT" | "OK" | "POOR">("GREAT");
  const [foodMatch, setFoodMatch] = useState(true);
  const [foreignEase, setForeignEase] = useState(true);
  const [comment, setComment] = useState("");
  const [submitted, setSubmitted] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (onSubmitFeedback) {
      onSubmitFeedback({ rating, foodMatch, foreignEase, comment });
    }
    setTimeout(() => {
      setSubmitted(false);
      onClose();
    }, 1200);
  };

  return (
    <div className="speak-modal-overlay" onClick={onClose}>
      <div className="speak-modal-card feedback-card" onClick={(e) => e.stopPropagation()}>
        <div className="speak-modal-header">
          <div>
            <span className="speak-badge">VISIT FEEDBACK & REVIEW</span>
            <h3>{isEn ? "How was your visit?" : "방문 후 만족도 피드백"}</h3>
            <p className="speak-target">📍 {placeName}</p>
          </div>
          <button type="button" className="close-btn" onClick={onClose}>✕</button>
        </div>

        {submitted ? (
          <div className="feedback-success">
            <span className="success-icon">🎉</span>
            <h4>{isEn ? "Thank you for your feedback!" : "피드백이 반영되었습니다!"}</h4>
            <p>{isEn ? "Your input helps us improve future recommendations." : "소중한 의견이 다음 여행 추천을 더 정확하게 만듭니다."}</p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="feedback-form">
            <div className="field-group">
              <label>{isEn ? "Overall Experience" : "전체 만족도"}</label>
              <div className="rating-buttons">
                <button
                  type="button"
                  className={rating === "GREAT" ? "selected" : ""}
                  onClick={() => setRating("GREAT")}
                >
                  😍 {isEn ? "Great" : "기대 이상!"}
                </button>
                <button
                  type="button"
                  className={rating === "OK" ? "selected" : ""}
                  onClick={() => setRating("OK")}
                >
                  🙂 {isEn ? "Good" : "괜찮았어요"}
                </button>
                <button
                  type="button"
                  className={rating === "POOR" ? "selected" : ""}
                  onClick={() => setRating("POOR")}
                >
                  🙁 {isEn ? "Disappointing" : "아쉬웠어요"}
                </button>
              </div>
            </div>

            <div className="check-options">
              <label className="toggle-label">
                <input
                  type="checkbox"
                  checked={foodMatch}
                  onChange={(e) => setFoodMatch(e.target.checked)}
                />
                <span>{isEn ? "Food taste & price matched expectation" : "음식 맛과 가격대가 예상과 일치했어요"}</span>
              </label>

              <label className="toggle-label">
                <input
                  type="checkbox"
                  checked={foreignEase}
                  onChange={(e) => setForeignEase(e.target.checked)}
                />
                <span>{isEn ? "Easy to order & pay for international visitors" : "외국인이 주문하고 결제하기 수월했어요"}</span>
              </label>
            </div>

            <div className="field-group" style={{ marginTop: "16px" }}>
              <label>{isEn ? "Short Comment (Optional)" : "한 줄 기한 메모 (선택)"}</label>
              <input
                type="text"
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder={isEn ? "e.g., Milmyeon broth was super refreshing!" : "예: 밀면 육수가 정말 시원하고 직원이 친절했어요."}
              />
            </div>

            <button type="submit" className="submit-feedback-btn">
              {isEn ? "Submit Feedback" : "만족도 제출하기"}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
