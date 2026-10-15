// 근처 편의시설 카드 넷 (S15P21E201-2052).
// 🔴 이 시험이 지키는 것은 «모른다를 아는 척하지 않는 것»이다 — 요금을 모르는 주차장은 「무료」도 「0원」도 아니고, 영업 시간을 모르는 곳은
//    「영업 종료」가 아니며, 물품보관함의 칸 수는 «비어 있는 칸»이 아니다. 화면의 결함은 타입도 번역 검사도 못 잡는다.
import { fireEvent, render, within } from '@testing-library/react-native';

import { romanizeKorean } from '@/discovery/romanize';

import {
  LockerCard,
  ParkingCard,
  PharmacyCard,
  ToiletCard,
  type FacilityActions,
  type ListLink,
} from '../NearbyFacilityCards';
import type { FacilityItem, LockerDetail, ParkingDetail, PharmacyDetail, ToiletDetail } from '../nearbyFacilities';

let mockLang: 'ko' | 'en' | 'ja' = 'ko';
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string, en: string) => (mockLang === 'ko' ? ko : en), language: mockLang }) }));

type Item<K extends FacilityItem['kind']> = Extract<FacilityItem, { kind: K }>;

const base = { id: 'f1', address: '부산광역시 중구 중앙동 1', lat: 35.1, lng: 129.0, distanceM: 230 };

const parking = (detail: Partial<ParkingDetail> = {}, name = '시험주차장'): Item<'PARKING'> => ({
  ...base, name, kind: 'PARKING',
  detail: {
    operator: 'PUBLIC', free: false, spaces: 94,
    fee: { baseMinutes: 30, baseWon: 300, addMinutes: 10, addWon: 100, dayMaxWon: 2400, freeMinutes: 0 },
    estimatedWon: 1200, note: null, openNow: true, todayHours: { open: '08:00', close: '20:00' }, closesAt: '20:00', allDay: false,
    ...detail,
  },
});

const pharmacy = (detail: Partial<PharmacyDetail> = {}): Item<'PHARMACY'> => ({
  ...base, name: '시험약국', kind: 'PHARMACY',
  detail: {
    tel: '051-241-0123', directions: '1호선 토성역 8번출구', note: null, hoursAreReported: true, openNow: true,
    todayHours: { open: '09:00', close: '18:00' }, closesAt: '18:00', allDay: false, opensSunday: false, opensHoliday: false, lateNight: false,
    ...detail,
  },
});

const toilet = (detail: Partial<ToiletDetail> = {}): Item<'TOILET'> => ({
  ...base, name: '시험화장실', kind: 'TOILET',
  detail: { type: 'PUBLIC', openHours: '09:00~18:00', alwaysOpen: false, accessible: false, diaper: false, diaperPlace: null, emergencyBell: false, cctv: false, tel: null, ...detail },
});

const locker = (detail: Partial<LockerDetail> = {}): Item<'LOCKER'> => ({
  ...base, id: 'l1', name: '하단역', address: null, kind: 'LOCKER',
  detail: {
    station: '하단', stationEn: 'Hadan', line: '1', location: '(B1) 1번 엘리베이터 옆, 3번출입구 방향', small: 10, medium: 10, large: 0, xlarge: 7,
    feeText: '특대: 6,000원 / 대:4,000원 / 중: 3,000원 / 소: 2,000원(3시간당)', operator: '위드락커', ...detail,
  },
});

const actions = (over: Partial<FacilityActions> = {}): FacilityActions => ({ onRoute: jest.fn(), onMap: jest.fn(), ...over });

beforeEach(() => { mockLang = 'ko'; });

describe('주차장 카드', () => {
  it('요금을 아는 유료 주차장 — 예상요금과 «몇 시간 기준», 하루 최대, 공영, 영업 중, 면수, 거리와 걸음', () => {
    const view = render(<ParkingCard item={parking()} actions={actions()} stayHours={2} />);

    expect(view.getByText('시험주차장')).toBeTruthy();
    expect(view.getByText('약 1,200원')).toBeTruthy();
    expect(view.getByText('2시간 기준')).toBeTruthy();
    expect(view.getByText('하루 최대 2,400원')).toBeTruthy();
    expect(view.getByText('공영')).toBeTruthy();
    expect(view.getByText('지금 영업 중 · 20:00까지')).toBeTruthy();
    expect(view.getByText('94면')).toBeTruthy();
    expect(view.getByText('230m')).toBeTruthy();
    // 230m ÷ 80m/분 = 2.9 → 3분
    expect(view.getByText('걸어서 3분')).toBeTruthy();
    expect(view.getByText('부산광역시 중구 중앙동 1')).toBeTruthy();
  });

  it('무료 주차장은 「무료」 — 금액도 «기준»도 없다', () => {
    const view = render(<ParkingCard item={parking({ free: true, fee: null, estimatedWon: 0 })} actions={actions()} stayHours={2} />);

    expect(view.getByTestId('facility-fee-f1')).toBeTruthy();
    expect(view.getByText('무료')).toBeTruthy();
    expect(view.queryByText(/원/)).toBeNull();
    expect(view.queryByText('2시간 기준')).toBeNull();
  });

  it('🔴 요금을 모르는 유료 주차장은 「요금 정보 없음」 — 무료도 0원도 아니다', () => {
    const view = render(<ParkingCard item={parking({ fee: null, estimatedWon: null })} actions={actions()} stayHours={2} />);

    expect(view.getByText('요금 정보 없음')).toBeTruthy();
    expect(view.getByText('현장에서 확인해 주세요')).toBeTruthy();
    expect(view.queryByText('무료')).toBeNull();
    expect(view.queryByText(/0원/)).toBeNull();
    expect(view.queryByText(/약 /)).toBeNull();
  });

  it('🔴 기본요금만 아는 곳은 «기본 30분 300원»까지만 말하고 추가요금은 현장에서 — 예상 금액을 지어내지 않는다', () => {
    const view = render(<ParkingCard
      item={parking({ fee: { baseMinutes: 30, baseWon: 300, addMinutes: 10, addWon: null, dayMaxWon: 2400, freeMinutes: 0 }, estimatedWon: null })}
      actions={actions()} stayHours={2} />);

    expect(view.getByText('기본 30분 300원')).toBeTruthy();
    expect(view.getByText('추가요금은 현장에서 확인해 주세요')).toBeTruthy();
    expect(view.queryByText(/약 /)).toBeNull();
    expect(view.queryByText('무료')).toBeNull();
  });

  it('영업 상태 셋 — 닫았으면 오늘 시간, 오늘 쉬면 «오늘 쉬어요», 모르면 «운영시간 정보 없음»(닫았다고 하지 않는다)', () => {
    const closed = render(<ParkingCard item={parking({ openNow: false, closesAt: null })} actions={actions()} stayHours={2} />);
    expect(closed.getByText('지금은 닫았어요 · 오늘 08:00–20:00')).toBeTruthy();

    const off = render(<ParkingCard item={parking({ openNow: false, todayHours: null, closesAt: null })} actions={actions()} stayHours={2} />);
    expect(off.getByText('오늘 쉬어요')).toBeTruthy();

    const unknown = render(<ParkingCard item={parking({ openNow: null, todayHours: null, closesAt: null })} actions={actions()} stayHours={2} />);
    expect(unknown.getByText('운영시간 정보 없음')).toBeTruthy();
    expect(unknown.queryByText(/닫았어요/)).toBeNull();
  });

  it('하루 종일 여는 곳은 «24시간 영업»', () => {
    const view = render(<ParkingCard item={parking({ allDay: true, closesAt: '24:00' })} actions={actions()} stayHours={2} />);

    expect(view.getByText('24시간 영업')).toBeTruthy();
  });

  it('지하철 환승주차장 — 주소 대신 메모(위치·관리기관·전화)가 한 줄, 요금은 모름', () => {
    const view = render(<ParkingCard
      item={{ ...parking({ fee: null, estimatedWon: null, openNow: null, todayHours: null, closesAt: null, note: '2번 출구 · 부산시설공단 · T.860-7713' }, '하단역 환승주차장'), address: null }}
      actions={actions()} stayHours={2} />);

    expect(view.getByText('2번 출구 · 부산시설공단 · T.860-7713')).toBeTruthy();
    expect(view.getByText('요금 정보 없음')).toBeTruthy();
  });

  it('민영 주차장 표시', () => {
    const view = render(<ParkingCard item={parking({ operator: 'PRIVATE' })} actions={actions()} stayHours={1} />);

    expect(view.getByText('민영')).toBeTruthy();
    expect(view.queryByText('공영')).toBeNull();
  });

  it('영어 화면 — 글은 영어로, 이름은 한국어 간판 + 읽는 법, 주소는 로마자', () => {
    mockLang = 'en';
    const view = render(<ParkingCard item={parking()} actions={actions()} stayHours={2} />);

    expect(view.getByText('About ₩1,200')).toBeTruthy();
    expect(view.getByText('for 2 hours')).toBeTruthy();
    expect(view.getByText('Open now · until 20:00')).toBeTruthy();
    expect(view.getByText('3 min walk')).toBeTruthy();
    expect(view.getByText('Public')).toBeTruthy();
    expect(view.getByText('94 spaces')).toBeTruthy();
    expect(view.getByText('시험주차장')).toBeTruthy();
    // 한글이 하나도 없는 줄이 아래에 — 읽는 법
    expect(view.getAllByText(/^[\x20-\x7E]+$/).length).toBeGreaterThan(0);
    expect(view.queryByText('부산광역시 중구 중앙동 1')).toBeNull();
  });

  it('한 시간 체류는 영어로 «for 1 hour» — 복수형을 맞춘다', () => {
    mockLang = 'en';
    const view = render(<ParkingCard item={parking()} actions={actions()} stayHours={1} />);

    expect(view.getByText('for 1 hour')).toBeTruthy();
  });

  it('카드를 누르면 길 안내(있을 때), 없으면 지도 앱을 연다. 버튼도 따로 있다', () => {
    const withRoute = actions();
    const view = render(<ParkingCard item={parking()} actions={withRoute} stayHours={2} />);
    fireEvent.press(view.getByTestId('facility-open-f1'));
    expect(withRoute.onRoute).toHaveBeenCalledTimes(1);
    expect(withRoute.onMap).not.toHaveBeenCalled();
    fireEvent.press(view.getByTestId('facility-map-f1'));
    expect(withRoute.onMap).toHaveBeenCalledTimes(1);

    // 내 위치를 모르면 길 안내 버튼이 없고, 카드를 눌러도 지도 앱이다
    const noRoute = actions({ onRoute: null });
    const fallback = render(<ParkingCard item={parking()} actions={noRoute} stayHours={2} />);
    expect(fallback.queryByTestId('facility-route-f1')).toBeNull();
    fireEvent.press(fallback.getByTestId('facility-open-f1'));
    expect(noRoute.onMap).toHaveBeenCalledTimes(1);
  });
});

describe('약국 카드', () => {
  it('지금 여는 약국 — 닫는 시각, 일요일·공휴일·심야 표시, 찾아가는 길, 전화', () => {
    const onCall = jest.fn();
    const view = render(<PharmacyCard
      item={pharmacy({ opensSunday: true, opensHoliday: true, lateNight: true, note: '점심시간 13:00-13:30' })}
      actions={actions()} onCall={onCall} />);

    expect(view.getByText('지금 영업 중 · 18:00까지')).toBeTruthy();
    expect(view.getByTestId('facility-sunday-f1')).toBeTruthy();
    expect(view.getByTestId('facility-holiday-f1')).toBeTruthy();
    expect(view.getByTestId('facility-late-f1')).toBeTruthy();
    expect(view.getByText('일요일 영업')).toBeTruthy();
    expect(view.getByText('찾아가는 길 · 1호선 토성역 8번출구')).toBeTruthy();
    expect(view.getByText('점심시간 13:00-13:30')).toBeTruthy();
    fireEvent.press(view.getByTestId('facility-call-f1'));
    expect(onCall).toHaveBeenCalledWith('051-241-0123');
  });

  it('평일만 여는 약국은 일요일·공휴일·심야 표시가 없다', () => {
    const view = render(<PharmacyCard item={pharmacy()} actions={actions()} onCall={jest.fn()} />);

    expect(view.queryByTestId('facility-sunday-f1')).toBeNull();
    expect(view.queryByTestId('facility-holiday-f1')).toBeNull();
    expect(view.queryByTestId('facility-late-f1')).toBeNull();
  });

  it('🔴 시간을 모르는 약국은 «운영시간 정보 없음» — 닫았다고 하지 않는다', () => {
    const view = render(<PharmacyCard
      item={pharmacy({ openNow: null, todayHours: null, closesAt: null })} actions={actions()} onCall={jest.fn()} />);

    expect(view.getByText('운영시간 정보 없음')).toBeTruthy();
    expect(view.queryByText(/닫았어요/)).toBeNull();
    expect(view.queryByText('지금 영업 중')).toBeNull();
  });

  it('전화번호가 없으면 전화 버튼이 없다. 찾아가는 길이 없으면 그 줄도 없다', () => {
    const view = render(<PharmacyCard item={pharmacy({ tel: null, directions: null })} actions={actions()} onCall={jest.fn()} />);

    expect(view.queryByTestId('facility-call-f1')).toBeNull();
    expect(view.queryByText(/찾아가는 길/)).toBeNull();
  });

  it('닫은 약국은 오늘 시간을 같이 보여 준다', () => {
    const view = render(<PharmacyCard
      item={pharmacy({ openNow: false, closesAt: null, todayHours: { open: '12:00', close: '18:00' } })} actions={actions()} onCall={jest.fn()} />);

    expect(view.getByText('지금은 닫았어요 · 오늘 12:00–18:00')).toBeTruthy();
  });
});

describe('화장실 카드', () => {
  it('♿ 장애인용과 👶 기저귀 교환대(어느 칸인지) 표시, 종류, 개방시간 글', () => {
    const view = render(<ToiletCard
      item={toilet({ accessible: true, diaper: true, diaperPlace: 'WOMEN' })} actions={actions()} />);

    expect(view.getByText('♿ 장애인용')).toBeTruthy();
    expect(view.getByText('👶 기저귀 교환대 · 여자화장실')).toBeTruthy();
    expect(view.getByText('공중화장실')).toBeTruthy();
    expect(view.getByText('개방시간 09:00~18:00')).toBeTruthy();
  });

  it('교환대 위치를 모르면 «기저귀 교환대»만, 둘 다 없으면 두 표시가 없다', () => {
    const only = render(<ToiletCard item={toilet({ diaper: true, diaperPlace: null })} actions={actions()} />);
    expect(only.getByText('👶 기저귀 교환대')).toBeTruthy();
    expect(only.queryByTestId('facility-accessible-f1')).toBeNull();

    const none = render(<ToiletCard item={toilet()} actions={actions()} />);
    expect(none.queryByTestId('facility-accessible-f1')).toBeNull();
    expect(none.queryByTestId('facility-diaper-f1')).toBeNull();
  });

  it('늘 여는 곳은 «24시간 개방», 개방시간 글이 없으면 그 줄이 없다', () => {
    const always = render(<ToiletCard item={toilet({ alwaysOpen: true, openHours: '상시' })} actions={actions()} />);
    expect(always.getByText('24시간 개방')).toBeTruthy();
    expect(always.queryByText(/개방시간/)).toBeNull();

    const none = render(<ToiletCard item={toilet({ openHours: null })} actions={actions()} />);
    expect(none.queryByTestId('facility-hours-f1')).toBeNull();
  });

  it('🔴 시각 없이 «12시간»만 적힌 개방시간은 «하루 12시간 개방» — 영어 화면에 「Open 12시간」이 나오지 않는다(운영 자료에 있다)', () => {
    const ko = render(<ToiletCard item={toilet({ openHours: '12시간' })} actions={actions()} />);
    expect(ko.getByText('하루 12시간 개방')).toBeTruthy();
    expect(ko.queryByText(/개방시간/)).toBeNull();

    mockLang = 'en';
    const en = render(<ToiletCard item={toilet({ openHours: '6시간' })} actions={actions()} />);
    expect(en.getByText('Open 6 hours a day')).toBeTruthy();
    // 시각이 있는 글은 그대로 «개방시간 …»
    expect(render(<ToiletCard item={toilet({ openHours: '09:00~18:00' })} actions={actions()} />).getByText('Open 09:00~18:00')).toBeTruthy();
  });

  it('남자·남녀 모두 교환대 위치, 종류 넷', () => {
    const men = render(<ToiletCard item={toilet({ diaper: true, diaperPlace: 'MEN' })} actions={actions()} />);
    expect(men.getByText('👶 기저귀 교환대 · 남자화장실')).toBeTruthy();
    const both = render(<ToiletCard item={toilet({ diaper: true, diaperPlace: 'BOTH', type: 'OPEN' })} actions={actions()} />);
    expect(both.getByText('👶 기저귀 교환대 · 남녀 화장실 모두')).toBeTruthy();
    expect(both.getByText('개방화장실')).toBeTruthy();
    expect(render(<ToiletCard item={toilet({ type: 'SIMPLE' })} actions={actions()} />).getByText('간이화장실')).toBeTruthy();
    expect(render(<ToiletCard item={toilet({ type: 'MOBILE' })} actions={actions()} />).getByText('이동화장실')).toBeTruthy();
  });

  it('영어 화면', () => {
    mockLang = 'en';
    const view = render(<ToiletCard item={toilet({ accessible: true, diaper: true, diaperPlace: 'BOTH' })} actions={actions()} />);

    expect(view.getByText('♿ Accessible')).toBeTruthy();
    expect(view.getByText('👶 Diaper changing · Both restrooms')).toBeTruthy();
    expect(view.getByText('Public restroom')).toBeTruthy();
    expect(view.getByText('Open 09:00~18:00')).toBeTruthy();
  });
});

describe('물품보관함 카드', () => {
  it('한국어 — 역 이름, 호선, 위치 글, 크기별 칸 수와 요금, «설치된 칸 수»라는 말', () => {
    const view = render(<LockerCard item={locker()} actions={actions()} />);

    expect(view.getByText('하단역')).toBeTruthy();
    expect(view.getByText('1호선')).toBeTruthy();
    expect(view.getByText('(B1) 1번 엘리베이터 옆, 3번출입구 방향')).toBeTruthy();
    expect(view.getByTestId('facility-size-small-l1')).toBeTruthy();
    expect(view.getByText('소형')).toBeTruthy();
    expect(view.getByText('특대형')).toBeTruthy();
    expect(view.getByText('2,000원')).toBeTruthy();
    expect(view.getByText('6,000원')).toBeTruthy();
    expect(view.getByText('요금은 3시간 기준이에요')).toBeTruthy();
    expect(view.getByText('설치된 칸 수예요. 비어 있는지는 현장에서 확인해 주세요.')).toBeTruthy();
  });

  it('칸 수 숫자가 크기마다 맞게 들어간다 — 0 칸도 0 으로 보인다', () => {
    const view = render(<LockerCard item={locker()} actions={actions()} />);

    expect(within(view.getByTestId('facility-size-small-l1')).getByText('10')).toBeTruthy();
    expect(within(view.getByTestId('facility-size-large-l1')).getByText('0')).toBeTruthy();
    expect(within(view.getByTestId('facility-size-xlarge-l1')).getByText('7')).toBeTruthy();
  });

  it('영어 화면 — 영어 역명을 크게, 한국어 역명을 아래에, 크기·호선·요금 단위도 영어', () => {
    mockLang = 'en';
    const view = render(<LockerCard item={locker()} actions={actions()} />);

    expect(view.getByText('Hadan')).toBeTruthy();
    expect(view.getByText('하단역')).toBeTruthy();
    expect(view.getByText('Line 1')).toBeTruthy();
    expect(view.getByText('Small')).toBeTruthy();
    expect(view.getByText('X-Large')).toBeTruthy();
    expect(view.getByText('₩2,000')).toBeTruthy();
    expect(view.getByText('Prices are per 3 hours')).toBeTruthy();
  });

  it('영어 역명이 없으면 영어 화면에서도 한국어 이름이 제목이다', () => {
    mockLang = 'en';
    const view = render(<LockerCard item={locker({ stationEn: null })} actions={actions()} />);

    expect(view.getByText('하단역')).toBeTruthy();
    expect(view.queryByText('Hadan')).toBeNull();
  });

  it('🔴 요금 글을 읽지 못하면 원문을 그대로 보여 준다 — 없는 값을 지어내지 않는다', () => {
    const view = render(<LockerCard item={locker({ feeText: '현장 문의' })} actions={actions()} />);

    expect(view.getByText('요금 · 현장 문의')).toBeTruthy();
    expect(view.queryByText(/시간 기준이에요/)).toBeNull();
  });

  it('요금 글이 없으면 요금 줄이 없다', () => {
    const view = render(<LockerCard item={locker({ feeText: null })} actions={actions()} />);

    expect(view.queryByTestId('facility-fee-l1')).toBeNull();
  });

  it('주소 칸은 없다 — 역 안의 위치 글이 대신한다', () => {
    const view = render(<LockerCard item={locker()} actions={actions()} />);

    expect(view.queryByText(/부산광역시/)).toBeNull();
  });
});

describe('지도와 짝지은 카드(link) — S15P21E201-2052', () => {
  const link = (over: Partial<ListLink> = {}): ListLink => ({ number: 3, selected: false, onSelect: jest.fn(), ...over });

  it('🔴 네 카드 모두 지도의 번호 점과 같은 번호 핀이 있다 — 안 짝지으면(link 없음) 핀이 없다', () => {
    const cards = [
      <ParkingCard key="p" item={parking()} actions={actions()} link={link({ number: 1 })} stayHours={2} />,
      <PharmacyCard key="ph" item={pharmacy()} actions={actions()} link={link({ number: 2 })} onCall={null} />,
      <ToiletCard key="t" item={toilet()} actions={actions()} link={link({ number: 3 })} />,
      <LockerCard key="l" item={locker()} actions={actions()} link={link({ number: 4 })} />,
    ];
    cards.forEach((card, index) => {
      const view = render(card);
      expect(within(view.getByTestId(/^facility-pin-/)).getByText(String(index + 1))).toBeTruthy();
    });
    const alone = render(<ParkingCard item={parking()} actions={actions()} stayHours={2} />);
    expect(alone.queryByTestId('facility-pin-f1')).toBeNull();
  });

  it('🔴 카드를 누르면 «고르는» 함수만 불린다 — 길 안내·지도 앱은 아래 단추가 따로 한다', () => {
    const own = actions();
    const mine = link();
    const view = render(<ParkingCard item={parking()} actions={own} link={mine} stayHours={2} />);

    fireEvent.press(view.getByTestId('facility-open-f1'));
    expect(mine.onSelect).toHaveBeenCalledTimes(1);
    expect(own.onRoute).not.toHaveBeenCalled();
    expect(own.onMap).not.toHaveBeenCalled();

    fireEvent.press(view.getByTestId('facility-route-f1'));
    expect(own.onRoute).toHaveBeenCalledTimes(1);
    fireEvent.press(view.getByTestId('facility-map-f1'));
    expect(own.onMap).toHaveBeenCalledTimes(1);
    expect(mine.onSelect).toHaveBeenCalledTimes(1);
  });

  it('골라진 카드는 선택 상태로 읽히고, 아닌 카드는 아니다 — 안 짝지은 카드는 선택 상태를 말하지 않는다', () => {
    const on = render(<ToiletCard item={toilet()} actions={actions()} link={link({ selected: true })} />);
    expect(on.getByTestId('facility-open-f1').props.accessibilityState).toEqual({ selected: true });
    const off = render(<ToiletCard item={toilet()} actions={actions()} link={link({ selected: false })} />);
    expect(off.getByTestId('facility-open-f1').props.accessibilityState).toEqual({ selected: false });
    const alone = render(<ToiletCard item={toilet()} actions={actions()} />);
    expect(alone.getByTestId('facility-open-f1').props.accessibilityState?.selected).toBeUndefined();
  });

  it('읽어 주는 말은 「지도에서 보기」 — 이름·거리와 함께(가까운 도움의 줄과 같은 문구)', () => {
    const view = render(<ParkingCard item={parking()} actions={actions()} link={link()} stayHours={2} />);
    expect(view.getByTestId('facility-open-f1').props.accessibilityLabel).toBe('시험주차장, 230m 지도에서 보기');
    mockLang = 'en';
    const english = render(<ParkingCard item={parking()} actions={actions()} link={link()} stayHours={2} />);
    expect(english.getByTestId('facility-open-f1').props.accessibilityLabel).toBe('Show 시험주차장, 230m on the map');
  });
});
