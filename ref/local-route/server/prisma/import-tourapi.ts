import "dotenv/config";
import { PrismaClient } from "@prisma/client";
import { deriveForeignConvenience } from "../src/lib/foreignConvenience.js";
import { haversineDistanceM } from "../src/services/kakao.js";
import {
  fetchAreaBasedList,
  fetchDetailIntro,
  fetchEnglishName,
  parseClosedDays,
  parseTimeRange,
} from "../src/lib/tourapi.js";

const prisma = new PrismaClient();

const BUSAN_AREA_CODE = 6;
const ROWS_PER_TYPE = 40;
// 이름이 겹치면 실제로는 같은 장소인데 좌표 소수점 오차(수기 입력 vs TourAPI 실측)로
// 100~400m씩 벌어지는 사례가 실측 확인됨 → 이름 매칭이 신뢰도의 핵심이므로 반경을 넉넉히 잡는다.
const DEDUP_RADIUS_M = 600;

type Category = "TOURIST" | "RESTAURANT" | "CAFE" | "LODGING";

const CAFE_KEYWORD = /카페|커피|베이커리|디저트|빵집|찻집|브런치/;

const CONTENT_TYPE_CONFIG: Record<
  number,
  { category?: Category; stayMin: number; priceTier: number; tasteTags: string[] }
> = {
  12: { category: "TOURIST", stayMin: 90, priceTier: 1, tasteTags: ["landmark", "photo"] },
  14: { category: "TOURIST", stayMin: 90, priceTier: 1, tasteTags: ["culture", "indoor"] },
  28: { category: "TOURIST", stayMin: 90, priceTier: 2, tasteTags: ["activity"] },
  38: { category: "TOURIST", stayMin: 60, priceTier: 2, tasteTags: ["shopping"] },
  32: { category: "LODGING", stayMin: 480, priceTier: 3, tasteTags: ["relax"] },
  39: { stayMin: 50, priceTier: 2, tasteTags: ["food"] }, // RESTAURANT/CAFE는 제목으로 분리
};

function resolveCategory(contentTypeId: number, title: string): { category: Category; tasteTags: string[] } {
  const cfg = CONTENT_TYPE_CONFIG[contentTypeId];
  if (cfg.category) return { category: cfg.category, tasteTags: cfg.tasteTags };
  const isCafe = CAFE_KEYWORD.test(title);
  return { category: isCafe ? "CAFE" : "RESTAURANT", tasteTags: isCafe ? ["cafe"] : ["food"] };
}

function normalizeName(name: string): string {
  return name.replace(/\s+/g, "").toLowerCase();
}

interface ManualRef {
  nameKo: string;
  lat: number;
  lng: number;
}

function isDuplicateOfManual(candidate: { nameKo: string; lat: number; lng: number }, manual: ManualRef[]): boolean {
  const candNorm = normalizeName(candidate.nameKo);
  return manual.some((m) => {
    if (haversineDistanceM(candidate.lat, candidate.lng, m.lat, m.lng) > DEDUP_RADIUS_M) return false;
    const mNorm = normalizeName(m.nameKo);
    return candNorm.includes(mNorm) || mNorm.includes(candNorm);
  });
}

async function main() {
  console.log("1) 기존 수기 시드(MANUAL) 로드 — 중복 제거 기준");
  const manualPlaces = await prisma.place.findMany({
    where: { dataSource: "MANUAL" },
    select: { nameKo: true, lat: true, lng: true },
  });
  console.log(`   MANUAL 장소 ${manualPlaces.length}곳`);

  const seenContentIds = new Set<string>();
  let created = 0;
  let skippedDuplicate = 0;
  let skippedNoCoords = 0;

  for (const contentTypeId of Object.keys(CONTENT_TYPE_CONFIG).map(Number)) {
    console.log(`\n3) contentTypeId=${contentTypeId} 목록 조회 (최대 ${ROWS_PER_TYPE}건)`);
    const list = await fetchAreaBasedList(contentTypeId, BUSAN_AREA_CODE, ROWS_PER_TYPE, 1);
    console.log(`   ${list.length}건 수신`);

    for (const item of list) {
      if (seenContentIds.has(item.contentid)) continue;
      seenContentIds.add(item.contentid);

      const lat = Number(item.mapy);
      const lng = Number(item.mapx);
      if (!lat || !lng) {
        skippedNoCoords++;
        continue;
      }

      if (isDuplicateOfManual({ nameKo: item.title, lat, lng }, manualPlaces)) {
        skippedDuplicate++;
        continue;
      }

      const { category, tasteTags } = resolveCategory(contentTypeId, item.title);

      let openTime = "00:00";
      let closeTime = "23:59";
      let closedDays: string[] = [];
      let parkingAvailable = false;
      try {
        const intro = await fetchDetailIntro(item.contentid, contentTypeId);
        const parsed = parseTimeRange(intro.openTimeRaw);
        if (parsed) {
          openTime = parsed.openTime;
          closeTime = parsed.closeTime;
        }
        closedDays = parseClosedDays(intro.restDateRaw);
        parkingAvailable = intro.parkingAvailable;
      } catch (e) {
        console.warn(`   detailIntro2 실패 (${item.contentid}): ${(e as Error).message}`);
      }

      const nameEn = await fetchEnglishName(item.contentid);

      const { englishMenu, cardPayment } = deriveForeignConvenience({
        category,
        priceTier: CONTENT_TYPE_CONFIG[contentTypeId].priceTier,
        address: item.addr1,
        nameKo: item.title,
      });

      await prisma.place.create({
        data: {
          nameKo: item.title,
          nameEn,
          category,
          address: item.addr1,
          lat,
          lng,
          openTime,
          closeTime,
          closedDays: JSON.stringify(closedDays),
          recommendedStayMin: CONTENT_TYPE_CONFIG[contentTypeId].stayMin,
          priceTier: CONTENT_TYPE_CONFIG[contentTypeId].priceTier,
          parkingAvailable,
          reservationRequired: false,
          // TourAPI에는 우리 12.4장 로컬점수에 대응하는 지표가 없어 콜드스타트 중립값(0.5)으로 시작한다.
          localScore: 0.5,
          tasteTags: JSON.stringify(tasteTags),
          hasEnglishMenu: englishMenu || !!nameEn,
          foreignCardPayment: cardPayment,
          contentId: item.contentid,
          dataSource: "TOURAPI",
          imageUrl: item.firstimage || null,
        },
      });
      created++;
    }
  }

  console.log("\n=== 임포트 완료 ===");
  console.log(`생성: ${created}건`);
  console.log(`MANUAL과 중복으로 스킵: ${skippedDuplicate}건`);
  console.log(`좌표 없음으로 스킵: ${skippedNoCoords}건`);
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
