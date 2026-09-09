CREATE TABLE "RouteExecution" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "tripId" TEXT NOT NULL,
  "sessionId" TEXT NOT NULL,
  "status" TEXT NOT NULL DEFAULT 'ACTIVE',
  "transport" TEXT NOT NULL,
  "startedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "pausedAt" DATETIME,
  "endedAt" DATETIME,
  "lastSignalAt" DATETIME,
  CONSTRAINT "RouteExecution_tripId_fkey" FOREIGN KEY ("tripId") REFERENCES "Trip" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "RouteExecution_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES "AnonymousSession" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX "RouteExecution_tripId_status_idx" ON "RouteExecution"("tripId", "status");
CREATE INDEX "RouteExecution_sessionId_startedAt_idx" ON "RouteExecution"("sessionId", "startedAt");

CREATE TABLE "LocationSignal" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "routeExecutionId" TEXT NOT NULL,
  "latitude" REAL NOT NULL,
  "longitude" REAL NOT NULL,
  "accuracy" REAL NOT NULL,
  "heading" REAL,
  "capturedAt" DATETIME NOT NULL,
  "deleteAfter" DATETIME NOT NULL,
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "LocationSignal_routeExecutionId_fkey" FOREIGN KEY ("routeExecutionId") REFERENCES "RouteExecution" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX "LocationSignal_routeExecutionId_capturedAt_idx" ON "LocationSignal"("routeExecutionId", "capturedAt");
CREATE INDEX "LocationSignal_deleteAfter_idx" ON "LocationSignal"("deleteAfter");
