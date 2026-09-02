CREATE TABLE "UserConsent" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "userId" TEXT NOT NULL,
  "category" TEXT NOT NULL,
  "granted" BOOLEAN NOT NULL DEFAULT false,
  "policyVersion" TEXT NOT NULL,
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" DATETIME NOT NULL,
  CONSTRAINT "UserConsent_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE UNIQUE INDEX "UserConsent_userId_category_key" ON "UserConsent"("userId", "category");
CREATE INDEX "UserConsent_userId_updatedAt_idx" ON "UserConsent"("userId", "updatedAt");
