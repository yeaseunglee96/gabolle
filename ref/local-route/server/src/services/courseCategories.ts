import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

export interface CourseCategoryConfig {
  code: string; nameKo: string; nameEn: string; axis: string; summaryKo: string; enabled: boolean; disabledReason?: string; boostTasteTags?: string[];
  weightMultipliers?: Partial<Record<"tasteMatch" | "localScore" | "travelEfficiency" | "budgetFit" | "foreignerEase" | "freshness", number>>;
  scheduleParams?: { pace?: "RELAXED" | "NORMAL" | "PACKED"; placesPerDay?: [number, number]; transport?: string; stayMinutesScale?: number; maxWalkDistanceScale?: number; maxTravelMinutesBetween?: number; dayEndTimeCap?: string; forbidTimeRange?: { after?: string }; regularMealTimes?: boolean };
  landmarkPenalty?: number;
  budgetFitMode?: "INVERSE";
}

const configPath = fileURLToPath(new URL("../../config/course_categories.json", import.meta.url));
const config = JSON.parse(readFileSync(configPath, "utf8")) as { version: string; categories: CourseCategoryConfig[] };

export function getCourseCategories() { return config.categories; }
export function findCourseCategory(code: string | null | undefined) { return code ? config.categories.find((category) => category.code === code) ?? null : null; }
export function applyCourseCategoryTasteTags(tasteTags: string[], code: string | null | undefined) {
  const category = findCourseCategory(code);
  return [...new Set([...tasteTags, ...(category?.boostTasteTags ?? [])])];
}
