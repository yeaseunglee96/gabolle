import "dotenv/config";
import { PrismaClient } from "@prisma/client";

const prisma = new PrismaClient();

/**
 * "액티비티"로 시드된 장소들은 사실 성격이 여러 갈래로 갈린다:
 *   - 요트·케이블카·사격장·서핑·아이스링크·아쿠아리움 같은 "예약/체험형 액티비티"
 *   - ~길·~로드·~코스·공원 같은 "산책" 스팟
 *   - 해수욕장·산 같은 "자연" 스팟
 *   - 캠핑장·글램핑·카라반은 로컬 탭에서 아예 제외한다(장소 자체는 남기되 노출 태그만 뺀다)
 * 기존 "activity" 태그는 일정 추천 엔진(취향 태그 매칭)이 그대로 쓰고 있으므로 건드리지 않고,
 * 로컬 탭 세부 분류에만 쓰는 태그를 추가로 얹는다.
 */
const ADVENTURE_ACTIVITY = [
  "부산 아쿠아리움", "송도 해상케이블카", "요트탈래", "센텀시티 아이스링크", "미포정거장",
  "송정서핑학교", "서프마린", "삼락강변체육공원인라인스케이트장", "레이저태그스포츠 광안점",
  "광안리 SUP Zone", "삼락수상레포츠타운", "영도관광실탄사격장", "아시아드 컨트리클럽",
];

const WALK_TRAIL = [
  "이기대 해안산책로", "문탠로드", "[부산 갈맷길] 2코스 2구간", "남파랑길(부산)",
  "송도해안볼레길", "[해파랑길] 2코스", "구포무장애숲길", "송도 구름산책로",
  "해안누리길 몰운대길", "용두산공원", "동백공원", "삼익비치수변공원",
  "부산 중앙공원", "반송공원",
];

const NATURE_SPOT = [
  "해운대해수욕장", "광안리해수욕장", "송정해수욕장", "다대포해수욕장",
  "태종대", "늘푸른숲", "용호동일대 바다낚시",
  "금련산", "금정산", "땅뫼산", "백양산 웰빙숲", "송도반도 (부산 국가지질공원)",
];

const NIGHT_VIEW = [
  "광안리해수욕장", "용두산공원", "감천문화마을", "황령산 봉수전망대", "초량이바구길", "호천마을",
  "더베이101", "민락수변공원", "부산 동구도서관", "우암동 도시숲", "청학수변공원", "영화의전당",
];

// 야경 탭 큐레이션에서 뺀다: 다대포해수욕장/오륙도 스카이워크는 사용자 요청으로 제외,
// 청사포 카페거리/광안리 오션뷰 카페/부산바다축제는 카페·축제라 야경 "장소" 성격이 아니라서 기존에 잘못 태깅돼 있었다.
const NIGHT_VIEW_REMOVE = ["다대포해수욕장", "청사포 카페거리", "광안리 오션뷰 카페", "오륙도 스카이워크", "부산바다축제"];

// 시드 데이터에 없던 야경 명소 — 카카오 로컬 검색/visitbusan.net으로 좌표를 확인해 새로 등록한다(2026-08-26).
// nightImageUrl은 Wikimedia Commons의 CC 라이선스 야경 사진만 연결한다(찾지 못한 곳은 null로 두고
// 카드에는 플레이스홀더가 뜬다 — 절대 낮 사진을 대신 보여주지 않는다).
const NEW_PLACES: Array<{ nameKo: string; address: string; lat: number; lng: number; tags: string[]; nightImageUrl?: string }> = [
  { nameKo: "초량이바구길", address: "부산 동구 초량동 994-12", lat: 35.116820678714, lng: 129.03669648381614, tags: ["nightview"] },
  { nameKo: "더베이101", address: "부산 해운대구 동백로 52", lat: 35.1565648156251, lng: 129.152021092751, tags: ["nightview"] },
  { nameKo: "민락수변공원", address: "부산 수영구 민락동 110-19", lat: 35.154940897926885, lng: 129.13399667057104, tags: ["nightview"],
    nightImageUrl: "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Marine_City_Night_2022_01.jpg/1280px-Marine_City_Night_2022_01.jpg" },
  { nameKo: "우암동 도시숲", address: "부산 남구 동항로106번가길 6", lat: 35.1274502511258, lng: 129.072306688976, tags: ["nightview"] },
  { nameKo: "청학수변공원", address: "부산 영도구 청학동 217-43", lat: 35.0977132064528, lng: 129.061157551844, tags: ["nightview"] },
  { nameKo: "영화의전당", address: "부산 해운대구 수영강변대로 120", lat: 35.1710249248016, lng: 129.127011337686, tags: ["nightview"],
    nightImageUrl: "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ef/Busan_Cinema_Center_and_Centum_City_Skyline_at_Night.jpg/1280px-Busan_Cinema_Center_and_Centum_City_Skyline_at_Night.jpg" },
];

// 기존 시드 좌표 오류 수정 — 반송공원이 부산이 아니라 남중국해 근처 좌표를 갖고 있어
// 거리 기반(haversine) 필터에서 항상 걸러지고 있었다. 카카오 로컬 검색으로 재확인한 실제 좌표로 교정(2026-08-26).
const COORDINATE_FIXES: Array<{ nameKo: string; lat: number; lng: number }> = [
  { nameKo: "반송공원", lat: 35.221441578576766, lng: 129.16162214668637 },
];

// 이전 실행에서 블로그(2차 출처) 기준으로 대략 등록했던 두 곳을, 사용자가 알려준 부산관광공사
// 공식 페이지(visitbusan.net) 주소/명칭으로 다시 맞춘다(2026-08-26). 같은 산 정상/동네를 가리키는
// 지점이라 좌표는 기존 값과 큰 차이가 없지만, 명칭과 행정 주소를 공식 표기로 교정한다.
const PLACE_RENAMES: Array<{ from: string; to: string; address: string; lat: number; lng: number }> = [
  { from: "황령산 전망쉼터", to: "황령산 봉수전망대", address: "부산 부산진구 전포동 산 50-18", lat: 35.1572252656077, lng: 129.081869442441 },
];
const ADDRESS_FIXES: Array<{ nameKo: string; address: string; lat: number; lng: number }> = [
  { nameKo: "호천마을", address: "부산 부산진구 엄광로 491", lat: 35.144212844385, lng: 129.051210579134 },
];

// 야경 탭 전용 사진 — 기존 장소들의 imageUrl은 자연/산책 탭 등에서 낮 사진으로 계속 쓰이므로 건드리지 않고,
// 야경 탭에서만 쓰는 nightImageUrl에 Wikimedia Commons CC 라이선스 야경 사진을 별도로 연결한다(2026-08-26).
const NIGHT_IMAGE_FIXES: Array<{ nameKo: string; nightImageUrl: string }> = [
  { nameKo: "광안리해수욕장", nightImageUrl: "https://upload.wikimedia.org/wikipedia/commons/d/da/Busan_Gwangalli_Night.jpg" },
  { nameKo: "감천문화마을", nightImageUrl: "https://upload.wikimedia.org/wikipedia/commons/thumb/2/23/Gamcheon_Culture_Village_at_Night.jpg/1280px-Gamcheon_Culture_Village_at_Night.jpg" },
  { nameKo: "용두산공원", nightImageUrl: "https://upload.wikimedia.org/wikipedia/commons/thumb/d/da/Yongdusan_Park_at_night2.jpg/1280px-Yongdusan_Park_at_night2.jpg" },
];

// 세분화된 walk_trail/nature_spot 로 대체하는 예전 태그 — 캠핑장류가 여기 남아있었다.
const RETIRED_TAGS = ["nature_walk"];

async function addTag(nameKo: string, tag: string) {
  const place = await prisma.place.findFirst({ where: { nameKo } });
  if (!place) { console.warn(`[skip] not found: ${nameKo}`); return; }
  const tags: string[] = JSON.parse(place.tasteTags);
  if (tags.includes(tag)) return;
  await prisma.place.update({ where: { id: place.id }, data: { tasteTags: JSON.stringify([...tags, tag]) } });
}

async function removeTag(tag: string) {
  const places = await prisma.place.findMany({ where: { tasteTags: { contains: tag } } });
  for (const place of places) {
    const tags: string[] = JSON.parse(place.tasteTags).filter((t: string) => t !== tag);
    await prisma.place.update({ where: { id: place.id }, data: { tasteTags: JSON.stringify(tags) } });
  }
  return places.length;
}

async function removeTagFromNames(names: string[], tag: string) {
  let count = 0;
  for (const nameKo of names) {
    const place = await prisma.place.findFirst({ where: { nameKo } });
    if (!place) continue;
    const tags: string[] = JSON.parse(place.tasteTags).filter((t: string) => t !== tag);
    if (tags.length === JSON.parse(place.tasteTags).length) continue;
    await prisma.place.update({ where: { id: place.id }, data: { tasteTags: JSON.stringify(tags) } });
    count++;
  }
  return count;
}

async function ensurePlace(input: { nameKo: string; address: string; lat: number; lng: number; tags: string[]; nightImageUrl?: string }) {
  const existing = await prisma.place.findFirst({ where: { nameKo: input.nameKo } });
  if (existing) return false;
  await prisma.place.create({ data: {
    nameKo: input.nameKo,
    category: "TOURIST",
    address: input.address,
    lat: input.lat,
    lng: input.lng,
    openTime: "00:00",
    closeTime: "23:59",
    closedDays: "[]",
    recommendedStayMin: 40,
    priceTier: 1,
    localScore: 0.5,
    localScoreSource: "SEED",
    tasteTags: JSON.stringify(input.tags),
    dataSource: "MANUAL",
    nightImageUrl: input.nightImageUrl ?? null,
  } });
  return true;
}

async function fixCoordinates(fixes: Array<{ nameKo: string; lat: number; lng: number }>) {
  let count = 0;
  for (const fix of fixes) {
    const place = await prisma.place.findFirst({ where: { nameKo: fix.nameKo } });
    if (!place) continue;
    await prisma.place.update({ where: { id: place.id }, data: { lat: fix.lat, lng: fix.lng } });
    count++;
  }
  return count;
}

async function applyRenames(renames: typeof PLACE_RENAMES) {
  let count = 0;
  for (const rename of renames) {
    const place = await prisma.place.findFirst({ where: { nameKo: rename.from } });
    if (!place) continue;
    await prisma.place.update({ where: { id: place.id }, data: { nameKo: rename.to, address: rename.address, lat: rename.lat, lng: rename.lng } });
    count++;
  }
  return count;
}

async function applyAddressFixes(fixes: typeof ADDRESS_FIXES) {
  let count = 0;
  for (const fix of fixes) {
    const place = await prisma.place.findFirst({ where: { nameKo: fix.nameKo } });
    if (!place) continue;
    await prisma.place.update({ where: { id: place.id }, data: { address: fix.address, lat: fix.lat, lng: fix.lng } });
    count++;
  }
  return count;
}

async function applyNightImageFixes(fixes: typeof NIGHT_IMAGE_FIXES) {
  let count = 0;
  for (const fix of fixes) {
    const place = await prisma.place.findFirst({ where: { nameKo: fix.nameKo } });
    if (!place) continue;
    await prisma.place.update({ where: { id: place.id }, data: { nightImageUrl: fix.nightImageUrl } });
    count++;
  }
  return count;
}

async function main() {
  const retired: Record<string, number> = {};
  for (const tag of RETIRED_TAGS) retired[tag] = await removeTag(tag);
  for (const name of ADVENTURE_ACTIVITY) await addTag(name, "adventure_activity");
  for (const name of WALK_TRAIL) await addTag(name, "walk_trail");
  for (const name of NATURE_SPOT) await addTag(name, "nature_spot");
  const renames = await applyRenames(PLACE_RENAMES);
  const addressFixes = await applyAddressFixes(ADDRESS_FIXES);
  let newPlaces = 0;
  for (const place of NEW_PLACES) if (await ensurePlace(place)) newPlaces++;
  const coordinateFixes = await fixCoordinates(COORDINATE_FIXES);
  const nightImageFixes = await applyNightImageFixes(NIGHT_IMAGE_FIXES);
  const nightViewRemoved = await removeTagFromNames(NIGHT_VIEW_REMOVE, "nightview");
  for (const name of NIGHT_VIEW) await addTag(name, "nightview");
  console.log(JSON.stringify({ retired, adventureActivity: ADVENTURE_ACTIVITY.length, walkTrail: WALK_TRAIL.length, natureSpot: NATURE_SPOT.length, nightView: NIGHT_VIEW.length, nightViewRemoved, renames, addressFixes, coordinateFixes, nightImageFixes, newPlaces }));
}

main().finally(() => prisma.$disconnect());
