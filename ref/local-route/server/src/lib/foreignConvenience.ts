/**
 * 영어메뉴/카드결제 여부를 실측 데이터 없이 추정해야 할 때 쓰는 공통 휴리스틱.
 * seed.ts(수기 시드)와 prisma/import-tourapi.ts(관광공사 API 임포트) 양쪽에서 공유한다.
 */

export const TOURISTY_AREA_HINTS = ["해운대", "광안리", "남포동", "서면", "용두산", "송정", "자갈치"];

export interface ForeignConvenienceInput {
  category: "TOURIST" | "RESTAURANT" | "CAFE" | "LODGING";
  priceTier: number;
  address: string;
  nameKo: string;
}

export function deriveForeignConvenience(p: ForeignConvenienceInput): {
  englishMenu: boolean;
  cardPayment: boolean;
} {
  const isFoodOrCafe = p.category === "RESTAURANT" || p.category === "CAFE";
  const inTouristyArea = TOURISTY_AREA_HINTS.some((a) => p.address.includes(a) || p.nameKo.includes(a));
  const englishMenu = isFoodOrCafe && (p.priceTier >= 3 || inTouristyArea);
  const cardPayment = p.category === "LODGING" || p.priceTier >= 2;
  return { englishMenu, cardPayment };
}
