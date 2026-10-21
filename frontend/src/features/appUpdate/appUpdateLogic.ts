// 앱 안 업데이트 안내·새로워진 점의 판정(S15P21E201-2048). 화면 없이 시험할 수 있게 순수 함수만 둔다.
import type { LanguageCode } from '@/i18n/languages';

/** 「1.0.3」 끼리 견준다. a<b 면 음수. 숫자가 아닌 마디는 0 으로 본다. */
export function compareVersions(a: string, b: string): number {
  const pa = a.split('.').map((part) => Number.parseInt(part, 10) || 0);
  const pb = b.split('.').map((part) => Number.parseInt(part, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i += 1) {
    const diff = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (diff !== 0) return diff;
  }
  return 0;
}

const VERSION_SHAPE = /^\d+(\.\d+){0,3}$/;
export function isVersion(value: unknown): value is string {
  return typeof value === 'string' && VERSION_SHAPE.test(value.trim());
}

export type AppRelease = { latestVersion: string; minSupportedVersion: string; storeUrl: string };
export type UpdatePrompt = { kind: 'none' } | { kind: 'soft' | 'force'; latestVersion: string; storeUrl: string };

/**
 * 🔴 최소 지원 버전 미만이면 막는다(force). 최신보다 낮고 그 판을 「나중에」로 닫은 적이 없으면 권한다(soft).
 * 서버 값이 버전 모양이 아니면 아무것도 안 한다 — 모르는 값으로 화면을 막지 않는다.
 */
export function decideUpdatePrompt(current: string | null, release: AppRelease | null, dismissedVersion: string | null): UpdatePrompt {
  if (!current || !isVersion(current) || !release) return { kind: 'none' };
  if (!isVersion(release.latestVersion) || !isVersion(release.minSupportedVersion) || !release.storeUrl) return { kind: 'none' };
  if (compareVersions(current, release.minSupportedVersion) < 0) {
    return { kind: 'force', latestVersion: release.latestVersion, storeUrl: release.storeUrl };
  }
  if (compareVersions(current, release.latestVersion) < 0 && dismissedVersion !== release.latestVersion) {
    return { kind: 'soft', latestVersion: release.latestVersion, storeUrl: release.storeUrl };
  }
  return { kind: 'none' };
}

export const CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000;

/** 하루에 한 번만 묻는다. 기록이 없거나 시계가 뒤로 갔으면 묻는다. */
export function shouldCheckNow(lastCheckedAt: number | null, now: number): boolean {
  if (lastCheckedAt === null || !Number.isFinite(lastCheckedAt)) return true;
  if (now < lastCheckedAt) return true;
  return now - lastCheckedAt >= CHECK_INTERVAL_MS;
}

/**
 * 「새로워진 점」을 보일까.
 * - 마지막으로 본 판이 지금보다 낮으면 → 업데이트한 것이다 → 보인다
 * - 기록이 없으면: 이 기능 전의 앱은 판을 안 적었다. 이미 앱에 들어온 적이 있는 사람(온보딩을 마쳤다)은
 *   업데이트한 사람으로 보고 보인다. 처음 설치한 사람에게는 안 보인다
 * 새로워진 점 목록이 없는 판이면 보이지 않는다.
 */
export function shouldShowWhatsNew(current: string | null, lastSeenVersion: string | null, isExistingUser: boolean): boolean {
  if (!current || !isVersion(current) || !WHATS_NEW[current]) return false;
  if (lastSeenVersion && isVersion(lastSeenVersion)) return compareVersions(lastSeenVersion, current) < 0;
  return isExistingUser;
}

export type Localized = Record<LanguageCode, string>;

/** 판마다 새로워진 점. 새 판을 낼 때 여기에 한 묶음을 더한다(짧게, 다섯 줄 안팎). */
export const WHATS_NEW: Record<string, Localized[]> = {
  '1.0.3': [
    {
      ko: '일정을 바로 고치고, 직접 일정을 짤 수 있어요',
      en: 'Edit your itinerary on the spot, or build one yourself',
      ja: '日程をその場で直したり、自分で組んだりできます',
      'zh-Hans': '可以直接修改行程，也可以自己安排行程',
      'zh-Hant': '可以直接修改行程，也可以自己安排行程',
      vi: 'Sửa lịch trình ngay hoặc tự lên lịch trình của bạn',
    },
    {
      ko: '다른 여행자의 루트를 루트 피드에서 볼 수 있어요',
      en: "Browse other travelers' routes in the Route Feed",
      ja: 'ルートフィードで他の旅行者のルートを見られます',
      'zh-Hans': '在路线动态中查看其他旅行者的路线',
      'zh-Hant': '在路線動態中查看其他旅行者的路線',
      vi: 'Xem lộ trình của du khách khác trong Bảng tin lộ trình',
    },
    {
      ko: '현지인이 추천한 장소를 만나 보세요',
      en: 'Discover places recommended by locals',
      ja: '地元の人がすすめる場所に出会えます',
      'zh-Hans': '发现当地人推荐的地点',
      'zh-Hant': '發現當地人推薦的地點',
      vi: 'Khám phá những địa điểm do người địa phương giới thiệu',
    },
    {
      ko: '베트남어를 지원해요',
      en: 'Now available in Vietnamese',
      ja: 'ベトナム語に対応しました',
      'zh-Hans': '新增越南语',
      'zh-Hant': '新增越南語',
      vi: 'Đã hỗ trợ tiếng Việt',
    },
    {
      ko: '홈 화면과 길의 경사 표시가 더 보기 쉬워졌어요',
      en: 'The home screen and slope display are easier to read',
      ja: 'ホーム画面と坂道の表示が見やすくなりました',
      'zh-Hans': '首页和坡度显示更清晰了',
      'zh-Hant': '首頁和坡度顯示更清楚了',
      vi: 'Màn hình chính và hiển thị độ dốc dễ xem hơn',
    },
  ],
};

export const COPY = {
  updateTitle: { ko: '새 버전이 나왔어요', en: 'A new version is available', ja: '新しいバージョンがあります', 'zh-Hans': '有新版本了', 'zh-Hant': '有新版本了', vi: 'Đã có phiên bản mới' },
  updateBody: { ko: '업데이트하고 더 나아진 가볼래를 만나 보세요.', en: 'Update to get the latest improvements.', ja: 'アップデートして、より良くなったアプリをお使いください。', 'zh-Hans': '更新后即可使用最新改进。', 'zh-Hant': '更新後即可使用最新改進。', vi: 'Cập nhật để dùng những cải tiến mới nhất.' },
  forceTitle: { ko: '업데이트가 필요해요', en: 'Update required', ja: 'アップデートが必要です', 'zh-Hans': '需要更新', 'zh-Hant': '需要更新', vi: 'Cần cập nhật' },
  forceBody: { ko: '이 버전은 더 이상 쓸 수 없어요. 새 버전으로 업데이트해 주세요.', en: 'This version is no longer supported. Please update to continue.', ja: 'このバージョンは利用できなくなりました。アップデートしてください。', 'zh-Hans': '此版本已停止支持，请更新后继续使用。', 'zh-Hant': '此版本已停止支援，請更新後繼續使用。', vi: 'Phiên bản này không còn được hỗ trợ. Vui lòng cập nhật để tiếp tục.' },
  update: { ko: '업데이트', en: 'Update', ja: 'アップデート', 'zh-Hans': '更新', 'zh-Hant': '更新', vi: 'Cập nhật' },
  later: { ko: '나중에', en: 'Later', ja: 'あとで', 'zh-Hans': '以后再说', 'zh-Hant': '以後再說', vi: 'Để sau' },
  whatsNewTitle: { ko: '새로워진 점', en: "What's new", ja: '新機能', 'zh-Hans': '新功能', 'zh-Hant': '新功能', vi: 'Có gì mới' },
  ok: { ko: '확인', en: 'OK', ja: 'OK', 'zh-Hans': '确定', 'zh-Hant': '確定', vi: 'OK' },
} satisfies Record<string, Localized>;
