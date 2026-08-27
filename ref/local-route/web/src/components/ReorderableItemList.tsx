import { useState } from "react";
import type { ItineraryDayOutput, ItineraryItemOutput } from "../types";
import { ItemCard, TravelSegment } from "./ItemCard";

interface Props {
  day: ItineraryDayOutput;
  hasCar: boolean;
  pinnedPlaceIds: string[];
  busy: boolean;
  language: "KO" | "EN";
  onTogglePin: (item: ItineraryItemOutput) => void;
  onExclude: (item: ItineraryItemOutput) => void;
  onReplace: (item: ItineraryItemOutput) => void;
  onSelect: (item: ItineraryItemOutput) => void;
  onReorder?: (dayIndex: number, itemIds: string[]) => Promise<void>;
}

/**
 * 하루치 방문지 목록 + 드래그(또는 ▲▼ 버튼)로 순서를 바꾸는 로직.
 * DayTimeline(날짜별 보기)과 ScheduleSummaryList(전체 일정 보기) 둘 다 같은 날짜 단위 리스트를
 * 쓰므로 여기 하나로 합쳤다 - 전에는 DayTimeline에만 있어서, 기본 화면인 "전체 일정" 탭에서는
 * 드래그가 아예 동작하지 않았다.
 */
export function ReorderableItemList({ day, hasCar, pinnedPlaceIds, busy, language, onTogglePin, onExclude, onReplace, onSelect, onReorder }: Props) {
  const en = language === "EN";
  const [draggedId, setDraggedId] = useState<string | null>(null);
  const [dragOverId, setDragOverId] = useState<string | null>(null);
  const canReorder = !!onReorder && !busy;

  const move = (itemId: string, direction: -1 | 1) => {
    if (!canReorder) return;
    const ids = day.items.map((item) => item.itemId!);
    const index = ids.indexOf(itemId);
    const target = index + direction;
    if (target < 0 || target >= ids.length) return;
    [ids[index], ids[target]] = [ids[target], ids[index]];
    void onReorder!(day.dayIndex, ids);
  };

  const drop = (overItemId: string) => {
    setDragOverId(null);
    if (!canReorder || !draggedId || draggedId === overItemId) { setDraggedId(null); return; }
    const ids = day.items.map((item) => item.itemId!);
    const from = ids.indexOf(draggedId);
    const to = ids.indexOf(overItemId);
    setDraggedId(null);
    if (from === -1 || to === -1) return;
    ids.splice(to, 0, ids.splice(from, 1)[0]);
    void onReorder!(day.dayIndex, ids);
  };

  return (
    <div className="summary-item-list">
      {day.startTravelMin !== null && <div className="base-travel-note">{en ? `Base to first stop ${day.startTravelMin} min · ${(day.startDistanceM! / 1000).toFixed(1)}km${day.startTravelIsEstimate ? " (estimated)" : ""}` : `출발 기준점에서 첫 장소까지 ${day.startTravelMin}분 · ${(day.startDistanceM! / 1000).toFixed(1)}km${day.startTravelIsEstimate ? " (추정)" : ""}`}</div>}
      {day.items.map((item, index) => {
        const itemId = item.itemId ?? `${item.placeId}-${item.seqOrder}`;
        return (
          <div key={item.placeId + item.seqOrder}>
            <div
              className={`schedule-item-row ${draggedId === itemId ? "dragging" : ""} ${dragOverId === itemId ? "drop-target" : ""}`}
              draggable={canReorder}
              onDragStart={() => setDraggedId(itemId)}
              onDragOver={(event) => { if (canReorder) { event.preventDefault(); setDragOverId(itemId); } }}
              onDragLeave={() => setDragOverId((current) => current === itemId ? null : current)}
              onDrop={(event) => { event.preventDefault(); drop(itemId); }}
              onDragEnd={() => { setDraggedId(null); setDragOverId(null); }}
            >
              {onReorder && (
                <div className="reorder-controls">
                  <button type="button" className="reorder-handle" disabled={!canReorder} aria-label={en ? "Drag to reorder" : "드래그해서 순서 변경"} title={en ? "Drag to reorder" : "드래그해서 순서 변경"}>⠿</button>
                  <button type="button" className="reorder-move-btn" disabled={!canReorder || index === 0} onClick={() => move(itemId, -1)} aria-label={en ? "Move up" : "위로 이동"}>▲</button>
                  <button type="button" className="reorder-move-btn" disabled={!canReorder || index === day.items.length - 1} onClick={() => move(itemId, 1)} aria-label={en ? "Move down" : "아래로 이동"}>▼</button>
                </div>
              )}
              <ItemCard item={item} language={language} pinned={pinnedPlaceIds.includes(item.placeId)} busy={busy} onTogglePin={onTogglePin} onExclude={onExclude} onReplace={onReplace} onSelect={onSelect} />
            </div>
            <TravelSegment item={item} hasCar={hasCar} language={language} />
          </div>
        );
      })}
      {day.returnTravelMin !== null && <div className="base-travel-note">{en ? `Last stop to base ${day.returnTravelMin} min · ${(day.returnDistanceM! / 1000).toFixed(1)}km${day.returnTravelIsEstimate ? " (estimated)" : ""}` : `마지막 장소에서 기준점 복귀 ${day.returnTravelMin}분 · ${(day.returnDistanceM! / 1000).toFixed(1)}km${day.returnTravelIsEstimate ? " (추정)" : ""}`}</div>}
      {day.items.length === 0 && <p className="item-empty">{en ? "No stops assigned to this date." : "이 날짜에 배정된 일정이 없습니다."}</p>}
    </div>
  );
}
