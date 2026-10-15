package com.gabolle.backend.place.loader;

import java.util.List;
import java.util.UUID;

import com.gabolle.backend.place.domain.CertifiedRestaurant;

/**
 * 로컬 탐색의 구 → 동 갈래(맛집·카페)에 올릴 한 곳 — 수집 파일의 한 줄을 적재 엔진({@link LocalPickLoader})이 읽는 모양
 * (S15P21E201-2059).
 *
 * <p>맛집 파일({@link CertifiedRestaurantRow})과 카페 파일({@link LocalCafeRow})은 칸이 다르다. 엔진이 알아야 하는 것은
 * 아래 셋뿐이라 여기로 모았다 — <b>무엇을 달까</b>({@link #certMarks}), <b>없으면 어떤 장소로 만들까</b>({@link #newPlace}),
 * <b>이미 있는 장소가 어디인가</b>({@link #existingPlaceId}).
 */
public interface LocalPickRow {

	/** 파일이 매긴 번호 — 표식의 {@code source_id} 에 간다. 새 장소의 번호가 아니다({@link NewPlace#sourceId}). */
	String sourceId();

	String name();

	double lat();

	double lng();

	String address();

	/** 표식의 구·동 — 좌표를 역지오코딩해 얻은 법정동이다. */
	CertifiedRestaurant.Area area();

	/** 우리 DB 에 이미 있는 장소의 번호. 있으면 장소는 새로 만들지 않고 표식만 단다. 없으면 {@code null}. */
	UUID existingPlaceId();

	/** 새 장소의 대표 사진. 출처({@link #photoSource})와 반드시 짝이다. 없으면 {@code null}. */
	String photoUrl();

	String photoSource();

	/** 이 줄이 말하는 인증 표식들 — 같은 {@code source} 가 두 번 오면 엔진이 합친다. */
	List<CertMark> certMarks();

	/** 이 줄로 장소를 새로 만든다면 어떤 장소인가 — 같은 줄에서는 언제나 같은 번호가 나와야 두 번 돌려도 행이 안 는다. */
	NewPlace newPlace();

	/**
	 * 인증 표식 하나.
	 *
	 * @param broadcastOrAward 레드테이블이 방송·수상 이력도 적어 둔 곳인가 — 표식 값에 {@code "broadcastOrAward":true} 로 간다.
	 *     그 밖의 목록에서는 늘 거짓이다
	 */
	record CertMark(CertifiedRestaurant.Source source, boolean broadcastOrAward) {
	}

	/**
	 * 새로 만들 장소의 신원.
	 *
	 * @param placeId 장소 번호 — 원천 번호에서 계산한다({@code UUID.nameUUIDFromBytes})
	 * @param sourceType {@code place.source_type}
	 * @param sourceId {@code place.source_id} — {@code (sourceType, sourceId)} 는 유일하다({@code uq_place_source})
	 * @param category {@code place.category} — 앱의 여섯 갈래 낱말
	 */
	record NewPlace(UUID placeId, String sourceType, String sourceId, String category) {
	}
}
