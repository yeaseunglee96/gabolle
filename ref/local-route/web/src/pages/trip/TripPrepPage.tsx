import { InfoCards } from "../../components/InfoCards";
import { RegionalTipCard } from "../../components/RegionalTipCard";
import { ShareBar } from "../../components/ShareBar";
import { useTrip } from "./TripContext";

/** /trips/:tripId/prep — 예산·여행 팁·필수 서비스 광고·예약 제휴·공유 */
export function TripPrepPage() {
  const { itinerary, itemProps } = useTrip();
  const lang = itemProps.language;

  return (
    <div className="service-view prep-view">
      <header className="service-heading">
        <div>
          <span className="section-eyebrow">READY TO GO</span>
          <h1>{lang === "EN" ? "Prepare the trip together" : "함께 준비하는 부산 여행"}</h1>
          <p>{lang === "EN" ? "Invite companions, review the budget, and check only the tips needed for this trip." : "동행자를 초대하고 예상 경비와 이번 여행에 필요한 팁만 확인하세요."}</p>
        </div>
      </header>

      <InfoCards
        itinerary={itinerary}
        collaboration={<ShareBar itineraryId={itinerary.itineraryId} tripId={itinerary.tripId} language={itinerary.trip.language} />}
      />
      <RegionalTipCard language={lang} />

      <div className="data-honesty-bar">
        <strong>데이터 안내</strong>
        <span>식당은 승인된 카카오 평점·후기 집계가 있을 때만 추천 점수에 반영합니다.</span>
        <span>비용과 실시간 교통 미연동 구간은 추정값으로 구분합니다.</span>
      </div>
    </div>
  );
}
