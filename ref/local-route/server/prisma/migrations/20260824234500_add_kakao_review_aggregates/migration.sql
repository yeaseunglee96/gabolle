ALTER TABLE "Place" ADD COLUMN "kakaoPlaceId" TEXT;
ALTER TABLE "Place" ADD COLUMN "kakaoPlaceUrl" TEXT;
ALTER TABLE "Place" ADD COLUMN "kakaoRating" REAL;
ALTER TABLE "Place" ADD COLUMN "kakaoReviewCount" INTEGER;
ALTER TABLE "Place" ADD COLUMN "kakaoPositiveReviewRate" REAL;
ALTER TABLE "Place" ADD COLUMN "kakaoReviewKeywords" TEXT NOT NULL DEFAULT '[]';
ALTER TABLE "Place" ADD COLUMN "kakaoReviewSource" TEXT;
ALTER TABLE "Place" ADD COLUMN "kakaoReviewCollectedAt" DATETIME;

CREATE UNIQUE INDEX "Place_kakaoPlaceId_key" ON "Place"("kakaoPlaceId");
