package com.gabolle.backend.place.loader;

/**
 * 부산 레드테이블(부산광역시가 공공데이터포털에 연 식당 목록 API) 식당 한 곳 — {@link RedtablePlaceReader} 가 읽은 한 줄이다.
 *
 * <p>{@code rstrId} 는 레드테이블이 매긴 식당 번호({@code RSTR_ID})이고 장소 id 를 이 값에서 계산한다.
 * {@code businessType} 은 영업신고 업태({@code BSNS_STATM_BZCND_NM} — 한식·커피숍·호프/통닭 …, 비어 오기도 한다),
 * {@code licenseType} 은 영업 허가 종류({@code BSNS_LCNC_NM} — 일반음식점·휴게음식점·제과점영업)다.
 *
 * <p>{@code tel}·{@code intro}·{@code imageUrl}·{@code surveyRecommenders} 는 읽기만 하고 아직 DB 에 넣지 않는다 —
 * 사진은 이용 조건이 확인되지 않았고({@link RedtablePlaceLoader}), 소개글은 「○○구 맛집 "△△"을 추천합니다!」 같은
 * 틀 문장이라 보여 줄 값이 없다. 파일에 남겨 두는 것은 나중 결정이 원문을 다시 받지 않아도 되게 하려는 것이다.
 */
public record RedtablePlaceRow(
		String rstrId,
		String name,
		double lat,
		double lng,
		String roadAddress,
		String jibunAddress,
		String businessType,
		String licenseType,
		String tel,
		String intro,
		String imageUrl,
		Integer surveyRecommenders) {

	/** 도로명 주소가 있으면 그것, 없으면 지번 주소. */
	public String address() {
		return (this.roadAddress != null) ? this.roadAddress : this.jibunAddress;
	}
}
