-- DropIndex
DROP INDEX "PetPolicyReport_policyId_createdAt_idx";

-- DropIndex
DROP INDEX "PlacePetPolicy_placeId_key";

-- DropIndex
DROP INDEX "Trip_lodgingPlaceId_idx";

-- DropTable
PRAGMA foreign_keys=off;
DROP TABLE "PetPolicyReport";
PRAGMA foreign_keys=on;

-- DropTable
PRAGMA foreign_keys=off;
DROP TABLE "PlacePetPolicy";
PRAGMA foreign_keys=on;

-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_AdCampaign" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "businessId" TEXT NOT NULL,
    "placeId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "serviceCategory" TEXT NOT NULL DEFAULT 'LODGING',
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "budget" REAL NOT NULL,
    "spent" REAL NOT NULL DEFAULT 0,
    "bidCpc" REAL NOT NULL,
    "targetingModes" TEXT NOT NULL DEFAULT '[]',
    "targetingLanguage" TEXT,
    "startsAt" DATETIME NOT NULL,
    "endsAt" DATETIME NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "AdCampaign_businessId_fkey" FOREIGN KEY ("businessId") REFERENCES "Business" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "AdCampaign_placeId_fkey" FOREIGN KEY ("placeId") REFERENCES "Place" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
INSERT INTO "new_AdCampaign" ("bidCpc", "budget", "businessId", "createdAt", "endsAt", "id", "name", "placeId", "serviceCategory", "spent", "startsAt", "status", "targetingLanguage", "targetingModes", "updatedAt") SELECT "bidCpc", "budget", "businessId", "createdAt", "endsAt", "id", "name", "placeId", "serviceCategory", "spent", "startsAt", "status", "targetingLanguage", "targetingModes", "updatedAt" FROM "AdCampaign";
DROP TABLE "AdCampaign";
ALTER TABLE "new_AdCampaign" RENAME TO "AdCampaign";
CREATE INDEX "AdCampaign_status_startsAt_endsAt_idx" ON "AdCampaign"("status", "startsAt", "endsAt");
CREATE INDEX "AdCampaign_placeId_status_idx" ON "AdCampaign"("placeId", "status");
CREATE TABLE "new_Follow" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "followerSessionId" TEXT NOT NULL,
    "followeeSessionId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'ACTIVE',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "Follow_followerSessionId_fkey" FOREIGN KEY ("followerSessionId") REFERENCES "AnonymousSession" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "Follow_followeeSessionId_fkey" FOREIGN KEY ("followeeSessionId") REFERENCES "AnonymousSession" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
INSERT INTO "new_Follow" ("createdAt", "followeeSessionId", "followerSessionId", "id", "status") SELECT "createdAt", "followeeSessionId", "followerSessionId", "id", "status" FROM "Follow";
DROP TABLE "Follow";
ALTER TABLE "new_Follow" RENAME TO "Follow";
CREATE INDEX "Follow_followeeSessionId_status_idx" ON "Follow"("followeeSessionId", "status");
CREATE UNIQUE INDEX "Follow_followerSessionId_followeeSessionId_key" ON "Follow"("followerSessionId", "followeeSessionId");
CREATE TABLE "new_ItineraryDay" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "itineraryId" TEXT NOT NULL,
    "dayIndex" INTEGER NOT NULL,
    "visitDate" TEXT NOT NULL,
    "dayBudget" REAL NOT NULL,
    "startTravelMin" INTEGER,
    "startDistanceM" INTEGER,
    "startTravelIsEstimate" BOOLEAN NOT NULL DEFAULT true,
    "returnTravelMin" INTEGER,
    "returnDistanceM" INTEGER,
    "returnTravelIsEstimate" BOOLEAN NOT NULL DEFAULT true,
    "lockedBySessionId" TEXT,
    "lockedUntil" DATETIME,
    CONSTRAINT "ItineraryDay_itineraryId_fkey" FOREIGN KEY ("itineraryId") REFERENCES "Itinerary" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
INSERT INTO "new_ItineraryDay" ("dayBudget", "dayIndex", "id", "itineraryId", "lockedBySessionId", "lockedUntil", "returnDistanceM", "returnTravelIsEstimate", "returnTravelMin", "startDistanceM", "startTravelIsEstimate", "startTravelMin", "visitDate") SELECT "dayBudget", "dayIndex", "id", "itineraryId", "lockedBySessionId", "lockedUntil", "returnDistanceM", "returnTravelIsEstimate", "returnTravelMin", "startDistanceM", "startTravelIsEstimate", "startTravelMin", "visitDate" FROM "ItineraryDay";
DROP TABLE "ItineraryDay";
ALTER TABLE "new_ItineraryDay" RENAME TO "ItineraryDay";
CREATE TABLE "new_Story" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "authorSessionId" TEXT NOT NULL,
    "placeId" TEXT NOT NULL,
    "itineraryId" TEXT,
    "itineraryItemId" TEXT,
    "content" TEXT NOT NULL,
    "imageDataJson" TEXT NOT NULL DEFAULT '[]',
    "rating" INTEGER,
    "visibility" TEXT NOT NULL DEFAULT 'PUBLIC',
    "visitVerified" BOOLEAN NOT NULL DEFAULT false,
    "areaLabel" TEXT NOT NULL,
    "publishAt" DATETIME NOT NULL,
    "moderationStatus" TEXT NOT NULL DEFAULT 'PUBLISHED',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "Story_authorSessionId_fkey" FOREIGN KEY ("authorSessionId") REFERENCES "AnonymousSession" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "Story_placeId_fkey" FOREIGN KEY ("placeId") REFERENCES "Place" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "Story_itineraryId_fkey" FOREIGN KEY ("itineraryId") REFERENCES "Itinerary" ("id") ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT "Story_itineraryItemId_fkey" FOREIGN KEY ("itineraryItemId") REFERENCES "ItineraryItem" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);
INSERT INTO "new_Story" ("areaLabel", "authorSessionId", "content", "createdAt", "id", "imageDataJson", "itineraryId", "itineraryItemId", "moderationStatus", "placeId", "publishAt", "rating", "updatedAt", "visibility", "visitVerified") SELECT "areaLabel", "authorSessionId", "content", "createdAt", "id", "imageDataJson", "itineraryId", "itineraryItemId", "moderationStatus", "placeId", "publishAt", "rating", "updatedAt", "visibility", "visitVerified" FROM "Story";
DROP TABLE "Story";
ALTER TABLE "new_Story" RENAME TO "Story";
CREATE INDEX "Story_authorSessionId_publishAt_idx" ON "Story"("authorSessionId", "publishAt");
CREATE INDEX "Story_placeId_moderationStatus_publishAt_idx" ON "Story"("placeId", "moderationStatus", "publishAt");
CREATE TABLE "new_TripPreference" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tripId" TEXT NOT NULL,
    "tasteTags" TEXT NOT NULL,
    "courseCategory" TEXT,
    "mustVisitPlaceIds" TEXT NOT NULL,
    "excludedPlaceIds" TEXT NOT NULL,
    "language" TEXT NOT NULL DEFAULT 'KO',
    "needsEnglishMenu" BOOLEAN NOT NULL DEFAULT false,
    "needsForeignCard" BOOLEAN NOT NULL DEFAULT false,
    "allergies" TEXT NOT NULL DEFAULT '[]',
    "dietType" TEXT NOT NULL DEFAULT 'NONE',
    "needsOnlineReservation" BOOLEAN NOT NULL DEFAULT false,
    "maxTransferCount" INTEGER NOT NULL DEFAULT 2,
    "landmarkRatio" INTEGER NOT NULL DEFAULT 30,
    "localRatio" INTEGER NOT NULL DEFAULT 50,
    "easyRatio" INTEGER NOT NULL DEFAULT 20,
    CONSTRAINT "TripPreference_tripId_fkey" FOREIGN KEY ("tripId") REFERENCES "Trip" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
INSERT INTO "new_TripPreference" ("allergies", "courseCategory", "dietType", "excludedPlaceIds", "id", "landmarkRatio", "language", "localRatio", "maxTransferCount", "mustVisitPlaceIds", "needsEnglishMenu", "needsForeignCard", "needsOnlineReservation", "tasteTags", "tripId") SELECT "allergies", "courseCategory", "dietType", "excludedPlaceIds", "id", "landmarkRatio", "language", "localRatio", "maxTransferCount", "mustVisitPlaceIds", "needsEnglishMenu", "needsForeignCard", "needsOnlineReservation", "tasteTags", "tripId" FROM "TripPreference";
DROP TABLE "TripPreference";
ALTER TABLE "new_TripPreference" RENAME TO "TripPreference";
CREATE UNIQUE INDEX "TripPreference_tripId_key" ON "TripPreference"("tripId");
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;

