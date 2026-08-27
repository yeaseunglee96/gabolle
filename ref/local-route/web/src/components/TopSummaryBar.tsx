import type { ItineraryOutput, Pace } from "../types";

const PACE_LABEL: Record<Pace, string> = {
  RELAXED: "여유롭게",
  NORMAL: "균형 있게",
  PACKED: "알차게",
};

function formatHM(totalMinutes: number): string {
  const h = Math.floor(totalMinutes / 60);
  const m = totalMinutes % 60;
  return h > 0 ? `${h}시간 ${m}분` : `${m}분`;
}

interface Props {
  itinerary: ItineraryOutput;
  onEdit: () => void;
  onRegenerate: () => void;
  regenerating: boolean;
  canEdit?: boolean;
}

export function TopSummaryBar({ itinerary, onEdit, onRegenerate, regenerating, canEdit = true }: Props) {
  const { trip, days } = itinerary;
  const en = trip.language === "EN";
  const allItems = days.flatMap((d) => d.items);

  const numDays = days.length;
  const nights = Math.max(0, numDays - 1);
  const title = en ? `${nights}-night, ${numDays}-day Busan trip` : `${nights}박${numDays}일 부산 여행`;

  const totalMinutes = allItems.reduce(
    (sum, it) => sum + it.stayMinutes + (it.travelMinToNext ?? 0),
    0
  );
  const totalCost = days.reduce((sum, d) => sum + d.totalEstCost, 0);
  const perPersonCost = Math.round(totalCost / Math.max(1, trip.partySize));
  const avgLocalScore =
    allItems.length > 0 ? allItems.reduce((s, it) => s + it.localScore, 0) / allItems.length : 0;
  const totalDistanceM = allItems.reduce((s, it) => s + (it.distanceToNextM ?? 0), 0);
  return (
    <div className="top-summary-bar">
      <div className="top-summary-title-row">
        <div>
          <h1 className="trip-title">
            {title}
            {canEdit && <button type="button" className="edit-btn" onClick={onEdit} title={en ? "Edit trip settings" : "여행 조건 수정"} aria-label={en ? "Edit trip settings" : "여행 조건 수정"}>
              {en ? "Edit" : "수정"}
            </button>}
            {canEdit && <button type="button" className="edit-btn" onClick={onRegenerate} disabled={regenerating} title={en ? "Re-optimize with the same settings" : "동일 조건으로 재최적화"} aria-label={en ? "Regenerate itinerary" : "일정 재생성"}>
              {regenerating ? (en ? "Regenerating..." : "재생성 중...") : (en ? "Regenerate" : "일정 재생성")}
            </button>}
          </h1>
          <div className="trip-tags">
            <span className="tag-chip-sm">
              {en ? `${trip.partySize} traveler(s)` : `${trip.partySize}인`}
            </span>
            <span className="tag-chip-sm">{en ? (trip.hasCar ? "Car" : "Public transit") : (trip.hasCar ? "자차 이용" : "대중교통 이용")}</span>
            <span className="tag-chip-sm">{en ? ({ RELAXED: "Relaxed", NORMAL: "Normal", PACKED: "Packed" }[trip.pace]) : PACE_LABEL[trip.pace]}</span>
          </div>
        </div>
        {!canEdit && <span className="viewer-badge">{en ? "View only" : "열람 전용"}</span>}
      </div>

      <div className="stat-pills">
        <div className="stat-pill">
          <div>
            <div className="stat-pill-label">{en ? "Total duration" : "총 소요시간"}</div>
            <div className="stat-pill-value">{en ? `${Math.floor(totalMinutes / 60)}h ${totalMinutes % 60}m` : formatHM(totalMinutes)}</div>
          </div>
        </div>
        <div className="stat-pill">
          <div>
            <div className="stat-pill-label">{en ? "Estimated cost" : "예상 경비"}</div>
            <div className="stat-pill-value">{en ? `₩${perPersonCost.toLocaleString()} per traveler` : `1인 ${perPersonCost.toLocaleString()}원`}</div>
          </div>
        </div>
        <div className="stat-pill">
          <div>
            <div className="stat-pill-label">{en ? "Local score average" : "현지인 만족도"}</div>
            <div className="stat-pill-value">{(avgLocalScore * 5).toFixed(1)}/5.0</div>
          </div>
        </div>
        <div className="stat-pill">
          <div>
            <div className="stat-pill-label">{en ? "Travel distance" : "이동 거리"}</div>
            <div className="stat-pill-value">{(totalDistanceM / 1000).toFixed(1)} km</div>
          </div>
        </div>
      </div>
    </div>
  );
}
