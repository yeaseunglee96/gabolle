import { PrismaClient } from "@prisma/client";

const prisma = new PrismaClient();

async function main() {
  console.log("Starting database image updates for souvenir shops and festivals with VisitBusan URLs...");

  const updates = [
    {
      contentId: "DEMO-SOUVENIR-1",
      nameKo: "부산역 동백상회",
      imageUrl: "https://www.visitbusan.net/uploadImgs/files/cntnts/20230630173529259" // Official Dongbaek Shop photo
    },
    {
      contentId: "DEMO-SOUVENIR-2",
      nameKo: "영도 로컬 기프트숍",
      imageUrl: "http://tong.visitkorea.or.kr/cms2/website/12/1131212.jpg" // Local crafts/pottery
    },
    {
      contentId: "DEMO-SOUVENIR-3",
      nameKo: "광안리 부산기념품점",
      imageUrl: "https://www.visitbusan.net/uploadImgs/files/cntnts/20230704095406026" // Official Gwangalli Souvenir Shop photo ("여가~거가! 광안리")
    },
    {
      contentId: "DEMO-FESTIVAL-1",
      nameKo: "부산바다축제",
      imageUrl: "http://tong.visitkorea.or.kr/cms2/website/04/1046404.jpg" // Crowded summer beach (lively festival theme)
    },
    {
      contentId: "DEMO-FESTIVAL-2",
      nameKo: "영도다리축제",
      imageUrl: "https://tong.visitkorea.or.kr/cms/resource_photo/44/2927844_image2_1.jpg" // Yeongdo Bridge drawing up (festival core)
    }
  ];

  for (const item of updates) {
    const updated = await prisma.place.updateMany({
      where: { contentId: item.contentId },
      data: { imageUrl: item.imageUrl }
    });
    console.log(`Updated ${item.nameKo} (${item.contentId}): ${updated.count} record(s)`);
  }

  console.log("All database image updates complete!");
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
