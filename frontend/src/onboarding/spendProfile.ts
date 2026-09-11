// 온보딩 세 질문(오는 교통·숙소·식사) — S15P21E201-807.
// 문장·선택지·코드값은 지어내지 않고 docs/COLDSTART-THREE-QUESTIONS.md 2·3절을 그대로 옮긴다.
// 코드값은 눈금형(안 B, 2026-09-10 결정)을 쓴다 — 등급형(LOW/MID/HIGH)은 눈금이 바뀌면
// 옛 답과 새 답이 조용히 섞이지만, 눈금형은 눈금이 바뀔 때 새 코드가 생겨 구별된다.
//
// 이 화면의 범위는 "답을 받아 저장하는 데까지"다(문서 5.2) — tier·foodLean·comfort 계산과
// 그 값을 추천 가중치에 얼마나 반영할지는 짝 비교 설문(S15P21E201-716)의 몫이라 여기서
// 계산하지 않는다. 배수가 정해지기 전까지는 1.0(변화 없음)이 안전장치다.
import { apiRequest, ApiClientError } from '@/api/client';

export type SpendKey = 'transport' | 'stay' | 'meal';

export type SpendOption = {
  code: string;
  labelKo: string;
  labelEn: string;
  descKo?: string;
  descEn?: string;
};

export type SpendQuestion = {
  key: SpendKey;
  titleKo: (scope: 'USER' | 'TRIP') => string;
  titleEn: (scope: 'USER' | 'TRIP') => string;
  skipLabelKo: string;
  skipLabelEn: string;
  options: SpendOption[];
};

// 문서 2.2 — 계정(USER) 첫 실행과 여행(TRIP) 조건 입력에서 머리말의 시간 표현만 다르다.
export const SPEND_HEADER: Record<'USER' | 'TRIP', { titleKo: string; titleEn: string; bodyKo: string; bodyEn: string }> = {
  USER: {
    titleKo: '여행 스타일을 3개만 여쭤볼게요',
    titleEn: 'Just 3 quick questions about your travel style',
    bodyKo: '여행할 때 보통 어떻게 쓰시는지 알면 추천 순서가 달라져요. 건너뛰셔도 돼요.',
    bodyEn: "Knowing how you usually spend on trips helps us order recommendations. Feel free to skip.",
  },
  TRIP: {
    titleKo: '이번 여행은 평소와 다르신가요?',
    titleEn: 'Is this trip different from usual?',
    bodyKo: '이번 여행에만 적용돼요. 평소 답은 그대로 둡니다.',
    bodyEn: 'This only applies to this trip. Your usual answers stay as they are.',
  },
};

// 문서 2.3 Q1 · 오는 교통 — 🔴 "항공편"이라고 쓰지 않는다. 국내 사용자는 KTX·SRT·고속버스로 온다.
const TRANSPORT: SpendQuestion = {
  key: 'transport',
  titleKo: (scope) => `부산에 갈 때, ${scope === 'TRIP' ? '이번 여행에는' : '보통'} 어느 쪽을 고르세요?`,
  titleEn: (scope) => `When you travel to Busan, which do you ${scope === 'TRIP' ? 'plan to pick this time' : 'usually pick'}?`,
  skipLabelKo: '잘 모르겠어요',
  skipLabelEn: 'Not sure',
  options: [
    { code: 'PRICE_FIRST', labelKo: '값이 가장 싼 쪽', labelEn: 'The cheapest option', descKo: '새벽에 출발하거나 한 번 갈아타야 해도, 싼 쪽으로 골라요', descEn: "I'll take an early departure or one transfer to save money" },
    { code: 'TIME_AND_PRICE', labelKo: '시간과 값을 함께 보는 쪽', labelEn: 'Time and money together', descKo: '훨씬 빨라지면 더 내지만, 조금 빨라지는 정도면 싼 쪽을 골라요', descEn: "I'll pay more if it's much faster, but not for a small gain" },
    { code: 'COMFORT_FIRST', labelKo: '가장 편한 쪽', labelEn: 'The most comfortable option', descKo: '원하는 시간에, 갈아타지 않고 가는 쪽이면 돈을 더 내요', descEn: "I'll pay more to leave when I want, with no transfers" },
  ],
};

// 문서 2.4 Q2 · 숙소 — 🔴 "1인"을 반드시 굵게/명확히 보여준다. 없으면 가족 여행자가
// 전부 최상위 구간으로 잘못 잡힌다.
const STAY: SpendQuestion = {
  key: 'stay',
  titleKo: (scope) => `숙소는 1인 1박 얼마까지 ${scope === 'TRIP' ? '이번 여행에 내실 생각이세요' : '보통 내세요'}?`,
  titleEn: (scope) => `How much do you ${scope === 'TRIP' ? 'plan to spend' : 'usually spend'} on a room, per person per night?`,
  skipLabelKo: '아직 안 정했어요',
  skipLabelEn: "Haven't decided",
  options: [
    { code: 'KRW_UNDER_50K', labelKo: '5만원 이하', labelEn: 'Under ₩50,000', descKo: '게스트하우스 · 도미토리 · 모텔', descEn: 'Hostel, dormitory, motel' },
    { code: 'KRW_50K_120K', labelKo: '5만 ~ 12만원', labelEn: '₩50,000 – 120,000', descKo: '비즈니스호텔 · 에어비앤비', descEn: 'Business hotel, Airbnb' },
    { code: 'KRW_120K_250K', labelKo: '12만 ~ 25만원', labelEn: '₩120,000 – 250,000', descKo: '오션뷰 · 4성급', descEn: '4-star, ocean view' },
    { code: 'KRW_OVER_250K', labelKo: '25만원 이상', labelEn: 'Over ₩250,000', descKo: '특급호텔 · 리조트', descEn: 'Luxury hotel, resort' },
  ],
};

// 문서 2.5 Q3 · 식사 — 가격을 주 축, 유명세를 부 축으로 일부러 한 문항에 섞었다(문서가
// 명시). "그날그날 달라요"는 "잘 모르겠어요"와 다른 답이라 건너뛰기가 아니라 VARIES 값으로
// 저장한다(문서 D2 권고안 B — 저장하고 계산에서만 뺀다, 나중에 쓸 수 있고 지금 아무것도
// 안 망가뜨린다).
const MEAL: SpendQuestion = {
  key: 'meal',
  titleKo: (scope) => `부산에서 저녁 한 끼, ${scope === 'TRIP' ? '이번 여행에는' : '보통'} 어느 쪽이 더 끌리세요?`,
  titleEn: (scope) => `For dinner in Busan, which sounds better ${scope === 'TRIP' ? 'for this trip' : 'to you'}?`,
  skipLabelKo: '그날그날 달라요',
  skipLabelEn: 'It depends on the day',
  options: [
    { code: 'EVERYDAY_LOCAL', labelKo: '동네 사람들이 가는 국밥·백반집', labelEn: 'A local rice-soup or home-style place', descKo: '1만원 안팎, 줄 안 서요', descEn: 'Around ₩10,000, no queue' },
    { code: 'KNOWN_IN_AREA', labelKo: '그 동네에서 이름난 집', labelEn: 'A place the neighborhood is known for', descKo: '2만 ~ 4만원, 20분쯤은 줄 서도 가요', descEn: '₩20,000–40,000, worth a 20-minute wait' },
    { code: 'SPECIAL_BOOKED', labelKo: '예약하고 가는 특별한 한 끼', labelEn: 'One special meal, booked ahead', descKo: '5만원 이상, 코스·오마카세', descEn: '₩50,000+, tasting menu / omakase' },
  ],
};

// meal 의 "그날그날 달라요"는 건너뛰기가 아니라 답이므로 skip 처리와 분리해 둔다.
export const MEAL_VARIES_CODE = 'VARIES';

export const SPEND_QUESTIONS: SpendQuestion[] = [TRANSPORT, STAY, MEAL];

export type SpendAnswers = Partial<Record<SpendKey, string>>;

export type SpendProfileStatus = 'SELECTED' | 'SKIPPED' | 'UNKNOWN';
export type SpendProfileGetResult = { status: SpendProfileStatus; answers: SpendAnswers | null };

// GET /api/v1/me/preferences/spend — 저장한 적 없으면 404가 아니라 status:UNKNOWN 200이다
// (서버 계약, SpendProfileController 참고). "한 번도 안 물어봤다"는 오류가 아니라
// 정상 상태이기 때문이다.
export async function getSpendProfile(accessToken: string | null): Promise<SpendProfileGetResult> {
  const dto = await apiRequest<{ status: SpendProfileStatus; value: string | null }>('/api/v1/me/preferences/spend', { accessToken });
  if (dto.status !== 'SELECTED' || !dto.value) return { status: dto.status, answers: null };
  try {
    return { status: dto.status, answers: JSON.parse(dto.value) as SpendAnswers };
  } catch {
    // 서버가 우리가 모르는 모양을 보내도 화면을 죽이지 않는다 — 안 물어본 것처럼 다룬다.
    return { status: 'UNKNOWN', answers: null };
  }
}

// PUT /api/v1/me/preferences/spend — 하나라도 답했으면 SELECTED(답한 키만 담는다, 건너뛴
// 키는 값이 없다 — null 이 아니라 키 자체가 없다), 셋 다 건너뛰면 SKIPPED(value 없음).
export function putSpendProfile(answers: SpendAnswers, accessToken: string | null) {
  const hasAnswer = Object.keys(answers).length > 0;
  return apiRequest<{ status: SpendProfileStatus; value: string | null }>('/api/v1/me/preferences/spend', {
    method: 'PUT',
    accessToken,
    body: hasAnswer
      ? { value: JSON.stringify(answers), answerStatus: 'SELECTED' }
      : { value: null, answerStatus: 'SKIPPED' },
  });
}

export function isSpendProfileUnavailable(error: unknown) {
  return error instanceof ApiClientError && (error.status === 404 || error.status === 501);
}
