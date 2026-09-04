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
 * 장소 하나의 상세 (S15P21E201-476).
 *
 * <p>완료 기준 셋을 각각 이렇게 만족시킨다.
 *
 * <ul>
 * <li>"한 번의 조회로 항목이 모두 온다" — HTTP 한 번, 내부 질의는 장소 1 + 피처 1 이다. 피처를
 *     종류마다 따로 읽지 않는다</li>
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
				new PlaceDetailResponse.ItineraryInclusion(inclusion.state(), inclusion.reason()));
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
