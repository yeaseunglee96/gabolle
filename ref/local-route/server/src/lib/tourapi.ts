/**
 * 한국관광공사 TourAPI 4.0 클라이언트 (17장 원칙: 배치 수집으로 자체 DB에 적재).
 * 두 서비스 모두 같은 인증키(TOUR_API_KEY)를 쓴다:
 *   KorService2 국문 관광정보
 *   EngService2 영문 관광정보 (contentId로만 조회 — areaCode 필터는 국문과 다른 contentTypeId 체계라 신뢰 불가)
 */

const KOR_BASE = "https://apis.data.go.kr/B551011/KorService2";
const ENG_BASE = "https://apis.data.go.kr/B551011/EngService2";

const MOBILE_APP = "LocalRoute";

function getKey(): string {
  const key = process.env.TOUR_API_KEY;
  if (!key) throw new Error("TOUR_API_KEY가 .env에 설정되어 있지 않습니다.");
  return key;
}

async function callApi<T = any>(base: string, operation: string, params: Record<string, string | number>): Promise<T> {
  const qs = new URLSearchParams({
    serviceKey: getKey(),
    MobileOS: "ETC",
    MobileApp: MOBILE_APP,
    _type: "json",
    ...Object.fromEntries(Object.entries(params).map(([k, v]) => [k, String(v)])),
  });
  const url = `${base}/${operation}?${qs.toString()}`;
  const res = await fetch(url, { signal: AbortSignal.timeout(10000) });
  if (!res.ok) throw new Error(`TourAPI ${operation} HTTP ${res.status}`);
  const data = (await res.json()) as any;
  const header = data?.response?.header;
  if (header?.resultCode !== "0000") {
    throw new Error(`TourAPI ${operation} error: ${header?.resultCode} ${header?.resultMsg}`);
  }
  return data.response.body as T;
}

/** items가 없거나("") 1건뿐이거나 배열인 경우를 전부 배열로 정규화 */
function normalizeItems<T>(body: any): T[] {
  const items = body?.items;
  if (!items || items === "") return [];
  const item = items.item;
  if (!item) return [];
  return Array.isArray(item) ? item : [item];
}

export interface AreaBasedItem {
  contentid: string;
  contenttypeid: string;
  title: string;
  addr1: string;
  mapx: string;
  mapy: string;
  firstimage?: string;
  cat3?: string;
}

export async function fetchAreaBasedList(
  contentTypeId: number,
  areaCode: number,
  numOfRows: number,
  pageNo: number
): Promise<AreaBasedItem[]> {
  const body = await callApi(KOR_BASE, "areaBasedList2", {
    contentTypeId,
    areaCode,
    numOfRows,
    pageNo,
    arrange: "Q", // 수정일 최신순 (콘텐츠 품질이 비교적 최근 관리된 것 우선)
  });
  return normalizeItems<AreaBasedItem>(body);
}

export interface DetailCommon {
  overview?: string;
  homepage?: string;
  tel?: string;
}

export async function fetchDetailCommon(contentId: string): Promise<DetailCommon | null> {
  const body = await callApi(KOR_BASE, "detailCommon2", {
    contentId,
    defaultYN: "Y",
    overviewYN: "Y",
    firstImageYN: "N",
    areacodeYN: "N",
    catcodeYN: "N",
    addrinfoYN: "N",
    mapinfoYN: "N",
    transGuideYN: "N",
  });
  const items = normalizeItems<DetailCommon>(body);
  return items[0] ?? null;
}

/** contentTypeId별로 필드명이 전부 다른 detailIntro2 응답을 우리 스키마로 통일 */
export interface ParsedIntro {
  openTimeRaw: string | null;
  restDateRaw: string | null;
  parkingAvailable: boolean;
}

const INTRO_FIELD_MAP: Record<number, { time?: string; restDate?: string; parking?: string }> = {
  12: { time: "usetime", restDate: "restdate", parking: "parking" }, // 관광지
  14: { time: "usetimeculture", restDate: "restdateculture", parking: "parkingculture" }, // 문화시설
  28: { time: "usetimeleports", restDate: "restdateleports", parking: "parkingleports" }, // 레포츠
  38: { time: "opentime", restDate: "restdateshopping", parking: "parkingshopping" }, // 쇼핑
  32: { time: "checkintime", restDate: undefined, parking: "parkinglodging" }, // 숙박(체크인시각만 참고)
  39: { time: "opentimefood", restDate: "restdatefood", parking: "parkingfood" }, // 음식점
};

export async function fetchDetailIntro(contentId: string, contentTypeId: number): Promise<ParsedIntro> {
  const body = await callApi(KOR_BASE, "detailIntro2", { contentId, contentTypeId });
  const items = normalizeItems<Record<string, string>>(body);
  const item = items[0] ?? {};
  const fields = INTRO_FIELD_MAP[contentTypeId] ?? {};
  return {
    openTimeRaw: (fields.time && item[fields.time]) || null,
    restDateRaw: (fields.restDate && item[fields.restDate]) || null,
    parkingAvailable: !!(fields.parking && item[fields.parking] && item[fields.parking] !== "없음"),
  };
}

/** contentId로 영문 명칭만 best-effort 조회. 번역이 없으면 null(에러로 취급하지 않음). */
export async function fetchEnglishName(contentId: string): Promise<string | null> {
  try {
    const body = await callApi(ENG_BASE, "detailCommon2", {
      contentId,
      defaultYN: "Y",
      overviewYN: "N",
      firstImageYN: "N",
      areacodeYN: "N",
      catcodeYN: "N",
      addrinfoYN: "N",
      mapinfoYN: "N",
      transGuideYN: "N",
    });
    const items = normalizeItems<{ title?: string }>(body);
    return items[0]?.title || null;
  } catch {
    return null;
  }
}

/** "09:00~18:00" 류의 자유 텍스트에서 HH:MM 시간창을 뽑아낸다. 못 찾으면 null(호출부에서 하루종일로 폴백). */
export function parseTimeRange(raw: string | null): { openTime: string; closeTime: string } | null {
  if (!raw) return null;
  const match = raw.match(/(\d{1,2})[:시]\s?(\d{2})?\s*[~-]\s*(\d{1,2})[:시]\s?(\d{2})?/);
  if (!match) return null;
  const [, h1, m1, h2, m2] = match;
  const openTime = `${h1.padStart(2, "0")}:${(m1 ?? "00").padStart(2, "0")}`;
  const closeTime = `${h2.padStart(2, "0")}:${(m2 ?? "00").padStart(2, "0")}`;
  return { openTime, closeTime };
}

const WEEKDAY_MAP: Record<string, string> = {
  월: "MON",
  화: "TUE",
  수: "WED",
  목: "THU",
  금: "FRI",
  토: "SAT",
  일: "SUN",
};

/** "매주 월요일 휴무" 류의 텍스트에서 휴무 요일을 뽑아낸다. 확신 없으면 빈 배열(휴무 없음으로 간주하지 않고, 알 수 없음으로 처리). */
export function parseClosedDays(raw: string | null): string[] {
  if (!raw) return [];
  if (raw.includes("연중무휴") || raw.includes("없음")) return [];
  const found = new Set<string>();
  for (const [ko, en] of Object.entries(WEEKDAY_MAP)) {
    if (raw.includes(`${ko}요일`)) found.add(en);
  }
  return [...found];
}
