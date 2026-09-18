package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.domain.InterestTagCode;

/**
 * 관광공사 분류 → 로컬 탐색 갈래 — S15P21E201-474.
 *
 * <p>이 판정이 틀리면 화면의 여덟 줄 중 하나가 조용히 비거나 엉뚱한 것으로 찬다. 응답은
 * 200 이고 아무것도 빨개지지 않는다.
 *
 * <p>코드값은 2026-09-11 수집본에서 실제로 본 것만 쓴다.
 */
class TourApiExploreFacetTest {

	@Test
	@DisplayName("행사 유형은 축제다 — 해운대 빛축제·옥토버페스타가 이 유형이다")
	void festivalComesFromContentType() {
		assertThat(TourApiExploreFacet.of("15", "A02", "A02070200")).containsExactly("FESTIVAL");
	}

	@Test
	@DisplayName("걷는 길이 레포츠보다 먼저다 — 문탠로드가 실탄사격장과 같은 갈래가 되면 안 된다")
	void trailWinsOverSports() {
		assertThat(TourApiExploreFacet.of("28", "A03", "A03022700")).containsExactly("WALK");
		assertThat(TourApiExploreFacet.of("28", "A03", "A03021600")).containsExactly("ACTIVITY");
	}

	@Test
	@DisplayName("자연 아래 해안 산책로도 산책이다 — 절영해안산책로가 여기다")
	void coastWalkIsAlsoWalk() {
		assertThat(TourApiExploreFacet.of("12", "A01", "A01010500")).containsExactly("WALK");
		assertThat(TourApiExploreFacet.of("12", "A01", "A01010400")).containsExactly("NATURE");
	}

	@Test
	@DisplayName("5일장과 상설시장이 전통시장이다 — 구포시장·국제시장이 각각 그 코드다")
	void bothMarketCodesAreTraditionalMarket() {
		assertThat(TourApiExploreFacet.of("38", "A04", "A04010100")).containsExactly("TRADITIONAL_MARKET");
		assertThat(TourApiExploreFacet.of("38", "A04", "A04010200")).containsExactly("TRADITIONAL_MARKET");
	}

	@Test
	@DisplayName("전망대를 야경으로 옮긴다 — 황령산 전망대가 이 코드다")
	void observatoryBecomesNightView() {
		assertThat(TourApiExploreFacet.of("12", "A02", "A02050600")).containsExactly("NIGHT_VIEW");
	}

	@Test
	@DisplayName("백화점과 지하도상가는 어느 갈래도 아니다 — 쇼핑이라고 다 시장이 아니다")
	void otherShoppingGetsNothing() {
		assertThat(TourApiExploreFacet.of("38", "A04", "A04010300")).isEmpty();
		assertThat(TourApiExploreFacet.of("38", "A04", "A04010600")).isEmpty();
	}

	@Test
	@DisplayName("음식과 숙박은 비운다 — 식당은 탐색 여덟 갈래 어디에도 안 든다")
	void foodAndLodgingGetNothing() {
		assertThat(TourApiExploreFacet.of("39", "A05", "A05020100")).isEmpty();
		assertThat(TourApiExploreFacet.of("32", "B02", "B02010100")).isEmpty();
	}

	@Test
	@DisplayName("모르는 값이 와도 지어내지 않는다")
	void unknownInputGivesNothing() {
		assertThat(TourApiExploreFacet.of(null, null, null)).isEmpty();
		assertThat(TourApiExploreFacet.of("", "C01", "C0101")).isEmpty();
	}

	@Test
	@DisplayName("탐색 갈래는 설문이 쓰는 낱말과 하나도 안 겹친다 — 표식을 더해도 추천 점수가 안 움직인다")
	void exploreFacetsNeverCollideWithSurveyCodes() {
		// 점수는 matched.size() / userCodes.size() 로 구한다(BaselineCandidateScorer.applyTagComponent).
		// 분모가 사용자가 고른 낱말 수라, 장소에 표식이 늘어도 그중 하나가 사용자의 낱말과
		// 같지 않으면 점수가 안 변한다. 그 "같지 않다" 를 여기서 못 박는다 — 티켓의
		// 완료 기준이 "표식을 붙이기 전과 후에 추천 결과가 달라지지 않았다" 다.
		List<String> surveyCodes = List.of(
				"SEA_BEACH", "CITY", "CAFE_HEALING", "CULTURE_TEMPLE", "FOOD", "NATURE_WALK");

		assertThat(InterestTagCode.displayOrder().stream().map(Enum::name).toList())
				.as("탐색 갈래가 설문 낱말과 겹치면 그 갈래에 표식을 붙이는 순간 추천이 조용히 달라진다")
				.doesNotContainAnyElementsOf(surveyCodes);
	}

	@Test
	@DisplayName("내는 값은 전부 화면이 아는 여덟 갈래 안에 있다 — 하나만 벗어나도 그 줄이 조용히 0건이 된다")
	void everyProducedCodeIsOneTheScreenKnows() {
		List<String> known = InterestTagCode.displayOrder().stream().map(Enum::name).toList();
		String[] contentTypes = { "12", "14", "15", "28", "32", "38", "39", null };
		String[] cat1s = { "A01", "A02", "A03", "A04", "A05", "B02", null };
		String[] cat3s = { "A03022700", "A01010500", "A01010400", "A04010100", "A04010200",
				"A04010300", "A02050600", "A02070200", null };

		for (String contentType : contentTypes) {
			for (String cat1 : cat1s) {
				for (String cat3 : cat3s) {
					assertThat(TourApiExploreFacet.of(contentType, cat1, cat3))
							.as("화면이 모르는 코드다: type=%s cat1=%s cat3=%s", contentType, cat1, cat3)
							.allMatch(known::contains);
				}
			}
		}
		assertThat(Arrays.asList("NIGHT_MARKET", "SOUVENIR_SHOP"))
				.as("이 둘은 원천에 신호가 없어 아직 아무 데서도 안 나온다")
				.isNotEmpty();
	}
}
