import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

export type Transport = 'TRANSIT' | 'WALK' | 'CAR';
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
  englishMenuRequired: boolean;
  foreignCardRequired: boolean;
  soloDiningPreferred: boolean;
  accommodation: string;
  maxTransfers: number | null;
};

const VERSION = 1;
const STORAGE_KEY = '@gabolle/plan-draft';
export const EMPTY_PLAN: PlanDraft = { startDate: '', endDate: '', travelers: 1, adults: 1, children: 0, origin: '', transport: 'TRANSIT', budgetKrw: 100000, dayStartTime: '09:00', dayEndTime: '18:00', walkingLevel: 'MEDIUM', companionType: 'SOLO', preferences: [], preferenceAnswerStatus: { category: 'UNKNOWN', atmosphere: 'UNKNOWN', locality: 'UNKNOWN', quietness: 'UNKNOWN', touristPreference: 'UNKNOWN', foodPreference: 'UNKNOWN' }, atmospheres: [], localityLevel: null, quietLevel: null, touristLevel: null, foods: [], dietTypes: [], allergies: [], allergyStatus: 'UNKNOWN', allergyAnswered: false, dietStatus: 'UNKNOWN', dietAnswered: false, maxWalkingDistanceM: null, slopeConstraint: null, stairsConstraint: null, shadePreference: null, wheelchair: null, stroller: null, luggage: null, accessibilityNeeds: [], travelAreas: [], maxCompletedStep: 0, englishMenuRequired: false, foreignCardRequired: false, soloDiningPreferred: false, accommodation: '', maxTransfers: null };

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
};

const PlanContext = createContext<PlanContextValue | null>(null);

export function PlanProvider({ children }: { children: ReactNode }) {
  const [draft, setDraft] = useState<PlanDraft>(EMPTY_PLAN);
  const [ready, setReady] = useState(false);
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

  const value = useMemo<PlanContextValue>(() => ({
    draft,
    ready,
    update: (patch) => { if (!ready) changedBeforeHydration.current = true; setDraft((current) => ({ ...current, ...patch })); },
    completeStep: (step) => setDraft((current) => ({ ...current, maxCompletedStep: Math.max(current.maxCompletedStep, step) })),
    clear: async () => { setDraft(EMPTY_PLAN); await AsyncStorage.removeItem(STORAGE_KEY); },
    basicComplete: Boolean(draft.startDate && draft.endDate && draft.endDate >= draft.startDate && draft.travelers > 0),
  }), [draft, ready]);

  return <PlanContext.Provider value={value}>{children}</PlanContext.Provider>;
}

export function usePlan() {
  const value = useContext(PlanContext);
  if (!value) throw new Error('usePlan은 PlanProvider 안에서 사용해야 합니다.');
  return value;
}
