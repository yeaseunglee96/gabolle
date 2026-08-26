import { useNavigate } from "react-router-dom";
import { FilterRow } from "../../components/FilterRow";
import { OverviewHub } from "../../components/OverviewHub";
import { TopSummaryBar } from "../../components/TopSummaryBar";
import { TAB_TO_PATH } from "../../routes/paths";
import { useTrip } from "./TripContext";

/** /trips/:tripId/overview — 여행 요약 홈 */
export function TripOverviewPage() {
  const { tripId, itinerary, regenerating, canEdit, regenerate, editConditions } = useTrip();
  const navigate = useNavigate();

  return (
    <div className="service-view home-view">
      <TopSummaryBar itinerary={itinerary} onEdit={editConditions} onRegenerate={() => void regenerate()} regenerating={regenerating} canEdit={canEdit} />
      <OverviewHub itinerary={itinerary} onNavigate={(tab) => navigate(TAB_TO_PATH[tab](tripId))} />
      <FilterRow trip={itinerary.trip} />
    </div>
  );
}
