package com.gabolle.backend.recommendation.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
 * 채식·비건을 고르면 고기가 중심인 집이 빠진다 — S15P21E201-1815 (QA: 채식인데 고기집이 추천됨).
 * 식단 지원 표식(DIETARY_SUPPORT_TAG)이 운영에 0건이라 전에는 전부 「확인 안 됨」 경고로 통과했다.
 */
class BaselineCandidateScorerVegetarianTest {

	private final BaselineCandidateScorer scorer = new BaselineCandidateScorer(new ObjectMapper());

	private final List<UserPlaceCodeMap> constraintCodeMap = List.of(
			codeMap("DIET", "DIETARY_SUPPORT_TAG", MatchKind.HARD_FILTER));

	@Test
	@DisplayName("🔴 채식이면 갈비·삼겹살·곱창·족발·치킨집은 FAIL 이다")
	void 채식이면_고기집이_빠진다() {
		for (String name : List.of("해운대암소갈비집", "돼지 삼겹살 전문", "OO곱창", "원조족발", "BBQ치킨 서면점", "소고기국밥")) {
			EngineCandidate result = score(candidate(name, List.of()), diet("VEGETARIAN"));
			assertThat(result.constraintVerdict()).as(name).isEqualTo(ConstraintVerdict.FAIL);
			assertThat(result.violations()).as(name)
					.anySatisfy(v -> assertThat(v.get("code")).isEqualTo("DIET_NOT_SUPPORTED"));
		}
	}

	@Test
	@DisplayName("비건도 같다. 돼지국밥 태그만 붙은 집도 빠진다")
	void 비건과_돼지국밥태그() {
		assertThat(score(candidate("부산할매집", List.of(new PlaceFeatureView("CUISINE_TAG", "PORK_SOUP",
				"ESTIMATED", null, null, "FIXTURE"))), diet("VEGAN")).constraintVerdict())
				.isEqualTo(ConstraintVerdict.FAIL);
		assertThat(score(candidate("한우명가", List.of()), diet("VEGAN")).constraintVerdict())
				.isEqualTo(ConstraintVerdict.FAIL);
	}

	@Test
	@DisplayName("채식 근거가 있는 식당과 식당이 아닌 곳(물고기 체험관)은 남긴다 — 확인된 것은 아니므로 경고")
	void 고기집이_아니면_그대로() {
		for (PlaceCandidateResponse.Candidate candidate : List.of(candidate("비빔밥 카페", List.of()),
				candidate("해운대 두부마을", List.of()), place("물고기 체험관", "CULTURE_TEMPLE"))) {
			EngineCandidate result = score(candidate, diet("VEGETARIAN"));
			assertThat(result.constraintVerdict()).as(candidate.nameKo()).isEqualTo(ConstraintVerdict.PASS);
			assertThat(result.warningCodes()).contains("DIET_SUPPORT_UNVERIFIED");
		}
	}

	@Test
	@DisplayName("채식을 안 고른 사람에게는 고기집이 그대로 나온다 (할랄은 이제 고기를 뺀다 — BaselineCandidateScorerHalalTest)")
	void 다른_식단이면_안뺀다() {
		assertThat(score(candidate("해운대암소갈비집", List.of()), diet("GLUTEN_FREE")).constraintVerdict())
				.isNotEqualTo(ConstraintVerdict.FAIL);
		assertThat(score(candidate("해운대암소갈비집", List.of()), null).constraintVerdict())
				.isEqualTo(ConstraintVerdict.PASS);
	}

	@Test
	@DisplayName("🔴 운영 비건 후보에 들어갔던 집이 전부 빠진다 — S15P21E201-1822")
	void 비건이면_운영에서_새던_집이_빠진다() {
		for (String name : List.of("꿀꿀이감자탕", "자매국밥", "엘까르니따스광안리점", "살몬브라더스", "스시시안",
				"마끼몬스타", "아저씨대구탕", "부다면옥", "비학산칼국수", "화목뷔페", "OO횟집", "민락회센터",
				"해운대 대게", "원조복국", "수제버거하우스", "밀면전문점")) {
			assertThat(score(candidate(name, List.of()), diet("VEGAN")).constraintVerdict()).as(name)
					.isEqualTo(ConstraintVerdict.FAIL);
		}
	}

	@Test
	@DisplayName("채식은 고기 육수 집(감자탕·국밥·면옥·뷔페)에 회·칼국수까지 뺀다 — S15P21E201-1828 채식 통합")
	void 채식은_고기육수와_해산물까지() {
		for (String name : List.of("꿀꿀이감자탕", "자매국밥", "엘까르니따스광안리점", "부다면옥", "화목뷔페",
				"스시시안", "비학산칼국수")) {
			assertThat(score(candidate(name, List.of()), diet("VEGETARIAN")).constraintVerdict()).as(name)
					.isEqualTo(ConstraintVerdict.FAIL);
		}
	}

	@Test
	@DisplayName("🔴 비건·페스코는 채식과 똑같이 판정한다 — 앱에서 뺐지만 옛 여행에 남은 코드(S15P21E201-1828)")
	void 비건과_페스코는_채식과_같다() {
		for (String name : List.of("해운대암소갈비집", "돈반", "OO횟집", "우동토오루", "비학산칼국수", "채식뷔페 소담",
				"동네빵집", "해운대회관")) {
			ConstraintVerdict vegetarian = score(candidate(name, List.of()), diet("VEGETARIAN")).constraintVerdict();
			for (String code : List.of("VEGAN", "PESCATARIAN")) {
				assertThat(score(candidate(name, List.of()), diet(code)).constraintVerdict()).as(code + " " + name)
						.isEqualTo(vegetarian);
			}
		}
		// 페스코는 전에 거르는 규칙이 없어 갈비집이 통과했다.
		assertThat(score(candidate("해운대암소갈비집", List.of()), diet("PESCATARIAN")).constraintVerdict())
				.isEqualTo(ConstraintVerdict.FAIL);
	}

	@Test
	@DisplayName("비건은 해산물·밀면·복국 태그만 붙은 집도 뺀다")
	void 비건_구조화_태그() {
		for (PlaceFeatureView tag : List.of(
				new PlaceFeatureView("CUISINE_TAG", "SEAFOOD", "ESTIMATED", null, null, "FIXTURE"),
				new PlaceFeatureView("CUISINE_TAG", "MILMYEON", "ESTIMATED", null, null, "FIXTURE"),
				new PlaceFeatureView("DESIRED_FOOD_TAG", "BOKGUK", "ESTIMATED", null, null, "FIXTURE"))) {
			assertThat(score(candidate("부산할매집", List.of(tag)), diet("VEGAN")).constraintVerdict())
					.as(tag.featureKey()).isEqualTo(ConstraintVerdict.FAIL);
		}
	}

	@Test
	@DisplayName("비건·채식을 위한 집과 디저트 업종은 남기고, 짧은 글자(회관·가게·게스트하우스)를 해산물로 읽지 않는다")
	void 비건_오탐_안난다() {
		for (PlaceCandidateResponse.Candidate candidate : List.of(candidate("채식뷔페 연화", List.of()),
				candidate("비건버거 해운대", List.of()), candidate("사찰음식 전문점", List.of()),
				candidate("카페오뜨", List.of(subCategory("카페"))), candidate("젤라또조이", List.of(subCategory("아이스크림/빙수"))),
				candidate("회현 베이커리", List.of(subCategory("빵/도넛"))))) {
			assertThat(score(candidate, diet("VEGAN")).constraintVerdict()).as(candidate.nameKo())
					.isEqualTo(ConstraintVerdict.PASS);
		}
		// 근거가 없어 허용 목록에서 빠질 뿐, 해산물 집으로 읽혀 빠지는 것이 아니다.
		for (String name : List.of("해운대회관", "행복한 가게", "광안리 게스트하우스")) {
			assertThat(reasonOf(score(candidate(name, List.of()), diet("VEGAN")))).as(name).isEqualTo("NO_PLANT_EVIDENCE");
		}
	}

	@Test
	@DisplayName("S15P21E201-1873 — 식당은 채식 근거가 있어야 남는다. 고기가 안 보이는 국수집·경양식집이 새던 것")
	void 근거_없는_식당은_빠진다() {
		assertThat(reasonOf(score(candidate("해운대 국수", List.of()), diet("VEGETARIAN")))).isEqualTo("NO_PLANT_EVIDENCE");
		assertThat(reasonOf(score(candidate("라온", List.of(subCategory("경양식"))), diet("VEGETARIAN"))))
				.as("경양식은 업종만으로 고기 쪽").isEqualTo("MEAT_CENTRIC");
		assertThat(reasonOf(score(candidate("바다정", List.of(subCategory("횟집"))), diet("VEGETARIAN"))))
				.as("횟집은 업종만으로 해산물 쪽").isEqualTo("SEAFOOD_CENTRIC");
	}

	@Test
	@DisplayName("S15P21E201-1873 — 샐러드·포케·샌드위치 가게는 채식에 남는다. 해산물 이름이 붙은 포케는 빠진다")
	void 샐러드_포케_샌드위치는_남긴다() {
		for (PlaceCandidateResponse.Candidate candidate : List.of(candidate("서브웨이 서면점", List.of()),
				candidate("포케올데이 광안점", List.of()), candidate("샐러디 해운대점", List.of()),
				candidate("모어", List.of(subCategory("토스트/샌드위치/샐러드"))))) {
			assertThat(score(candidate, diet("VEGETARIAN")).constraintVerdict()).as(candidate.nameKo())
					.isEqualTo(ConstraintVerdict.PASS);
		}
		assertThat(reasonOf(score(candidate("연어포케 전문", List.of()), diet("VEGETARIAN")))).isEqualTo("SEAFOOD_CENTRIC");
	}

	@Test
	@DisplayName("S15P21E201-1873 — 한정식·백반은 이름이나 글에 근거가 있으면 남긴다. 고기 근거가 있으면 빠진다")
	void 한정식_백반은_남긴다() {
		for (PlaceCandidateResponse.Candidate candidate : List.of(
				candidate("백번집", List.of(subCategory("백반/한정식"), menu("된장찌개 정식 · 나물 반찬"))),
				candidate("청학 한정식", List.of()), candidate("시골쌈밥", List.of()))) {
			EngineCandidate result = score(candidate, diet("VEGETARIAN"));
			assertThat(result.constraintVerdict()).as(candidate.nameKo()).isEqualTo(ConstraintVerdict.PASS);
			assertThat(result.warningCodes()).as(candidate.nameKo()).contains("DIET_SUPPORT_UNVERIFIED");
		}
		assertThat(reasonOf(score(candidate("돼지백반", List.of(subCategory("백반/한정식"))), diet("VEGETARIAN"))))
				.isEqualTo("MEAT_CENTRIC");
		assertThat(reasonOf(score(candidate("기사식당", List.of(subCategory("백반/한정식"), menu("제육볶음 9,000원"))),
				diet("VEGETARIAN")))).isEqualTo("MEAT_CENTRIC");
	}

	@Test
	@DisplayName("S15P21E201-1873 — 죽집·맷돌 두부·집밥·도시락 가게는 백반 업종이어도 이름으로 남는다")
	void 죽_맷돌_집밥_도시락은_남긴다() {
		for (String name : List.of("본죽 부산용호점", "죽이야기", "거창맷돌", "집밥파는집", "한솥도시락 센텀중앙로점")) {
			assertThat(score(candidate(name, List.of(subCategory("백반/한정식"))), diet("VEGETARIAN")).constraintVerdict())
					.as(name).isEqualTo(ConstraintVerdict.PASS);
		}
		assertThat(reasonOf(score(candidate("전복죽 전문", List.of()), diet("VEGETARIAN")))).isEqualTo("SEAFOOD_CENTRIC");
	}

	@Test
	@DisplayName("S15P21E201-1873 — 방문 이유 글의 「백반집」「도시락」은 채식 근거가 아니다. 쭈꾸미 집과 시장 백반집이 남았다")
	void 글의_가게_종류_낱말은_근거가_아니다() {
		assertThat(reasonOf(score(candidate("양순식당", List.of(subCategory("백반/한정식"),
				whyVisit("새벽시장 상인과 인근 주민들이 찾는 생활형 백반집"))), diet("VEGETARIAN")))).isEqualTo("NO_PLANT_EVIDENCE");
		assertThat(reasonOf(score(candidate("열린밥집", List.of(subCategory("백반/한정식"),
				whyVisit("도시락 및 김밥 등 간편 식사"))), diet("VEGETARIAN")))).isEqualTo("NO_PLANT_EVIDENCE");
		assertThat(reasonOf(score(candidate("쭈야네", List.of(subCategory("백반/한정식"))), diet("VEGETARIAN"))))
				.isEqualTo("SEAFOOD_CENTRIC");
		// 샐러드바가 있어도 스테이크하우스 체인은 고기 쪽이다.
		assertThat(reasonOf(score(candidate("씨제이푸드빌빕스부산 서면점", List.of(subCategory("패밀리레스토랑"),
				whyVisit("다양한 샐러드바 메뉴"))), diet("VEGETARIAN")))).isEqualTo("MEAT_CENTRIC");
		// 음식을 말하는 글은 그대로 근거다.
		assertThat(score(candidate("미소가", List.of(subCategory("백반/한정식"), whyVisit("정갈한 나물 반찬과 된장찌개"))),
				diet("VEGETARIAN")).constraintVerdict()).isEqualTo(ConstraintVerdict.PASS);
	}

	@Test
	@DisplayName("S15P21E201-1873 — 「백반/한정식」 업종만으로는 채식에 안 남는다. 운영 채식 여행에 곰탕·오돌뼈 집과 술집이 들어갔다")
	void 백반_업종만으로는_안_남긴다() {
		for (String name : List.of("누구나오돌뼈구이", "서울육계장", "바로해장")) {
			assertThat(reasonOf(score(candidate(name, List.of(subCategory("백반/한정식"))), diet("VEGETARIAN")))).as(name)
					.isEqualTo("MEAT_CENTRIC");
		}
		for (String name : List.of("의령식당", "광안술잔", "파자마바")) {
			assertThat(reasonOf(score(candidate(name, List.of(subCategory("백반/한정식"))), diet("VEGETARIAN")))).as(name)
					.isEqualTo("NO_PLANT_EVIDENCE");
		}
	}

	@Test
	@DisplayName("🔴 운영 채식·비건 후보로 새던 이름이 빠진다 — 대패·돈·라멘·짬뽕·식육 (2026-09-29 실측)")
	void 이름으로_새던_고기집이_빠진다() {
		for (String name : List.of("팔팔대패", "돈반", "뚱돈", "사카나라멘", "총각짬뽕", "옛날짜장면", "태영식육식당",
				"뚱보김치찜", "야키토리온정", "툴스돈카츠", "송해와오리백숙", "Outback Steakhouse", "바모스타코 율하점",
				"구구닭촌 경성대점")) {
			for (String code : List.of("VEGETARIAN", "VEGAN")) {
				EngineCandidate result = score(candidate(name, List.of()), diet(code));
				assertThat(result.constraintVerdict()).as(code + " " + name).isEqualTo(ConstraintVerdict.FAIL);
				assertThat(result.violations()).as(name)
						.anySatisfy(v -> assertThat(v.get("reason")).isEqualTo("MEAT_CENTRIC"));
			}
		}
	}

	@Test
	@DisplayName("채식은 우동·소바·추어탕·석화 집도 뺀다 — 가다랑어 육수·해산물 중심")
	void 채식은_해산물_이름도_빠진다() {
		for (String name : List.of("우동토오루", "칸다소바", "참추어탕", "석화연", "해운대 곰장어")) {
			assertThat(score(candidate(name, List.of()), diet("VEGETARIAN")).constraintVerdict()).as(name)
					.isEqualTo(ConstraintVerdict.FAIL);
		}
	}

	@Test
	@DisplayName("🔴 이름이 고기를 말하지 않아도 대표 메뉴가 고기면 뺀다 — 근거가 MENU_PRICE_WON 으로 남는다")
	void 대표_메뉴가_고기면_빠진다() {
		EngineCandidate result = score(candidate("신흥관", List.of(menu("사천짜장 9,500원"))), diet("VEGETARIAN"));
		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.violations()).anySatisfy(v -> {
			assertThat(v.get("reason")).isEqualTo("MEAT_CENTRIC");
			assertThat(v.get("evidence")).isEqualTo("MENU_PRICE_WON");
		});
		assertThat(score(candidate("안목에프비", List.of(menu("목살 스테이크 덮밥"))), diet("VEGAN")).constraintVerdict())
				.isEqualTo(ConstraintVerdict.FAIL);
	}

	@Test
	@DisplayName("방문 이유 글이 해산물 집이라고 말하면 채식에서 뺀다")
	void 방문_이유가_해산물이면_채식에서_빠진다() {
		PlaceFeatureView why = whyVisit("제철 해산물로 차리는 맡김 코스");
		EngineCandidate vegetarian = score(candidate("해루질", List.of(why)), diet("VEGETARIAN"));
		assertThat(vegetarian.constraintVerdict()).isEqualTo(ConstraintVerdict.FAIL);
		assertThat(vegetarian.violations()).anySatisfy(v -> {
			assertThat(v.get("reason")).isEqualTo("SEAFOOD_CENTRIC");
			assertThat(v.get("evidence")).isEqualTo("WHY_VISIT");
		});
	}

	@Test
	@DisplayName("🔴 어느 글에든 채식·비건이 있으면 글 근거로는 빼지 않는다 — 추정으로 통과시키지도 않는다(경고)")
	void 글에_비건이_있으면_글로는_안뺀다() {
		EngineCandidate result = score(candidate("오늘샌드위치",
				List.of(menu("베이컨 에그 샌드위치"), whyVisit("비건 샌드위치도 따로 있다"))), diet("VEGAN"));
		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.PASS);
		assertThat(result.warningCodes()).contains("DIET_SUPPORT_UNVERIFIED");
	}

	@Test
	@DisplayName("인도·네팔 식당은 메뉴에 닭고기 커리가 있어도 글 근거로 빼지 않는다 — 채식 메뉴를 늘 따로 둔다")
	void 인도식당은_글로_안뺀다() {
		EngineCandidate result = score(candidate("펀자브인도요리", List.of(menu("치킨 티카 마살라"))),
				diet("VEGETARIAN"));
		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.PASS);
		assertThat(result.warningCodes()).contains("DIET_SUPPORT_UNVERIFIED");
	}

	@Test
	@DisplayName("오탐을 막는다 — 에스프레소바는 소바가 아니고, 「돈」은 식당 이름에서만 보고, 출처 주소는 안 읽는다")
	void 새_낱말의_오탐_안난다() {
		// 허용 목록 뒤로는 근거 없는 식당이 빠지므로, 오탐이 없다는 것은 «해산물·고기로 읽혀 빠지지 않는다» 로 본다.
		assertThat(reasonOf(score(candidate("오엘스에스프레소바", List.of()), diet("VEGAN")))).isEqualTo("NO_PLANT_EVIDENCE");
		assertThat(score(new PlaceCandidateResponse.Candidate(UUID.randomUUID(), "다대포 돈대", "CULTURE_TEMPLE", 35.1,
				129.0, 1000L, List.of()), diet("VEGAN")).constraintVerdict()).isEqualTo(ConstraintVerdict.PASS);
		PlaceFeatureView sourcedFromSushiBlog = feature("WHY_VISIT",
				"{\"reasons\":[{\"type\":\"맛 자체\",\"note\":\"직접 볶은 원두의 산미\"}],"
						+ "\"sources\":[\"https://sushi-and-seafood.example/busan\"]}");
		assertThat(reasonOf(score(candidate("원두공방", List.of(sourcedFromSushiBlog)), diet("VEGAN"))))
				.isEqualTo("NO_PLANT_EVIDENCE");
		// 글의 「자갈치」는 위치다 — 갈치가 아니다. 이름의 자갈치는 여전히 해산물 집으로 본다.
		assertThat(score(candidate("동네빵집", List.of(whyVisit("자갈치시장 앞에서 40년 된 식빵"), subCategory("빵/도넛"))),
				diet("VEGAN")).constraintVerdict()).isEqualTo(ConstraintVerdict.PASS);
		assertThat(score(candidate("일번지자갈치산곰장어", List.of()), diet("VEGAN")).constraintVerdict())
				.isEqualTo(ConstraintVerdict.FAIL);
	}

	private static String reasonOf(EngineCandidate result) {
		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.FAIL);
		return String.valueOf(result.violations().get(0).get("reason"));
	}

	private static PlaceFeatureView subCategory(String name) {
		return feature("BUSINESS_SUBCATEGORY", "{\"name\":\"" + name + "\"}");
	}

	private static PlaceCandidateResponse.Candidate place(String name, String category) {
		return new PlaceCandidateResponse.Candidate(UUID.randomUUID(), name, category, 35.1, 129.0, 1000L, List.of());
	}

	private static PlaceFeatureView menu(String menu) {
		return feature("MENU_PRICE_WON", "{\"priceWon\":9500,\"menu\":\"" + menu + "\"}");
	}

	private static PlaceFeatureView whyVisit(String note) {
		return feature("WHY_VISIT", "{\"reasons\":[{\"type\":\"맛 자체\",\"note\":\"" + note + "\"}]}");
	}

	private static PlaceFeatureView feature(String type, String json) {
		return new PlaceFeatureView(type, null, "ESTIMATED", new ObjectMapper().readTree(json), null, "FIXTURE");
	}

	private EngineCandidate score(PlaceCandidateResponse.Candidate candidate, TripConstraint constraint) {
		return this.scorer.score(candidate, null, constraint == null ? List.of() : List.of(constraint), 5000,
				new BaselineEngineProperties.Weights(0.30, 0.20, 0.15, 0.15, 0.10, 0.10),
				new PreferenceAlignmentWeights(null, null, null, null, null), List.of(), this.constraintCodeMap,
				List.of(), 0.05);
	}

	private static PlaceCandidateResponse.Candidate candidate(String name, List<PlaceFeatureView> features) {
		return new PlaceCandidateResponse.Candidate(UUID.randomUUID(), name, "FOOD", 35.1, 129.0, 1000L, features);
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
