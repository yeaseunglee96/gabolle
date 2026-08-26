export type PlaceImageProvider = "DATABASE" | "NAVER" | "GOOGLE";

export interface PlaceImageMatch {
  imageUrl: string;
  sourceUrl: string;
  provider: PlaceImageProvider;
  title: string;
}

const CACHE_TTL_MS = 24 * 60 * 60 * 1000;
const cache = new Map<string, { value: PlaceImageMatch | null; expiresAt: number }>();

function cleanText(value: unknown) {
  return String(value ?? "").replace(/<[^>]*>/g, "").replace(/&quot;/g, '"').replace(/&amp;/g, "&").trim();
}

function safeHttpUrl(value: unknown) {
  try {
    const url = new URL(String(value ?? ""));
    return ["http:", "https:"].includes(url.protocol) ? url.toString() : null;
  } catch { return null; }
}

async function searchNaver(query: string): Promise<PlaceImageMatch | null> {
  const clientId = process.env.NAVER_SEARCH_CLIENT_ID;
  const clientSecret = process.env.NAVER_SEARCH_CLIENT_SECRET;
  if (!clientId || !clientSecret) return null;
  const params = new URLSearchParams({ query, display: "5", sort: "sim", filter: "large" });
  const response = await fetch(`https://openapi.naver.com/v1/search/image?${params}`, { headers: { "X-Naver-Client-Id": clientId, "X-Naver-Client-Secret": clientSecret }, signal: AbortSignal.timeout(4000) });
  if (!response.ok) return null;
  const data = await response.json() as any;
  for (const item of data.items ?? []) {
    const imageUrl = safeHttpUrl(item.thumbnail || item.link);
    const sourceUrl = safeHttpUrl(item.link);
    if (imageUrl && sourceUrl) return { imageUrl, sourceUrl, provider: "NAVER", title: cleanText(item.title) || query };
  }
  return null;
}

async function searchGoogle(query: string): Promise<PlaceImageMatch | null> {
  const key = process.env.GOOGLE_CUSTOM_SEARCH_KEY;
  const cx = process.env.GOOGLE_CUSTOM_SEARCH_CX;
  if (!key || !cx) return null;
  const params = new URLSearchParams({ key, cx, q: query, searchType: "image", num: "5", safe: "active", imgSize: "large", rights: "cc_publicdomain|cc_attribute|cc_sharealike" });
  const response = await fetch(`https://customsearch.googleapis.com/customsearch/v1?${params}`, { signal: AbortSignal.timeout(4000) });
  if (!response.ok) return null;
  const data = await response.json() as any;
  for (const item of data.items ?? []) {
    const imageUrl = safeHttpUrl(item.image?.thumbnailLink || item.link);
    const sourceUrl = safeHttpUrl(item.image?.contextLink || item.link);
    if (imageUrl && sourceUrl) return { imageUrl, sourceUrl, provider: "GOOGLE", title: cleanText(item.title) || query };
  }
  return null;
}

function getFallbackImage(place: { nameKo: string; category: string }): PlaceImageMatch {
  const name = place.nameKo;
  const category = place.category;

  if (name.includes("야시장")) {
    return {
      imageUrl: "https://images.unsplash.com/photo-1501594907352-04cda38ebc29?auto=format&fit=crop&w=600&q=80",
      sourceUrl: "https://unsplash.com/photos/people-walking-on-street-during-night-time-Z6h9_9495H0",
      provider: "GOOGLE",
      title: "야시장 (기본 이미지)"
    };
  }

  if (name.includes("시장")) {
    return {
      imageUrl: "https://images.unsplash.com/photo-1543083503-0c355536ee47?auto=format&fit=crop&w=600&q=80",
      sourceUrl: "https://unsplash.com/photos/people-standing-near-store-during-daytime-O12M9L5_7aM",
      provider: "GOOGLE",
      title: "전통시장 (기본 이미지)"
    };
  }

  switch (category) {
    case "FESTIVAL":
      return {
        imageUrl: "https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?auto=format&fit=crop&w=600&q=80",
        sourceUrl: "https://unsplash.com/photos/fireworks-display-during-nighttime-A5rSp5VJn50",
        provider: "GOOGLE",
        title: "축제 (기본 이미지)"
      };
    case "SOUVENIR":
      return {
        imageUrl: "https://images.unsplash.com/photo-1472851294608-062f824d29cc?auto=format&fit=crop&w=600&q=80",
        sourceUrl: "https://unsplash.com/photos/assorted-color-hanging-decorations-g1DgUToG56g",
        provider: "GOOGLE",
        title: "기념품샵 (기본 이미지)"
      };
    case "RESTAURANT":
      return {
        imageUrl: "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=600&q=80",
        sourceUrl: "https://unsplash.com/photos/brown-wooden-table-with-chairs-inside-room-N7c7a2r1Z_A",
        provider: "GOOGLE",
        title: "식당 (기본 이미지)"
      };
    case "CAFE":
      return {
        imageUrl: "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?auto=format&fit=crop&w=600&q=80",
        sourceUrl: "https://unsplash.com/photos/white-ceramic-mug-on-saucer-3GZ7s5_71A4",
        provider: "GOOGLE",
        title: "카페 (기본 이미지)"
      };
    case "LODGING":
      return {
        imageUrl: "https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=600&q=80",
        sourceUrl: "https://unsplash.com/photos/brown-concrete-building-near-swimming-pool-during-daytime-wlhOQnZ18gY",
        provider: "GOOGLE",
        title: "숙소 (기본 이미지)"
      };
    case "TOURIST":
    default:
      return {
        imageUrl: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=600&q=80",
        sourceUrl: "https://unsplash.com/photos/body-of-water-under-blue-sky-during-daytime-O33s9g_7mE4",
        provider: "GOOGLE",
        title: "관광지 (기본 이미지)"
      };
  }
}

export async function searchPlaceImage(place: { id: string; nameKo: string; address: string; category: string; imageUrl: string | null }): Promise<PlaceImageMatch | null> {
  if (place.imageUrl) return { imageUrl: place.imageUrl, sourceUrl: place.imageUrl, provider: "DATABASE", title: place.nameKo };
  if (!["TOURIST", "RESTAURANT", "CAFE", "LODGING", "FESTIVAL", "SOUVENIR"].includes(place.category)) return null;
  const cached = cache.get(place.id);
  if (cached && cached.expiresAt > Date.now()) return cached.value;
  const area = place.address.split(" ").slice(0, 2).join(" ");
  
  let suffix = "관광지 여행 사진";
  if (place.category === "RESTAURANT") {
    suffix = "식당 음식점 외관";
  } else if (place.category === "CAFE") {
    suffix = "예쁜 카페 매장 내부 외관";
  } else if (place.category === "LODGING") {
    suffix = "호텔 펜션 숙소 외관";
  } else if (place.category === "FESTIVAL") {
    suffix = "축제 행사 전경";
  } else if (place.category === "SOUVENIR") {
    suffix = "기념품 소품샵 매장";
  }

  if (place.nameKo.includes("야시장")) {
    suffix = "야시장 풍경";
  } else if (place.nameKo.includes("시장")) {
    suffix = "전통시장 풍경";
  }

  const query = `${area} ${place.nameKo} ${suffix}`.trim();
  let result: PlaceImageMatch | null = null;
  try { result = await searchNaver(query); } catch { result = null; }
  if (!result) { try { result = await searchGoogle(query); } catch { result = null; } }
  
  if (!result) {
    result = getFallbackImage(place);
  }
  
  cache.set(place.id, { value: result, expiresAt: Date.now() + CACHE_TTL_MS });
  return result;
}
