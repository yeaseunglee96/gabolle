export type Pace = "RELAXED" | "NORMAL" | "PACKED";
export type Category = "TOURIST" | "RESTAURANT" | "CAFE" | "LODGING";
export type RecommendationMode = "ESSENTIAL" | "LOCAL" | "EASY";
export type Language = "KO" | "EN";
export type DietType = "NONE" | "VEGETARIAN" | "VEGAN" | "HALAL" | "GLUTEN_FREE";

export interface CreateTripRequest {
  origin: string;
  originLat: number;
  originLng: number;
  startDate: string; // YYYY-MM-DD
  endDate: string;
  partySize: number;
  adultCount?: number;
  childCount?: number;
  totalBudget: number;
  hasCar: boolean;
  pace: Pace;
  tasteTags: string[];
  courseCategory?: string;
  mustVisitPlaceIds?: string[];
  mustVisitAssignments?: { placeId: string; dayIndex: number }[];
  excludedPlaceIds?: string[];
  recommendationMode?: RecommendationMode;
  dayStart?: string;
  dayEnd?: string;
  maxWalkingKm?: number;
  language?: Language;
  allergies?: string[];
  dietType?: DietType;
  desiredFoods?: string[];
  lodgingPlaceId?: string;
  landmarkRatio?: number;
  localRatio?: number;
  easyRatio?: number;
}

export interface TripMeta {
  origin: string;
  courseCategory: string | null;
  originLat: number;
  originLng: number;
  startDate: string;
  endDate: string;
  partySize: number;
  adultCount: number;
  childCount: number;
  totalBudget: number;
  hasCar: boolean;
  pace: Pace;
  dayStart: string;
  dayEnd: string;
  maxWalkingKm: number;
  recommendationMode: RecommendationMode;
  tasteTags: string[];
  language: Language;
  allergies: string[];
  dietType: DietType;
  desiredFoods?: string[];
  lodgingPlaceId: string | null;
  lodgingName: string | null;
  lodgingAddress: string | null;
  landmarkRatio: number;
  localRatio: number;
  easyRatio: number;
}

export interface ScoredPlace {
  id: string;
  nameKo: string;
  nameEn: string | null;
  category: string;
  address: string;
  addressEn: string | null;
  allergens: string[];
  dietOptions: string[];
  onlineReservation: boolean;
  lat: number;
  lng: number;
  openTime: string;
  closeTime: string;
  closedDays: string[];
  recommendedStayMin: number;
  priceTier: number;
  localScore: number;
  tasteTags: string[];
  hasEnglishMenu: boolean;
  foreignCardPayment: boolean;
  dataSource?: string;
  imageUrl?: string | null;
  kakaoPlaceId: string | null;
  kakaoPlaceUrl: string | null;
  kakaoRating: number | null;
  kakaoReviewCount: number | null;
  kakaoPositiveReviewRate: number | null;
  kakaoReviewKeywords: string[];
  kakaoReviewSource: string | null;
  kakaoReviewCollectedAt: Date | null;
  score: number;
}

export interface ItineraryItemOutput {
  itemId?: string;
  seqOrder: number;
  placeId: string;
  nameKo: string;
  nameEn: string | null;
  category: string;
  address: string;
  addressEn: string | null;
  allergens: string[];
  dietOptions: string[];
  onlineReservation: boolean;
  lat: number;
  lng: number;
  openTime: string;
  closeTime: string;
  plannedArrival: string;
  stayMinutes: number;
  estCost: number;
  travelMinToNext: number | null;
  distanceToNextM: number | null;
  travelIsEstimate: boolean;
  travelSource: "KAKAO_MOBILITY" | "HAVERSINE";
  recommendReason: string;
  hasEnglishMenu: boolean;
  foreignCardPayment: boolean;
  localScore: number;
  dataSource: string;
  imageUrl: string | null;
  kakaoPlaceId: string | null;
  kakaoPlaceUrl: string | null;
  kakaoRating: number | null;
  kakaoReviewCount: number | null;
  kakaoPositiveReviewRate: number | null;
  kakaoReviewKeywords: string[];
  kakaoReviewSource: string | null;
  kakaoReviewCollectedAt: Date | null;
}

export interface ItineraryDayOutput {
  dayIndex: number;
  visitDate: string;
  dayBudget: number;
  totalEstCost: number;
  startTravelMin: number | null;
  startDistanceM: number | null;
  startTravelIsEstimate: boolean;
  returnTravelMin: number | null;
  returnDistanceM: number | null;
  returnTravelIsEstimate: boolean;
  items: ItineraryItemOutput[];
}

export interface ItineraryOutput {
  itineraryId: string;
  tripId: string;
  trip: TripMeta;
  days: ItineraryDayOutput[];
  warnings: string[];
  solverSource: "OR_TOOLS" | "HEURISTIC";
  mode: RecommendationMode;
}
