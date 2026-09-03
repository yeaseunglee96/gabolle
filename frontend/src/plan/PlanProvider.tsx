import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

export type Transport = 'TRANSIT' | 'WALK' | 'CAR';
export type PlanDraft = {
  startDate: string;
  endDate: string;
  travelers: number;
  origin: string;
  transport: Transport;
  budgetPerPerson: number;
  walkingLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  companionType: 'SOLO' | 'COUPLE' | 'FRIENDS' | 'FAMILY';
  preferences: string[];
  foods: string[];
  dietTypes: string[];
  allergies: string[];
  accessibilityNeeds: string[];
  maxCompletedStep: number;
};

const VERSION = 1;
const STORAGE_KEY = '@gabolle/plan-draft';
export const EMPTY_PLAN: PlanDraft = { startDate: '', endDate: '', travelers: 1, origin: '', transport: 'TRANSIT', budgetPerPerson: 100000, walkingLevel: 'MEDIUM', companionType: 'SOLO', preferences: [], foods: [], dietTypes: [], allergies: [], accessibilityNeeds: [], maxCompletedStep: 0 };

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

  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY).then((raw) => {
      if (!raw) return;
      try {
        const stored = JSON.parse(raw) as { version?: number; draft?: PlanDraft };
        if (stored.version === VERSION && stored.draft) setDraft({ ...EMPTY_PLAN, ...stored.draft });
      } catch {
        void AsyncStorage.removeItem(STORAGE_KEY);
      }
    }).finally(() => setReady(true));
  }, []);

  useEffect(() => {
    if (ready) void AsyncStorage.setItem(STORAGE_KEY, JSON.stringify({ version: VERSION, draft }));
  }, [draft, ready]);

  const value = useMemo<PlanContextValue>(() => ({
    draft,
    ready,
    update: (patch) => setDraft((current) => ({ ...current, ...patch })),
    completeStep: (step) => setDraft((current) => ({ ...current, maxCompletedStep: Math.max(current.maxCompletedStep, step) })),
    clear: async () => { setDraft(EMPTY_PLAN); await AsyncStorage.removeItem(STORAGE_KEY); },
    basicComplete: Boolean(draft.startDate && draft.endDate && draft.endDate >= draft.startDate && draft.origin.trim() && draft.travelers > 0),
  }), [draft, ready]);

  return <PlanContext.Provider value={value}>{children}</PlanContext.Provider>;
}

export function usePlan() {
  const value = useContext(PlanContext);
  if (!value) throw new Error('usePlan은 PlanProvider 안에서 사용해야 합니다.');
  return value;
}
