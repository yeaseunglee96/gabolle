import { useState } from "react";
import { MapPanel } from "../../components/MapPanel";
import { useTrip } from "./TripContext";

/**
 * /trips/:tripId/map — 지도 전체보기
 * 목록 없이 지도만 확대해 전체 동선과 기념품샵 레이어를 함께 본다.
 */
export function TripMapPage() {
  const { itinerary, selectedPlaceId } = useTrip();
  const [mapDayIndex, setMapDayIndex] = useState<number | null>(null);

  return (
    <div className="service-view map-view">
      <header className="service-heading">
        <div>
          <span className="section-eyebrow">FULL MAP</span>
          <h1>전체 동선 보기</h1>
          <p>날짜별 경로와 기념품샵을 지도 위에서 한 번에 확인하세요.</p>
        </div>
      </header>
      <div className="map-full-canvas">
        <MapPanel
          days={itinerary.days}
          originLat={itinerary.trip.originLat}
          originLng={itinerary.trip.originLng}
          activeDayIndex={mapDayIndex}
          onActiveDayChange={setMapDayIndex}
          selectedPlaceId={selectedPlaceId}
          showSouvenirControl
        />
      </div>
    </div>
  );
}
