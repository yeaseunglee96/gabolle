package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;
import com.gabolle.backend.place.domain.Place;

/**
 * 장소 상세. HTTP 는 한 번이고 안에서 도는 질의는 셋이다 — 장소 하나, 그 장소의 피처 전부,
 * {@code NOT_COLLECTED} 판정에 쓰는 대조표 전체({@code UserPlaceCodeMapRepository.findAll()}).
 * 세 번째는 요청마다 같은 결과를 주는 정적 기준 데이터라 캐시 없이 둔다({@code PlaceDetailService}).
 * {@link #openingHours()} · {@link #priceLevel()} 은 이미 읽은 {@link #features()} 에서 골라낸
 * 것이라 질의를 늘리지 않는다.
 *
 * <p>기존 열 개 필드(placeId ~ itineraryInclusion)의 이름·자리·타입은 그대로 둔다. 이미 배포된 앱이
 * 그 이름을 읽고 있다. 새 칸은 record 뒤쪽에 붙인다.
 *
 * @param provenance 이 장소 행 자체의 출처. 피처마다 붙는 출처와 다르다 — "이 피처는 어디서 왔나"
 *        와 "이 장소는 어느 수집분에서 왔나" 는 다른 질문이고 둘 다 답할 수 있어야 한다
 * @param itineraryInclusion 이 장소가 질의 파라미터 {@code itineraryId} 가 가리키는 일정에 들어
 *        있는가. 판정 기준은 그 일정의 최신 판이다. 파라미터가 없거나 요청자가 그 일정을 볼 수
 *        없으면 {@code UNAVAILABLE} 이고 이유는 {@code reason} 에 담긴다. 없는 일정과 남의 일정이
 *        같은 응답을 받는다 — 둘을 나누면 이 칸으로 남의 일정 내용을 알아낼 수 있다
 *        ({@code ItineraryMembershipPort})
 * @param addressEn 영문 주소. 없으면 키 자체가 빠진다
 * @param photoUrl 대표 사진 주소. 없으면 키 자체가 빠진다
 * @param photoSource 사진 출처 표기 문구. {@code photoUrl} 과 짝이다. 없으면 키 자체가 빠진다
 * @param photoSubject 그 사진이 무엇을 찍은 것인가 — {@code SELF}=이 장소를 찍은 사진,
 *        {@code VENUE}=이 장소가 들어 있는 곳을 찍은 사진. 이 칸이 없으면 화면은 둘을 구분할 방법이
 *        없어 주변 시설 사진을 이 장소 사진처럼 그린다. 출처({@code photoSource})와는 다른 질문이라
 *        칸을 갈랐다. 값이 없으면 키가 빠진다 — 모르는 것을 아는 척하지 않는다
 * @param openingHours {@code place_feature} 의 {@code OPENING_HOURS} 표식. 그 표식에 행이 있으면
 *        (VERIFIED·ESTIMATED·UNKNOWN 무엇이든) 실리고, 행이 아예 없으면({@code NOT_COLLECTED})
 *        키 자체가 빠진다. 이 값은 {@link #features()} 에도 그대로 들어 있다 — {@code features} 는
 *        "이 장소에 붙은 표식 전부" 라는 뜻이라 거기서 빼면 계약이 깨지므로 중복을 그대로 둔다
 * @param priceLevel {@code place_feature} 의 {@code PRICE_LEVEL} 표식. {@code openingHours} 와
 *        같은 규칙이다
 * @param resolvedLanguage 실제로 어느 언어로 답했는가 — {@code "ko"} 또는 {@code "en"}. 영어를
 *        요청했는데 영문 이름이 없어 한국어로 되돌린 경우를 화면이 구분할 수 있어야 "번역이
 *        없습니다" 안내를 할 수 있다. {@code nameKo}·{@code nameEn} 은 언어와 무관하게 둘 다
 *        나간다 — 언어 선택은 칸을 더하는 것이지 기존 칸을 바꾸는 것이 아니다
 * @param photoLicense 사진의 라이선스 — 이름·주소·원본 파일 페이지(S15P21E201-1606). 위키미디어
 *        사진(CC BY 등)은 출처 문구와 함께 이것을 보여야 쓸 수 있다. 없으면 키 자체가 빠진다
 * @param photos 사진 여러 장(S15P21E201-1840). 첫 장은 대표 사진({@code photoUrl})과 같고, 그 뒤로
 *        {@code place_photo} 의 사진이 순서대로 붙는다. 사진이 한 장도 없으면 키 자체가 빠진다. 화면은 이
 *        칸이 없으면 지금처럼 {@code photoUrl} 한 장을 그린다 — 이미 배포된 앱은 이 칸을 모른다
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
		@JsonInclude(JsonInclude.Include.NON_NULL) Place.PhotoSubject photoSubject,
		@JsonInclude(JsonInclude.Include.NON_NULL) FeatureSlot openingHours,
		@JsonInclude(JsonInclude.Include.NON_NULL) FeatureSlot priceLevel,
		String resolvedLanguage,
		@JsonInclude(JsonInclude.Include.NON_NULL) Place.PhotoLicense photoLicense,
		@JsonInclude(JsonInclude.Include.NON_EMPTY) List<Photo> photos,
		/**
		 * 장소 이름의 일본어·중국어(간체·번체) — 관광공사가 번역해 둔 곳만 있다(V20260930130000, S15P21E201-1859).
		 * 키는 앱의 언어 코드({@code ja} · {@code zh-Hans} · {@code zh-Hant}), 없는 언어는 빠지고 다 없으면 칸째 빠진다.
		 */
		@JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> localNames) {

	/**
	 * 사진 한 장. {@code source} 는 사진 옆에 그대로 보여 줄 출처 문구다(예: {@code 출처 : 부산관광아카이브}).
	 *
	 * @param license 라이선스가 따로 있으면 이름·주소·원본 페이지. 없으면 키가 빠진다
	 */
	public record Photo(String url, String source,
			@JsonInclude(JsonInclude.Include.NON_NULL) Place.PhotoLicense license) {
	}

	/**
	 * @param observedAt 원천에서 관측된 시각. 어제 수집한 지난달 정보가 있을 수 있어
	 *        {@code collectedAt} 과 나눈다
	 * @param datasetVersion 추천 결과의 datasetVersion 과 대조해야 "그때 어느 데이터로 계산했나" 를
	 *        되짚을 수 있다
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
	 * @param reason {@code UNAVAILABLE} 일 때만 채워진다. 값은 셋이다 —
	 *        {@code ITINERARY_NOT_SPECIFIED}(요청이 일정을 지정하지 않았다),
	 *        {@code ITINERARY_NOT_VISIBLE}(없는 일정과 남의 일정이 함께 여기 온다),
	 *        {@code ITINERARY_LOOKUP_UNAVAILABLE}(서버가 지금 판정할 수 없다).
	 *        정본은 {@code ItineraryMembershipPort} 의 상수다
	 */
	public record ItineraryInclusion(String state, String reason) {
	}

	/**
	 * 영업시간·예상비용처럼 {@code place_feature} 표식 하나를 상세 응답의 전용 칸으로 옮겨 실을 때
	 * 쓰는 그릇.
	 *
	 * @param value 원본 JSONB 값 그대로. 값 구조는 아직 확정되지 않았다
	 * @param evidenceStatus {@code VERIFIED}·{@code ESTIMATED}·{@code UNKNOWN} 중 하나.
	 *        {@code NOT_COLLECTED}(행이 아예 없음)는 이 칸 자체가 응답에서 빠지므로 여기 오지 않는다
	 */
	public record FeatureSlot(JsonNode value, String evidenceStatus) {
	}
}
