ALTER TABLE "TripPreference" ADD COLUMN "landmarkRatio" INTEGER NOT NULL DEFAULT 30;
ALTER TABLE "TripPreference" ADD COLUMN "localRatio" INTEGER NOT NULL DEFAULT 50;
ALTER TABLE "TripPreference" ADD COLUMN "petRatio" INTEGER NOT NULL DEFAULT 20;
ALTER TABLE "Itinerary" ADD COLUMN "mode" TEXT NOT NULL DEFAULT 'LOCAL';
CREATE INDEX "Itinerary_tripId_mode_generatedAt_idx" ON "Itinerary"("tripId", "mode", "generatedAt");
