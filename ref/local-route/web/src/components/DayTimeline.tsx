import type { ItineraryDayOutput } from "../types";
import type { ItineraryItemOutput } from "../types";
import { ReorderableItemList } from "./ReorderableItemList";

interface Props {
  days: ItineraryDayOutput[];
  activeDayIndex: number;
  onSelectDay: (dayIndex: number) => void;
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

export function DayTimeline({ days, activeDayIndex, onSelectDay, hasCar, pinnedPlaceIds, busy, language, onTogglePin, onExclude, onReplace, onSelect, onReorder }: Props) {
  const day = days.find((d) => d.dayIndex === activeDayIndex) ?? days[0];
  const en = language === "EN";

  return (
    <div className="timeline">
      <div className="day-tabs">
        {days.map((d) => (
          <button
            key={d.dayIndex}
            type="button"
            className={`day-tab ${d.dayIndex === day.dayIndex ? "active" : ""}`}
            onClick={() => onSelectDay(d.dayIndex)}
          >
            {en ? `DAY ${d.dayIndex}` : `${d.dayIndex}일차`}
          </button>
        ))}
      </div>

      <div className="day-budget-bar">
        {en ? `Estimated ₩${day.totalEstCost.toLocaleString()} / daily budget ₩${Math.round(day.dayBudget).toLocaleString()}` : `예상 지출 ${day.totalEstCost.toLocaleString()}원 / 일 예산 ${Math.round(day.dayBudget).toLocaleString()}원`}
      </div>

      <ReorderableItemList day={day} hasCar={hasCar} pinnedPlaceIds={pinnedPlaceIds} busy={busy} language={language} onTogglePin={onTogglePin} onExclude={onExclude} onReplace={onReplace} onSelect={onSelect} onReorder={onReorder} />
    </div>
  );
}
