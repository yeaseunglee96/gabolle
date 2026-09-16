package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;
import com.gabolle.backend.place.domain.Place;

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
 * @param itineraryInclusion 이 장소가 <b>요청이 지정한 일정</b>에 들어 있는가. 어느 일정인지는
 *        질의 파라미터 {@code itineraryId} 로 받고, 판정 기준은 그 일정의 최신 판이다. 파라미터가
 *        없거나 요청자가 그 일정을 볼 수 없으면 {@code UNAVAILABLE} 이고, 왜 모르는지는
 *        {@code reason} 에 담긴다. 🔴 <b>없는 일정과 남의 일정이 같은 응답을 받는다</b> — 둘을
 *        나누면 이 칸으로 남의 일정 내용을 알아낼 수 있다. 자세한 것은
 *        {@code ItineraryMembershipPort}
 * @param addressEn 영문 주소({@code Place.getAddressEn()}). 없으면 키 자체가 빠진다
 * @param photoUrl 대표 사진 주소. 🔴 지금은 채우는 경로가 없어 항상 없다({@code null}) — 외부 사진
 *        검색(-146 · -480)이 붙어야 값이 생긴다. 값이 없을 때도 칸을 미리 만들어 둔 이유는 화면이
 *        사진 자리를 비워 두는 형태로 먼저 만들어질 수 있게 하기 위해서다. 없으면 키 자체가 빠진다
 * @param photoSource 사진 출처 표기 문구. {@code photoUrl} 과 짝이다. 없으면 키 자체가 빠진다
 * @param photoSubject 그 사진이 <b>무엇을 찍은 것인가</b> — S15P21E201-1039.
 *        {@code SELF}=이 장소를 찍은 사진, {@code VENUE}=이 장소가 <b>들어 있는 곳</b>을 찍은 사진.
 *        <p>이 칸이 없으면 화면은 둘을 구분할 방법이 없어 <b>주변 시설 사진을 이 장소
 *        사진처럼</b> 그린다. 축제에서 먼저 드러난 문제이고(-1021 실측: 부산 축제 사진 35건 중
 *        축제를 실제로 찍은 것은 1건), 식당·해수욕장도 같은 자료에서 온다.
 *        <p>출처({@code photoSource})와 <b>다른 질문</b>이라 칸을 갈랐다 — 「누가 준 사진인가」와
 *        「무엇을 찍은 사진인가」를 한 칸에 담으면 둘 중 하나는 반드시 거짓말이 된다.
 *        값이 없으면 키가 빠진다. 모르는 것을 아는 척하지 않는다 — 화면도 칸이 없으면 아무 말도
 *        안 하도록 되어 있다
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
		@JsonInclude(JsonInclude.Include.NON_NULL) Place.PhotoSubject photoSubject,
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
	 * @param reason {@code UNAVAILABLE} 일 때만 채워진다. 값은 셋이다 —
	 *        {@code ITINERARY_NOT_SPECIFIED}(요청이 일정을 지정하지 않았다),
	 *        {@code ITINERARY_NOT_VISIBLE}(그 일정을 이 요청자에게 보여줄 수 없다 — 없는 일정과
	 *        남의 일정이 함께 여기 온다), {@code ITINERARY_LOOKUP_UNAVAILABLE}(서버가 지금
	 *        판정할 수 없다). 정본은 {@code ItineraryMembershipPort} 의 상수다
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
