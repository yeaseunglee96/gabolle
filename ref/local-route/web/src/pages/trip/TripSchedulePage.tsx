import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { DayTimeline } from "../../components/DayTimeline";
import { MapPanel } from "../../components/MapPanel";
import { ScheduleSummaryList } from "../../components/ScheduleSummaryList";
import { PaceForecastBar } from "../../components/PaceForecastBar";
import { PaceTracker } from "../../components/PaceTracker";
import { ReplanModal } from "../../components/ReplanModal";
import { RhythmBriefing } from "../../components/RhythmBriefing";
import { usePaceLearning } from "../../hooks/usePaceLearning";
import { paths } from "../../routes/paths";
import { useTrip } from "./TripContext";

type ListMode = "summary" | "detail";

/**
 * /trips/:tripId/schedule          — 전체 일정
 * /trips/:tripId/schedule/:dayIndex — 특정 날짜 (딥링크 대상)
 */
export function TripSchedulePage() {
  const { dayIndex: dayIndexParam } = useParams();
  const { tripId, itinerary, regenerating, canEdit, pinnedPlaceIds, selectedPlaceId, itemProps, regenerate, editConditions, undo, reloadItinerary } = useTrip();

  const deepLinkedDay = dayIndexParam ? Number(dayIndexParam) : null;
  const [listMode, setListMode] = useState<ListMode>(deepLinkedDay ? "detail" : "summary");
  const [activeDayIndex, setActiveDayIndex] = useState(deepLinkedDay ?? itinerary.days[0]?.dayIndex ?? 1);
  const [mapDayIndex, setMapDayIndex] = useState<number | null>(deepLinkedDay);

  const activeDay = itinerary.days.find((day) => day.dayIndex === activeDayIndex) ?? null;
  const pace = usePaceLearning({
    itineraryId: itinerary.itineraryId,
    tripId,
    day: activeDay,
    canEdit,
    onItineraryChanged: reloadItinerary,
  });

  return (
    <div className="service-view">
      <header className="service-heading">
        <div>
          <span className="section-eyebrow">ROUTE PLANNER</span>
          <h1>동선 보며 일정 짜기</h1>
          <p>날짜별 경로를 보고 장소를 고정하거나 교체할 수 있어요.</p>
        </div>
        <div className="service-heading-actions">
          {canEdit && <button type="button" className="secondary-btn" onClick={editConditions}>여행 조건 수정</button>}
          <button type="button" className="primary-btn" onClick={() => void regenerate()} disabled={regenerating || !canEdit}>{regenerating ? "계산 중…" : "일정 재생성"}</button>
        </div>
      </header>

      {/* 페이스 러닝 - 오늘 일정이거나 실측이 시작된 날에만 나타난다. */}
      {pace.active && pace.forecast && activeDay && (
        <section className="pace-panel" aria-label="오늘의 페이스">
          <PaceForecastBar
            forecast={pace.forecast}
            language={itemProps.language}
            onReplan={canEdit ? pace.openReplan : undefined}
            replanning={pace.busy}
          />
          {canEdit && (
            <PaceTracker
              day={activeDay}
              forecast={pace.forecast}
              language={itemProps.language}
              busy={pace.busy}
              onArrive={(itemId) => void pace.markArrived(itemId)}
              onDepart={(itemId) => void pace.markDeparted(itemId)}
            />
          )}
          {pace.error && <div className="error-box" role="alert"><span>{pace.error}</span></div>}
        </section>
      )}

      {pace.showRhythm && pace.rhythm && (
        <RhythmBriefing
          profile={pace.rhythm}
          language={itemProps.language}
          onApply={canEdit ? pace.openReplan : undefined}
          applying={pace.busy}
          onDismiss={pace.dismissRhythm}
        />
      )}

      {pace.replanOpen && pace.forecast && (
        <ReplanModal
          forecast={pace.forecast}
          language={itemProps.language}
          busy={pace.busy}
          errorMessage={pace.error}
          onConfirm={(strategy) => void pace.applyReplan(strategy)}
          onClose={pace.closeReplan}
        />
      )}

      {itinerary.warnings.length > 0 && (
        <details className="warnings">
          <summary>일정 검증 참고사항 {itinerary.warnings.length}건</summary>
          <ul>{itinerary.warnings.map((warning, index) => <li key={index}>{warning}</li>)}</ul>
        </details>
      )}

      {canEdit && (
        <div className="undo-row">
          <button type="button" className="secondary-btn" disabled={regenerating} onClick={() => void undo()}>최근 변경 취소</button>
        </div>
      )}

      <div className="dashboard-columns schedule-workspace">
        <section className={`dashboard-left schedule-list-canvas ${listMode === "summary" ? "all-days" : "single-day"}`} aria-label="여행 일정">
          <div className="schedule-toolbar">
            <div className="tab-switch" role="tablist">
              <button type="button" role="tab" aria-selected={listMode === "summary"} className={`tab-btn ${listMode === "summary" ? "active" : ""}`} onClick={() => setListMode("summary")}>전체 일정</button>
              <button type="button" role="tab" aria-selected={listMode === "detail"} className={`tab-btn ${listMode === "detail" ? "active" : ""}`} onClick={() => setListMode("detail")}>날짜별 보기</button>
            </div>
            <span>{pinnedPlaceIds.length ? `고정 장소 ${pinnedPlaceIds.length}곳` : "장소를 고정하거나 제외해 다시 계산할 수 있어요"}</span>
            <Link className="story-launch-btn" to={paths.tripTogether(itinerary.tripId)}>＋ 사진·기록 남기기</Link>
            <Link className="nav-launch-btn" to={paths.tripScheduleNavigate(itinerary.tripId, activeDayIndex)}>네비게이션</Link>
          </div>
          {listMode === "summary"
            ? <ScheduleSummaryList days={itinerary.days} {...itemProps} />
            : <DayTimeline days={itinerary.days} activeDayIndex={activeDayIndex} onSelectDay={setActiveDayIndex} {...itemProps} />}
        </section>
        <aside className="dashboard-right">
          <MapPanel
            days={itinerary.days}
            originLat={itinerary.trip.originLat}
            originLng={itinerary.trip.originLng}
            activeDayIndex={mapDayIndex}
            onActiveDayChange={setMapDayIndex}
            selectedPlaceId={selectedPlaceId}
            showSouvenirControl={false}
          />
        </aside>
      </div>
    </div>
  );
}
