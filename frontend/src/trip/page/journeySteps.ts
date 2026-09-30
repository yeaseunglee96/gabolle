/**
 * 「지금 할 이동」 한 구간을 걸음마다 한 문장으로 — UI 캔버스 ㉓-2b(여정 한 줄), S15P21E201-1884.
 *
 * 경로 상세(app/route-detail.tsx)는 탈 것마다 칩과 두 줄을 그린다. 일정 화면에서는 그것을 열기 전에
 * 「무엇을 해야 하는지」만 순서대로 말한다 — 정류장까지 걷고, 몇 번을 타고, 어디서 내려 걷는다.
 *
 * 🔴 서버 대중교통 단계(TransitRouteAdapter.toLeg)에는 **탄 구간과 걷는 환승만** 있다. 정류장까지 걷는 시간과
 *    내려서 걷는 시간은 단계가 아니라 전체 시간(durationMin)에만 들어 있다. 그래서 둘을 합친 «걷는 시간»은
 *    전체에서 탄 시간을 뺀 값으로만 센다 — 앞뒤로 나누는 비율은 모르니 문장에 분을 적지 않는다.
 *
 * 🔴 정류장·역 이름은 서버가 한국어로 준다. 외국어 화면은 읽는 이름(역은 부산교통공사 공식 영문 역명, 버스
 *    정류장은 로마자)을 적고, 길의 표지판과 맞춰 볼 한국어 원문을 `sign` 으로 따로 넘긴다.
 */
import type { LanguageCode } from '@/i18n/languages';
import type { RouteDirections } from '@/map/routeDirections';
import { txf } from '@/i18n/format';
import { romanizeKorean } from '@/discovery/romanize';
import { stationEnglishName, stationEnglishTitle } from '@/field/subwayStations';
import { parseRideGuidance, subwayRide } from '@/field/subwayRide';
import { parseTransitGuidance, transitStepKind, type TransitStepKind } from '@/field/routeLegs';

type Tx = (ko: string, en: string) => string;

export type JourneyStep = {
  kind: TransitStepKind;
  /** 칩 글자 — 「도보」 · 「139」 · 「2호선」 */
  chip: string;
  /** 해야 할 일 한 문장 */
  text: string;
  /** 외국어 화면에서 표지판과 맞춰 볼 한국어 이름. 한국어 화면·모르는 이름이면 없음 */
  sign?: string;
};

export type JourneyBarPart = { kind: TransitStepKind; minutes: number };

export type Journey = { steps: JourneyStep[]; bar: JourneyBarPart[]; minutes: number };

/** 정류장 이름 — 한국어는 그대로, 그 밖은 로마자. */
function stopLabel(name: string, language: LanguageCode): string {
  if (language === 'ko') return name;
  return romanizeKorean(name) ?? name;
}

/** 역 이름 — 한국어는 「서면역」, 그 밖은 공식 영문 역명(route-detail 의 stationLabel 과 같은 규칙). */
function stationLabel(name: string, language: LanguageCode): string {
  if (language === 'ko') return /역$/.test(name) ? name : `${name}역`;
  return stationEnglishTitle(name) ?? romanizeKorean(name) ?? name;
}

function signOf(name: string, language: LanguageCode): string | undefined {
  return language === 'ko' ? undefined : name;
}

function lineChip(name: string, tx: Tx): string {
  const m = name.match(/^(\d+)호선$/);
  return m ? txf(tx, '%s호선', 'Line %s', m[1]) : name;
}

/**
 * 받은 경로를 걸음 문장으로. 대중교통이 아니거나 탄 구간이 없으면 걷기 한 줄.
 * @param destName 화면에 적는 목적지 이름(이미 언어에 맞춘 것)
 */
export function journeyOf(directions: RouteDirections, destName: string, language: LanguageCode, tx: Tx): Journey {
  const minutes = Math.max(0, Math.round(directions.durationMin));
  const rides = directions.mode === 'TRANSIT' ? directions.steps.filter((step) => transitStepKind(step) !== 'walk') : [];
  if (rides.length === 0) {
    return {
      minutes,
      steps: [{ kind: 'walk', chip: tx('도보', 'Walk'), text: txf(tx, '%s까지 걸어서 %s분', 'Walk to %s · %s min', destName, String(minutes)) }],
      bar: [{ kind: 'walk', minutes: Math.max(1, minutes) }],
    };
  }

  const steps: JourneyStep[] = [];
  const bar: JourneyBarPart[] = [];
  const rideMinutes = directions.steps.reduce((sum, step) => sum + Math.max(0, step.durationMin), 0);
  // 정류장까지 + 내려서 — 앞뒤 비율을 모르므로 막대에는 반씩 나눠 그린다(길이의 느낌만 준다).
  const accessWalk = Math.max(0, minutes - rideMinutes);

  const subway = transitStepKind(rides[0]) === 'subway';
  // 지하철 안내의 역 이름에는 「서면역(1호선)」처럼 호선이 붙어 온다 — 역 이름 풀이는 parseRideGuidance 가 한다.
  const first = subway ? parseRideGuidance(rides[0].guidance) : parseTransitGuidance(rides[0].guidance);
  if (first) {
    const place = subway ? stationLabel(first.from, language) : stopLabel(first.from, language);
    steps.push({
      kind: 'walk',
      chip: tx('도보', 'Walk'),
      text: subway ? txf(tx, '%s까지 걸어가요', 'Walk to %s', place) : txf(tx, '%s 정류장까지 걸어가요', 'Walk to the %s stop', place),
      sign: signOf(first.from, language),
    });
  }
  if (accessWalk > 0) bar.push({ kind: 'walk', minutes: accessWalk / 2 });

  for (const step of directions.steps) {
    const kind = transitStepKind(step);
    bar.push({ kind, minutes: Math.max(1, step.durationMin) });
    if (kind === 'walk') {
      steps.push({ kind, chip: tx('도보', 'Walk'), text: tx('걸어서 갈아타요', 'Walk to your transfer') });
      continue;
    }
    if (kind === 'subway') {
      const parsed = parseRideGuidance(step.guidance);
      const ride = parsed ? subwayRide(step.name, parsed.from, parsed.to) : null;
      const to = parsed?.to ?? parseTransitGuidance(step.guidance)?.to ?? null;
      const towards = ride ? (language === 'ko' ? ride.towards : stationEnglishName(ride.towards) ?? romanizeKorean(ride.towards) ?? ride.towards) : null;
      const text = ride && to
        ? txf(tx, '%s 방면을 타고 %s정거장 · %s에서 내려요', 'Take the train towards %s for %s stops · get off at %s', towards!, String(ride.stopCount), stationLabel(to, language))
        : to ? txf(tx, '%s에서 내려요', 'Get off at %s', stationLabel(to, language)) : step.guidance;
      steps.push({ kind, chip: lineChip(step.name, tx), text, sign: to ? signOf(to, language) : undefined });
      continue;
    }
    const parsed = parseTransitGuidance(step.guidance);
    steps.push({
      kind,
      chip: step.name,
      text: parsed
        ? txf(tx, '%s번 버스를 타고 %s에서 내려요', 'Take bus %s · get off at %s', step.name, stopLabel(parsed.to, language))
        : step.guidance,
      sign: parsed ? signOf(parsed.to, language) : undefined,
    });
  }

  if (accessWalk > 0) bar.push({ kind: 'walk', minutes: accessWalk / 2 });
  steps.push({ kind: 'walk', chip: tx('도보', 'Walk'), text: txf(tx, '내려서 %s까지 걸어요', 'Walk to %s', destName) });
  return { minutes, steps, bar };
}
