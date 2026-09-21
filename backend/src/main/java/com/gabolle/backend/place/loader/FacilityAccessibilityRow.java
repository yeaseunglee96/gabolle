package com.gabolle.backend.place.loader;

import java.util.List;
import java.util.UUID;

/**
 * 실태조사에서 뽑아 온 시설 한 곳 — S15P21E201-1365.
 *
 * <p>장소를 가리키는 열쇠가 <b>출처마다 다르다.</b> 관광공사분은 {@code contentid} 로,
 * 상가분은 상가업소번호로 장소 id 를 만든다. 그래서 {@code sourceType} 없이 {@code sourceId}
 * 만 보면 엉뚱한 장소를 만든다.
 *
 * @param sourceType 장소를 어디서 만들었나. {@value #TOURAPI} 또는 {@value #SBIZ}
 * @param sourceId   그 출처의 장소 열쇠. 관광공사는 {@code contentid}, 상가는 상가업소번호
 * @param facilityId 실태조사 시설 고유번호({@code wfcltId}). <b>이 값을 저장한다</b> —
 *                   나중에 "이 표시가 어디서 왔나" 를 되짚는 유일한 열쇠다
 * @param evalRaw    실태조사가 준 항목 이름 목록 원문. 규칙을 바꿀 때 다시 안 부르고 다시 판정한다
 * @param codes      붙일 접근성 코드. {@link FacilityAccessibility} 가 정한다
 */
public record FacilityAccessibilityRow(String sourceType, String sourceId, String facilityId,
		String evalRaw, List<String> codes) {

	/** 관광공사 자료에서 만든 장소. */
	public static final String TOURAPI = "TOURAPI";

	/** 소상공인 상가 자료에서 만든 장소. */
	public static final String SBIZ = "SBIZ";

	/**
	 * 이 줄이 가리키는 장소 id.
	 *
	 * <p>계산식은 장소를 만든 적재기의 것을 그대로 쓴다 — 다른 식을 쓰면 DB 에 없는 id 가
	 * 나오고, 적재기는 실패하지 않고 <b>"붙일 장소 없음" 만 찍는다.</b>
	 */
	public UUID placeId() {
		return switch (this.sourceType) {
			case TOURAPI -> TourApiPlaceLoader.placeIdOf(this.sourceId);
			case SBIZ -> SbizPlaceLoader.placeIdOf(this.sourceId);
			default -> throw new IllegalStateException("모르는 출처다: " + this.sourceType);
		};
	}

	/**
	 * 이 표식의 id.
	 *
	 * <p>🔴 장소를 만든 적재기의 계산식을 그대로 쓰는 것이 중요하다. 그래야 <b>관광공사
	 * 무장애 자료로 이미 붙어 있는 표식과 같은 id</b> 가 나오고, 같은 곳에 두 번 붙는 대신
	 * "이미 있어 넘김" 으로 세어진다.
	 */
	public UUID featureIdOf(String featureType, String featureKey) {
		return switch (this.sourceType) {
			case TOURAPI -> TourApiPlaceLoader.featureIdOf(this.sourceId, featureType, featureKey);
			case SBIZ -> SbizPlaceLoader.featureIdOf(this.sourceId, featureType, featureKey);
			default -> throw new IllegalStateException("모르는 출처다: " + this.sourceType);
		};
	}

	/** 출처 이름이 우리가 아는 둘 중 하나인가. */
	public static boolean knownSource(String sourceType) {
		return TOURAPI.equals(sourceType) || SBIZ.equals(sourceType);
	}

	/**
	 * 데이터 파트가 계산해 보낸 장소 id 와 우리 계산이 맞는가.
	 *
	 * <p>양쪽이 서로 다른 언어로 같은 식(MD5 기반 이름 UUID)을 구현했다. 어긋나면 그 줄을
	 * 버리고 센다 — 한쪽만 맞는 채로 적재되면 어느 쪽이 틀렸는지 나중에 못 가린다.
	 */
	public boolean agreesWith(String claimedPlaceId) {
		if (claimedPlaceId == null || claimedPlaceId.isBlank()) {
			return true;
		}
		return placeId().equals(UUID.fromString(claimedPlaceId));
	}
}
