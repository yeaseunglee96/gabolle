import { PrismaClient } from "@prisma/client";
const prisma = new PrismaClient();
const places = await prisma.place.findMany({ where: { nameEn: { not: null } } });
for (const place of places) await prisma.placeTranslation.upsert({ where: { placeId_lang: { placeId: place.id, lang: "EN" } }, update: { name: place.nameEn! }, create: { placeId: place.id, lang: "EN", name: place.nameEn!, address: place.addressEn, source: "PLACE_STRUCTURED" } });
await prisma.$disconnect();
