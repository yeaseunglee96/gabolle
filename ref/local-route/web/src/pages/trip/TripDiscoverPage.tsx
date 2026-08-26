import { ExperiencePanel } from "../../components/ExperiencePanel";
import { useTrip } from "./TripContext";

/** /trips/:tripId/discover — 지역 축제·행사와 기념품샵 */
export function TripDiscoverPage() {
  const { itinerary, canEdit } = useTrip();

  return (
    <div className="service-view">
      <header className="service-heading">
        <div>
          <span className="section-eyebrow">LOCAL BUSAN</span>
          <h1>부산의 로컬을 만나요</h1>
          <p>여행 기간에 열리는 축제와 야시장, 즐길 거리와 가까운 기념품샵을 확인하세요.</p>
        </div>
      </header>
      <ExperiencePanel itinerary={itinerary} canEdit={canEdit} />
    </div>
  );
}
