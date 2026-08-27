import type { RecommendationMode } from "../types";

/**
 * 여행 무드 - 카드 한 장으로 추천 모드·코스 카테고리·취향 태그를 한꺼번에 정한다.
 *
 * 기존에는 같은 질문을 세 번(모드 2지선다 + 코스 11지선다 + 태그 12개) 나눠 물었다.
 * 서버는 이 셋을 결국 하나의 tasteTags 로 합치므로, 사용자에게도 하나로 물어보는 편이 정직하다.
 * 세밀하게 고르고 싶은 사람을 위해 원래의 세 블록은 "직접 고르기"로 그대로 남겨둔다.
 */

export interface TravelMood {
  id: string;
  emoji: string;
  titleKo: string;
  titleEn: string;
  descKo: string;
  descEn: string;
  mode: RecommendationMode;
  /** 빈 문자열이면 코스 카테고리를 쓰지 않는 기본 코스. */
  courseCategory: string;
  tasteTags: string[];
}

export const TRAVEL_MOODS: TravelMood[] = [
  {
    id: "FIRST_TIME", emoji: "🏛",
    titleKo: "처음이라 유명한 곳부터", titleEn: "First time in Busan",
    descKo: "누구나 아는 대표 명소 위주로", descEn: "Start with the landmarks everyone knows",
    mode: "ESSENTIAL", courseCategory: "", tasteTags: ["landmark", "photo", "culture"],
  },
  {
    id: "LIKE_LOCAL", emoji: "🥢",
    titleKo: "현지인처럼 먹고 걷기", titleEn: "Eat and walk like a local",
    descKo: "관광지보다 동네 골목과 로컬 맛집", descEn: "Neighborhood alleys over tourist stops",
    mode: "LOCAL", courseCategory: "OLD_TOWN", tasteTags: ["food", "hidden_local", "culture"],
  },
  {
    id: "PHOTO", emoji: "📸",
    titleKo: "사진이 남는 여행", titleEn: "A trip that photographs well",
    descKo: "풍경과 야경, 카페까지 그림 되는 곳", descEn: "Views, night scenes and photogenic cafes",
    mode: "LOCAL", courseCategory: "PICTURE_PERFECT", tasteTags: ["photo", "nightview", "cafe"],
  },
  {
    id: "SLOW", emoji: "🌿",
    titleKo: "조용히 쉬다 오기", titleEn: "Slow and quiet",
    descKo: "바다와 자연, 여유로운 하루", descEn: "Sea, nature and an unhurried day",
    mode: "LOCAL", courseCategory: "NATURE_FIX", tasteTags: ["nature", "cafe", "photo"],
  },
  {
    id: "VALUE", emoji: "💸",
    titleKo: "가성비로 알차게", titleEn: "Great value, packed day",
    descKo: "돈은 아끼고 볼 건 다 보고", descEn: "Spend less, see more",
    mode: "LOCAL", courseCategory: "BEST_BANG", tasteTags: ["food", "hidden_local", "shopping"],
  },
  {
    id: "EASY", emoji: "🫶",
    titleKo: "여유롭게 편안하게", titleEn: "Easy and comfortable",
    descKo: "이동이 적고 쉬어갈 곳이 많은", descEn: "Less walking, more places to rest",
    mode: "ESSENTIAL", courseCategory: "EASY_PACE", tasteTags: ["culture", "nature", "food"],
  },
];

export function findMood(id: string | null): TravelMood | null {
  return id ? TRAVEL_MOODS.find((mood) => mood.id === id) ?? null : null;
}

interface Props {
  /** 선택된 무드 id. 사용자가 세부 설정을 직접 건드렸다면 null 이 되어 "직접 설정"으로 표시된다. */
  value: string | null;
  onSelect: (mood: TravelMood) => void;
  /**
   * "직접 고를래요" 카드를 눌렀을 때. 세부 설정을 접힌 바로 두면 이것만 조작 방식이 달라
   * 흐름이 끊긴다. 같은 카드로 두어 "고르면 넘어간다"는 규칙을 하나로 유지한다.
   */
  onCustom?: () => void;
  /** 지금 직접 고르기 화면에 있는지 */
  customActive?: boolean;
  language?: "KO" | "EN";
}

export function TravelMoodPicker({ value, onSelect, onCustom, customActive, language = "KO" }: Props) {
  const isEn = language === "EN";
  return (
    <div className="mood-grid" role="radiogroup" aria-label={isEn ? "Travel mood" : "여행 무드"}>
      {TRAVEL_MOODS.map((mood) => {
        const selected = value === mood.id;
        return (
          <button
            type="button" key={mood.id} role="radio" aria-checked={selected}
            className={`mood-card ${selected ? "selected" : ""}`}
            onClick={() => onSelect(mood)}
          >
            <span className="mood-emoji" aria-hidden="true">{mood.emoji}</span>
            <div className="mood-info">
              <strong>{isEn ? mood.titleEn : mood.titleKo}</strong>
              <small>{isEn ? mood.descEn : mood.descKo}</small>
            </div>
            {selected && <span className="mood-check" aria-hidden="true">✓</span>}
          </button>
        );
      })}
      {onCustom && (
        <button
          type="button" role="radio" aria-checked={Boolean(customActive)}
          className={`mood-card mood-card-custom ${customActive ? "selected" : ""}`}
          onClick={onCustom}
        >
          <span className="mood-emoji" aria-hidden="true">🎛</span>
          <div className="mood-info">
            <strong>{isEn ? "I'll pick it myself" : "직접 고를래요"}</strong>
            <small>{isEn ? "Mode, course theme and tags" : "추천 모드 · 코스 · 취향 태그"}</small>
          </div>
        </button>
      )}
    </div>
  );
}
