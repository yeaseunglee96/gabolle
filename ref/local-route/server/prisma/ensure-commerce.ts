import "dotenv/config";
import { PrismaClient } from "@prisma/client";
import { createHash } from "node:crypto";

const prisma = new PrismaClient();
const ownerSessionHash = createHash("sha256").update("local-route-demo-business-owner").digest("hex").slice(0, 24);

async function main() {
  const lodging = await prisma.place.findFirst({ where: { category: "LODGING" }, orderBy: { localScore: "desc" } });
  if (!lodging) throw new Error("숙소 시드 장소가 필요합니다.");
  const business = await prisma.business.upsert({
    where: { placeId: lodging.id },
    create: { name: `${lodging.nameKo} 파트너`, contactEmail: "demo-business@localroute.example", ownerSessionHash, status: "VERIFIED", placeId: lodging.id },
    update: { status: "VERIFIED" },
  });
  const existing = await prisma.adCampaign.findFirst({ where: { businessId: business.id, name: "부산 숙박 데모" } });
  if (!existing) await prisma.adCampaign.create({ data: { businessId: business.id, placeId: lodging.id, name: "부산 숙박 데모", serviceCategory: "LODGING", status: "ACTIVE", budget: 100000, bidCpc: 350, targetingModes: JSON.stringify(["LOCAL", "PET_SAFE"]), startsAt: new Date("2025-01-01T00:00:00Z"), endsAt: new Date("2030-12-31T23:59:59Z") } });
  await prisma.bookingPartner.upsert({
    where: { placeId_provider: { placeId: lodging.id, provider: "LOCAL_ROUTE_DEMO" } },
    create: { placeId: lodging.id, provider: "LOCAL_ROUTE_DEMO", bookingUrl: "https://example.com/booking/local-route-demo", commissionRate: 0.03 },
    update: { active: true },
  });
  console.log(JSON.stringify({ businessId: business.id, adPlace: lodging.nameKo, bookingPlace: lodging.nameKo }));
}

main().finally(() => prisma.$disconnect());
