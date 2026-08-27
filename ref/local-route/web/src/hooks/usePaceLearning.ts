import { useCallback, useEffect, useMemo, useState } from "react";
import { getPaceForecast, getRhythmProfile, recordItemProgress, replanRemainingDay } from "../api/client";
import type { ItineraryDayOutput, PaceForecast, RhythmProfile } from "../types";
import type { ReplanStrategy } from "../components/ReplanModal";

/**
 * 페이스 러닝 상태를 한곳에 모은다.
 *
 * 화면(TripSchedulePage)은 "언제 무엇을 보여줄지"만 결정하고,
 * 실측 기록 → 예보 갱신 → 재계산으로 이어지는 흐름은 전부 여기서 처리한다.
 */

function todayString(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

function nowHHMM(): string {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
}

/** 재계산 출발점. GPS를 쓸 수 있으면 실제 위치를, 아니면 마지막으로 다녀온 장소를 기준으로 삼는다. */
function resolveCurrentPosition(fallback: { lat: number; lng: number }): Promise<{ lat: number; lng: number; fromGps: boolean }> {
  return new Promise((resolve) => {
    if (!navigator.geolocation) { resolve({ ...fallback, fromGps: false }); return; }
    navigator.geolocation.getCurrentPosition(
      (position) => resolve({ lat: position.coords.latitude, lng: position.coords.longitude, fromGps: true }),
      () => resolve({ ...fallback, fromGps: false }),
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 30_000 }
    );
  });
}

interface Options {
  itineraryId: string;
  tripId: string;
  day: ItineraryDayOutput | null;
  canEdit: boolean;
  /** 일정이 바뀌었을 때 상위에서 다시 불러오게 한다. */
  onItineraryChanged: () => Promise<void> | void;
}

export function usePaceLearning({ itineraryId, tripId, day, canEdit, onItineraryChanged }: Options) {
  const [forecast, setForecast] = useState<PaceForecast | null>(null);
  const [rhythm, setRhythm] = useState<RhythmProfile | null>(null);
  const [replanOpen, setReplanOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rhythmDismissed, setRhythmDismissed] = useState(false);

  const hasActuals = useMemo(() => (day?.items ?? []).some((item) => item.actualArrival), [day]);
  const isToday = day?.visitDate === todayString();
  // 계획만 있는 미래 일정에까지 예보를 띄우면 소음이 된다. 오늘이거나 실측이 시작된 날에만 켠다.
  const active = Boolean(day) && (isToday || hasActuals);

  const refresh = useCallback(async () => {
    if (!day || !active) { setForecast(null); return; }
    try {
      const next = await getPaceForecast(itineraryId, day.dayIndex, nowHHMM());
      setForecast(next);
    } catch {
      setForecast(null);
    }
  }, [itineraryId, day, active]);

  useEffect(() => { void refresh(); }, [refresh]);

  useEffect(() => {
    if (!active) { setRhythm(null); return; }
    let cancelled = false;
    getRhythmProfile(tripId)
      .then((profile) => { if (!cancelled) setRhythm(profile); })
      .catch(() => { if (!cancelled) setRhythm(null); });
    return () => { cancelled = true; };
  }, [tripId, active]);

  /** 도착·출발 실측 기록. 기록 즉시 예보를 다시 계산한다. */
  const record = useCallback(async (itemId: string, payload: { arrivedAt?: string | null; departedAt?: string | null }) => {
    if (!canEdit) return;
    setBusy(true); setError(null);
    try {
      await recordItemProgress(itineraryId, itemId, payload);
      await onItineraryChanged();
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "방문 기록을 저장하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }, [canEdit, itineraryId, onItineraryChanged, refresh]);

  const markArrived = useCallback((itemId: string) => record(itemId, { arrivedAt: nowHHMM() }), [record]);
  const markDeparted = useCallback((itemId: string) => record(itemId, { departedAt: nowHHMM() }), [record]);

  /** 지금 위치·시각을 출발점으로 남은 일정 다시 짜기. */
  const applyReplan = useCallback(async (strategy: ReplanStrategy) => {
    if (!day || !canEdit) return;
    setBusy(true); setError(null);
    try {
      const visited = [...day.items].filter((item) => item.actualArrival).sort((a, b) => b.seqOrder - a.seqOrder)[0];
      const fallback = visited ? { lat: visited.lat, lng: visited.lng } : { lat: day.items[0]?.lat ?? 0, lng: day.items[0]?.lng ?? 0 };
      const position = await resolveCurrentPosition(fallback);
      await replanRemainingDay(itineraryId, day.dayIndex, {
        currentTime: nowHHMM(), lat: position.lat, lng: position.lng, strategy,
      });
      await onItineraryChanged();
      await refresh();
      setReplanOpen(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "남은 일정을 다시 계산하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }, [day, canEdit, itineraryId, onItineraryChanged, refresh]);

  const showRhythm = Boolean(rhythm?.hasProfile) && !rhythmDismissed && active;

  return {
    active,
    forecast,
    rhythm,
    showRhythm,
    busy,
    error,
    replanOpen,
    openReplan: () => { setError(null); setReplanOpen(true); },
    closeReplan: () => setReplanOpen(false),
    applyReplan,
    markArrived,
    markDeparted,
    dismissRhythm: () => setRhythmDismissed(true),
    refresh,
  };
}
