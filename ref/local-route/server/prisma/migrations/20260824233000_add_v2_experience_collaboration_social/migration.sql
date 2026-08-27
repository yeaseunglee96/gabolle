ALTER TABLE "Place" ADD COLUMN "eventStartDate" TEXT;
ALTER TABLE "Place" ADD COLUMN "eventEndDate" TEXT;
ALTER TABLE "Place" ADD COLUMN "playTime" TEXT;
ALTER TABLE "Place" ADD COLUMN "isOutdoor" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "Place" ADD COLUMN "souvenirItems" TEXT NOT NULL DEFAULT '[]';
ALTER TABLE "Place" ADD COLUMN "foreignAssistance" BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE "AdCampaign" ADD COLUMN "serviceCategory" TEXT NOT NULL DEFAULT 'LODGING';
ALTER TABLE "Trip" ADD COLUMN "ownerSessionId" TEXT REFERENCES "AnonymousSession"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "Itinerary" ADD COLUMN "version" INTEGER NOT NULL DEFAULT 1;
ALTER TABLE "ItineraryDay" ADD COLUMN "lockedBySessionId" TEXT;
ALTER TABLE "ItineraryDay" ADD COLUMN "lockedUntil" DATETIME;
ALTER TABLE "ItineraryItem" ADD COLUMN "version" INTEGER NOT NULL DEFAULT 1;

CREATE TABLE "TripMember" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "tripId" TEXT NOT NULL,
  "sessionId" TEXT,
  "role" TEXT NOT NULL DEFAULT 'VIEWER',
  "inviteTokenHash" TEXT,
  "invitedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "expiresAt" DATETIME,
  "joinedAt" DATETIME,
  "revokedAt" DATETIME,
  CONSTRAINT "TripMember_tripId_fkey" FOREIGN KEY ("tripId") REFERENCES "Trip"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "TripMember_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES "AnonymousSession"("id") ON DELETE SET NULL ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "TripMember_inviteTokenHash_key" ON "TripMember"("inviteTokenHash");
CREATE UNIQUE INDEX "TripMember_tripId_sessionId_key" ON "TripMember"("tripId", "sessionId");
CREATE INDEX "TripMember_tripId_role_revokedAt_idx" ON "TripMember"("tripId", "role", "revokedAt");

CREATE TABLE "ItineraryShare" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "itineraryId" TEXT NOT NULL,
  "ownerSessionId" TEXT NOT NULL,
  "shareSlug" TEXT NOT NULL,
  "visibility" TEXT NOT NULL DEFAULT 'LINK',
  "viewCount" INTEGER NOT NULL DEFAULT 0,
  "cloneCount" INTEGER NOT NULL DEFAULT 0,
  "expiresAt" DATETIME NOT NULL,
  "revokedAt" DATETIME,
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "ItineraryShare_itineraryId_fkey" FOREIGN KEY ("itineraryId") REFERENCES "Itinerary"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "ItineraryShare_ownerSessionId_fkey" FOREIGN KEY ("ownerSessionId") REFERENCES "AnonymousSession"("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "ItineraryShare_shareSlug_key" ON "ItineraryShare"("shareSlug");
CREATE INDEX "ItineraryShare_itineraryId_expiresAt_idx" ON "ItineraryShare"("itineraryId", "expiresAt");

CREATE TABLE "Story" (
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
  "petTagged" BOOLEAN NOT NULL DEFAULT false,
  "areaLabel" TEXT NOT NULL,
  "publishAt" DATETIME NOT NULL,
  "moderationStatus" TEXT NOT NULL DEFAULT 'PUBLISHED',
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" DATETIME NOT NULL,
  CONSTRAINT "Story_authorSessionId_fkey" FOREIGN KEY ("authorSessionId") REFERENCES "AnonymousSession"("id") ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT "Story_placeId_fkey" FOREIGN KEY ("placeId") REFERENCES "Place"("id") ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT "Story_itineraryId_fkey" FOREIGN KEY ("itineraryId") REFERENCES "Itinerary"("id") ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT "Story_itineraryItemId_fkey" FOREIGN KEY ("itineraryItemId") REFERENCES "ItineraryItem"("id") ON DELETE SET NULL ON UPDATE CASCADE
);
CREATE INDEX "Story_authorSessionId_publishAt_idx" ON "Story"("authorSessionId", "publishAt");
CREATE INDEX "Story_placeId_moderationStatus_publishAt_idx" ON "Story"("placeId", "moderationStatus", "publishAt");

CREATE TABLE "StoryReport" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "storyId" TEXT NOT NULL,
  "reporterSessionId" TEXT NOT NULL,
  "reason" TEXT NOT NULL,
  "status" TEXT NOT NULL DEFAULT 'PENDING',
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "resolvedAt" DATETIME,
  CONSTRAINT "StoryReport_storyId_fkey" FOREIGN KEY ("storyId") REFERENCES "Story"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "StoryReport_reporterSessionId_fkey" FOREIGN KEY ("reporterSessionId") REFERENCES "AnonymousSession"("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "StoryReport_storyId_reporterSessionId_key" ON "StoryReport"("storyId", "reporterSessionId");
CREATE INDEX "StoryReport_status_createdAt_idx" ON "StoryReport"("status", "createdAt");

CREATE TABLE "Follow" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "followerSessionId" TEXT NOT NULL,
  "followeeSessionId" TEXT NOT NULL,
  "status" TEXT NOT NULL DEFAULT 'ACTIVE',
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "Follow_followerSessionId_fkey" FOREIGN KEY ("followerSessionId") REFERENCES "AnonymousSession"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "Follow_followeeSessionId_fkey" FOREIGN KEY ("followeeSessionId") REFERENCES "AnonymousSession"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "Follow_followerSessionId_followeeSessionId_key" ON "Follow"("followerSessionId", "followeeSessionId");
CREATE INDEX "Follow_followeeSessionId_status_idx" ON "Follow"("followeeSessionId", "status");
