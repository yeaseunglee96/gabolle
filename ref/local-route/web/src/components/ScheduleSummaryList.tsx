import type { ItineraryDayOutput } from "../types";
import type { ItineraryItemOutput } from "../types";
import { ReorderableItemList } from "./ReorderableItemList";

const DAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"];
const DAY_LABELS_EN = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

function formatDateLabel(dateStr: string, language: "KO" | "EN"): string {
  const d = new Date(`${dateStr}T00:00:00`);
  const md = dateStr.slice(5).replace("-", ".");
  return `${md} (${(language === "EN" ? DAY_LABELS_EN : DAY_LABELS)[d.getDay()]})`;
}

interface Props {
  days: ItineraryDayOutput[];
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

export function ScheduleSummaryList({ days, hasCar, pinnedPlaceIds, busy, language, onTogglePin, onExclude, onReplace, onSelect, onReorder }: Props) {
  return (
    <div className="schedule-summary">
      {days.map((day) => (
        <div key={day.dayIndex} className="summary-day-block">
          <div className="summary-day-header">
            <span className="summary-day-badge">DAY {day.dayIndex}</span>
            <span className="summary-day-date">{formatDateLabel(day.visitDate, language)}</span>
          </div>
          <ReorderableItemList day={day} hasCar={hasCar} pinnedPlaceIds={pinnedPlaceIds} busy={busy} language={language} onTogglePin={onTogglePin} onExclude={onExclude} onReplace={onReplace} onSelect={onSelect} onReorder={onReorder} />
        </div>
      ))}
    </div>
  );
}
