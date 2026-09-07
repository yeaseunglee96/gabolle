import AsyncStorage from '@react-native-async-storage/async-storage';

import type { ItineraryDto } from '@/plan/itinerary';

const STORAGE_KEY = 'gabolle:my-trips:v1';

export type SavedTripSummary = {
  id: string;
  title: string;
  startDate: string | null;
  endDate: string | null;
  dayCount: number;
  placeCount: number;
  savedAt: string;
};

function isSavedTripSummary(value: unknown): value is SavedTripSummary {
  if (!value || typeof value !== 'object') return false;
  const trip = value as Partial<SavedTripSummary>;
  return typeof trip.id === 'string' && typeof trip.title === 'string' && typeof trip.dayCount === 'number' && typeof trip.placeCount === 'number' && typeof trip.savedAt === 'string';
}

export async function loadSavedTrips(): Promise<SavedTripSummary[]> {
  try {
    const stored = await AsyncStorage.getItem(STORAGE_KEY);
    const parsed: unknown = stored ? JSON.parse(stored) : [];
    return Array.isArray(parsed) ? parsed.filter(isSavedTripSummary).sort((a, b) => b.savedAt.localeCompare(a.savedAt)) : [];
  } catch {
    return [];
  }
}

export async function rememberItinerary(itinerary: ItineraryDto): Promise<void> {
  const dates = itinerary.days.map((day) => day.date).filter(Boolean).sort();
  const summary: SavedTripSummary = {
    id: itinerary.id,
    title: itinerary.title || '부산 여행',
    startDate: dates[0] ?? null,
    endDate: dates.at(-1) ?? null,
    dayCount: itinerary.days.length,
    placeCount: itinerary.days.reduce((total, day) => total + day.items.length, 0),
    savedAt: new Date().toISOString(),
  };
  try {
    const current = await loadSavedTrips();
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify([summary, ...current.filter((trip) => trip.id !== summary.id)]));
  } catch {
    // 일정 조회 자체는 성공했으므로 기기 보관 실패가 상세 화면을 막지 않게 한다.
  }
}

export async function forgetSavedTrip(id: string): Promise<void> {
  const current = await loadSavedTrips();
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(current.filter((trip) => trip.id !== id)));
}

export async function clearSavedTrips(): Promise<void> {
  await AsyncStorage.removeItem(STORAGE_KEY);
}
