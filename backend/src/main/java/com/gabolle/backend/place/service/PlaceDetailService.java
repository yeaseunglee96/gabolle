package com.gabolle.backend.place.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.api.PlaceDetailResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 장소 하나의 상세 (S15P21E201-476 · -217 · -430 부분).
 *
 * <p>완료 기준 셋을 각각 이렇게 만족시킨다.
 *
 * <ul>
 * <li>"한 번의 조회로 항목이 모두 온다" — HTTP 는 한 번이다. 🔴 내부 질의는 <b>셋</b>이다 — 장소 1,
 *     피처 1({@code findByPlaceId}), 그리고 {@link #expectedFeatureTypes()} 가 부르는
 *     {@code codeMapRepository.findAll()} 1. 예전에는 "장소 1 + 피처 1, 둘" 이라고 적혀 있었는데
 *     실제로는 셋이었다 — 문서를 사실에 맞게 고쳤다. 세 번째 질의는 요청마다 도는 <b>정적 기준
 *     데이터</b>(대조표) 조회라 매번 같은 결과를 돌려준다. 지금은 캐시를 넣지 않는다 — 캐시
 *     무효화가 대조표를 마이그레이션으로만 바꾸는 이 표에는 필요 없는 새 실패 지점을 만든다.
 *     느려지면(대조표가 아주 커지거나 이 API 가 아주 자주 불리면) 그때 캐시를 검토한다.
 *     피처를 종류마다 따로 읽지는 않는다는 점은 그대로다. {@code openingHours}·{@code priceLevel}
 *     전용 칸(-476 추가분)도 새 질의 없이 이미 읽은 피처 목록에서 골라낸다 — {@link #featureSlot}
 *     참고. 언어 선택(-430 부분)도 이미 읽은 {@code Place} 의 {@code nameEn} 만 보므로 질의가
 *     늘지 않는다</li>
 * <li>"정보 없음과 해당 없음이 구분된다" — {@link PlaceFeatureView} 의 네 상태로 나눈다</li>
 * <li>"일정 포함 여부가 응답에 있다" — {@link ItineraryMembershipPort} 가 답한다. 지금은 담을 표가
 *     없어 "알 수 없음" 이다</li>
 * </ul>
 */
@Service
@Profile({"db", "dev"})
public class PlaceDetailService {

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	private final UserPlaceCodeMapRepository codeMapRepository;

	private final ItineraryMembershipPort itineraryMembership;

	private final ObjectMapper objectMapper;

	public PlaceDetailService(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository,
			UserPlaceCodeMapRepository codeMapRepository, ItineraryMembershipPort itineraryMembership,
			ObjectMapper objectMapper) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
		this.codeMapRepository = codeMapRepository;
		this.itineraryMembership = itineraryMembership;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public PlaceDetailResponse get(UUID placeId, UUID viewerId) {
		// Accept-Language 없이 부르는 기존 호출부(컨트롤러 배선 전, 그리고 이 서비스를 직접 부르는
		// 기존 테스트)를 위해 둔 자리다. 헤더가 없을 때와 같은 경로라 한국어를 우선한다.
		return get(placeId, viewerId, null);
	}

	/**
	 * @param acceptLanguageHeader 요청의 {@code Accept-Language} 값 그대로. 없으면 {@code null} —
	 *        그 경우 한국어를 우선한다. 🔴 지금 {@code PlaceDetailController} 는 이 값을 넘기지
	 *        않는다 — 컨트롤러가 이 작업의 수정 대상 목록 밖이라 배선하지 않았다. 보고서에 그
	 *        컨트롤러가 어떻게 바뀌어야 하는지 적어 뒀다
	 */
	@Transactional(readOnly = true)
	public PlaceDetailResponse get(UUID placeId, UUID viewerId, String acceptLanguageHeader) {
		Place place = this.placeRepository.findById(placeId)
				.orElseThrow(() -> new PlaceNotFoundException(placeId));

		List<PlaceFeature> stored = this.placeFeatureRepository.findByPlaceId(placeId);
		List<PlaceFeatureView> features = buildFeatureViews(stored);

		ItineraryMembershipPort.Inclusion inclusion = this.itineraryMembership.inclusionOf(viewerId, placeId);

		return new PlaceDetailResponse(
				place.getPlaceId(), place.getNameKo(), place.getNameEn(),
				place.getCategory(), place.getAddress(), place.getLat(), place.getLng(),
				new PlaceDetailResponse.Provenance(place.getSourceType(), place.getSourceId(),
						place.getCollectedAt(), place.getObservedAt(), place.getDatasetVersion()),
				features,
				new PlaceDetailResponse.ItineraryInclusion(inclusion.state(), inclusion.reason()),
				place.getAddressEn(), place.getPhotoUrl(), place.getPhotoSource(),
				featureSlot(features, "OPENING_HOURS"),
				featureSlot(features, "PRICE_LEVEL"),
				// 이 화면의 주된 값은 이름이라 영문 이름 유무로 판정한다. 어느 필드를 기준으로
				// 삼는지가 응답마다 다른 이유는 RequestLanguage 주석에 있다
				RequestLanguage.resolve(acceptLanguageHeader, place.getNameEn() != null));
	}

	/**
	 * 이미 만든 {@code features} 목록에서 한 종류를 골라 전용 칸({@link PlaceDetailResponse.FeatureSlot})으로
	 * 바꾼다. 새 질의를 만들지 않는다.
	 *
	 * <p>행이 아예 없어 {@code NOT_COLLECTED} 로 합성된 항목은 "표식이 없다" 로 보고 {@code null}
	 * 을 돌려준다 — {@code @JsonInclude(NON_NULL)} 이 붙은 record 컴포넌트라 그러면 응답에서
	 * 키 자체가 빠진다.
	 */
	private PlaceDetailResponse.FeatureSlot featureSlot(List<PlaceFeatureView> features, String featureType) {
		return features.stream()
				.filter(view -> featureType.equals(view.featureType()))
				.filter(view -> !"NOT_COLLECTED".equals(view.evidenceStatus()))
				.findFirst()
				.map(view -> new PlaceDetailResponse.FeatureSlot(view.value(), view.evidenceStatus()))
				.orElse(null);
	}


	/**
	 * 저장된 피처를 그대로 내보내고, <b>행이 아예 없는 종류</b>에는 {@code NOT_COLLECTED} 를 붙인다.
	 *
	 * <p>🔴 어떤 종류에 대해 {@code NOT_COLLECTED} 를 만들 것인가가 이 메서드의 판단이다. 자바에
	 * 종류 목록을 박으면 -473 과 같은 이유로 틀린다 — 마이그레이션이 종류를 하나 더해도 응답은
	 * 그대로다. 그래서 <b>대조표에 있는 종류</b>만 대상으로 삼는다. 대조표는 "사용자 입력과 짝이
	 * 있는 피처" 의 목록이고, 짝이 없는 둘({@code POPULARITY_SCORE}, {@code CROWDING_SCORE})은
	 * 사용자가 직접 고르는 화면이 없어서 빠져 있다 — 빠뜨린 것이 아니라 짝이 없는 것이다
	 * ({@code PlaceFeatureCodeMapTest.UNPAIRED_BY_DESIGN} 이 그렇게 정의해 뒀다).
	 */
	private List<PlaceFeatureView> buildFeatureViews(List<PlaceFeature> stored) {
		List<PlaceFeatureView> views = new ArrayList<>();
		Set<String> present = new LinkedHashSet<>();

		for (PlaceFeature feature : stored) {
			present.add(feature.getFeatureType());
			views.add(new PlaceFeatureView(
					feature.getFeatureType(), feature.getFeatureKey(),
					feature.getEvidenceStatus().name(), readValue(feature.getValue()),
					feature.getObservedAt(), feature.getSourceType()));
		}

		for (String expected : expectedFeatureTypes()) {
			if (!present.contains(expected)) {
				views.add(PlaceFeatureView.notCollected(expected));
			}
		}

		views.sort(Comparator.comparing(PlaceFeatureView::featureType)
				.thenComparing(view -> view.featureKey() == null ? "" : view.featureKey()));
		return views;
	}

	/** 대조표에 이름이 오른 피처 종류. 자바가 아니라 표가 정본이다. */
	private Set<String> expectedFeatureTypes() {
		Set<String> types = new LinkedHashSet<>();
		for (UserPlaceCodeMap mapping : this.codeMapRepository.findAll()) {
			types.add(mapping.getPlaceFeatureType());
		}
		return types;
	}

	/**
	 * JSONB 문자열을 그대로 응답에 실을 수 있는 모양으로 바꾼다.
	 *
	 * <p>🔴 문자열 그대로 내보내면 응답에서 한 번 더 이스케이프돼서 클라이언트가 두 번 파싱해야
	 * 한다. 값이 깨져 있으면 상세 조회 전체를 실패시키지 않고 {@code null} 로 둔다 — 피처 하나가
	 * 잘못 적재된 것 때문에 장소를 못 보는 것이 더 나쁘다.
	 */
	private JsonNode readValue(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return this.objectMapper.readTree(raw);
		}
		catch (JacksonException exception) {
			return null;
		}
	}
}
