import { useState } from "react";

interface WeatherDayInfo {
  dayIndex: number;
  date: string;
  condition: "SUNNY" | "RAINY" | "CLOUDY" | "HOT";
  tempHigh: number;
  tempLow: number;
  rainChance: number;
}

interface Props {
  language?: "KO" | "EN";
  onReplaceRainyIndoor?: () => void;
}

export function WeatherPrepWidget({ language = "KO", onReplaceRainyIndoor }: Props) {
  const isEn = language === "EN";
  const [replaced, setReplaced] = useState(false);

  const mockWeather: WeatherDayInfo[] = [
    { dayIndex: 1, date: "Day 1", condition: "SUNNY", tempHigh: 28, tempLow: 20, rainChance: 10 },
    { dayIndex: 2, date: "Day 2", condition: "RAINY", tempHigh: 24, tempLow: 19, rainChance: 80 },
    { dayIndex: 3, date: "Day 3", condition: "CLOUDY", tempHigh: 26, tempLow: 21, rainChance: 20 },
  ];

  const hasRain = mockWeather.some((w) => w.condition === "RAINY" || w.rainChance >= 60);

  const handleReplaceClick = () => {
    setReplaced(true);
    if (onReplaceRainyIndoor) onReplaceRainyIndoor();
  };

  return (
    <div className="weather-prep-widget">
      <div className="widget-header">
        <span className="widget-tag">WEATHER & PACKING GUIDE</span>
        <h4>{isEn ? "Busan Weather & Packing Tips" : "날짜별 날씨 & 여행 준비물 안내"}</h4>
      </div>

      <div className="weather-days-row">
        {mockWeather.map((w) => (
          <div key={w.dayIndex} className={`weather-day-card ${w.condition === "RAINY" ? "rainy" : ""}`}>
            <span className="day-name">{w.date}</span>
            <div className="weather-icon">
              {w.condition === "SUNNY" && "☀️"}
              {w.condition === "RAINY" && "🌧️"}
              {w.condition === "CLOUDY" && "⛅"}
            </div>
            <div className="temps">
              <strong>{w.tempHigh}°C</strong> / <small>{w.tempLow}°C</small>
            </div>
            <span className="rain-chance">{isEn ? `Rain ${w.rainChance}%` : `강수확률 ${w.rainChance}%`}</span>
          </div>
        ))}
      </div>

      {hasRain && (
        <div className="rain-warning-box">
          <div className="warning-text">
            <strong>⚠️ {isEn ? "Rain Expected on Day 2!" : "2일 차에 비 예보가 있습니다!"}</strong>
            <p>
              {isEn
                ? "Recommended items: Compact umbrella, waterproof shoes, light jacket. Would you like to switch outdoor spots to indoor attractions?"
                : "추천 준비물: 접이식 우산, 방수 신발, 얇은 겉옷. 야외 관광지를 쾌적한 실내 명소(미술관/카페)로 바꿀까요?"}
            </p>
          </div>
          <button
            type="button"
            className={`indoor-switch-btn ${replaced ? "done" : ""}`}
            onClick={handleReplaceClick}
            disabled={replaced}
          >
            {replaced
              ? isEn
                ? "✓ Switched to Indoor Spots"
                : "✓ 실내 장소로 변경 완료"
              : isEn
              ? "🌧️ Switch to Indoor Spots"
              : "🌧️ 실내 장소로 자동 교체"}
          </button>
        </div>
      )}
    </div>
  );
}
