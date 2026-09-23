import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { getApiLanguage } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { conflictingFoodCode, foodLabel } from './foodConflicts';
import { conditionsToDraftPatch, loadTravelConditions } from './travelConditions';
import type { PlaceSnapshot } from './origins';
import { hasPlanInput, parseStoredDraft } from './planDraftCarry';
import { getTasteProfile, type TasteAnswers } from '@/preferences/tasteProfile';

const tx = (ko: string, en: string) => (getApiLanguage() === 'en' ? en : ko);

export type Transport = 'TRANSIT' | 'WALK' | 'CAR';
export type MustVisitPlace = { placeId: string; nameKo: string; nameEn: string | null; lat: number; lng: number };
export type AccommodationPlace = MustVisitPlace & { address: string; addressEn?: string };
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
  /** 홈 시작 바의 숙소 칸 — design_handoff_home_lodging. '' 는 미정. */
  lodging: string;
  lodgingLat: number | null;
  lodgingLng: number | null;
  /** 홈 시작 바에서 고른 숙소의 스냅샷 — 여행 만들기 때 accommodation 으로 싣는다(S15P21E201-1536). */
  lodgingPlace: PlaceSnapshot | null;
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
  accommodationPlace: AccommodationPlace | null;
  maxTransfers: number | null;
  mustVisitPlaces: MustVisitPlace[];
  /**
   * 공유 일정에서 "내 조건으로 새 여행 만들기"로 들어왔을 때만 채워진다.
   * 채워져 있으면 제출 시 일반 생성 대신 POST /api/v1/shares/{token}/clone 을 부른다.
   */
  cloneShareToken: string | null;
};

const VERSION = 1;

// — 초안을 계정별로 나눠 저장한다.
const STORAGE_PREFIX = '@gabolle/plan-draft';
const ANONYMOUS_KEY = `${STORAGE_PREFIX}:anonymous`;

// 계정 구분이 없던 시절의 열쇠. 남겨 두면 옛 값이 계속 남아 있으므로 한 번 지운다.
const LEGACY_STORAGE_KEY = STORAGE_PREFIX;

const storageKeyFor = (userId: string | null) => (userId ? `${STORAGE_PREFIX}:${userId}` : ANONYMOUS_KEY);
export const EMPTY_PLAN: PlanDraft = { startDate: '', endDate: '', travelers: 1, adults: 1, children: 0, origin: '', originLat: null, originLng: null, lodging: '', lodgingLat: null, lodgingLng: null, lodgingPlace: null, transport: 'TRANSIT', budgetKrw: 100000, dayStartTime: '09:00', dayEndTime: '18:00', walkingLevel: 'MEDIUM', companionType: 'SOLO', preferences: [], preferenceAnswerStatus: { category: 'UNKNOWN', atmosphere: 'UNKNOWN', locality: 'UNKNOWN', quietness: 'UNKNOWN', touristPreference: 'UNKNOWN', foodPreference: 'UNKNOWN' }, atmospheres: [], localityLevel: null, quietLevel: null, touristLevel: null, foods: [], dietTypes: [], allergies: [], allergyStatus: 'UNKNOWN', allergyAnswered: false, dietStatus: 'UNKNOWN', dietAnswered: false, maxWalkingDistanceM: null, slopeConstraint: null, stairsConstraint: null, shadePreference: null, wheelchair: null, stroller: null, luggage: null, accessibilityNeeds: [], travelAreas: [], maxCompletedStep: 0, paceLevel: null, englishMenuRequired: false, foreignCardRequired: false, soloDiningPreferred: false, accommodation: '', accommodationPlace: null, maxTransfers: null, mustVisitPlaces: [], cloneShareToken: null };

const VOLATILE_CONSTRAINTS: Partial<PlanDraft> = {
  allergies: [], dietTypes: [], allergyStatus: 'UNKNOWN', allergyAnswered: false, dietStatus: 'UNKNOWN', dietAnswered: false,
  maxWalkingDistanceM: null, slopeConstraint: null, stairsConstraint: null,
  shadePreference: null, wheelchair: null, stroller: null, luggage: null,
  accessibilityNeeds: [],
};

/** 계정에 저장된 평소 취향은 이번 여행에서 아직 답하지 않은 칸에만 기본값으로 넣는다. (jinmiri S15P21E201-76) */
export function applyTasteProfile(current: PlanDraft, saved: TasteAnswers): PlanDraft {
  const status = { ...current.preferenceAnswerStatus };
  const patch: Partial<PlanDraft> = {};
  let changed = false;
  const applyScale = (key: 'localityLevel' | 'quietLevel' | 'touristLevel', dimension: 'locality' | 'quietness' | 'touristPreference', value: number | undefined) => {
    if (value === undefined || status[dimension] !== 'UNKNOWN') return;
    patch[key] = value;
    status[dimension] = 'SELECTED';
    changed = true;
  };
  applyScale('localityLevel', 'locality', saved.locality);
  applyScale('quietLevel', 'quietness', saved.quiet);
  applyScale('touristLevel', 'touristPreference', saved.tourist);
  if (saved.foods?.length && status.foodPreference === 'UNKNOWN') {
    patch.foods = [...saved.foods];
    status.foodPreference = 'SELECTED';
    changed = true;
  }
  if (saved.slope !== undefined && current.slopeConstraint === null) {
    patch.slopeConstraint = saved.slope;
    changed = true;
  }
  return changed ? { ...current, ...patch, preferenceAnswerStatus: status } : current;
}

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
  const { user, accessToken, ready: authReady } = useAuth();
  const storageKey = storageKeyFor(user?.userId ?? null);

  const [draft, setDraft] = useState<PlanDraft>(EMPTY_PLAN);
  // 어느 열쇠까지 읽어 왔는지. 계정이 바뀌면 이 값과 storageKey 가 어긋나고, 그 동안에는
  // 저장을 멈춘다 — 안 그러면 앞 계정의 초안이 새 계정 자리에 그대로 복사된다.
  const [hydratedKey, setHydratedKey] = useState<string | null>(null);
  const [foodConflictNotice, setFoodConflictNotice] = useState<string | null>(null);
  const changedBeforeHydration = useRef(false);
  const tasteProfileAppliedKey = useRef<string | null>(null);
  const ready = hydratedKey !== null;

  useEffect(() => {
    // 로그인 상태를 알기 전에는 아무것도 읽지 않는다. 세션을 되살리는 동안에는 user 가
    // 잠깐 null 이라, 이 조건이 없으면 그 순간 익명 열쇠를 읽어 초안을 비우고, 뒤이어
    // 계정 열쇠로 옮기면서 그 빈 초안을 저장된 것 위에 덮어쓴다. 새로고침 한 번에 입력이
    // 사라진다 — 실제로 그렇게 됐다.
    if (!authReady) return;
    if (hydratedKey === storageKey) return;

    // 로그인하지 않고 채운 초안은 로그인 뒤에도 그대로 쓴다. 확인 화면에 "로그인하고 일정
    // 만들기" 가 있어서, 여기서 비우면 네 단계를 처음부터 다시 채우게 된다.
    if (hydratedKey === ANONYMOUS_KEY && storageKey !== ANONYMOUS_KEY) {
      void AsyncStorage.removeItem(ANONYMOUS_KEY);
      setHydratedKey(storageKey);
      return;
    }

    let cancelled = false;
    changedBeforeHydration.current = false;
    // 🔴 로그인된 채로 «처음» 읽을 때만 손님 초안을 본다 — 웹 소셜 로그인은 페이지를 떠났다 돌아와서
    //    위 갈래(로그인 흐름 안에서 넘겨주기)가 안 걸린다(S15P21E201-1541, planDraftCarry.ts).
    //    계정을 바꾸는 중(hydratedKey 가 다른 계정)이면 보지 않는다.
    const carryFromGuest = hydratedKey === null && storageKey !== ANONYMOUS_KEY;
    Promise.all([AsyncStorage.getItem(storageKey), carryFromGuest ? AsyncStorage.getItem(ANONYMOUS_KEY) : Promise.resolve(null)]).then(([raw, guestRaw]) => {
      if (cancelled) return;
      const guest = parseStoredDraft<PlanDraft>(guestRaw, VERSION);
      if (hasPlanInput(guest)) {
        void AsyncStorage.removeItem(ANONYMOUS_KEY);
        if (!changedBeforeHydration.current) setDraft({ ...EMPTY_PLAN, ...guest, ...VOLATILE_CONSTRAINTS });
        return;
      }
      let restored: PlanDraft | null = null;
      if (raw) {
        try {
          const stored = JSON.parse(raw) as { version?: number; draft?: PlanDraft };
          if (stored.version === VERSION && stored.draft) restored = { ...EMPTY_PLAN, ...stored.draft, ...VOLATILE_CONSTRAINTS };
        } catch {
          void AsyncStorage.removeItem(storageKey);
        }
      }
      // 이 계정으로 저장해 둔 것이 없으면 기본값으로 되돌린다. 화면에 남아 있는 값이
      // 앞 계정의 것이기 때문이다.
      if (!changedBeforeHydration.current) setDraft(restored ?? EMPTY_PLAN);
    }).finally(() => { if (!cancelled) setHydratedKey(storageKey); });
    return () => { cancelled = true; };
  }, [authReady, hydratedKey, storageKey]);

  useEffect(() => { void AsyncStorage.removeItem(LEGACY_STORAGE_KEY); }, []);

  // 저장해 둔 여행 조건을 모든 새 여행의 기본값으로 얹는다.
  const userId = user?.userId ?? null;
  useEffect(() => {
    // 로그인 안 한 사람에게도 얹는다. 그 사람의 답은 기기에만 있지만, 이번 여행에는
    // 똑같이 걸린다 — 여기서 빼면 로그인 전에 적은 알레르기가 새로고침 한 번에 사라진다.
    if (!authReady || hydratedKey !== storageKey) return;
    let alive = true;
    void loadTravelConditions(userId, accessToken).then((record) => {
      const saved = record.conditions;
      if (!alive || !saved) return;
      setDraft((current) => (current.allergyStatus === 'UNKNOWN' && current.dietStatus === 'UNKNOWN'
        ? { ...current, ...conditionsToDraftPatch(saved) }
        : current));
    });
    return () => { alive = false; };
  }, [accessToken, authReady, hydratedKey, storageKey, userId]);

  // 계정에 저장된 평소 취향(로컬성·조용함·음식·경사)을 새 여행의 기본값으로 얹는다.
  // 여행 조건(위)과 같은 방식이다 — 아직 답하지 않은 칸에만 넣으므로, /plan 에서 그 문항을
  // 빼도 온보딩에서 답한 값이 추천까지 흐른다. 로그인한 사람에게만(계정에만 저장되므로).
  useEffect(() => {
    if (!user || !accessToken || hydratedKey !== storageKey || tasteProfileAppliedKey.current === storageKey) return;
    tasteProfileAppliedKey.current = storageKey;
    let cancelled = false;
    void getTasteProfile(accessToken)
      .then((saved) => {
        if (cancelled) return;
        setDraft((current) => applyTasteProfile(current, saved));
      })
      .catch(() => { /* 서버 취향 조회 실패는 여행 작성 자체를 막지 않는다. */ });
    return () => { cancelled = true; };
  }, [accessToken, hydratedKey, storageKey, user]);

  useEffect(() => {
    if (hydratedKey !== storageKey) return;
    void AsyncStorage.setItem(storageKey, JSON.stringify({ version: VERSION, draft: { ...draft, ...VOLATILE_CONSTRAINTS } }));
  }, [draft, hydratedKey, storageKey]);

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
    clear: async () => { setDraft(EMPTY_PLAN); await AsyncStorage.removeItem(storageKey); },
    basicComplete: Boolean(draft.startDate && draft.endDate && draft.endDate >= draft.startDate && draft.travelers > 0),
    foodConflictNotice,
    clearFoodConflictNotice: () => setFoodConflictNotice(null),
  }), [draft, ready, storageKey, foodConflictNotice]);

  return <PlanContext.Provider value={value}>{children}</PlanContext.Provider>;
}

export function usePlan() {
  const value = useContext(PlanContext);
  if (!value) throw new Error(tx('usePlan은 PlanProvider 안에서 사용해야 합니다.', 'usePlan must be used inside PlanProvider.'));
  return value;
}
