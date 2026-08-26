import { useState } from "react";

export interface DesiredFoodItem {
  id: string;
  nameKo: string;
  nameEn: string;
  emoji: string;
  spicyLevel: string;
  descriptionKo: string;
  descriptionEn: string;
}

export const KOREAN_FOOD_PRESETS: DesiredFoodItem[] = [
  { id: "milmyeon", nameKo: "밀면", nameEn: "Milmyeon (Wheat Noodles)", emoji: "🍜", spicyLevel: "🌶️ 보통", descriptionKo: "부산 대표 시원한 육수 밀면", descriptionEn: "Busan classic cold wheat noodles in chilled broth" },
  { id: "dwaeji_gukbap", nameKo: "돼지국밥", nameEn: "Dwaeji-gukbap (Pork Soup)", emoji: "🍲", spicyLevel: "순한맛", descriptionKo: "구수하고 진한 부산 돼지국밥", descriptionEn: "Hearty and rich pork rice soup, a Busan staple" },
  { id: "ssiat_hotteok", nameKo: "씨앗호떡", nameEn: "Ssiat Hotteok (Seed Pancake)", emoji: "🥞", spicyLevel: "달콤함", descriptionKo: "견과류가 듬뿍 든 남포동 씨앗호떡", descriptionEn: "Sweet fried pancake stuffed with brown sugar and seeds" },
  { id: "seafood", nameKo: "자갈치 회/해산물", nameEn: "Fresh Seafood & Sashimi", emoji: "🐟", spicyLevel: "담백함", descriptionKo: "신선한 바다 해산물 및 회", descriptionEn: "Freshly caught sea fish and raw sashimi at Jagalchi" },
  { id: "samgyeopsal", nameKo: "K-바비큐 삼겹살", nameEn: "Samgyeopsal (K-BBQ)", emoji: "🥓", spicyLevel: "담백함", descriptionKo: "숯불에 구워 먹는 직화 삼겹살", descriptionEn: "Grilled pork belly with garlic, ssamjang and lettuce wrap" },
  { id: "pajeon", nameKo: "동래파전", nameEn: "Dongnae Pajeon (Scallion Pancake)", emoji: "🍕", spicyLevel: "담백함", descriptionKo: "해물과 쪽파가 가득한 전통 파전", descriptionEn: "Traditional savory pancake with green onions and seafood" },
  { id: "tteokbokki", nameKo: "떡볶이·어묵", nameEn: "Tteokbokki & Fishcake", emoji: "🍢", spicyLevel: "🌶️🌶️ 매콤함", descriptionKo: "부산 특유의 물떡과 매콤한 떡볶이", descriptionEn: "Spicy rice cakes and rice-cake skewers in hot broth" },
  { id: "galbi", nameKo: "양념갈비", nameEn: "Galbi (Marinated Ribs)", emoji: "🍖", spicyLevel: "달콤 짭조름", descriptionKo: "부드럽고 숯불향 가득한 갈비", descriptionEn: "Tender grilled marinated beef/pork ribs" },
];

interface Props {
  selectedFoods: string[];
  onChange: (foods: string[]) => void;
  language?: "KO" | "EN";
}

export function DesiredFoodPicker({ selectedFoods, onChange, language = "KO" }: Props) {
  const isEn = language === "EN";

  const toggleFood = (id: string) => {
    if (selectedFoods.includes(id)) {
      onChange(selectedFoods.filter((f) => f !== id));
    } else {
      onChange([...selectedFoods, id]);
    }
  };

  return (
    <div className="desired-food-picker">
      <div className="picker-heading">
        <span className="picker-eyebrow">FOOD EXPERIENCE</span>
        <h4>{isEn ? "Must-Try Korean Dishes in Busan" : "한국에서 꼭 먹어보고 싶은 음식"}</h4>
        <p className="picker-desc">
          {isEn
            ? "Select dishes you want to eat. We will automatically place verified restaurants into your schedule!"
            : "먹고 싶은 음식을 선택하면 해당 음식을 판매하는 검증된 식당을 일정에 자동 반영합니다."}
        </p>
      </div>

      <div className="food-grid">
        {KOREAN_FOOD_PRESETS.map((food) => {
          const active = selectedFoods.includes(food.id);
          return (
            <button
              key={food.id}
              type="button"
              className={`food-chip ${active ? "active" : ""}`}
              onClick={() => toggleFood(food.id)}
            >
              <span className="food-emoji">{food.emoji}</span>
              <div className="food-info">
                <strong>{isEn ? `${food.nameKo} (${food.nameEn})` : food.nameKo}</strong>
                <small>{isEn ? food.descriptionEn : food.descriptionKo}</small>
              </div>
              {active && <span className="food-check">✓</span>}
            </button>
          );
        })}
      </div>
    </div>
  );
}
