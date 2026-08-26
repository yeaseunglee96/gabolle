CREATE TABLE "ItineraryRevision" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "itineraryId" TEXT NOT NULL,
  "dayIndex" INTEGER NOT NULL,
  "action" TEXT NOT NULL,
  "beforeJson" TEXT NOT NULL,
  "afterJson" TEXT NOT NULL,
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "undoneAt" DATETIME,
  CONSTRAINT "ItineraryRevision_itineraryId_fkey" FOREIGN KEY ("itineraryId") REFERENCES "Itinerary"("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
CREATE INDEX "ItineraryRevision_itineraryId_createdAt_idx" ON "ItineraryRevision"("itineraryId", "createdAt");
