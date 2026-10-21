/**
 * 가까운 병원·약국·경찰 — 서버 조회(S15P21E201-1894). 서버 자료는 건강보험심사평가원 「전국 병의원 및 약국 현황」이라
 * 앱에 실은 OSM 자료(nearbyHelp.ts)보다 훨씬 많다(부산 약국 1,733 대 70). 서버 계약: back/dev
 * `GET /api/v1/help-places/nearby`(S15P21E201-1893).
 *
 * 🔴 급할 때 부르는 화면이라 오래 기다리지 않는다 — 6초 안에 못 받으면 앱 자료로 물러선다(화면이 그렇게 말한다).
 */
import { apiRequest } from '@/api/client';
import { coarseCoordinate } from '@/personalization/locationConsent';

import type { HelpKind } from './nearbyHelp';

export type ServerHelpPlace = {
  name: string;
  nameEn: string | null;
  /** 종별 — 「상급종합」「종합병원」「병원」「의원」「보건소」「약국」「경찰」 */
  type: string | null;
  address: string | null;
  phone: string | null;
  lat: number;
  lng: number;
  distanceMeters: number;
  emergency: boolean;
  /** 오늘 여는·닫는 시각 「09:00」. 모르거나 쉬는 날이면 null */
  todayOpen: string | null;
  todayClose: string | null;
  /** 지금 진료 중인가. 오늘 쉬면 false, 진료시간을 모르면 null */
  openNow: boolean | null;
};

export type ServerNearbyHelp = { kind: string; places: ServerHelpPlace[]; source: string; basedOn: string };

export const HELP_TIMEOUT_MS = 6000;

const SERVER_KIND: Record<HelpKind, string> = { hospital: 'HOSPITAL', pharmacy: 'PHARMACY', police: 'POLICE' };

/** 받으면 목록, 못 받으면(오프라인·서버 없음·늦음) null — 부르는 쪽이 앱 자료로 물러선다. */
export async function getNearbyHelp(kind: HelpKind, at: { latitude: number; longitude: number }, accessToken: string | null, limit = 5): Promise<ServerNearbyHelp | null> {
  // 🔴 좌표는 약 100m 단위로 줄여서 보낸다 — 위치 동의가 「내 주변 찾기는 약 100m 단위로」라고 약속한다(둘러보기·버스와 같다)
  const query = new URLSearchParams({ kind: SERVER_KIND[kind], lat: String(coarseCoordinate(at.latitude)), lng: String(coarseCoordinate(at.longitude)), limit: String(limit) });
  // 🔴 요청 한 번의 제한(timeoutMs)만으로는 모자란다 — 503 이면 apiRequest 가 쉬었다 다시 묻기를 되풀이해서(client.ts)
  //    전체가 6초를 훌쩍 넘었다(서버가 없을 때 「찾고 있어요…」에 멈춤, 화면 확인). 전체에 한 번 더 제한을 건다.
  let timer: ReturnType<typeof setTimeout> | undefined;
  const giveUp = new Promise<null>((resolve) => { timer = setTimeout(() => resolve(null), HELP_TIMEOUT_MS); });
  const ask = apiRequest<ServerNearbyHelp>(`/api/v1/help-places/nearby?${query.toString()}`, { accessToken, timeoutMs: HELP_TIMEOUT_MS })
    .then((result) => (Array.isArray(result?.places) ? result : null))
    .catch(() => null);
  try {
    return await Promise.race([ask, giveUp]);
  } finally {
    clearTimeout(timer);
  }
}
