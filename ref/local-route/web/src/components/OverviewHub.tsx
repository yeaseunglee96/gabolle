import { useEffect, useState } from "react";
import { getWeather } from "../api/client";
import type { ItineraryOutput, WeatherForecast } from "../types";
import type { DashboardTab } from "./Sidebar";

const TAB_CARDS: { id: Exclude<DashboardTab, "home">; number: string; title: string; titleEn: string; description: string; descriptionEn: string }[] = [
  { id: "schedule", number: "01", title: "일정과 동선", titleEn: "Itinerary & Route", description: "지도와 날짜별 일정을 함께 확인하고 장소를 교체합니다.", descriptionEn: "Explore map and daily schedule, and swap locations easily." },
  { id: "discover", number: "02", title: "부산 로컬", titleEn: "Busan Local", description: "축제, 야시장, 전통시장과 기념품샵을 여행 기간에 맞춰 확인합니다.", descriptionEn: "Discover festivals, night markets, traditional markets, and souvenir shops." },
  { id: "together", number: "03", title: "동행과 기록", titleEn: "Share & Moments", description: "일정을 공유하고 여행 중 발견한 장면을 기록합니다.", descriptionEn: "Share your trip schedule and record travel memories together." },
  { id: "prep", number: "04", title: "여행 준비", titleEn: "Travel Prep", description: "예산, 편의정보와 꼭 필요한 예약 서비스를 확인합니다.", descriptionEn: "Check budget, travel tips, and essential booking options." },
];

export function OverviewHub({ itinerary, onNavigate }: { itinerary: ItineraryOutput; onNavigate: (tab: DashboardTab) => void }) {
  const [weather, setWeather] = useState<Record<string, WeatherForecast>>({});
  useEffect(() => { Promise.all(itinerary.days.map((day) => getWeather(day.visitDate))).then((forecasts) => setWeather(Object.fromEntries(forecasts.map((forecast) => [forecast.date, forecast])))).catch(() => setWeather({})); }, [itinerary.days]);
  const en = itinerary.trip.language === "EN";
  const firstDay = itinerary.days[0];
  const nextStop = firstDay?.items[0];
  const nights = Math.max(0, itinerary.days.length - 1);
  const nextStopTitle = nextStop ? (en && nextStop.nameEn ? `${nextStop.nameKo} (${nextStop.nameEn})` : nextStop.nameKo) : (en ? "Preparing your first stop" : "첫 장소를 준비 중이에요");

  return <div className="overview-hub">
    <section className="home-welcome-card">
      <div className="home-welcome-copy">
        <span className="section-eyebrow">MY LOCAL ROUTE</span>
        <h2>{en ? "Travel Light,\nDiscover Busan Deeply." : "여행 준비는 가볍게,\n부산은 더 깊게."}</h2>
        <p>{en ? `Follow the local vibe for ${nights} nights and ${itinerary.days.length} days from ${itinerary.trip.startDate}.` : `${itinerary.trip.startDate}부터 ${nights}박 ${itinerary.days.length}일 동안 현지의 결을 따라가요.`}</p>
        <button type="button" onClick={() => onNavigate("schedule")}>{en ? "Open My Schedule" : "내 일정 열기"} <span>→</span></button>
      </div>
      <div className="next-stop-card">
        <span>{en ? "First Stop Scene" : "여행의 첫 장면"}</span>
        {nextStop?.imageUrl ? <img src={nextStop.imageUrl} alt={`${nextStop.nameKo} view`} /> : <div className="next-stop-art" aria-hidden="true" />}
        <div>
          <small>DAY {firstDay?.dayIndex ?? 1} · {nextStop?.plannedArrival ?? "09:30"}</small>
          <strong>{nextStopTitle}</strong>
          <p>{en ? nextStop?.addressEn ?? nextStop?.address ?? "Check your route on the schedule." : nextStop?.address ?? "일정을 열어 여행 동선을 확인하세요."}</p>
        </div>
      </div>
    </section>

    <section className="home-section" aria-labelledby="home-days-title">
      <div className="home-section-heading">
        <div><span className="section-eyebrow">YOUR JOURNEY</span><h2 id="home-days-title">{en ? "Journey Overview" : "한눈에 보는 여행"}</h2></div>
        <button type="button" onClick={() => onNavigate("schedule")}>{en ? "View Full Schedule" : "전체 일정 보기"}</button>
      </div>
      <div className="home-day-strip">
        {itinerary.days.map((day) => {
          const forecast = weather[day.visitDate];
          const dateStr = new Intl.DateTimeFormat(en ? "en-US" : "ko-KR", { month: "short", day: "numeric", weekday: "short" }).format(new Date(`${day.visitDate}T00:00:00`));
          const weatherSky = forecast ? (en ? ({ RAIN: "Rain", CLOUDY: "Cloudy", SUNNY: "Sunny", CLEAR: "Sunny" }[forecast.sky] ?? forecast.sky) : (forecast.sky === "RAIN" ? "비" : forecast.sky === "CLOUDY" ? "흐림" : "맑음")) : "";
          return <button type="button" key={day.dayIndex} onClick={() => onNavigate("schedule")}>
            <span>DAY {day.dayIndex}</span>
            <b>{dateStr}</b>
            {forecast && <span className="day-weather"><i>{weatherSky}</i>{forecast.tempMin}~{forecast.tempMax}℃ · {en ? "Rain" : "강수"} {forecast.rainProbability}%</span>}
            <div>
              {day.items.slice(0, 3).map((item) => {
                const itemTitle = en && item.nameEn ? `${item.nameKo} (${item.nameEn})` : item.nameKo;
                return <small key={item.placeId}>{item.plannedArrival} · {item.placeId ? itemTitle : item.nameKo}</small>;
              })}
            </div>
            <em>{en ? `${day.items.length} stops · ₩${day.totalEstCost.toLocaleString()}` : `${day.items.length}곳 · ${day.totalEstCost.toLocaleString()}원`}</em>
          </button>;
        })}
      </div>
    </section>

    <section className="home-section" aria-labelledby="home-services-title">
      <div className="home-section-heading"><div><span className="section-eyebrow">TRAVEL, SIMPLIFIED</span><h2 id="home-services-title">{en ? "What to do next?" : "무엇을 할까요?"}</h2></div></div>
      <div className="home-action-grid">
        {TAB_CARDS.map((card) => <button type="button" key={card.id} onClick={() => onNavigate(card.id)}>
          <span className="home-action-number">{card.number}</span>
          <strong>{en ? card.titleEn : card.title}</strong>
          <small>{en ? card.descriptionEn : card.description}</small>
          <em>{en ? "Open" : "열기"}</em>
        </button>)}
      </div>
    </section>
  </div>;
}
