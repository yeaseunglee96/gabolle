CREATE TABLE "EventOutbox" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "eventId" TEXT NOT NULL,
  "eventType" TEXT NOT NULL,
  "topic" TEXT NOT NULL,
  "partitionKey" TEXT NOT NULL,
  "actorId" TEXT,
  "entityType" TEXT,
  "entityId" TEXT,
  "language" TEXT,
  "payloadJson" TEXT NOT NULL,
  "occurredAt" DATETIME NOT NULL,
  "receivedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "publishStatus" TEXT NOT NULL DEFAULT 'PENDING',
  "publishedAt" DATETIME,
  "publishAttempts" INTEGER NOT NULL DEFAULT 0,
  "lastError" TEXT
);
CREATE UNIQUE INDEX "EventOutbox_eventId_key" ON "EventOutbox"("eventId");
CREATE INDEX "EventOutbox_topic_publishStatus_receivedAt_idx" ON "EventOutbox"("topic", "publishStatus", "receivedAt");
CREATE INDEX "EventOutbox_eventType_occurredAt_idx" ON "EventOutbox"("eventType", "occurredAt");
CREATE INDEX "EventOutbox_entityType_entityId_occurredAt_idx" ON "EventOutbox"("entityType", "entityId", "occurredAt");
