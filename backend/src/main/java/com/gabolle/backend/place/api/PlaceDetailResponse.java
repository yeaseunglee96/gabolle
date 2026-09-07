package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;

/**
 * 장소 상세 (S15P21E201-476 · -217 · -430 부분).
 *
 * <p>완료 기준 "한 번의 조회로 위 항목이 모두 온다" 를 위해 HTTP 는 한 번이다. 🔴 안에서 도는
 * 질의는 <b>셋</b>이다 — 장소 하나, 그 장소의 피처 전부, 그리고 {@code NOT_COLLECTED} 판정에 쓰는
 * 대조표 전체({@code UserPlaceCodeMapRepository.findAll()}). 세 번째는 요청마다 같은 결과를 주는
 * 정적 기준 데이터 조회라 지금은 캐시 없이 둔다 — 자세한 근거는 {@code PlaceDetailService} 참고.
 * 피처는 여전히 종류마다 따로 읽지 않는다. {@link #openingHours()} · {@link #priceLevel()} 도
 * 이미 읽은 {@link #features()} 목록에서 골라낸 것이라 질의를 늘리지 않는다.
 *
 * <p>🔴 <b>기존 열 개 필드(placeId ~ itineraryInclusion)의 이름·자리·타입은 그대로다.</b> 이미
 * 배포된 앱이 그 이름을 읽고 있다. 새로 더한 다섯 칸({@code addressEn} ~ {@code resolvedLanguage})은
 * record 뒤쪽에 붙였다.
 *
 * @param provenance 이 장소 행 자체의 출처. 피처마다 붙는 출처와 다르다 — "이 피처는 어디서
 *        왔나" 와 "이 장소는 어느 수집분에서 왔나" 는 다른 질문이고 둘 다 답할 수 있어야 한다
 * @param itineraryInclusion 🔴 지금은 항상 {@code UNAVAILABLE} 이다. 일정에 장소를 담는 표가
 *        아직 없어서 계산할 수 없다. 자세한 것은 {@code ItineraryMembershipPort}
 * @param addressEn 영문 주소({@code Place.getAddressEn()}). 없으면 키 자체가 빠진다
 * @param photoUrl 대표 사진 주소. 🔴 지금은 채우는 경로가 없어 항상 없다({@code null}) — 외부 사진
 *        검색(-146 · -480)이 붙어야 값이 생긴다. 값이 없을 때도 칸을 미리 만들어 둔 이유는 화면이
 *        사진 자리를 비워 두는 형태로 먼저 만들어질 수 있게 하기 위해서다. 없으면 키 자체가 빠진다
 * @param photoSource 사진 출처 표기 문구. {@code photoUrl} 과 짝이다. 없으면 키 자체가 빠진다
 * @param openingHours {@code place_feature} 의 {@code OPENING_HOURS} 표식. 그 표식에 행이 있으면
 *        (VERIFIED·ESTIMATED·UNKNOWN 무엇이든) 실리고, 행이 아예 없으면({@code NOT_COLLECTED})
 *        키 자체가 빠진다. 🔴 이 값은 {@link #features()} 에도 그대로 들어 있다 — 같은 사실이 두
 *        번 나가는 중복이다. {@code features} 는 "이 장소에 붙은 표식 전부" 라는 뜻이라 거기서
 *        이 둘을 빼면 그 계약이 깨진다고 판단해 편의 필드로 <b>더했다</b>(중복 유지, 삭제 아님)
 * @param priceLevel {@code place_feature} 의 {@code PRICE_LEVEL} 표식. {@code openingHours} 와
 *        같은 규칙이다
 * @param resolvedLanguage 요청 언어(Accept-Language, S15P21E201-430 부분)를 실제로 어떻게
 *        반영했는가 — {@code "ko"} 또는 {@code "en"}. 영어를 요청했는데 영문 이름이 없어 한국어로
 *        되돌린(fallback) 경우를 화면이 구분할 수 있어야 "번역이 없습니다" 안내를 할 수 있다.
 *        🔴 {@code nameKo}·{@code nameEn} 은 언어와 무관하게 그대로 둘 다 나간다 — 언어 선택은
 *        칸을 더하는 것이지 기존 칸을 바꾸는 것이 아니다
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
		ItineraryInclusion itineraryInclusion,
		@JsonInclude(JsonInclude.Include.NON_NULL) String addressEn,
		@JsonInclude(JsonInclude.Include.NON_NULL) String photoUrl,
		@JsonInclude(JsonInclude.Include.NON_NULL) String photoSource,
		@JsonInclude(JsonInclude.Include.NON_NULL) FeatureSlot openingHours,
		@JsonInclude(JsonInclude.Include.NON_NULL) FeatureSlot priceLevel,
		String resolvedLanguage) {

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

	/**
	 * 영업시간·예상비용처럼 {@code place_feature} 표식 하나를 상세 응답의 전용 칸으로 옮겨 실을 때
	 * 쓰는 그릇 (S15P21E201-476).
	 *
	 * @param value 원본 JSONB 값 그대로. 값 구조는 아직 데이터 담당이 확정하지 않았다
	 * @param evidenceStatus {@code VERIFIED}·{@code ESTIMATED}·{@code UNKNOWN} 중 하나. 화면이
	 *        "확인된 값" 과 "추정값" 을 다르게 보여줄 수 있어야 이 칸을 둔 의미가 있다.
	 *        {@code NOT_COLLECTED}(행이 아예 없음)는 이 칸 자체가 응답에서 빠지므로 여기 오지 않는다
	 */
	public record FeatureSlot(JsonNode value, String evidenceStatus) {
	}
}
