import "dotenv/config";
import { PrismaClient } from "@prisma/client";
import { createHash } from "node:crypto";

const prisma = new PrismaClient();

const places = [
  { contentId: "DEMO-FESTIVAL-1", nameKo: "부산바다축제", nameEn: "Busan Sea Festival", category: "FESTIVAL", address: "부산 해운대구 해운대해변로", lat: 35.1587, lng: 129.1604, openTime: "18:00", closeTime: "22:00", recommendedStayMin: 120, priceTier: 1, localScore: .78, tasteTags: '["culture","nightview","activity"]', eventStartDate: "2026-08-01", eventEndDate: "2026-09-30", playTime: "18:00~22:00", isOutdoor: true },
  { contentId: "DEMO-FESTIVAL-2", nameKo: "영도다리축제", nameEn: "Yeongdo Bridge Festival", category: "FESTIVAL", address: "부산 영도구 봉래동", lat: 35.094, lng: 129.039, openTime: "10:00", closeTime: "20:00", recommendedStayMin: 100, priceTier: 1, localScore: .84, tasteTags: '["culture","hidden_local","food"]', eventStartDate: "2026-08-15", eventEndDate: "2026-10-15", playTime: "10:00~20:00", isOutdoor: true },
  { contentId: "DEMO-FESTIVAL-3", nameKo: "광안리 M 드론 라이트쇼", nameEn: "Gwangalli M Drone Light Show", category: "FESTIVAL", address: "부산 수영구 광안해변로 219", lat: 35.1531, lng: 129.1186, openTime: "19:00", closeTime: "22:30", recommendedStayMin: 60, priceTier: 1, localScore: .9, tasteTags: '["culture","nightview","photo"]', eventStartDate: "2026-01-01", eventEndDate: "2030-12-31", playTime: "매주 토요일 19:00/21:00(동절기), 20:00/22:00(하절기)", isOutdoor: true },
  { contentId: "DEMO-SOUVENIR-1", nameKo: "부산역 동백상회", nameEn: "Busan Station Camellia Shop", category: "SOUVENIR", address: "부산 동구 중앙대로 206", lat: 35.1152, lng: 129.0414, openTime: "09:00", closeTime: "21:00", recommendedStayMin: 25, priceTier: 2, localScore: .76, tasteTags: '["shopping","local"]', souvenirItems: '["부산 어묵","동백 굿즈","지역 엽서"]', foreignCardPayment: true, foreignAssistance: true },
  { contentId: "DEMO-SOUVENIR-2", nameKo: "영도 로컬 기프트숍", nameEn: "Yeongdo Local Gift Shop", category: "SOUVENIR", address: "부산 영도구 절영로", lat: 35.0768, lng: 129.0485, openTime: "11:00", closeTime: "19:00", recommendedStayMin: 30, priceTier: 2, localScore: .88, tasteTags: '["shopping","hidden_local"]', souvenirItems: '["해녀 공예품","영도 커피","로컬 포스터"]', foreignCardPayment: true, foreignAssistance: false },
  { contentId: "DEMO-SOUVENIR-3", nameKo: "광안리 부산기념품점", nameEn: "Gwangalli Busan Souvenirs", category: "SOUVENIR", address: "부산 수영구 광안해변로", lat: 35.1532, lng: 129.119, openTime: "10:00", closeTime: "22:00", recommendedStayMin: 25, priceTier: 2, localScore: .7, tasteTags: '["shopping","photo"]', souvenirItems: '["광안대교 마그넷","바다 캔들","부산 키링"]', foreignCardPayment: true, foreignAssistance: true },
] as const;

async function main() {
  for (const place of places) await prisma.place.upsert({ where: { contentId: place.contentId }, create: { ...place, closedDays: "[]", parkingAvailable: false, reservationRequired: false, hasEnglishMenu: false, dataSource: "TOURAPI_DEMO" }, update: place });
  const lodging = await prisma.place.findFirst({ where: { category: "LODGING" }, orderBy: { localScore: "desc" } });
  if (!lodging) throw new Error("숙소 시드가 필요합니다.");
  await prisma.adCampaign.updateMany({ where: { serviceCategory: { notIn: ["LODGING", "RENTAL_CAR", "TRAVEL_INSURANCE", "TAXI", "AIRPORT_TRANSFER", "ESIM"] } }, data: { status: "PAUSED" } });
  await prisma.adCampaign.updateMany({ where: { place: { category: { in: ["CAFE", "RESTAURANT", "SOUVENIR"] } } }, data: { status: "PAUSED" } });
  const ownerSessionHash = createHash("sha256").update("local-route-essential-services").digest("hex").slice(0, 24);
  const business = await prisma.business.upsert({ where: { placeId: lodging.id }, create: { name: "LOCAL ROUTE 여행 필수 파트너", contactEmail: "essential@localroute.example", ownerSessionHash, status: "VERIFIED", placeId: lodging.id }, update: { status: "VERIFIED", name: "LOCAL ROUTE 여행 필수 파트너" } });
  const campaigns = [
    ["안심 숙박 예약", "LODGING", 260], ["부산 렌터카", "RENTAL_CAR", 240], ["공항·기차역·항구 eSIM", "ESIM", 230], ["여행자 보험", "TRAVEL_INSURANCE", 180], ["부산 택시 호출", "TAXI", 160], ["공항 픽업", "AIRPORT_TRANSFER", 210],
  ] as const;
  for (const [name, serviceCategory, bidCpc] of campaigns) {
    const found = await prisma.adCampaign.findFirst({ where: { businessId: business.id, name } });
    const data = { placeId: lodging.id, name, serviceCategory, status: "ACTIVE", budget: 100000, bidCpc, targetingModes: "[]", startsAt: new Date("2025-01-01T00:00:00Z"), endsAt: new Date("2030-12-31T23:59:59Z") };
    if (found) await prisma.adCampaign.update({ where: { id: found.id }, data }); else await prisma.adCampaign.create({ data: { businessId: business.id, ...data } });
  }
  console.log(JSON.stringify({ experiencePlaces: places.length, essentialCampaigns: campaigns.length }));
}

main().finally(() => prisma.$disconnect());
