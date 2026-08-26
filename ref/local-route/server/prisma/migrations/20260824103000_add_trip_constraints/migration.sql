-- Preserve existing records while making the new UX conditions durable.
ALTER TABLE "Trip" ADD COLUMN "dayStart" TEXT NOT NULL DEFAULT '09:30';
ALTER TABLE "Trip" ADD COLUMN "dayEnd" TEXT NOT NULL DEFAULT '20:00';
ALTER TABLE "Trip" ADD COLUMN "maxWalkingKm" REAL NOT NULL DEFAULT 8;
ALTER TABLE "Trip" ADD COLUMN "recommendationMode" TEXT NOT NULL DEFAULT 'LOCAL';

ALTER TABLE "TripPreference" ADD COLUMN "language" TEXT NOT NULL DEFAULT 'KO';
ALTER TABLE "TripPreference" ADD COLUMN "needsEnglishMenu" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "TripPreference" ADD COLUMN "needsForeignCard" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "TripPreference" ADD COLUMN "petIndoorRequired" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "TripPreference" ADD COLUMN "usesPetCarrier" BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE "ItineraryItem" ADD COLUMN "travelIsEstimate" BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE "ItineraryItem" ADD COLUMN "travelSource" TEXT NOT NULL DEFAULT 'HAVERSINE';
