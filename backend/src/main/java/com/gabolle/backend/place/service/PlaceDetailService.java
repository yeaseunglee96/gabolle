package com.gabolle.backend.place.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.api.PlaceDetailResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.domain.PlacePhoto;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlacePhotoRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 장소 하나의 상세.
 *
 * <p>요청 한 번에 도는 질의는 셋이다 — 장소, 피처, 그리고 {@link #expectedFeatureTypes()} 의
 * 대조표. 대조표는 마이그레이션으로만 바뀌는 정적 데이터라 캐시를 두지 않았다.
 * {@code itineraryId} 를 지정한 요청만 일정 쪽 질의를 하나 더 돈다.
 */
@Service
@Profile({"db", "dev"})
public class PlaceDetailService {

	private static final Logger log = LoggerFactory.getLogger(PlaceDetailService.class);

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	private final UserPlaceCodeMapRepository codeMapRepository;

	/**
	 * 구현이 {@code itinerary} 패키지에 있어 {@code place} 만 스캔하는 슬라이스에는 빈이 없다.
	 * 그래서 "있으면 주고 없으면 빈손" 인 {@code ObjectProvider} 로 받는다.
	 */
	private final ObjectProvider<ItineraryMembershipPort> itineraryMembership;

	private final ObjectMapper objectMapper;

	/** 대표 사진 밖의 사진(S15P21E201-1840). {@code null} 이면 여러 장이 없는 것으로 본다. */
	private final PlacePhotoRepository placePhotoRepository;

	public PlaceDetailService(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository,
			UserPlaceCodeMapRepository codeMapRepository,
			ObjectProvider<ItineraryMembershipPort> itineraryMembership,
			ObjectMapper objectMapper) {
		this(placeRepository, placeFeatureRepository, codeMapRepository, itineraryMembership, objectMapper, null);
	}

	@Autowired
	public PlaceDetailService(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository,
			UserPlaceCodeMapRepository codeMapRepository,
			ObjectProvider<ItineraryMembershipPort> itineraryMembership,
			ObjectMapper objectMapper, PlacePhotoRepository placePhotoRepository) {
		this.placePhotoRepository = placePhotoRepository;
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
		this.codeMapRepository = codeMapRepository;
		this.itineraryMembership = itineraryMembership;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public PlaceDetailResponse get(UUID placeId, UUID viewerId) {
		// Accept-Language 없이 부르는 호출부를 위한 자리. 헤더가 없을 때와 같이 한국어를 우선한다.
		return get(placeId, viewerId, null);
	}

	/** 일정을 지정하지 않는 호출부를 위한 자리. 포함 여부는 "모른다" 로 나간다. */
	@Transactional(readOnly = true)
	public PlaceDetailResponse get(UUID placeId, UUID viewerId, String acceptLanguageHeader) {
		return get(placeId, viewerId, acceptLanguageHeader, null);
	}

	/**
	 * {@code itineraryId} 가 {@code null} 이면 묻지 않은 것이고 정상적인 요청이다 — 여행 맥락 없이
	 * 장소만 열어 보는 화면이 있다.
	 */
	@Transactional(readOnly = true)
	public PlaceDetailResponse get(UUID placeId, UUID viewerId, String acceptLanguageHeader, UUID itineraryId) {
		Place place = this.placeRepository.findById(placeId)
				.orElseThrow(() -> new PlaceNotFoundException(placeId));

		List<PlaceFeature> stored = this.placeFeatureRepository.findByPlaceId(placeId);
		List<PlaceFeatureView> features = buildFeatureViews(stored);

		ItineraryMembershipPort.Inclusion inclusion = inclusionOf(viewerId, itineraryId, placeId);

		return new PlaceDetailResponse(
				place.getPlaceId(), place.getNameKo(), place.getNameEn(),
				place.getCategory(), place.getAddress(), place.getLat(), place.getLng(),
				new PlaceDetailResponse.Provenance(place.getSourceType(), place.getSourceId(),
						place.getCollectedAt(), place.getObservedAt(), place.getDatasetVersion()),
				features,
				new PlaceDetailResponse.ItineraryInclusion(inclusion.state(), inclusion.reason()),
				place.getAddressEn(), place.getPhotoUrl(), place.getPhotoSource(), place.getPhotoSubject(),
				featureSlot(features, "OPENING_HOURS"),
				featureSlot(features, "PRICE_LEVEL"),
				// 이 화면의 주된 값은 이름이라 영문 이름 유무로 판정한다
				RequestLanguage.resolve(acceptLanguageHeader, place.getNameEn() != null),
				place.getPhotoLicense(),
				photosOf(place));
	}

	/**
	 * 대표 사진을 맨 앞에 두고 {@code place_photo} 를 순서대로 붙인다. 대표 사진과 같은 주소는 한 번만 싣는다 —
	 * 적재 때 대표 사진을 목록에도 넣었어도 화면에 같은 사진이 두 번 나오지 않게.
	 */
	private List<PlaceDetailResponse.Photo> photosOf(Place place) {
		List<PlaceDetailResponse.Photo> photos = new ArrayList<>();
		if (place.getPhotoUrl() != null) {
			photos.add(new PlaceDetailResponse.Photo(place.getPhotoUrl(), place.getPhotoSource(),
					place.getPhotoLicense()));
		}
		if (this.placePhotoRepository != null) {
			for (PlacePhoto extra : this.placePhotoRepository.findByPlaceIdOrderByPositionAsc(place.getPlaceId())) {
				if (!extra.getUrl().equals(place.getPhotoUrl())) {
					photos.add(new PlaceDetailResponse.Photo(extra.getUrl(), extra.getSource(), extra.getLicense()));
				}
			}
		}
		return photos;
	}

	/**
	 * 묻기 전에 끝나는 두 경우를 여기서 가른다. 둘 다 {@code UNAVAILABLE} 이고 {@code reason} 이
	 * 어느 쪽인지 말한다.
	 *
	 * <p>포트 구현이 없을 때 {@code NOT_INCLUDED} 를 돌려주면 배선이 빠진 것을 "일정에 없다" 는
	 * 사실로 바꿔 내보내게 된다. 그렇다고 예외를 던지면 포함 여부 한 칸 때문에 상세 화면 전체가
	 * 안 그려진다. 그래서 모른다고 답한다.
	 */
	private ItineraryMembershipPort.Inclusion inclusionOf(UUID viewerId, UUID itineraryId, UUID placeId) {
		if (itineraryId == null) {
			return ItineraryMembershipPort.Inclusion.unavailable(ItineraryMembershipPort.REASON_NOT_SPECIFIED);
		}

		ItineraryMembershipPort port = this.itineraryMembership.getIfAvailable();
		if (port == null) {
			return ItineraryMembershipPort.Inclusion
					.unavailable(ItineraryMembershipPort.REASON_LOOKUP_UNAVAILABLE);
		}
		return port.inclusionOf(viewerId, itineraryId, placeId);
	}

	/**
	 * 이미 만든 {@code features} 목록에서 골라내므로 새 질의를 만들지 않는다. {@code NOT_COLLECTED}
	 * 로 합성된 항목은 {@code null} 이 되고, {@code @JsonInclude(NON_NULL)} 이라 응답에서 키가 빠진다.
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
	 * 저장된 피처를 그대로 내보내고, 행이 아예 없는 종류에는 {@code NOT_COLLECTED} 를 붙인다.
	 *
	 * <p>어떤 종류를 채울지는 자바가 아니라 대조표가 정한다 — 목록을 코드에 박으면 마이그레이션이
	 * 종류를 더해도 응답이 그대로다. 대조표는 사용자 입력과 짝이 있는 피처만 담으므로
	 * {@code POPULARITY_SCORE}·{@code CROWDING_SCORE} 는 빠뜨린 것이 아니라 짝이 없는 것이다.
	 */
	private List<PlaceFeatureView> buildFeatureViews(List<PlaceFeature> stored) {
		List<PlaceFeatureView> views = new ArrayList<>();
		Set<String> present = new LinkedHashSet<>();

		for (PlaceFeature feature : stored) {
			present.add(feature.getFeatureType());
			views.add(toView(feature));
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

	/**
	 * 값이 있는데 JSON 을 못 읽으면 {@code evidenceStatus} 를 {@code UNKNOWN} 으로 낮춘다.
	 * 그대로 두면 {@code VERIFIED} + {@code value:null} 이 되는데, 그 조합은
	 * {@link PlaceFeatureView} 가 정의한 상태에 없다. 후보 경로와 달리 행을 버리지 않는다 —
	 * 버리면 {@code NOT_COLLECTED}("수집 대상에 안 들어갔다")로 채워져 사실이 아닌 값이 나간다.
	 *
	 * <p>{@code place_feature.value} 는 {@code JSONB} 이고 PostgreSQL 이 쓰는 시점에 검사하므로,
	 * DB 에서만 읽는 지금 이 가지는 참이 될 수 없다. 칸 종류가 바뀌거나 DB 를 거치지 않은
	 * {@link PlaceFeature} 가 들어올 때를 위해 남겨 둔 것이지, 실제로 깨진 값이 들어오고 있는
	 * 것은 아니다.
	 */
	private PlaceFeatureView toView(PlaceFeature feature) {
		String raw = feature.getValue();
		JsonNode value = readValue(raw);
		if (value == null && raw != null && !raw.isBlank()) {
			log.warn("place_feature 값 파싱 실패 — 상세 응답에서 UNKNOWN 으로 낮춘다. "
					+ "placeId={}, featureType={}, featureKey={}", feature.getPlaceId(),
					feature.getFeatureType(), feature.getFeatureKey());
			return new PlaceFeatureView(feature.getFeatureType(), feature.getFeatureKey(),
					PlaceEvidenceStatus.UNKNOWN.name(), null, feature.getObservedAt(), feature.getSourceType());
		}
		return new PlaceFeatureView(feature.getFeatureType(), feature.getFeatureKey(),
				feature.getEvidenceStatus().name(), value, feature.getObservedAt(), feature.getSourceType());
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
	 * JSONB 문자열을 파싱해서 싣는다. 문자열 그대로 내보내면 응답에서 한 번 더 이스케이프돼
	 * 클라이언트가 두 번 파싱해야 한다. 깨진 값은 {@code null} 로 두고 조회를 실패시키지 않는다.
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
