-- AlterTable
ALTER TABLE "Trip" ADD COLUMN "adultCount" INTEGER NOT NULL DEFAULT 1;
ALTER TABLE "Trip" ADD COLUMN "childCount" INTEGER NOT NULL DEFAULT 0;

-- AlterTable
ALTER TABLE "TripPreference" ADD COLUMN "allergies" TEXT NOT NULL DEFAULT '[]';
ALTER TABLE "TripPreference" ADD COLUMN "dietType" TEXT NOT NULL DEFAULT 'NONE';
ALTER TABLE "TripPreference" ADD COLUMN "needsOnlineReservation" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "TripPreference" ADD COLUMN "maxTransferCount" INTEGER NOT NULL DEFAULT 2;
ALTER TABLE "TripPreference" ADD COLUMN "petWeightKg" REAL;
ALTER TABLE "TripPreference" ADD COLUMN "petCount" INTEGER NOT NULL DEFAULT 1;
ALTER TABLE "TripPreference" ADD COLUMN "usesPetStroller" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "TripPreference" ADD COLUMN "petRestaurantRequired" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "TripPreference" ADD COLUMN "petLodgingRequired" BOOLEAN NOT NULL DEFAULT false;
