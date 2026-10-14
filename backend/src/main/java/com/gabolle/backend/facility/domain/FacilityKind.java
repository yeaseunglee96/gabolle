package com.gabolle.backend.facility.domain;

import java.util.Optional;

/**
 * 근처 편의시설의 종류 넷 (S15P21E201-2052). DB 의 {@code ck_facility_kind}(종류 칸의 허용 목록)와 같은 낱말이다 —
 * 새 종류를 더하려면 이 목록과 마이그레이션의 CHECK 를 함께 고친다.
 */
public enum FacilityKind {

	/** 주차장 — 공영·민영. 저렴한 순으로 보여 준다. */
	PARKING,

	/** 약국 — 요일별 운영시간. 지금 연 곳이 먼저. */
	PHARMACY,

	/** 공중화장실 — 장애인용·기저귀 교환대 여부. */
	TOILET,

	/** 지하철 물품보관함 — 역 단위, 크기별 칸 수. */
	LOCKER;

	/** 이름으로 찾는다 — 대소문자를 가리지 않는다. 모르는 이름이면 비어 있다. */
	public static Optional<FacilityKind> fromName(String name) {
		if (name == null) {
			return Optional.empty();
		}
		for (FacilityKind kind : values()) {
			if (kind.name().equalsIgnoreCase(name.trim())) {
				return Optional.of(kind);
			}
		}
		return Optional.empty();
	}
}
