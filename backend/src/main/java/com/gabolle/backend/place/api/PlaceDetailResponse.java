package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 장소 상세 (S15P21E201-476).
 *
 * <p>완료 기준 "한 번의 조회로 위 항목이 모두 온다" 를 위해 HTTP 는 한 번이고, 안에서 도는 질의도
 * 둘이다 — 장소 하나와 그 장소의 피처 전부. 피처를 종류마다 따로 읽지 않는다.
 *
 * @param provenance 이 장소 행 자체의 출처. 피처마다 붙는 출처와 다르다 — "이 피처는 어디서
 *        왔나" 와 "이 장소는 어느 수집분에서 왔나" 는 다른 질문이고 둘 다 답할 수 있어야 한다
 * @param itineraryInclusion 🔴 지금은 항상 {@code UNAVAILABLE} 이다. 일정에 장소를 담는 표가
 *        아직 없어서 계산할 수 없다. 자세한 것은 {@code ItineraryMembershipPort}
 */
public record PlaceDetailResponse(
		UUID placeId,
		String nameKo,
		String nameEn,
		String category,
		String address,
		Double lat,
		Double lng,
		Provenance provenance,
		List<PlaceFeatureView> features,
		ItineraryInclusion itineraryInclusion) {

	/**
	 * @param collectedAt 우리가 가져온 시각
	 * @param observedAt 원천에서 관측된 시각. 어제 수집한 지난달 정보가 있을 수 있어 둘을 나눈다
	 * @param datasetVersion 추천 결과의 datasetVersion 과 대조해야 "그때 어느 데이터로 계산했나" 를
	 *        되짚을 수 있다 (FR-REC-12)
	 */
	public record Provenance(
			String sourceType,
			String sourceId,
			OffsetDateTime collectedAt,
			OffsetDateTime observedAt,
			String datasetVersion) {
	}

	/**
	 * @param state {@code INCLUDED} · {@code NOT_INCLUDED} · {@code UNAVAILABLE}
	 * @param reason {@code UNAVAILABLE} 일 때만 채워진다
	 */
	public record ItineraryInclusion(String state, String reason) {
	}
}
