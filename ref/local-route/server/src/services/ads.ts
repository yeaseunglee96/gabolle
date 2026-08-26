export type AdCandidate = {
  id: string;
  name: string;
  bidCpc: number;
  budget: number;
  spent: number;
  targetingModes: string;
  targetingLanguage: string | null;
  serviceCategory: string;
  place: { id: string; nameKo: string; nameEn: string | null; category: string; localScore: number; imageUrl: string | null };
};

export const ALLOWED_AD_CATEGORIES = ["LODGING", "RENTAL_CAR", "TRAVEL_INSURANCE", "TAXI", "AIRPORT_TRANSFER", "ESIM"] as const;
export function isAllowedAdCategory(value: string): value is typeof ALLOWED_AD_CATEGORIES[number] {
  return ALLOWED_AD_CATEGORIES.includes(value as typeof ALLOWED_AD_CATEGORIES[number]);
}

export function rankEligibleAds(candidates: AdCandidate[], context: { mode?: string; language?: string; category?: string }) {
  const eligible = candidates.filter((candidate) => {
    if (!isAllowedAdCategory(candidate.serviceCategory)) return false;
    if (candidate.spent >= candidate.budget) return false;
    const modes = JSON.parse(candidate.targetingModes) as string[];
    if (context.mode && modes.length > 0 && !modes.includes(context.mode)) return false;
    if (candidate.targetingLanguage && context.language && candidate.targetingLanguage !== context.language) return false;
    if (context.category && candidate.place.category !== context.category) return false;
    return true;
  });
  const maxBid = Math.max(1, ...eligible.map((candidate) => candidate.bidCpc));
  return eligible.map((candidate) => ({
    ...candidate,
    adRankScore: Math.round((candidate.place.localScore * 0.5 + (candidate.bidCpc / maxBid) * 0.3 + ((candidate.budget - candidate.spent) / candidate.budget) * 0.2) * 1000) / 1000,
  })).sort((a, b) => b.adRankScore - a.adRankScore);
}
