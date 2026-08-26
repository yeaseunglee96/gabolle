import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { paths } from "../routes/paths";

interface Props {
  tripTitle?: string;
  daysCount?: number;
  totalPlaces?: number;
  language?: "KO" | "EN";
  onCopyCourse?: () => void;
}

export function TripMemorySummary({ tripTitle = "부산 3일 로컬 먹방 여행", daysCount = 3, totalPlaces = 12, language = "KO", onCopyCourse }: Props) {
  const isEn = language === "EN";
  const navigate = useNavigate();
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    setCopied(true);
    if (onCopyCourse) onCopyCourse();
    setTimeout(() => {
      setCopied(false);
      navigate(paths.plan("basic"));
    }, 1000);
  };

  return (
    <div className="trip-memory-summary">
      <div className="memory-banner">
        <div className="banner-left">
          <span className="memory-badge">MAP & RECOLLECTION</span>
          <h3>{isEn ? `Memory Map: ${daysCount} Days in Busan` : `부산에서의 ${daysCount}일간의 추억 지도`}</h3>
          <p>{isEn ? `Visited ${totalPlaces} places with verified local food & sights` : `검증된 로컬 장소 및 맛집 ${totalPlaces}곳 방문 기록`}</p>
        </div>
        <div className="banner-actions">
          <button type="button" className={`copy-course-btn ${copied ? "copied" : ""}`} onClick={handleCopy}>
            {copied ? (isEn ? "✓ Course Copied!" : "✓ 내 일정으로 복제 완료!") : (isEn ? "📥 Copy This Course" : "📥 이 코스 내 일정으로 가져오기")}
          </button>
        </div>
      </div>

      <div className="memory-timeline-preview">
        <div className="memory-day">
          <strong>Day 1</strong>
          <span>
            {isEn
              ? "🍜 밀면 (Milmyeon) → 🏛️ 감천문화마을 (Gamcheon Village) → 🌅 광안리 야경 (Gwangalli Nightview)"
              : "🍜 밀면 → 🏛️ 감천문화마을 → 🌅 광안리 야경"}
          </span>
        </div>
        <div className="memory-day">
          <strong>Day 2</strong>
          <span>
            {isEn
              ? "🍲 돼지국밥 (Dwaeji-gukbap) → 🛍️ 국제시장 (Gukje Market) → ☕ 해운대 뷰 카페 (Haeundae View Cafe)"
              : "🍲 돼지국밥 → 🛍️ 국제시장 → ☕ 해운대 뷰 카페"}
          </span>
        </div>
        <div className="memory-day">
          <strong>Day 3</strong>
          <span>
            {isEn
              ? "🐟 자갈치 해산물 (Jagalchi Seafood) → 🌿 태종대 (Taejongdae) → 🥞 씨앗호떡 (Ssiat Hotteok)"
              : "🐟 자갈치 해산물 → 🌿 태종대 해안길 → 🥞 씨앗호떡"}
          </span>
        </div>
      </div>
    </div>
  );
}
