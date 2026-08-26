import { PrismaClient } from "@prisma/client";

const prisma = new PrismaClient();

async function main() {
  const shops = await prisma.place.findMany({
    where: { category: "SOUVENIR" }
  });
  console.log(JSON.stringify(shops, null, 2));
}

main().finally(() => prisma.$disconnect());
