package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.ConditionCoverageResponse;
import com.gabolle.backend.place.domain.MatchKind;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.place.service.ConditionCoverageService;

/**
 * 「이 조건을 판정할 자료가 있나」를 어떻게 세는가 — S15P21E201-1508.
 *
 * <p>이 기능이 있는 이유가 곧 이 시험이 보는 것이다. 여행 조건 화면은 조건을 묻고
 * <i>"임의로 완화하지 않습니다"</i> 라고 적어 두는데, 판정할 장소 값이 <b>0건</b>인 문항이
 * 있다. 그것을 화면이 알려면 서버가 세어 줘야 하고, <b>0건인 문항이 응답에서 빠지면</b>
 * 화면은 그 문항을 「자료 있음」으로 읽는다 — 고치려는 거짓말이 그대로 남는다.
 */
class ConditionCoverageServiceTest {

	private UserPlaceCodeMapRepository codeMapRepository;
	private PlaceFeatureRepository featureRepository;
	private PlaceRepository placeRepository;
	private ConditionCoverageService service;

	@BeforeEach
	void setUp() {
		this.codeMapRepository = mock(UserPlaceCodeMapRepository.class);
		this.featureRepository = mock(PlaceFeatureRepository.class);
		this.placeRepository = mock(PlaceRepository.class);
		this.service = new ConditionCoverageService(this.codeMapRepository, this.featureRepository,
				this.placeRepository);
		when(this.placeRepository.count()).thenReturn(6866L);
	}

	private static UserPlaceCodeMap codeMap(UserInputKind kind, String code, String featureType) {
		UserPlaceCodeMap row = mock(UserPlaceCodeMap.class);
		when(row.getUserInputKind()).thenReturn(kind);
		when(row.getUserInputCode()).thenReturn(code);
		when(row.getPlaceFeatureType()).thenReturn(featureType);
		when(row.getMatchKind()).thenReturn(MatchKind.HARD_FILTER);
		return row;
	}

	private static PlaceFeatureRepository.FeatureTypePlaceCount count(String featureType, long places) {
		PlaceFeatureRepository.FeatureTypePlaceCount row =
				mock(PlaceFeatureRepository.FeatureTypePlaceCount.class);
		when(row.getFeatureType()).thenReturn(featureType);
		when(row.getPlaceCount()).thenReturn(places);
		return row;
	}

	private ConditionCoverageResponse.Condition conditionOf(ConditionCoverageResponse response, String code) {
		return response.conditions().stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
	}

	@Test
	@DisplayName("🔴 자료가 0건인 문항도 응답에 «남는다» — 빠지면 화면이 「자료 있음」으로 읽는다")
	void aConditionWithNoDataStillAppears() {
		// 🔴 목을 먼저 다 만들고 나서 바깥 스텁을 건다. when(...) 안에서 또 when(...) 을
		//    부르면 Mockito 가 UnfinishedStubbingException 을 던진다.
		List<UserPlaceCodeMap> rows = List.of(
				codeMap(UserInputKind.CONSTRAINT, "ALLERGY", "ALLERGEN_TAG"),
				codeMap(UserInputKind.PREFERENCE, "SLOPE_PREFERENCE", "SLOPE_PERCENT"));
		// 질의는 «있는 것만» 돌려준다 — ALLERGEN_TAG 는 줄이 아예 없다. 그것이 0건의 모습이다.
		List<PlaceFeatureRepository.FeatureTypePlaceCount> counts = List.of(count("SLOPE_PERCENT", 2682L));
		when(this.codeMapRepository.findAll()).thenReturn(rows);
		when(this.featureRepository.countPlacesByFeatureType(any())).thenReturn(counts);

		ConditionCoverageResponse response = this.service.describe();

		assertThat(response.conditions()).as("문항이 사라지면 화면이 물어볼 것도 모른다").hasSize(2);
		assertThat(conditionOf(response, "ALLERGY").features())
				.singleElement()
				.satisfies(f -> {
					assertThat(f.featureType()).isEqualTo("ALLERGEN_TAG");
					assertThat(f.placeCount()).as("질의에 없던 갈래는 0곳이다").isZero();
					assertThat(f.totalPlaceCount()).isEqualTo(6866L);
				});
		assertThat(conditionOf(response, "SLOPE_PREFERENCE").features())
				.singleElement()
				.satisfies(f -> assertThat(f.placeCount()).isEqualTo(2682L));
	}

	@Test
	@DisplayName("🔴 표식 둘에 걸친 문항은 갈라서 준다 — 뭉치면 계단 0건이 접근성 102건에 덮인다")
	void aConditionWithTwoFeaturesKeepsThemApart() {
		List<UserPlaceCodeMap> rows = List.of(
				codeMap(UserInputKind.CONSTRAINT, "MOBILITY", "ACCESSIBILITY_TAG"),
				codeMap(UserInputKind.CONSTRAINT, "MOBILITY", "STAIRS_PRESENT"));
		List<PlaceFeatureRepository.FeatureTypePlaceCount> counts = List.of(count("ACCESSIBILITY_TAG", 102L),
				count("SLOPE_PERCENT", 2682L));
		when(this.codeMapRepository.findAll()).thenReturn(rows);
		when(this.featureRepository.countPlacesByFeatureType(any())).thenReturn(counts);

		ConditionCoverageResponse response = this.service.describe();

		assertThat(response.conditions()).as("같은 문항은 한 줄로 묶는다").hasSize(1);
		// 경사(SLOPE_PERCENT)는 대조표에 없지만 채점기가 이동 조건 판정에 쓰므로 맨 뒤에 따로 실린다 — 아래 시험.
		assertThat(conditionOf(response, "MOBILITY").features())
				.extracting(ConditionCoverageResponse.Feature::featureType,
						ConditionCoverageResponse.Feature::placeCount)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple("ACCESSIBILITY_TAG", 102L),
						org.assertj.core.groups.Tuple.tuple("STAIRS_PRESENT", 0L),
						org.assertj.core.groups.Tuple.tuple("SLOPE_PERCENT", 2682L));
	}

	@Test
	@DisplayName("🔴 이동 조건에는 채점기가 실제로 쓰는 경사도 센다 — 대조표만 세면 「판정할 자료가 거의 없다」로 보인다")
	void mobilityCoverageIncludesSlopeTheScorerUses() {
		List<UserPlaceCodeMap> rows = List.of(
				codeMap(UserInputKind.CONSTRAINT, "MOBILITY", "ACCESSIBILITY_TAG"),
				codeMap(UserInputKind.CONSTRAINT, "ALLERGY", "ALLERGEN_TAG"));
		List<PlaceFeatureRepository.FeatureTypePlaceCount> counts = List.of(count("SLOPE_PERCENT", 6500L));
		when(this.codeMapRepository.findAll()).thenReturn(rows);
		when(this.featureRepository.countPlacesByFeatureType(any())).thenReturn(counts);

		ConditionCoverageResponse response = this.service.describe();

		assertThat(conditionOf(response, "MOBILITY").features())
				.extracting(ConditionCoverageResponse.Feature::featureType)
				.containsExactly("ACCESSIBILITY_TAG", "SLOPE_PERCENT");
		assertThat(conditionOf(response, "MOBILITY").features().get(1).placeCount()).isEqualTo(6500L);
		assertThat(conditionOf(response, "ALLERGY").features()).as("다른 문항에는 안 붙는다").singleElement()
				.satisfies(f -> assertThat(f.featureType()).isEqualTo("ALLERGEN_TAG"));
	}

	@Test
	@DisplayName("대조표에 이동 조건이 없으면 경사만으로 문항을 지어내지 않는다")
	void slopeDoesNotInventAMobilityCondition() {
		List<UserPlaceCodeMap> rows = List.of(codeMap(UserInputKind.CONSTRAINT, "ALLERGY", "ALLERGEN_TAG"));
		when(this.codeMapRepository.findAll()).thenReturn(rows);
		when(this.featureRepository.countPlacesByFeatureType(any())).thenReturn(List.of());

		assertThat(this.service.describe().conditions()).extracting(ConditionCoverageResponse.Condition::code)
				.containsExactly("ALLERGY");
	}

	@Test
	@DisplayName("🔴 문항 목록을 코드에 안 박는다 — 대조표에 생긴 새 문항이 그대로 나온다")
	void aNewQuestionInTheCodeMapAppearsWithoutCodeChange() {
		// 대조표에만 있고 이 클래스는 이름조차 모르는 문항이다. 그래도 나와야 한다 —
		// 그것이 「자료가 들어오면 코드 수정 없이」의 반쪽이다.
		List<UserPlaceCodeMap> rows = List.of(
				codeMap(UserInputKind.PREFERENCE, "NOISE_TOLERANCE_2027", "NOISE_TOLERANCE_SCORE"));
		when(this.codeMapRepository.findAll()).thenReturn(rows);
		when(this.featureRepository.countPlacesByFeatureType(any())).thenReturn(List.of());

		ConditionCoverageResponse response = this.service.describe();

		assertThat(response.conditions()).singleElement()
				.satisfies(c -> assertThat(c.code()).isEqualTo("NOISE_TOLERANCE_2027"));
	}

	@Test
	@DisplayName("대조표가 비면 빈 목록이다 — 「자료가 없다」고 단정하지 않는다")
	void anEmptyCodeMapSaysNothing() {
		when(this.codeMapRepository.findAll()).thenReturn(List.of());

		assertThat(this.service.describe().conditions()).isEmpty();
	}
}
