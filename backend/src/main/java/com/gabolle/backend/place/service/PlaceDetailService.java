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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.api.PlaceDetailResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
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
 * <li>"일정 포함 여부가 응답에 있다" — {@link ItineraryMembershipPort} 가 답한다. 어느 일정인지는
 *     요청이 지정하고({@code itineraryId} 질의 파라미터), 지정하지 않으면 모른다고 답한다.
 *     그 경로만 일정 쪽 질의를 더 돈다 — 지정하지 않은 요청의 질의 수는 위의 셋 그대로다</li>
 * </ul>
 */
@Service
@Profile({"db", "dev"})
public class PlaceDetailService {

	private static final Logger log = LoggerFactory.getLogger(PlaceDetailService.class);

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	private final UserPlaceCodeMapRepository codeMapRepository;

	/**
	 * {@code ObjectProvider} 로 받는다 — 이 포트의 구현({@code itinerary.application
	 * .ItineraryPlaceMembershipService})은 {@code itinerary} 패키지에 있고,
	 * {@code PlaceSliceApplication} 은 {@code common}·{@code place} 만 스캔하므로 그 슬라이스에는
	 * 빈으로 없다. {@code ObjectProvider} 는 "있으면 주고 없으면 빈손" 인 주입 방식이다.
	 * {@code RecommendationRecorder} 가 {@code ItineraryDraftPort} 에 대해 이미 쓰고 있는 것과
	 * 같은 판단이다 — 여기서도 그것을 물려받는다.
	 */
	private final ObjectProvider<ItineraryMembershipPort> itineraryMembership;

	private final ObjectMapper objectMapper;

	public PlaceDetailService(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository,
			UserPlaceCodeMapRepository codeMapRepository,
			ObjectProvider<ItineraryMembershipPort> itineraryMembership,
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
	 * 일정을 지정하지 않는 호출부를 위해 둔 자리. 포함 여부는 "모른다" 로 나간다.
	 *
	 * @param acceptLanguageHeader 요청의 {@code Accept-Language} 값 그대로. 없으면 {@code null} —
	 *        그 경우 한국어를 우선한다
	 */
	@Transactional(readOnly = true)
	public PlaceDetailResponse get(UUID placeId, UUID viewerId, String acceptLanguageHeader) {
		return get(placeId, viewerId, acceptLanguageHeader, null);
	}

	/**
	 * @param itineraryId 어느 일정에 대해 포함 여부를 묻는가. {@code null} 이면 묻지 않은 것이고
	 *        정상적인 요청이다 — 여행 맥락 없이 장소만 열어 보는 화면이 있다. 왜 여행이 아니라
	 *        일정을 받는지는 {@link ItineraryMembershipPort} 클래스 주석에 있다
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
				// 이 화면의 주된 값은 이름이라 영문 이름 유무로 판정한다. 어느 필드를 기준으로
				// 삼는지가 응답마다 다른 이유는 RequestLanguage 주석에 있다
				RequestLanguage.resolve(acceptLanguageHeader, place.getNameEn() != null));
	}

	/**
	 * 일정 포함 여부를 정한다. 이 메서드가 정하는 것은 <b>묻기 전에 끝나는 두 경우</b>다.
	 *
	 * <p>첫째, 요청이 일정을 지정하지 않으면 포트를 부르지 않는다. 답할 수 없는 질문이 아니라
	 * 하지 않은 질문이라, 그 구분을 {@code reason} 에 그대로 싣는다.
	 *
	 * <p>둘째, 이 컨텍스트에 포트 구현이 없으면 <b>{@code UNAVAILABLE} 로 답한다.</b> 여기서
	 * {@code NOT_INCLUDED} 를 돌려주면 화면이 "이 장소는 일정에 없다" 고 단정하게 되고, 실제로는
	 * 들어 있었다는 것이 나중에 드러나도 사용자가 이미 본 것은 되돌릴 수 없다 — 배선이 빠진 것을
	 * 사실로 바꿔 내보내는 셈이다. 예외를 던져 요청을 실패시키는 것도 맞지 않다. 장소 상세의
	 * 본체는 장소 정보이고, 포함 여부 한 칸의 구현이 없다고 화면 전체를 못 그리게 할 이유가 없다.
	 * 그래서 "모른다" 로 답하고, 왜 모르는지는 {@code reason} 이 말한다.
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
	 * 저장된 행 하나를 응답 한 줄로 옮긴다.
	 *
	 * <h2>🔴 값이 있는데 못 읽으면 {@code UNKNOWN} 으로 낮춘다 — S15P21E201-749</h2>
	 *
	 * <p>{@link #readValue} 는 <b>"값이 원래 없음"</b> 과 <b>"값이 있는데 JSON 이 깨졌음"</b> 을
	 * 똑같이 {@code null} 로 돌려준다. 그것을 그대로 실으면 원래의 {@code evidenceStatus} 가
	 * 함께 나가서, 깨진 행이 <b>{@code VERIFIED} + {@code value:null}</b> 로 보인다. 그 조합은
	 * {@link PlaceFeatureView} 가 정의한 다섯 상태 어디에도 없다 — 읽는 쪽은 "확인됐다" 로
	 * 받는데 값이 없다. 알레르기 항목이 그렇게 나가면, 값을 보러 온 사람에게 가장 나쁘다.
	 *
	 * <p>🔴 <b>후보 경로({@code PlaceCandidateQueryService.toViews})처럼 행을 버리지 않는다.</b>
	 * 거기는 {@code BaselineCandidateScorer.bucketFor} 가 "행 없음" 을 {@code UNVERIFIED} 로
	 * 읽어 주므로 버리는 것이 곧 안전한 기본값이다. 여기는 사람이 보는 화면이고, 행을 버리면
	 * 그 종류가 아래 반복문에서 {@code NOT_COLLECTED}(<b>"수집 대상에 아직 안 들어갔다"</b>)로
	 * 채워진다 — 사실이 아니고, {@link PlaceFeatureView} 문서대로라면 <b>파이프라인을 고치라고
	 * 엉뚱한 사람을 부르는 것</b>이다.
	 *
	 * <p>{@code UNKNOWN} 은 <b>"보러 갔는데 못 정했다"</b> 이고 값이 반드시 비어 있어야 한다.
	 * 깨진 행이 정확히 그 상태다. 그래서 상태를 낮추고 값을 비운다 — 응답 계약을 지키면서,
	 * 혹시 이 뷰가 나중에 표식 판정에 쓰이더라도 {@code cannotRuleOutPresence} 가 참이라
	 * {@code UNVERIFIED} 쪽으로 떨어진다.
	 *
	 * <h2>🔴 지금은 이 가지에 못 들어온다 — 그래도 두는 이유</h2>
	 *
	 * <p><b>솔직하게 적어 둔다.</b> {@code place_feature.value} 는 {@code JSONB} 다
	 * ({@code V20260904000000__place_and_place_feature.sql}). PostgreSQL 이 <b>쓰는 시점에</b>
	 * JSON 을 검사하므로 깨진 값은 애초에 저장되지 않는다 — 직접 넣어도, {@code ::jsonb} 로
	 * 캐스팅해도 {@code invalid input syntax for type json} 으로 거부된다(2026-09-09 실측).
	 * 이 서비스는 {@code findByPlaceId} 로 DB 에서만 읽으므로, 오늘 이 {@code if} 는 참이 될 수
	 * 없다. <b>즉 이것은 지금 있는 결함을 고치는 코드가 아니다.</b>
	 *
	 * <p>그래도 두는 까닭은 둘이다. 첫째, {@code VERIFIED} + {@code value:null} 은
	 * {@link PlaceFeatureView} 가 정의한 다섯 상태에 없는 <b>표현 불가능한 조합</b>이라, 만들 수
	 * 있는 경로를 열어 두지 않는다. 둘째, 칸 종류가 {@code TEXT} 로 바뀌거나 DB 를 거치지 않고
	 * 만든 {@link PlaceFeature} 가 들어오는 날 이 가지가 살아난다.
	 *
	 * <p>🔴 <b>이 주석을 지우지 마라.</b> 지우면 다음 사람이 이 코드를 보고 "깨진 값이 실제로
	 * 들어온다" 고 읽는다. 그것이 S15P21E201-749 가 처음에 안전 결함으로 잘못 알려진 이유다.
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
