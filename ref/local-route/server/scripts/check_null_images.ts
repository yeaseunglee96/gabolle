import { PrismaClient } from "@prisma/client";
const prisma = new PrismaClient();

async function main() {
  const allPlaces = await prisma.place.findMany({
    select: { id: true, nameKo: true, category: true, imageUrl: true }
  });
  
  const nullImages = allPlaces.filter(p => !p.imageUrl);
  console.log(`Total places: ${allPlaces.length}`);
  console.log(`Places with null imageUrl: ${nullImages.length}`);
  
  const catCounts: Record<string, number> = {};
  nullImages.forEach(p => {
    catCounts[p.category] = (catCounts[p.category] || 0) + 1;
  });
  console.log("Categories of places with null imageUrl:", catCounts);
  
  console.log("Some examples of places with null imageUrl:");
  console.log(nullImages.slice(0, 15).map(p => `${p.nameKo} (${p.category})`));
}

main().finally(() => prisma.$disconnect());
