import { SocialPanel } from "../../components/SocialPanel";
import { useTrip } from "./TripContext";

/** /trips/:tripId/together — 스토리 피드와 팔로우 */
export function TripTogetherPage() {
  const { itinerary } = useTrip();

  return (
    <div className="service-view">
      <header className="service-heading">
        <div>
          <span className="section-eyebrow">TRAVEL TOGETHER</span>
          <h1>함께 여행하고 기록해요</h1>
          <p>여행자들의 순간을 둘러보고, 내가 팔로우한 여행 기록에 집중해보세요.</p>
        </div>
      </header>
      <SocialPanel itinerary={itinerary} />
    </div>
  );
}
