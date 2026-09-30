package com.gabolle.backend.recommendation.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.common.privacy.SensitivePayloadGuard;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.MatchKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.TripConstraint;

import tools.jackson.databind.ObjectMapper;

/**
 * 할랄을 고르면 고기가 중심인 집과 술이 중심인 집이 빠진다. 해산물·채소 집은 남는다(확인 안 됨 경고).
 *
 * <p>전에는 할랄에 아무 검사가 없어 운영 할랄 여행에 이자카야(로바타아키)가 들어갔다(2026-09-29 실측).
 * 할랄 도축을 확인할 수 없는 고기는 종류와 상관없이 할랄이 아니므로, 「국밥」이 돼지인지 소인지는 묻지 않는다.
 */
class BaselineCandidateScorerHalalTest {

	private final BaselineCandidateScorer scorer = new BaselineCandidateScorer(new ObjectMapper());

	private final List<UserPlaceCodeMap> constraintCodeMap = List.of(
			codeMap("DIET", "DIETARY_SUPPORT_TAG", MatchKind.HARD_FILTER));

	@Test
	@DisplayName("🔴 할랄이면 고기 중심 집은 고기 종류를 가리지 않고 빠진다 — 소고기국밥도, 빠지는 이유는 돼지가 아니다")
	void 고기_중심_집은_종류와_상관없이_빠진다() {
		for (String name : List.of("해운대암소갈비집", "밀양돼지국밥", "해운대원조할매국밥", "BBQ치킨 서면점", "양꼬치 전문",
				"한우명가", "송해와오리백숙", "팔팔대패", "뚱돈")) {
			EngineCandidate result = score(candidate(name, List.of()), halal());
			assertThat(result.constraintVerdict()).as(name).isEqualTo(ConstraintVerdict.FAIL);
			assertThat(result.violations()).as(name).anySatisfy(v -> {
				assertThat(v.get("code")).isEqualTo("DIET_NOT_SUPPORTED");
				assertThat(v.get("reason")).isEqualTo("MEAT_CENTRIC");
			});
			assertThat(result.violations().toString()).as(name + " — 국밥만 보고 돼지라고 말하지 않는다")
					.doesNotContain("PORK");
		}
	}

	@Test
	@DisplayName("이름이 고기를 말하지 않아도 대표 메뉴가 고기면 할랄에서 빠진다")
	void 대표_메뉴가_고기면_빠진다() {
		EngineCandidate result = score(candidate("신흥관", List.of(menu("사천짜장 9,500원"))), halal());
		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.violations()).anySatisfy(v -> assertThat(v.get("evidence")).isEqualTo("MENU_PRICE_WON"));
	}

	@Test
	@DisplayName("🔴 해산물·채소 집은 할랄에서 빼지 않는다 — 확인된 것은 아니므로 경고를 단다")
	void 해산물_채소_집은_남는다() {
		for (String name : List.of("원조할매복국", "고마대구탕", "옥미아구찜", "민락횟집", "스시시안", "비빔밥 카페",
				"베지나랑 부산광안리점")) {
			EngineCandidate result = score(candidate(name, List.of()), halal());
			assertThat(result.constraintVerdict()).as(name).isEqualTo(ConstraintVerdict.PASS);
			assertThat(result.warningCodes()).as(name).contains("DIET_SUPPORT_UNVERIFIED");
		}
	}

	@Test
	@DisplayName("🔴 술이 중심인 집은 할랄에서 빠진다 — 채식 집이어도 술집이면 뺀다")
	void 술_중심_집은_빠진다() {
		for (String name : List.of("로바타아키 이자카야", "이츠키 이자카야", "뚱이네포차", "팁시펍", "Before beer pub",
				"봉구비어 교대점", "손맥주집", "육전과막걸리가맛있는달", "송화소주방", "혼술바민지영", "청하통술", "달빛 술집",
				"남부단란주점", "로즈bar", "Road BAR", "The Beer", "비건 와인바")) {
			EngineCandidate result = score(candidate(name, List.of()), halal());
			assertThat(result.constraintVerdict()).as(name).isEqualTo(ConstraintVerdict.FAIL);
			assertThat(result.violations()).as(name)
					.anySatisfy(v -> assertThat(v.get("reason")).isEqualTo("ALCOHOL_CENTRIC"));
		}
	}

	@Test
	@DisplayName("술 낱말 오탐을 막는다 — 스낵바·에스프레소바·PUBLIC·사케동·Bombay Brau·바비큐, 식당이 아닌 미술관")
	void 술_낱말_오탐_안난다() {
		for (String name : List.of("Turtles Snack Bar", "오엘스에스프레소바", "그린 SALAD BAR", "PUBLIC 카페", "사케동 전문점",
				"Bombay Brau", "The Barn", "예술밥상")) {
			assertThat(score(candidate(name, List.of()), halal()).constraintVerdict()).as(name)
					.isEqualTo(ConstraintVerdict.PASS);
		}
		assertThat(score(new PlaceCandidateResponse.Candidate(UUID.randomUUID(), "부산시립미술관", "CULTURE_TEMPLE", 35.1,
				129.0, 1000L, List.of()), halal()).constraintVerdict()).isEqualTo(ConstraintVerdict.PASS);
	}

	@Test
	@DisplayName("술 규칙은 할랄에만 — 채식·비건에게 이자카야는 이름만으로는 빠지지 않는다")
	void 술_규칙은_할랄에만() {
		for (String code : List.of("VEGETARIAN", "VEGAN", "PESCATARIAN")) {
			assertThat(score(candidate("이츠키 이자카야", List.of()), diet(code)).constraintVerdict()).as(code)
					.isEqualTo(ConstraintVerdict.PASS);
		}
	}

	@Test
	@DisplayName("인도·네팔 식당은 메뉴에 닭고기 커리가 있어도 글 근거로 빼지 않는다 — 할랄도 같다")
	void 인도식당은_글로_안뺀다() {
		EngineCandidate result = score(candidate("펀자브인도요리", List.of(menu("치킨 티카 마살라"))), halal());
		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.PASS);
		assertThat(result.warningCodes()).contains("DIET_SUPPORT_UNVERIFIED");
	}

	@Test
	@DisplayName("🔴 새 위반 기록(reason·evidence)은 저장 직전 민감정보 검사를 통과한다 — 걸리면 추천 작업이 통째로 실패한다")
	void 위반_기록이_민감정보_검사를_통과한다() {
		SensitivePayloadGuard guard = new SensitivePayloadGuard();
		for (EngineCandidate result : List.of(
				score(candidate("해운대원조할매국밥", List.of()), halal()),
				score(candidate("신흥관", List.of(menu("사천짜장 9,500원 · 문의 010-1234-5678"))), halal()),
				score(candidate("이츠키 이자카야", List.of()), halal()))) {
			assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.FAIL);
			assertThatCode(() -> guard.verify(result.violations(), "violations")).doesNotThrowAnyException();
		}
	}

	@Test
	@DisplayName("할랄 지원 표식이 붙은 집은 고기·술 낱말이 있어도 빼지 않는다 — 목록의 케밥집·삼계탕집")
	void 할랄_표식이_있으면_낱말로_안뺀다() {
		for (PlaceCandidateResponse.Candidate candidate : List.of(
				candidate("사마르칸트", List.of(menu("샤슬릭 꼬치구이 · 카존 케밥"), halalTag())),
				candidate("원조서울삼계탕", List.of(halalTag())),
				candidate("발리우드 BAR", List.of(halalTag())))) {
			EngineCandidate result = score(candidate, halal());
			assertThat(result.constraintVerdict()).as(candidate.nameKo()).isEqualTo(ConstraintVerdict.PASS);
			assertThat(result.warningCodes()).as(candidate.nameKo()).doesNotContain("DIET_SUPPORT_UNVERIFIED");
		}
	}

	@Test
	@DisplayName("표식은 그 식단에만 — 할랄 표식이 있는 삼계탕집도 채식에서는 빠진다")
	void 할랄_표식은_채식_판정을_안푼다() {
		EngineCandidate result = score(candidate("원조서울삼계탕", List.of(halalTag())), diet("VEGETARIAN"));
		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.violations()).anySatisfy(v -> assertThat(v.get("reason")).isEqualTo("MEAT_CENTRIC"));
	}

	private static PlaceFeatureView halalTag() {
		return new PlaceFeatureView("DIETARY_SUPPORT_TAG", "HALAL", "VERIFIED", new ObjectMapper().readTree("true"),
				null, "KTO_MUSLIM_FRIENDLY");
	}

	private EngineCandidate score(PlaceCandidateResponse.Candidate candidate, TripConstraint constraint) {
		return this.scorer.score(candidate, null, List.of(constraint), 5000,
				new BaselineEngineProperties.Weights(0.30, 0.20, 0.15, 0.15, 0.10, 0.10),
				new PreferenceAlignmentWeights(null, null, null, null, null), List.of(), this.constraintCodeMap,
				List.of(), 0.05);
	}

	private static PlaceCandidateResponse.Candidate candidate(String name, List<PlaceFeatureView> features) {
		return new PlaceCandidateResponse.Candidate(UUID.randomUUID(), name, "FOOD", 35.1, 129.0, 1000L, features);
	}

	private static PlaceFeatureView menu(String menu) {
		return new PlaceFeatureView("MENU_PRICE_WON", null, "ESTIMATED",
				new ObjectMapper().readTree("{\"priceWon\":9500,\"menu\":\"" + menu + "\"}"), null, "FIXTURE");
	}

	private static TripConstraint halal() {
		return diet("HALAL");
	}

	private static TripConstraint diet(String code) {
		return new TripConstraint(UUID.randomUUID().toString(), "trip-1", "DIET", code, TripConstraint.Severity.HARD,
				"EXCLUDES", null, null, TripConstraint.EvidenceStatus.VERIFIED, TripConstraint.AnswerStatus.SELECTED,
				PersonalizationScope.TRIP, TripConstraint.DietRequirement.REQUIRED);
	}

	private static UserPlaceCodeMap codeMap(String userInputCode, String placeFeatureType, MatchKind matchKind) {
		UserPlaceCodeMap row = mock(UserPlaceCodeMap.class);
		when(row.getUserInputCode()).thenReturn(userInputCode);
		when(row.getPlaceFeatureType()).thenReturn(placeFeatureType);
		when(row.getMatchKind()).thenReturn(matchKind);
		return row;
	}
}
