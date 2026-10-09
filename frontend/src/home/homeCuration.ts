// 홈에 «골라서» 올리는 것들 — S15P21E201-2021 「홈이 어지럽다」.
//
// 사람이 고른 값은 코드 여기저기에 흩지 않고 이 파일 한 곳에만 둔다. 바꿀 때 이 파일만 고치면 된다.
// · 기록 줄 맨 앞에 고정할 글 — 사람 얼굴이 안 나오고 분위기 있는 사진의 글(2026-10-09 사진을 한 장씩 보고 골랐다)
// · 로컬 탐색 여섯 칸의 대표 장소 — 갈래마다 하나, 공공 사진이 좋은 곳
// 고른 글이 지워지거나 비공개가 되면 그 글만 빠진다(불러오기 실패 = 없는 것). 화면이 깨지지 않는다.
import type { LanguageCode } from '@/i18n/languages';

/**
 * 기록 줄 맨 앞에 고정하는 글(작성자 HJ Jang). 이 순서대로 앞에 서고, 나머지 글은 원래 순서대로 뒤에 선다.
 * 넣기 전에 첫 사진(홈 카드에 보이는 것)뿐 아니라 글의 사진 전부를 보고 사람 얼굴이 없는지 확인한다.
 */
export const FEATURED_STORY_IDS: readonly string[] = [
  '125340ea-c61d-4d78-a3c6-5f37b5f6ad68', // 황령산 — 오후의 산과 바다
  '3027fe90-9f22-4202-922d-40ba33de3c9b', // 모모스커피 — 대숲 아래 정자
  '21c03acf-8318-44b7-98dc-c3074a822615', // 코스피어 — 창고 카페
  '44e978ed-7394-4a0f-be8c-1998bca7c29c', // 넉아웃 빌리지 — 장미 아래 칠판
  'e8eee6a8-fda5-417c-b8b2-1356af7a19e7', // 송정 — 바다와 바위
];

/** 고정 글을 앞에, 나머지를 뒤에. 같은 글은 한 번만. */
export function featuredFirst<T extends { id: string }>(featured: T[], rest: T[]): T[] {
  const seen = new Set<string>();
  const out: T[] = [];
  for (const item of [...featured, ...rest]) {
    if (seen.has(item.id)) continue;
    seen.add(item.id);
    out.push(item);
  }
  return out;
}

/** 로컬 탐색 여섯 갈래 — 이 순서로 선다. featureKey 는 서버 갈래 코드(로컬 탐색 화면의 ?facet= 값)다. */
export type LocalPickKey = 'FESTIVAL' | 'TRADITIONAL_MARKET' | 'ACTIVITY' | 'WALK' | 'NATURE' | 'NIGHT_VIEW';

type Labels = Record<LanguageCode, string>;

export const LOCAL_PICK_LABELS: Record<LocalPickKey, Labels> = {
  FESTIVAL: { ko: '축제', en: 'Festival', ja: 'お祭り', 'zh-Hans': '节庆', 'zh-Hant': '節慶', vi: 'Lễ hội' },
  TRADITIONAL_MARKET: { ko: '전통시장', en: 'Market', ja: '伝統市場', 'zh-Hans': '传统市场', 'zh-Hant': '傳統市場', vi: 'Chợ truyền thống' },
  ACTIVITY: { ko: '액티비티', en: 'Activity', ja: 'アクティビティ', 'zh-Hans': '体验活动', 'zh-Hant': '體驗活動', vi: 'Hoạt động' },
  WALK: { ko: '산책', en: 'Walk', ja: '散策', 'zh-Hans': '散步', 'zh-Hant': '散步', vi: 'Dạo bộ' },
  NATURE: { ko: '자연', en: 'Nature', ja: '自然', 'zh-Hans': '自然', 'zh-Hant': '自然', vi: 'Thiên nhiên' },
  NIGHT_VIEW: { ko: '야경', en: 'Night view', ja: '夜景', 'zh-Hans': '夜景', 'zh-Hant': '夜景', vi: 'Cảnh đêm' },
};

/** 갈래마다 대표 장소 하나. 사진은 전부 관광공사 공공누리 사진이다(공공 사진 먼저). */
export const LOCAL_PICK_PLACES: Record<Exclude<LocalPickKey, 'FESTIVAL'>, string> = {
  TRADITIONAL_MARKET: 'f548797c-361a-3b3a-8210-616be343a29d', // 국제시장
  ACTIVITY: '6e2e7cfd-d5f6-3588-a8f2-7a8fa9733b0c', // 광안리 SUP Zone — 광안대교 아래 패들보드
  WALK: '3667f940-f493-318d-845c-53f701a365df', // 절영해안산책로
  NATURE: '8dac92c6-81e7-37ed-8f06-e2a0a089d0d0', // 아홉산숲
  NIGHT_VIEW: 'd7d68934-1a4e-31c5-836a-0f94134e81e4', // 황령산 전망대
};

export const LOCAL_PICK_ORDER: readonly LocalPickKey[] = ['FESTIVAL', 'TRADITIONAL_MARKET', 'ACTIVITY', 'WALK', 'NATURE', 'NIGHT_VIEW'];

/**
 * 기간을 아는 대표 축제. 축제 데이터(관광공사)에 아직 없는 큰 축제를 여기 적는다 — 기간 안에만 축제 칸에 선다.
 * 기간이 끝나면 저절로 빠지고, 축제 데이터에서 오늘 열리는 축제가 그 자리를 채운다.
 *
 * 제31회 부산국제영화제(BIFF) — 2026-10-06 ~ 10-15, 주무대 영화의전당. 개막 보도(연합뉴스·머니투데이 2026-10-05)로 확인.
 * 2026-10-09 운영 축제 조회에 BIFF 가 없어서 여기 둔다.
 */
export type FeaturedFestival = { placeId: string; startDate: string; endDate: string; title: Labels };
export const FEATURED_FESTIVALS: readonly FeaturedFestival[] = [
  {
    placeId: 'd8836d09-0c5c-4d15-a962-95894af98d2e', // 영화의전당
    startDate: '2026-10-06',
    endDate: '2026-10-15',
    title: {
      ko: '제31회 부산국제영화제',
      en: 'Busan International Film Festival',
      ja: '第31回 釜山国際映画祭',
      'zh-Hans': '第31届 釜山国际电影节',
      'zh-Hant': '第31屆 釜山國際電影節',
      vi: 'Liên hoan phim quốc tế Busan',
    },
  },
];

/** 오늘(YYYY-MM-DD) 열려 있는 대표 축제. */
export function currentFeaturedFestival(today: string, list: readonly FeaturedFestival[] = FEATURED_FESTIVALS): FeaturedFestival | null {
  return list.find((f) => f.startDate <= today && today <= f.endDate) ?? null;
}

/** 축제 데이터에서 축제 칸에 세울 하나 — 오늘 열리는 것 중 기간이 가장 짧은 것(1년 내내 하는 행사보다 지금만 하는 축제), 없으면 가장 먼저 열리는 것. */
export function pickFestival<T extends { startDate: string; endDate: string }>(items: T[], today: string): T | null {
  const span = (f: T) => Date.parse(f.endDate) - Date.parse(f.startDate);
  const ongoing = items.filter((f) => f.startDate <= today && today <= f.endDate).sort((a, b) => span(a) - span(b));
  if (ongoing.length) return ongoing[0];
  return [...items].filter((f) => f.startDate > today).sort((a, b) => a.startDate.localeCompare(b.startDate))[0] ?? null;
}

const SECTION_TITLE: Labels = { ko: '로컬 탐색', en: 'Explore local Busan', ja: 'ローカル探索', 'zh-Hans': '本地探索', 'zh-Hant': '在地探索', vi: 'Khám phá địa phương' };
const MORE: Labels = { ko: '더 보기', en: 'More', ja: 'もっと見る', 'zh-Hans': '查看更多', 'zh-Hant': '查看更多', vi: 'Xem thêm' };

export function localSectionTitle(language: LanguageCode): string { return SECTION_TITLE[language] ?? SECTION_TITLE.en; }
export function moreLabel(language: LanguageCode): string { return MORE[language] ?? MORE.en; }
export function pickLabel(key: LocalPickKey, language: LanguageCode): string { return LOCAL_PICK_LABELS[key][language] ?? LOCAL_PICK_LABELS[key].en; }
