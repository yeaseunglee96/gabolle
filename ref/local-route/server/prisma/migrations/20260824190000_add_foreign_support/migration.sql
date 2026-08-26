ALTER TABLE "Place" ADD COLUMN "addressEn" TEXT;
ALTER TABLE "Place" ADD COLUMN "allergens" TEXT NOT NULL DEFAULT '[]';
ALTER TABLE "Place" ADD COLUMN "dietOptions" TEXT NOT NULL DEFAULT '[]';
ALTER TABLE "Place" ADD COLUMN "onlineReservation" BOOLEAN NOT NULL DEFAULT false;
CREATE TABLE "PlaceTranslation" ("id" TEXT NOT NULL PRIMARY KEY,"placeId" TEXT NOT NULL,"lang" TEXT NOT NULL,"name" TEXT NOT NULL,"address" TEXT,"description" TEXT,"source" TEXT NOT NULL DEFAULT 'STRUCTURED',CONSTRAINT "PlaceTranslation_placeId_fkey" FOREIGN KEY("placeId") REFERENCES "Place"("id") ON DELETE RESTRICT ON UPDATE CASCADE);
CREATE UNIQUE INDEX "PlaceTranslation_placeId_lang_key" ON "PlaceTranslation"("placeId","lang");
