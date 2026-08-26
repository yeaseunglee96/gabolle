ALTER TABLE "Trip" ADD COLUMN "lodgingPlaceId" TEXT REFERENCES "Place"("id") ON DELETE SET NULL ON UPDATE CASCADE;
CREATE INDEX "Trip_lodgingPlaceId_idx" ON "Trip"("lodgingPlaceId");
ALTER TABLE "ItineraryDay" ADD COLUMN "startTravelMin" INTEGER;
ALTER TABLE "ItineraryDay" ADD COLUMN "startDistanceM" INTEGER;
ALTER TABLE "ItineraryDay" ADD COLUMN "startTravelIsEstimate" BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE "ItineraryDay" ADD COLUMN "returnTravelMin" INTEGER;
ALTER TABLE "ItineraryDay" ADD COLUMN "returnDistanceM" INTEGER;
ALTER TABLE "ItineraryDay" ADD COLUMN "returnTravelIsEstimate" BOOLEAN NOT NULL DEFAULT true;
