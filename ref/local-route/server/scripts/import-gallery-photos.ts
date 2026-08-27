import "dotenv/config";
import { PrismaClient } from "@prisma/client";

const prisma = new PrismaClient();

const serviceKey = process.env.TOUR_API_KEY;
if (!serviceKey) {
  console.error("TOUR_API_KEY is not defined in .env");
  process.exit(1);
}

function cleanKeyword(name: string): string {
  return name
    .replace(/\s+/g, " ")
    .replace(/(본점|지점|점)$/, "")
    .trim();
}

function getFallbackKeywords(name: string, category: string): string[] {
  const list: string[] = [];
  
  if (name.includes("돼지국밥")) list.push("돼지국밥", "부산 돼지국밥");
  if (name.includes("회센터") || name.includes("회")) list.push("자갈치시장", "회");
  if (name.includes("물회")) list.push("물회", "청사포");
  if (name.includes("곱창")) list.push("곱창");
  if (name.includes("호떡")) list.push("호떡", "씨앗호떡");
  if (name.includes("갈비")) list.push("갈비", "불고기");
  if (name.includes("밀면")) list.push("밀면");
  if (name.includes("이바구")) list.push("이바구길", "초량");
  if (name.includes("모모스")) list.push("전포동 카페거리", "전포동");
  if (name.includes("흰여울")) list.push("흰여울문화마을", "흰여울");
  if (name.includes("청사포")) list.push("청사포");
  if (name.includes("해리단길")) list.push("해리단길");
  if (name.includes("광안리")) list.push("광안리");
  if (name.includes("해운대")) list.push("해운대");
  if (name.includes("파라다이스")) list.push("해운대");
  if (name.includes("아난티")) list.push("기장");
  if (name.includes("영도")) list.push("영도");
  if (name.includes("서면")) list.push("서면");
  if (name.includes("송정")) list.push("송정");
  if (name.includes("사상")) list.push("사상");
  if (name.includes("근현대역사관")) list.push("부산근현대역사관", "중구");
  if (name.includes("임랑")) list.push("임랑해수욕장", "임랑");
  if (name.includes("함지골") || name.includes("청소년수련관")) list.push("영도", "부산");
  if (name.includes("동물") || name.includes("병원") || name.includes("펫")) list.push("반려동물", "강아지");
  if (name.includes("축제")) {
    if (name.includes("바다")) list.push("바다축제", "부산바다");
    if (name.includes("영도다리")) list.push("영도다리", "영도대교");
  }
  if (name.includes("동백상회")) list.push("동백꽃", "부산역");
  if (name.includes("기념품") || name.includes("기프트")) list.push("기념품", "부산");

  // General fallbacks based on category/location
  if (category === "RESTAURANT") list.push("부산 맛집", "음식");
  if (category === "CAFE") list.push("부산 카페", "커피");
  if (category === "LODGING") list.push("부산 호텔", "부산");
  if (category === "FESTIVAL") list.push("부산 축제", "축제");
  if (category === "SOUVENIR") list.push("부산 기념품", "기념품");
  if (category === "TOURIST") list.push("부산", "부산 여행");
  
  return list;
}

async function fetchPhoto(keyword: string): Promise<string | null> {
  const base = "https://apis.data.go.kr/B551011/PhotoGalleryService1/gallerySearchList1";
  const params = new URLSearchParams({
    serviceKey,
    MobileOS: "ETC",
    MobileApp: "LocalRoute",
    _type: "json",
    keyword,
    numOfRows: "1",
    pageNo: "1"
  });
  
  const url = `${base}?${params.toString()}`;
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(5000) });
    if (!res.ok) {
      console.warn(`   API HTTP error for "${keyword}": ${res.status}`);
      return null;
    }
    const data = await res.json() as any;
    const body = data?.response?.body;
    if (!body) return null;
    
    const items = body.items;
    if (!items || items === "") return null;
    const item = items.item;
    if (!item) return null;
    const itemArray = Array.isArray(item) ? item : [item];
    
    const firstItem = itemArray[0];
    if (firstItem && firstItem.galWebImageUrl) {
      return firstItem.galWebImageUrl;
    }
    return null;
  } catch (error) {
    console.error(`   Fetch error for keyword "${keyword}":`, error);
    return null;
  }
}

async function main() {
  const places = await prisma.place.findMany({
    where: { imageUrl: null },
    select: { id: true, nameKo: true, category: true }
  });

  console.log(`Found ${places.length} places with missing imageUrl. Starting smart fallback import...`);

  let successCount = 0;
  let failCount = 0;

  for (const place of places) {
    console.log(`Processing: ${place.nameKo} (${place.category})`);
    let imageUrl: string | null = null;
    
    // Check if it's one of the demo places we manually curated
    if (place.nameKo === "부산역 동백상회") {
      imageUrl = "https://www.visitbusan.net/uploadImgs/files/cntnts/20230630173529259";
    } else if (place.nameKo === "영도 로컬 기프트숍") {
      imageUrl = "http://tong.visitkorea.or.kr/cms2/website/12/1131212.jpg";
    } else if (place.nameKo === "광안리 부산기념품점") {
      imageUrl = "https://www.visitbusan.net/uploadImgs/files/cntnts/20230704095406026";
    } else if (place.nameKo === "부산바다축제") {
      imageUrl = "http://tong.visitkorea.or.kr/cms2/website/04/1046404.jpg";
    } else if (place.nameKo === "영도다리축제") {
      imageUrl = "https://tong.visitkorea.or.kr/cms/resource_photo/44/2927844_image2_1.jpg";
    }

    // 1. Try with original nameKo
    if (!imageUrl) {
      imageUrl = await fetchPhoto(place.nameKo);
    }
    
    // 2. Try with cleaned keyword
    if (!imageUrl) {
      const cleaned = cleanKeyword(place.nameKo);
      if (cleaned !== place.nameKo) {
        console.log(`   Trying cleaned keyword: "${cleaned}"`);
        imageUrl = await fetchPhoto(cleaned);
      }
    }

    // 3. Try prefixing with "부산 "
    if (!imageUrl && !place.nameKo.includes("부산")) {
      const busanPrefixed = `부산 ${place.nameKo}`;
      console.log(`   Trying prefixed keyword: "${busanPrefixed}"`);
      imageUrl = await fetchPhoto(busanPrefixed);
    }

    // 4. Try smart fallback keywords
    if (!imageUrl) {
      const fallbacks = getFallbackKeywords(place.nameKo, place.category);
      for (const fallback of fallbacks) {
        console.log(`   Trying smart fallback: "${fallback}"`);
        imageUrl = await fetchPhoto(fallback);
        if (imageUrl) break;
        await new Promise((resolve) => setTimeout(resolve, 200)); // Delay between fallback attempts
      }
    }

    if (imageUrl) {
      await prisma.place.update({
        where: { id: place.id },
        data: { imageUrl }
      });
      console.log(`   Successfully updated: ${imageUrl}`);
      successCount++;
    } else {
      console.log(`   Could not find photo for ${place.nameKo}`);
      failCount++;
    }

    // Rate limiting delay (200ms)
    await new Promise((resolve) => setTimeout(resolve, 200));
  }

  console.log(`\nSmart fallback import complete!`);
  console.log(`Successful updates: ${successCount}`);
  console.log(`Failed updates: ${failCount}`);
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
