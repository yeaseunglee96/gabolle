import "dotenv/config";
import { readFile } from "node:fs/promises";
import { PrismaClient } from "@prisma/client";

type ReviewAggregate = {
  placeId?: string;
  kakaoPlaceId: string;
  kakaoPlaceUrl: string;
  rating: number;
  reviewCount: number;
  positiveReviewRate?: number;
  keywords?: string[];
  collectedAt: string;
  source: "LICENSED_IMPORT" | "MANUAL_VERIFIED";
};

const inputPath = process.argv[2];
if (!inputPath) throw new Error("사용법: npm run import:kakao-reviews -- <승인된-집계파일.json>");

const payload = JSON.parse(await readFile(inputPath, "utf8")) as ReviewAggregate[];
if (!Array.isArray(payload)) throw new Error("입력 파일은 배열이어야 합니다.");

const prisma = new PrismaClient();
let updated = 0;

try {
  for (const row of payload) {
    if (!["LICENSED_IMPORT", "MANUAL_VERIFIED"].includes(row.source)) throw new Error(`허용되지 않은 출처: ${row.source}`);
    if (!/^https?:\/\/place\.map\.kakao\.com\/\d+\/?$/.test(row.kakaoPlaceUrl)) throw new Error(`잘못된 카카오맵 URL: ${row.kakaoPlaceUrl}`);
    if (!row.kakaoPlaceId || row.rating < 0 || row.rating > 5 || !Number.isInteger(row.reviewCount) || row.reviewCount < 0) throw new Error(`잘못된 평점 집계: ${row.kakaoPlaceId}`);
    if (row.positiveReviewRate !== undefined && (row.positiveReviewRate < 0 || row.positiveReviewRate > 1)) throw new Error(`잘못된 긍정 후기 비율: ${row.kakaoPlaceId}`);
    const collectedAt = new Date(row.collectedAt);
    if (Number.isNaN(collectedAt.getTime()) || collectedAt > new Date()) throw new Error(`잘못된 수집 시점: ${row.kakaoPlaceId}`);

    const place = row.placeId
      ? await prisma.place.findUnique({ where: { id: row.placeId } })
      : await prisma.place.findFirst({ where: { kakaoPlaceId: row.kakaoPlaceId } });
    if (!place) throw new Error(`LOCAL ROUTE 장소를 찾을 수 없습니다: ${row.placeId ?? row.kakaoPlaceId}`);
    if (place.category !== "RESTAURANT") throw new Error(`식당이 아닌 장소에는 카카오 식당 리뷰를 연결할 수 없습니다: ${place.nameKo}`);

    await prisma.place.update({
      where: { id: place.id },
      data: {
        kakaoPlaceId: row.kakaoPlaceId,
        kakaoPlaceUrl: row.kakaoPlaceUrl,
        kakaoRating: row.rating,
        kakaoReviewCount: row.reviewCount,
        kakaoPositiveReviewRate: row.positiveReviewRate ?? null,
        kakaoReviewKeywords: JSON.stringify((row.keywords ?? []).slice(0, 8).map((value) => value.trim()).filter(Boolean)),
        kakaoReviewSource: row.source,
        kakaoReviewCollectedAt: collectedAt,
      },
    });
    updated += 1;
  }
  console.log(`검증된 카카오 리뷰 집계 ${updated}건을 반영했습니다.`);
} finally {
  await prisma.$disconnect();
}
