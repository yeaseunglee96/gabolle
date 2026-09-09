// 해가 지금 하늘의 어디에 있는가.
//
// 왜 필요한가 — 지금까지 이 실험의 해는 지어낸 곡선이었다 (방위 = 90 + 시간 × 180).
// 그것으로도 화면은 그럴듯했지만 그림자를 그리는 순간 거짓말이 된다. 그림자는
// 길이와 방향이 전부라서, 해가 틀리면 도시 전체가 틀린 쪽으로 눕는다.
//
// 계산 방식은 NOAA Solar Calculator — 천문학자 Jean Meeus 의 알고리즘이다.
// 팀의 그림자 분석(bigData)이 이미 같은 방식으로 계산했고, 그때 남긴 검증값이
// sun-check.mjs 의 채점표가 된다. 그래서 이 파일이 맞게 쓰였는지 확인할 방법이 있다.
//
// 외부 라이브러리를 쓰지 않는다. 브라우저와 Node 양쪽에서 그대로 돈다.

const rad = (d) => (d * Math.PI) / 180;
const deg = (r) => (r * 180) / Math.PI;
const mod = (x, n) => ((x % n) + n) % n;

/**
 * @param {Date} when  시각 (UTC 기준으로 읽는다)
 * @param {number} latDeg 위도
 * @param {number} lonDeg 경도 (동쪽이 +)
 * @returns {{altitude:number, azimuth:number, declination:number, eqTimeMin:number}}
 *   altitude 지평선 위 각도(도). 음수면 해가 져 있다
 *   azimuth  북쪽 0, 동쪽 90, 남쪽 180, 서쪽 270 (도)
 */
export function sunPosition(when, latDeg, lonDeg) {
  // 율리우스일 — 기원전 4713년부터 며칠 지났는지를 소수로 센 것. 천문 계산의 시간 단위다.
  const jd = when.getTime() / 86400000 + 2440587.5;
  // 율리우스 세기 — 2000년 1월 1일 정오로부터 100년 단위로 얼마나 지났는가.
  const T = (jd - 2451545) / 36525;

  // 태양의 평균 황경 — 지구 궤도가 완전한 원이라면 해가 있을 자리.
  const L0 = mod(280.46646 + T * (36000.76983 + T * 0.0003032), 360);
  // 평균 근점이각 — 궤도 위에서 근일점으로부터 얼마나 돌았는가.
  const M = 357.52911 + T * (35999.05029 - 0.0001537 * T);
  // 궤도 이심률 — 지구 궤도가 원에서 얼마나 찌그러졌는가.
  const e = 0.016708634 - T * (0.000042037 + 0.0000001267 * T);
  // 중심차 — 실제 궤도가 타원이라서 생기는 어긋남을 더한다.
  const C =
    Math.sin(rad(M)) * (1.914602 - T * (0.004817 + 0.000014 * T)) +
    Math.sin(rad(2 * M)) * (0.019993 - 0.000101 * T) +
    Math.sin(rad(3 * M)) * 0.000289;

  const trueLong = L0 + C;
  // 달의 흔들림(장동)까지 보정한 겉보기 황경.
  const omega = 125.04 - 1934.136 * T;
  const lambda = trueLong - 0.00569 - 0.00478 * Math.sin(rad(omega));

  // 황도 경사각 — 지구 자전축이 궤도면에 대해 기울어진 각도. 계절을 만드는 값이다.
  const eps0 = 23 + (26 + (21.448 - T * (46.815 + T * (0.00059 - T * 0.001813))) / 60) / 60;
  const eps = eps0 + 0.00256 * Math.cos(rad(omega));

  // 적위 — 해가 적도에서 남북으로 얼마나 떨어져 있는가. 여름이면 +23도쯤이다.
  const declination = deg(Math.asin(Math.sin(rad(eps)) * Math.sin(rad(lambda))));

  // 균시차 — 시계의 정오와 해가 실제로 남중하는 시각의 차이(분).
  // 궤도가 타원이고 자전축이 기울어서 생긴다. 연중 -14분 ~ +16분 사이를 오간다.
  const y = Math.tan(rad(eps / 2)) ** 2;
  const eqTimeMin =
    4 *
    deg(
      y * Math.sin(2 * rad(L0)) -
        2 * e * Math.sin(rad(M)) +
        4 * e * y * Math.sin(rad(M)) * Math.cos(2 * rad(L0)) -
        0.5 * y * y * Math.sin(4 * rad(L0)) -
        1.25 * e * e * Math.sin(2 * rad(M))
    );

  // 진태양시 — "이 자리에서 해를 기준으로 몇 시인가". 720분이면 해가 정남에 있다.
  const minutesUTC =
    when.getUTCHours() * 60 + when.getUTCMinutes() + when.getUTCSeconds() / 60;
  const trueSolarMin = mod(minutesUTC + eqTimeMin + 4 * lonDeg, 1440);
  // 시간각 — 남중을 0으로 두고 한 시간에 15도씩. 오전이 음수, 오후가 양수다.
  const hourAngle = trueSolarMin / 4 - 180;

  const zenithCos =
    Math.sin(rad(latDeg)) * Math.sin(rad(declination)) +
    Math.cos(rad(latDeg)) * Math.cos(rad(declination)) * Math.cos(rad(hourAngle));
  const zenith = deg(Math.acos(Math.max(-1, Math.min(1, zenithCos))));
  const rawAltitude = 90 - zenith;

  // 방위 — 북쪽을 0으로 시계 방향.
  let azimuth;
  const azDenom = Math.cos(rad(latDeg)) * Math.sin(rad(zenith));
  if (Math.abs(azDenom) > 0.001) {
    let c = (Math.sin(rad(latDeg)) * Math.cos(rad(zenith)) - Math.sin(rad(declination))) / azDenom;
    c = Math.max(-1, Math.min(1, c));
    let a = 180 - deg(Math.acos(c));
    if (hourAngle > 0) a = -a;
    azimuth = mod(a, 360);
  } else {
    azimuth = latDeg > 0 ? 180 : 0;
  }

  return {
    altitude: rawAltitude + refractionDeg(rawAltitude),
    azimuth,
    declination,
    eqTimeMin,
  };
}

// 대기 굴절 — 공기가 빛을 휘어서, 해가 실제보다 조금 높이 떠 보인다.
// 지평선 근처에서 약 0.5도까지 벌어진다. 해 뜨는 순간이 계산보다 이른 이유가 이것이다.
function refractionDeg(altDeg) {
  if (altDeg > 85) return 0;
  const te = Math.tan(rad(altDeg));
  let arcsec;
  if (altDeg > 5) arcsec = 58.1 / te - 0.07 / te ** 3 + 0.000086 / te ** 5;
  else if (altDeg > -0.575) arcsec = 1735 + altDeg * (-518.2 + altDeg * (103.4 + altDeg * (-12.79 + altDeg * 0.711)));
  else arcsec = -20.772 / te;
  return arcsec / 3600;
}

/**
 * 해가 남중하는 시각 — 그 지역 시계로 몇 시 몇 분인가.
 * @param {Date} dayUTC 그 날 아무 시각
 * @param {number} lonDeg 경도
 * @param {number} tzOffsetHours 시간대 (한국은 9)
 * @returns {number} 자정부터의 분
 */
export function solarNoonMinutes(dayUTC, lonDeg, tzOffsetHours) {
  // 균시차는 하루 안에서 거의 안 변하므로 그날 정오 값으로 한 번만 구한다.
  const noonUTC = new Date(Date.UTC(dayUTC.getUTCFullYear(), dayUTC.getUTCMonth(), dayUTC.getUTCDate(), 12));
  const { eqTimeMin } = sunPosition(noonUTC, 0, lonDeg);
  return 720 - 4 * lonDeg - eqTimeMin + tzOffsetHours * 60;
}

/**
 * 높이 h 인 것이 만드는 그림자의 길이.
 * 해가 낮을수록 급격히 길어진다 — 해 5도면 60m 건물의 그림자가 686m 다.
 * 그래서 화면에서는 어느 각도 아래로는 그림자를 안 그리는 편이 낫다.
 */
export function shadowLengthM(heightM, altitudeDeg) {
  if (altitudeDeg <= 0) return Infinity;
  return heightM / Math.tan(rad(altitudeDeg));
}

/** 한국 시각(KST)으로 적은 날짜·시각을 Date 로. 서머타임 없음. */
export function kst(year, month, day, hour = 0, minute = 0) {
  return new Date(Date.UTC(year, month - 1, day, hour - 9, minute));
}

// ── 일출·일몰·낮 길이 ─────────────────────────────────────────────
// 왜 필요한가 — 그림자 우선 경로는 "몇 시에 그늘인가" 를 묻는 기능이다. 그런데 해가 떠 있는
// 시간 자체가 계절마다 3시간 넘게 다르다. 부산의 6월 낮은 14시간 27분, 12월 낮은 9시간 55분이다.
// 슬라이더를 4~22시로 고정해 두면 겨울에는 슬라이더의 절반이 캄캄한 시각이고,
// 여름에는 해 뜨는 시각이 슬라이더 밖에 있다. 그래서 그날의 낮에 맞춰 범위를 만든다.
//
// 계산 방식 — NOAA Solar Calculator 와 같다.
//   해의 중심이 지평선 아래 0.833° 에 올 때를 일출/일몰로 본다.
//   0.833° = 대기 굴절 0.567° + 해의 반지름 0.267° 다. 해의 **윗 가장자리**가 지평선에
//   걸리는 순간이 우리가 "해가 떴다" 고 부르는 순간이기 때문이다.
//   기상청·천문연구원의 일출/일몰표도 같은 기준이다.
const SUNRISE_ZENITH = 90.833;

/**
 * 그날의 일출·일몰·남중 시각. 전부 **그 지역 시계로 자정부터의 분**이다.
 * @param {number} year  연 (지역 시각 기준)
 * @param {number} month 월 1~12
 * @param {number} day   일
 * @param {number} latDeg 위도
 * @param {number} lonDeg 경도
 * @param {number} tzOffsetHours 시간대 (한국 9)
 * @returns {{sunriseMin:number|null, sunsetMin:number|null, noonMin:number,
 *            dayLengthMin:number, polar:'none'|'day'|'night'}}
 *   극지에서는 해가 안 뜨거나 안 지는 날이 있다. 그때 sunrise/sunset 은 null 이고
 *   polar 가 'day'(하루 종일 낮) 또는 'night'(하루 종일 밤)이 된다. 부산에서는 안 생기지만,
 *   좌표를 바꿔 쓸 수 있는 함수라 없는 셈 치지 않는다.
 */
export function sunTimes(year, month, day, latDeg, lonDeg, tzOffsetHours = 9) {
  // 그날의 적위는 하루 안에서 거의 안 변하므로 지역 정오 한 번으로 구한다.
  const localNoonUTC = new Date(Date.UTC(year, month - 1, day, 12 - tzOffsetHours));
  const { declination } = sunPosition(localNoonUTC, latDeg, lonDeg);
  const noonMin = solarNoonMinutes(localNoonUTC, lonDeg, tzOffsetHours);

  const cosH =
    (Math.cos(rad(SUNRISE_ZENITH)) - Math.sin(rad(latDeg)) * Math.sin(rad(declination))) /
    (Math.cos(rad(latDeg)) * Math.cos(rad(declination)));

  if (cosH > 1) return { sunriseMin: null, sunsetMin: null, noonMin, dayLengthMin: 0, polar: 'night' };
  if (cosH < -1) return { sunriseMin: null, sunsetMin: null, noonMin, dayLengthMin: 1440, polar: 'day' };

  // 시간각을 분으로. 한 시간에 15도 도니까 1도 = 4분이다.
  const halfDayMin = 4 * deg(Math.acos(cosH));
  return {
    sunriseMin: noonMin - halfDayMin,
    sunsetMin: noonMin + halfDayMin,
    noonMin,
    dayLengthMin: halfDayMin * 2,
    polar: 'none',
  };
}

/** 자정부터의 분을 'HH:MM' 으로. */
export function hhmm(minutes) {
  if (minutes === null || !Number.isFinite(minutes)) return '–';
  const m = Math.round(minutes);
  return String(Math.floor(m / 60) % 24).padStart(2, '0') + ':' + String(((m % 60) + 60) % 60).padStart(2, '0');
}

/**
 * 지금 한국 날짜. 이 PC 의 시간대가 무엇이든 **부산 현지 날짜**를 준다 —
 * 브라우저가 UTC 로 맞춰져 있으면 한국의 오전 8시가 전날로 읽히기 때문이다.
 * @returns {[number, number, number]} [연, 월(1~12), 일]
 */
export function todayKST(now = new Date()) {
  const k = new Date(now.getTime() + 9 * 3600000);
  return [k.getUTCFullYear(), k.getUTCMonth() + 1, k.getUTCDate()];
}

/** 지금 한국 시각을 소수 시(예: 14.5 = 14:30)로. 슬라이더의 초기값에 쓴다. */
export function nowHourKST(now = new Date()) {
  const k = new Date(now.getTime() + 9 * 3600000);
  return k.getUTCHours() + k.getUTCMinutes() / 60;
}
