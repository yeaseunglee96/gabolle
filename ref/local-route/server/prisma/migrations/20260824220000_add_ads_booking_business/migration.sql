CREATE TABLE "Business" (
  "id" TEXT NOT NULL PRIMARY KEY, "name" TEXT NOT NULL, "contactEmail" TEXT NOT NULL,
  "ownerSessionHash" TEXT NOT NULL, "status" TEXT NOT NULL DEFAULT 'PENDING', "subscriptionPlan" TEXT NOT NULL DEFAULT 'FREE',
  "placeId" TEXT, "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "updatedAt" DATETIME NOT NULL,
  CONSTRAINT "Business_placeId_fkey" FOREIGN KEY ("placeId") REFERENCES "Place" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "Business_placeId_key" ON "Business"("placeId");

CREATE TABLE "AdCampaign" (
  "id" TEXT NOT NULL PRIMARY KEY, "businessId" TEXT NOT NULL, "placeId" TEXT NOT NULL, "name" TEXT NOT NULL,
  "status" TEXT NOT NULL DEFAULT 'DRAFT', "budget" REAL NOT NULL, "spent" REAL NOT NULL DEFAULT 0, "bidCpc" REAL NOT NULL,
  "targetingModes" TEXT NOT NULL DEFAULT '[]', "targetingHasPet" BOOLEAN, "targetingLanguage" TEXT,
  "startsAt" DATETIME NOT NULL, "endsAt" DATETIME NOT NULL, "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "updatedAt" DATETIME NOT NULL,
  CONSTRAINT "AdCampaign_businessId_fkey" FOREIGN KEY ("businessId") REFERENCES "Business" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT "AdCampaign_placeId_fkey" FOREIGN KEY ("placeId") REFERENCES "Place" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
CREATE INDEX "AdCampaign_status_startsAt_endsAt_idx" ON "AdCampaign"("status", "startsAt", "endsAt");
CREATE INDEX "AdCampaign_placeId_status_idx" ON "AdCampaign"("placeId", "status");

CREATE TABLE "AdLedger" (
  "id" TEXT NOT NULL PRIMARY KEY, "eventId" TEXT NOT NULL, "campaignId" TEXT NOT NULL, "eventType" TEXT NOT NULL,
  "actorId" TEXT, "amount" REAL NOT NULL DEFAULT 0, "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "AdLedger_campaignId_fkey" FOREIGN KEY ("campaignId") REFERENCES "AdCampaign" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "AdLedger_eventId_key" ON "AdLedger"("eventId");
CREATE INDEX "AdLedger_campaignId_createdAt_idx" ON "AdLedger"("campaignId", "createdAt");

CREATE TABLE "BookingPartner" (
  "id" TEXT NOT NULL PRIMARY KEY, "placeId" TEXT NOT NULL, "provider" TEXT NOT NULL, "bookingUrl" TEXT NOT NULL,
  "commissionRate" REAL NOT NULL DEFAULT 0, "active" BOOLEAN NOT NULL DEFAULT true, "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "BookingPartner_placeId_fkey" FOREIGN KEY ("placeId") REFERENCES "Place" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "BookingPartner_placeId_provider_key" ON "BookingPartner"("placeId", "provider");

CREATE TABLE "BookingRecord" (
  "id" TEXT NOT NULL PRIMARY KEY, "eventId" TEXT NOT NULL, "partnerId" TEXT NOT NULL, "actorId" TEXT, "tripId" TEXT,
  "status" TEXT NOT NULL DEFAULT 'STARTED', "amount" REAL, "commission" REAL, "externalRef" TEXT,
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "updatedAt" DATETIME NOT NULL,
  CONSTRAINT "BookingRecord_partnerId_fkey" FOREIGN KEY ("partnerId") REFERENCES "BookingPartner" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "BookingRecord_eventId_key" ON "BookingRecord"("eventId");
CREATE INDEX "BookingRecord_partnerId_status_createdAt_idx" ON "BookingRecord"("partnerId", "status", "createdAt");
