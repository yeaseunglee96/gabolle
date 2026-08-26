-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_Place" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "nameKo" TEXT NOT NULL,
    "nameEn" TEXT,
    "category" TEXT NOT NULL,
    "address" TEXT NOT NULL,
    "lat" REAL NOT NULL,
    "lng" REAL NOT NULL,
    "openTime" TEXT NOT NULL,
    "closeTime" TEXT NOT NULL,
    "closedDays" TEXT NOT NULL,
    "recommendedStayMin" INTEGER NOT NULL,
    "priceTier" INTEGER NOT NULL,
    "parkingAvailable" BOOLEAN NOT NULL DEFAULT false,
    "reservationRequired" BOOLEAN NOT NULL DEFAULT false,
    "localScore" REAL NOT NULL,
    "tasteTags" TEXT NOT NULL,
    "hasEnglishMenu" BOOLEAN NOT NULL DEFAULT false,
    "foreignCardPayment" BOOLEAN NOT NULL DEFAULT false,
    "contentId" TEXT,
    "dataSource" TEXT NOT NULL DEFAULT 'MANUAL',
    "imageUrl" TEXT
);
INSERT INTO "new_Place" ("address", "category", "closeTime", "closedDays", "foreignCardPayment", "hasEnglishMenu", "id", "lat", "lng", "localScore", "nameEn", "nameKo", "openTime", "parkingAvailable", "priceTier", "recommendedStayMin", "reservationRequired", "tasteTags") SELECT "address", "category", "closeTime", "closedDays", "foreignCardPayment", "hasEnglishMenu", "id", "lat", "lng", "localScore", "nameEn", "nameKo", "openTime", "parkingAvailable", "priceTier", "recommendedStayMin", "reservationRequired", "tasteTags" FROM "Place";
DROP TABLE "Place";
ALTER TABLE "new_Place" RENAME TO "Place";
CREATE UNIQUE INDEX "Place_contentId_key" ON "Place"("contentId");
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;
