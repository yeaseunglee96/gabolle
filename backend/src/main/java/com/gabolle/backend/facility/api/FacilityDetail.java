package com.gabolle.backend.facility.api;

import com.gabolle.backend.facility.domain.ParkingFee;

/**
 * 종류별 응답 칸 (S15P21E201-2052) — {@link FacilityNearbyResponse.Item#detail()} 에 들어간다. 종류마다 모양이 다르다.
 *
 * <p>🔴 <b>{@code null} 은 «모른다»이다.</b> 칸째 빼지 않고 {@code null} 로 내보낸다 — 화면이 「없다」와 「모른다」를 구분해야 한다.
 * 예: {@code estimatedWon: 0} 은 무료이고 {@code estimatedWon: null} 은 요금 정보 없음이다. 절대 서로 바꿔 읽으면 안 된다.
 */
public sealed interface FacilityDetail {

	/** 오늘의 시간대. {@code close} 는 24:00 이상이면 다음 날의 시각으로 접은 글이다(25:00 → 01:00). */
	record Hours(String open, String close) {
	}

	/**
	 * 주차장.
	 *
	 * @param operator {@code PUBLIC}(공영) · {@code PRIVATE}(민영)
	 * @param free 무료 주차장인가
	 * @param spaces 주차 구획 수. 모르면 null
	 * @param fee 요금표. <b>null 이면 요금을 모른다</b>. 칸 하나가 null 이면 그 칸만 모른다(예: {@code addWon} 을 모르면 기본 시간을
	 *     넘는 요금은 모른다)
	 * @param estimatedWon 요청한 체류 시간의 예상요금(원). 무료면 0, <b>모르면 null</b>
	 * @param note 메모(지하철 환승주차장의 위치·관리기관·전화). 없으면 null
	 * @param openNow 지금 여는가. 시간을 모르면 null
	 * @param todayHours 오늘의 시간대. 오늘 쉬거나 모르면 null
	 * @param closesAt 열려 있을 때 닫는 시각 「20:00」. 열려 있지 않으면 null
	 * @param allDay 오늘 하루 종일 여는가
	 */
	record Parking(String operator, boolean free, Integer spaces, ParkingFee fee, Integer estimatedWon, String note,
			Boolean openNow, Hours todayHours, String closesAt, boolean allDay) implements FacilityDetail {
	}

	/**
	 * 약국.
	 *
	 * @param tel 전화번호. 없으면 null
	 * @param directions 찾아가는 길 글(「1호선 토성역 8번출구」). 없으면 null
	 * @param note 약국이 적은 메모(「점심시간 13:00-13:30」). 없으면 null
	 * @param hoursAreReported <b>늘 true</b> — 운영시간은 약국이 신고한 정보라 가기 전에 전화로 확인해야 한다. 화면이 안내문을 띄우는 기준
	 * @param openNow 지금 여는가. 시간을 하나도 모르면 null
	 * @param todayHours 오늘의 시간대. 오늘 쉬거나 모르면 null
	 * @param closesAt 열려 있을 때 닫는 시각. 열려 있지 않으면 null
	 * @param allDay 오늘 하루 종일 여는가
	 * @param opensSunday 일요일에 연다고 신고했는가
	 * @param opensHoliday 공휴일에 연다고 신고했는가
	 * @param lateNight 어느 날이든 밤 11시 이후까지 여는가(심야)
	 */
	record Pharmacy(String tel, String directions, String note, boolean hoursAreReported, Boolean openNow,
			Hours todayHours, String closesAt, boolean allDay, boolean opensSunday, boolean opensHoliday,
			boolean lateNight) implements FacilityDetail {
	}

	/**
	 * 공중화장실.
	 *
	 * @param type {@code OPEN}(개방화장실) · {@code PUBLIC}(공중화장실) · {@code SIMPLE}(간이) · {@code MOBILE}(이동)
	 * @param openHours 개방시간 글(한국어 그대로). 늘 열면 null 일 수도 있다 — {@code alwaysOpen} 을 같이 본다
	 * @param alwaysOpen 늘(24시간) 여는가
	 * @param diaperPlace 기저귀 교환대가 있는 칸 — {@code WOMEN} · {@code MEN} · {@code BOTH}. 없거나 모르면 null
	 */
	record Toilet(String type, String openHours, boolean alwaysOpen, boolean accessible, boolean diaper,
			String diaperPlace, boolean emergencyBell, boolean cctv, String tel) implements FacilityDetail {
	}

	/**
	 * 지하철 물품보관함 — 역 하나의 한 곳.
	 *
	 * @param station 역 이름(역 글자 없이 「하단」)
	 * @param stationEn 영어 역 이름. 모르면 null
	 * @param line 호선 「1」~「4」
	 * @param location 역 안의 위치 글(「(B1) 1번 엘리베이터 옆」). 모르면 null
	 * @param small 크기별 칸 수
	 * @param feeText 요금 글(한국어 그대로). 모르면 null
	 */
	record Locker(String station, String stationEn, String line, String location, int small, int medium, int large,
			int xlarge, String feeText, String operator) implements FacilityDetail {
	}

	/**
	 * 환전소 (S15P21E201-2066) — 카카오 로컬이 준 값 그대로. 영업시간·환율은 카카오가 주지 않아 없다 — 화면이 「가기 전에 확인」을 적는다.
	 *
	 * @param tel 전화. 모르면 null
	 * @param placeUrl 카카오맵 장소 주소. 모르면 null
	 */
	record Exchange(String tel, String placeUrl) implements FacilityDetail {
	}
}
