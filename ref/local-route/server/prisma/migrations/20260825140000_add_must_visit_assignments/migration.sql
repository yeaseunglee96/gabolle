-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_TripPreference" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tripId" TEXT NOT NULL,
    "tasteTags" TEXT NOT NULL,
    "courseCategory" TEXT,
    "mustVisitPlaceIds" TEXT NOT NULL,
    "mustVisitAssignments" TEXT NOT NULL DEFAULT '[]',
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
INSERT INTO "new_TripPreference" ("allergies", "courseCategory", "dietType", "easyRatio", "excludedPlaceIds", "id", "landmarkRatio", "language", "localRatio", "maxTransferCount", "mustVisitPlaceIds", "needsEnglishMenu", "needsForeignCard", "needsOnlineReservation", "tasteTags", "tripId") SELECT "allergies", "courseCategory", "dietType", "easyRatio", "excludedPlaceIds", "id", "landmarkRatio", "language", "localRatio", "maxTransferCount", "mustVisitPlaceIds", "needsEnglishMenu", "needsForeignCard", "needsOnlineReservation", "tasteTags", "tripId" FROM "TripPreference";
DROP TABLE "TripPreference";
ALTER TABLE "new_TripPreference" RENAME TO "TripPreference";
CREATE UNIQUE INDEX "TripPreference_tripId_key" ON "TripPreference"("tripId");
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;

