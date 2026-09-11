import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { getApiLanguage } from '@/api/client';
import { conflictingFoodCode, foodLabel } from './foodConflicts';

const tx = (ko: string, en: string) => (getApiLanguage() === 'en' ? en : ko);

export type Transport = 'TRANSIT' | 'WALK' | 'CAR';
export type MustVisitPlace = { placeId: string; nameKo: string; nameEn: string | null; lat: number; lng: number };
export type ConstraintSelectionStatus = 'UNKNOWN' | 'NONE' | 'VALUES';
export type PreferenceAnswerStatus = 'UNKNOWN' | 'SELECTED' | 'SKIPPED';
export type PreferenceDimension = 'category' | 'atmosphere' | 'locality' | 'quietness' | 'touristPreference' | 'foodPreference';
export type PlanDraft = {
  startDate: string;
  endDate: string;
  travelers: number;
  adults: number;
  children: number;
  origin: string;
  originLat: number | null;
  originLng: number | null;
  transport: Transport;
  budgetKrw: number | null;
  dayStartTime: string;
  dayEndTime: string;
  walkingLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  companionType: 'SOLO' | 'COUPLE' | 'FRIENDS' | 'FAMILY';
  preferences: string[];
  preferenceAnswerStatus: Record<PreferenceDimension, PreferenceAnswerStatus>;
  atmospheres: string[];
  localityLevel: number | null;
  quietLevel: number | null;
  touristLevel: number | null;
  foods: string[];
  dietTypes: string[];
  allergies: string[];
  allergyStatus: ConstraintSelectionStatus;
  allergyAnswered: boolean;
  dietStatus: ConstraintSelectionStatus;
  dietAnswered: boolean;
  maxWalkingDistanceM: number | null;
  slopeConstraint: 'AVOID' | 'ALLOW' | null;
  stairsConstraint: 'AVOID' | 'ALLOW' | null;
  shadePreference: 'PREFER' | 'NO_PREFERENCE' | null;
  wheelchair: boolean | null;
  stroller: boolean | null;
  luggage: boolean | null;
  accessibilityNeeds: string[];
  travelAreas: string[];
  maxCompletedStep: number;
  paceLevel: 'RELAXED' | 'BALANCED' | 'PACKED' | null;
  englishMenuRequired: boolean;
  foreignCardRequired: boolean;
  soloDiningPreferred: boolean;
  accommodation: string;
  maxTransfers: number | null;
  mustVisitPlaces: MustVisitPlace[];
};

const VERSION = 1;
const STORAGE_KEY = '@gabolle/plan-draft';
export const EMPTY_PLAN: PlanDraft = { startDate: '', endDate: '', travelers: 1, adults: 1, children: 0, origin: '', originLat: null, originLng: null, transport: 'TRANSIT', budgetKrw: 100000, dayStartTime: '09:00', dayEndTime: '18:00', walkingLevel: 'MEDIUM', companionType: 'SOLO', preferences: [], preferenceAnswerStatus: { category: 'UNKNOWN', atmosphere: 'UNKNOWN', locality: 'UNKNOWN', quietness: 'UNKNOWN', touristPreference: 'UNKNOWN', foodPreference: 'UNKNOWN' }, atmospheres: [], localityLevel: null, quietLevel: null, touristLevel: null, foods: [], dietTypes: [], allergies: [], allergyStatus: 'UNKNOWN', allergyAnswered: false, dietStatus: 'UNKNOWN', dietAnswered: false, maxWalkingDistanceM: null, slopeConstraint: null, stairsConstraint: null, shadePreference: null, wheelchair: null, stroller: null, luggage: null, accessibilityNeeds: [], travelAreas: [], maxCompletedStep: 0, paceLevel: null, englishMenuRequired: false, foreignCardRequired: false, soloDiningPreferred: false, accommodation: '', maxTransfers: null, mustVisitPlaces: [] };

const VOLATILE_CONSTRAINTS: Partial<PlanDraft> = {
  allergies: [], dietTypes: [], allergyStatus: 'UNKNOWN', allergyAnswered: false, dietStatus: 'UNKNOWN', dietAnswered: false,
  maxWalkingDistanceM: null, slopeConstraint: null, stairsConstraint: null,
  shadePreference: null, wheelchair: null, stroller: null, luggage: null,
  accessibilityNeeds: [],
};

type PlanContextValue = {
  draft: PlanDraft;
  ready: boolean;
  update: (patch: Partial<PlanDraft>) => void;
  completeStep: (step: number) => void;
  clear: () => Promise<void>;
  basicComplete: boolean;
  foodConflictNotice: string | null;
  clearFoodConflictNotice: () => void;
};

const PlanContext = createContext<PlanContextValue | null>(null);

export function PlanProvider({ children }: { children: ReactNode }) {
  const [draft, setDraft] = useState<PlanDraft>(EMPTY_PLAN);
  const [ready, setReady] = useState(false);
  const [foodConflictNotice, setFoodConflictNotice] = useState<string | null>(null);
  const changedBeforeHydration = useRef(false);

  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY).then((raw) => {
      if (!raw) return;
      try {
        const stored = JSON.parse(raw) as { version?: number; draft?: PlanDraft };
        if (stored.version === VERSION && stored.draft && !changedBeforeHydration.current) setDraft({ ...EMPTY_PLAN, ...stored.draft, ...VOLATILE_CONSTRAINTS });
      } catch {
        void AsyncStorage.removeItem(STORAGE_KEY);
      }
    }).finally(() => setReady(true));
  }, []);

  useEffect(() => {
    if (ready) void AsyncStorage.setItem(STORAGE_KEY, JSON.stringify({ version: VERSION, draft: { ...draft, ...VOLATILE_CONSTRAINTS } }));
  }, [draft, ready]);

  // 3단계 알레르기·식단(제외 재료)이 바뀔 때마다 2단계에서 이미 고른 음식과 다시 대조한다.
  // 단계를 오간 뒤에도 매번 다시 계산되도록 draft.foods 는 의존성에 넣지 않는다 — 이 효과 자체가 foods 를 바꾸므로 넣으면 무한 루프가 된다.
  useEffect(() => {
    if (!ready) return;
    const allergies = draft.allergyStatus === 'VALUES' ? draft.allergies : [];
    const dietTypes = draft.dietStatus === 'VALUES' ? draft.dietTypes : [];
    const removedKeys = draft.foods.filter((key) => conflictingFoodCode(key, allergies, dietTypes));
    if (!removedKeys.length) return;
    setDraft((current) => ({ ...current, foods: current.foods.filter((key) => !removedKeys.includes(key)) }));
    setFoodConflictNotice(removedKeys.map(foodLabel).join(', '));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draft.allergyStatus, draft.dietStatus, draft.allergies, draft.dietTypes, ready]);

  const value = useMemo<PlanContextValue>(() => ({
    draft,
    ready,
    update: (patch) => { if (!ready) changedBeforeHydration.current = true; setDraft((current) => ({ ...current, ...patch })); },
    completeStep: (step) => setDraft((current) => ({ ...current, maxCompletedStep: Math.max(current.maxCompletedStep, step) })),
    clear: async () => { setDraft(EMPTY_PLAN); await AsyncStorage.removeItem(STORAGE_KEY); },
    basicComplete: Boolean(draft.startDate && draft.endDate && draft.endDate >= draft.startDate && draft.travelers > 0),
    foodConflictNotice,
    clearFoodConflictNotice: () => setFoodConflictNotice(null),
  }), [draft, ready, foodConflictNotice]);

  return <PlanContext.Provider value={value}>{children}</PlanContext.Provider>;
}

export function usePlan() {
  const value = useContext(PlanContext);
  if (!value) throw new Error(tx('usePlan은 PlanProvider 안에서 사용해야 합니다.', 'usePlan must be used inside PlanProvider.'));
  return value;
}
