-- CreateTable
CREATE TABLE "Place" (
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
    "tasteTags" TEXT NOT NULL
);

-- CreateTable
CREATE TABLE "PlacePetPolicy" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "placeId" TEXT NOT NULL,
    "allowed" BOOLEAN NOT NULL,
    "indoorAllowed" BOOLEAN NOT NULL,
    "outdoorAllowed" BOOLEAN NOT NULL,
    "sizeLimit" TEXT NOT NULL,
    "extraFee" REAL NOT NULL DEFAULT 0,
    "freshnessGrade" TEXT NOT NULL,
    CONSTRAINT "PlacePetPolicy_placeId_fkey" FOREIGN KEY ("placeId") REFERENCES "Place" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "Trip" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "origin" TEXT NOT NULL,
    "originLat" REAL NOT NULL,
    "originLng" REAL NOT NULL,
    "startDate" TEXT NOT NULL,
    "endDate" TEXT NOT NULL,
    "partySize" INTEGER NOT NULL,
    "totalBudget" REAL NOT NULL,
    "hasCar" BOOLEAN NOT NULL,
    "pace" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CreateTable
CREATE TABLE "TripPreference" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tripId" TEXT NOT NULL,
    "tasteTags" TEXT NOT NULL,
    "mustVisitPlaceIds" TEXT NOT NULL,
    "excludedPlaceIds" TEXT NOT NULL,
    "hasPet" BOOLEAN NOT NULL DEFAULT false,
    "petSize" TEXT,
    CONSTRAINT "TripPreference_tripId_fkey" FOREIGN KEY ("tripId") REFERENCES "Trip" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "Itinerary" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tripId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'DONE',
    "generatedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "Itinerary_tripId_fkey" FOREIGN KEY ("tripId") REFERENCES "Trip" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ItineraryDay" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "itineraryId" TEXT NOT NULL,
    "dayIndex" INTEGER NOT NULL,
    "visitDate" TEXT NOT NULL,
    "dayBudget" REAL NOT NULL,
    CONSTRAINT "ItineraryDay_itineraryId_fkey" FOREIGN KEY ("itineraryId") REFERENCES "Itinerary" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ItineraryItem" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "dayId" TEXT NOT NULL,
    "placeId" TEXT NOT NULL,
    "seqOrder" INTEGER NOT NULL,
    "plannedArrival" TEXT NOT NULL,
    "stayMinutes" INTEGER NOT NULL,
    "estCost" REAL NOT NULL,
    "travelMinToNext" INTEGER,
    "distanceToNextM" INTEGER,
    "recommendReason" TEXT NOT NULL,
    "isPinned" BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT "ItineraryItem_dayId_fkey" FOREIGN KEY ("dayId") REFERENCES "ItineraryDay" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "ItineraryItem_placeId_fkey" FOREIGN KEY ("placeId") REFERENCES "Place" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateIndex
CREATE UNIQUE INDEX "PlacePetPolicy_placeId_key" ON "PlacePetPolicy"("placeId");

-- CreateIndex
CREATE UNIQUE INDEX "TripPreference_tripId_key" ON "TripPreference"("tripId");
