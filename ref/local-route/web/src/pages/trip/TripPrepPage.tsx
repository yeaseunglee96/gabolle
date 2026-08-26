import { useEffect, useState } from "react";
import { InfoCards } from "../../components/InfoCards";
import { RegionalTipCard } from "../../components/RegionalTipCard";
import { ShareBar } from "../../components/ShareBar";
import { WeatherPrepWidget } from "../../components/WeatherPrepWidget";
import { TripMemorySummary } from "../../components/TripMemorySummary";
import { BusanDialectWidget } from "../../components/BusanDialectWidget";
import { getBookingOptions, getSponsoredPlacements, startBooking, trackAd } from "../../api/client";
import type { BookingOption, SponsoredPlacement } from "../../types";
import { useTrip } from "./TripContext";

/** /trips/:tripId/prep — 예산·여행 팁·필수 서비스 광고·예약 제휴·공유 */
export function TripPrepPage() {
  const { itinerary, itemProps } = useTrip();
  const [sponsored, setSponsored] = useState<SponsoredPlacement[]>([]);
  const [bookingOptions, setBookingOptions] = useState<BookingOption[]>([]);
  const lang = itemProps.language;

  useEffect(() => {
    getSponsoredPlacements({ mode: itinerary.mode, language: lang })
      .then((placements) => {
        setSponsored(placements);
        placements.forEach((placement) => void trackAd(placement.campaignId, "impressions"));
      })
      .catch(() => setSponsored([]));
  }, [itinerary.mode, lang]);

  useEffect(() => {
    if (!itinerary.trip.lodgingPlaceId) { setBookingOptions([]); return; }
    getBookingOptions(itinerary.trip.lodgingPlaceId).then(setBookingOptions).catch(() => setBookingOptions([]));
  }, [itinerary.trip.lodgingPlaceId]);

  return (
    <div className="service-view prep-view">
      <header className="service-heading">
        <div>
          <span className="section-eyebrow">READY TO GO</span>
          <h1>{lang === "EN" ? "Travel Prep & Local Expressions" : "여행 준비 & 사투리 표현"}</h1>
          <p>{lang === "EN" ? "Check budget, travel tips, and master local Busan dialect expressions!" : "예산과 지역 여행 팁을 확인하고, 부산 사투리를 함께 익혀보세요."}</p>
        </div>
      </header>

      {sponsored.length > 0 && (
        <section className="sponsored-strip" aria-label="광고">
          <strong className="sponsored-strip-title">여행 조건에 맞는 필수 서비스</strong>
          <div className="sponsored-strip-cards">
            {sponsored.map((placement) => (
              <article key={placement.campaignId}>
                <span className="sponsored-label">{placement.label}</span>
                <b>{itinerary.trip.language === "EN" ? placement.nameEn ?? placement.nameKo : placement.nameKo}</b>
                <small>{placement.disclosure}</small>
                <button type="button" onClick={() => void trackAd(placement.campaignId, "clicks")}>서비스 확인</button>
              </article>
            ))}
          </div>
        </section>
      )}

      <RegionalTipCard language={lang} />

      <InfoCards
        itinerary={itinerary}
        collaboration={<ShareBar itineraryId={itinerary.itineraryId} tripId={itinerary.tripId} language={itinerary.trip.language} />}
      />

      <TripMemorySummary language={lang} daysCount={itinerary.days.length} />

      {bookingOptions.length > 0 && (
        <section className="booking-strip" aria-label="숙소 예약 제휴">
          <div>
            <strong>선택한 숙소 예약 확인</strong>
            <span>외부 제휴사에서 가격과 객실 조건을 최종 확인하세요.</span>
          </div>
          {bookingOptions.map((option) => (
            <button type="button" key={option.id} onClick={async () => {
              const booking = await startBooking(option.id, itinerary.tripId);
              window.open(booking.bookingUrl, "_blank", "noopener,noreferrer");
            }}>{option.provider}에서 예약 확인 <small>제휴 링크</small></button>
          ))}
        </section>
      )}

      <BusanDialectWidget language={lang} />
      <WeatherPrepWidget language={lang} />

      <div className="data-honesty-bar">
        <strong>데이터 안내</strong>
        <span>식당은 승인된 카카오 평점·후기 집계가 있을 때만 추천 점수에 반영합니다.</span>
        <span>비용과 실시간 교통 미연동 구간은 추정값으로 구분합니다.</span>
      </div>
    </div>
  );
}
