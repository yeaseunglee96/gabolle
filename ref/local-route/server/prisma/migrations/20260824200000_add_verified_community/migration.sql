ALTER TABLE "Place" ADD COLUMN "localScoreSource" TEXT NOT NULL DEFAULT 'SEED';
ALTER TABLE "Place" ADD COLUMN "localScoreUpdatedAt" DATETIME;

CREATE TABLE "AnonymousSession" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "tokenHash" TEXT NOT NULL,
  "locale" TEXT NOT NULL DEFAULT 'KO',
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "lastSeenAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "expiresAt" DATETIME NOT NULL
);
CREATE UNIQUE INDEX "AnonymousSession_tokenHash_key" ON "AnonymousSession"("tokenHash");

CREATE TABLE "VisitVerification" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "sessionId" TEXT NOT NULL,
  "placeId" TEXT NOT NULL,
  "visitDate" TEXT NOT NULL,
  "arrivedAt" DATETIME NOT NULL,
  "departedAt" DATETIME NOT NULL,
  "latitude" REAL NOT NULL,
  "longitude" REAL NOT NULL,
  "distanceM" INTEGER NOT NULL,
  "dwellMinutes" INTEGER NOT NULL,
  "status" TEXT NOT NULL DEFAULT 'VERIFIED',
  "rejectionCode" TEXT,
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "VisitVerification_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES "AnonymousSession" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT "VisitVerification_placeId_fkey" FOREIGN KEY ("placeId") REFERENCES "Place" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "VisitVerification_sessionId_placeId_visitDate_key" ON "VisitVerification"("sessionId", "placeId", "visitDate");
CREATE INDEX "VisitVerification_placeId_status_createdAt_idx" ON "VisitVerification"("placeId", "status", "createdAt");

CREATE TABLE "Review" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "sessionId" TEXT NOT NULL,
  "placeId" TEXT NOT NULL,
  "verificationId" TEXT NOT NULL,
  "rating" INTEGER NOT NULL,
  "body" TEXT NOT NULL,
  "language" TEXT NOT NULL DEFAULT 'KO',
  "status" TEXT NOT NULL DEFAULT 'PUBLISHED',
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" DATETIME NOT NULL,
  CONSTRAINT "Review_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES "AnonymousSession" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT "Review_placeId_fkey" FOREIGN KEY ("placeId") REFERENCES "Place" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT "Review_verificationId_fkey" FOREIGN KEY ("verificationId") REFERENCES "VisitVerification" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "Review_verificationId_key" ON "Review"("verificationId");
CREATE INDEX "Review_placeId_status_createdAt_idx" ON "Review"("placeId", "status", "createdAt");

CREATE TABLE "LocalProfile" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "sessionId" TEXT NOT NULL,
  "grade" TEXT NOT NULL DEFAULT 'NEWCOMER',
  "verifiedVisitCount" INTEGER NOT NULL DEFAULT 0,
  "uniquePlaceCount" INTEGER NOT NULL DEFAULT 0,
  "repeatVisitCount" INTEGER NOT NULL DEFAULT 0,
  "reviewCount" INTEGER NOT NULL DEFAULT 0,
  "trustScore" REAL NOT NULL DEFAULT 0,
  "updatedAt" DATETIME NOT NULL,
  CONSTRAINT "LocalProfile_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES "AnonymousSession" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "LocalProfile_sessionId_key" ON "LocalProfile"("sessionId");
