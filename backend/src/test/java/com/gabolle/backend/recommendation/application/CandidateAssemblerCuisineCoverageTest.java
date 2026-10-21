package com.gabolle.backend.recommendation.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.recommendation.adapter.EngineCandidate;
import com.gabolle.backend.recommendation.adapter.SeedBoost;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고른 음식 취향마다 «반환 창(상위 topK)» 안에 그 음식 후보가 하나는 있게 한다 — S15P21E201-2061.
 *
 * <p>일정 조립은 창 안의 후보로만 날과 끼니 칸을 채운다. 돼지국밥을 골랐는데 돼지국밥 후보가 전부 창 밖이면 조립이 아무리 애써도
 * 일정에 돼지국밥집을 못 넣는다. 도커 없이 도는 단위 시험이다 — 후보를 직접 만든다.
 */
class CandidateAssemblerCuisineCoverageTest {

	private final Map<UUID, String> names = new java.util.HashMap<>();

	/**
	 * @param matched 이 후보가 가진, 사용자가 고른 음식. 채점기가 {@code cuisine} 점수 항에 남기는 {@code matched} 와 같은 모양이다.
	 *     비어 있으면 그 항 자체가 «고른 것 없음» 모양이다
	 */
	private EngineCandidate candidate(String name, String category, double score, List<String> matched, List<String> reasons) {
		UUID id = UUID.randomUUID();
		this.names.put(id, name);
		Map<String, Object> components = matched.isEmpty() ? Map.of()
				: Map.of("cuisine", Map.of("weight", 0.1, "value", 1.0, "matched", matched));
		return new EngineCandidate(id, "TEST", ConstraintVerdict.PASS, List.of(), List.of(), 1.0,
				Map.of("category", category), components, score, reasons, List.of());
	}

	private EngineCandidate food(String name, double score, String... matched) {
		return candidate(name, "FOOD", score, List.of(matched), List.of());
	}

	private EngineCandidate sight(String name, double score) {
		return candidate(name, "CULTURE_TEMPLE", score, List.of(), List.of());
	}

	private List<String> namesOf(List<EngineCandidate> list) {
		return list.stream().map((c) -> this.names.get(c.placeId())).toList();
	}

	@Test
	@DisplayName("고른 음식이 없으면(어느 후보에도 matched 가 없으면) 순서를 그대로 돌려준다")
	void nothingChosenChangesNothing() {
		List<EngineCandidate> ordered = List.of(food("a", 0.9), food("b", 0.8), sight("s", 0.7), food("c", 0.6));

		assertThat(CandidateAssembler.withCuisineCoverage(ordered, 2)).isSameAs(ordered);
	}

	@Test
	@DisplayName("창이 곧 전부면(후보 수 <= topK) 올릴 후보가 없어 그대로다")
	void windowIsEverythingChangesNothing() {
		List<EngineCandidate> ordered = List.of(food("a", 0.9), food("pork", 0.8, "PORK_SOUP"));

		assertThat(CandidateAssembler.withCuisineCoverage(ordered, 5)).isSameAs(ordered);
	}

	@Test
	@DisplayName("창 안에 이미 그 음식이 있으면 건드리지 않는다")
	void alreadyCoveredInsideTheWindowChangesNothing() {
		List<EngineCandidate> ordered = List.of(food("pork", 0.9, "PORK_SOUP"), food("a", 0.8), sight("s", 0.7), food("pork2", 0.6, "PORK_SOUP"));

		assertThat(namesOf(CandidateAssembler.withCuisineCoverage(ordered, 3))).containsExactly("pork", "a", "s", "pork2");
	}

	@Test
	@DisplayName("🔴 창 안에 없고 밖에만 있으면 밖의 가장 높은 순위 후보를 올린다 — 창 안 가장 낮은 «같은 갈래·고른 음식 아님» 자리와 맞바꾼다")
	void outsideCandidateIsSwappedIntoTheLowestSameCategorySlot() {
		// 창(상위 4): a(밥) b(밥) s1(명소) c(밥) / 밖: s2 pork(돼지국밥) pork2
		List<EngineCandidate> ordered = List.of(
				food("a", 0.9), food("b", 0.8), sight("s1", 0.7), food("c", 0.6),
				sight("s2", 0.5), food("pork", 0.4, "PORK_SOUP"), food("pork2", 0.3, "PORK_SOUP"));

		List<EngineCandidate> result = CandidateAssembler.withCuisineCoverage(ordered, 4);

		// 창 안 «같은 갈래(밥집)이고 고른 음식이 아닌» 가장 낮은 순위는 c(4위). 거기에 pork 가 들어가고 c 는 pork 가 있던 6위로 간다.
		assertThat(namesOf(result)).containsExactly("a", "b", "s1", "pork", "s2", "c", "pork2");
	}

	@Test
	@DisplayName("꼭 갈 곳(MUST_VISIT_PLACE)은 맞바꾸지 않는다 — 그 위의 밥집이 자리를 내준다")
	void mustVisitIsNeverSwappedOut() {
		EngineCandidate mustVisit = candidate("must", "FOOD", 0.6, List.of(), List.of(SeedBoost.REASON_CODE_MUST_VISIT));
		List<EngineCandidate> ordered = List.of(food("a", 0.9), food("b", 0.8), mustVisit, sight("s2", 0.5), food("pork", 0.4, "PORK_SOUP"));

		List<EngineCandidate> result = CandidateAssembler.withCuisineCoverage(ordered, 3);

		assertThat(namesOf(result)).containsExactly("a", "pork", "must", "s2", "b");
	}

	@Test
	@DisplayName("창 안의 다른 고른 음식 후보는 맞바꾸지 않는다 — 그 음식이 빠진다")
	void aCandidateCoveringAnotherChosenCuisineStays() {
		// 창(상위 2): pork(돼지국밥) milm(밀면) / 밖: seafood(해산물). 해산물을 올리려 해도 창의 둘 다 고른 음식이라 맞바꿀 자리가 없다.
		List<EngineCandidate> ordered = List.of(food("pork", 0.9, "PORK_SOUP"), food("milm", 0.8, "MILMYEON"), food("seafood", 0.7, "SEAFOOD"));

		assertThat(namesOf(CandidateAssembler.withCuisineCoverage(ordered, 2))).containsExactly("pork", "milm", "seafood");
	}

	@Test
	@DisplayName("같은 갈래가 창에 없으면 갈래가 다른 곳과는 맞바꾸지 않는다 — 날의 구성(끼니·명소)이 안 바뀐다")
	void neverSwapsAcrossCategories() {
		// 창(상위 2)이 전부 명소다. 밖의 돼지국밥집(밥집)이 명소 자리를 빼앗지 않는다.
		List<EngineCandidate> ordered = List.of(sight("s1", 0.9), sight("s2", 0.8), food("pork", 0.7, "PORK_SOUP"));

		assertThat(namesOf(CandidateAssembler.withCuisineCoverage(ordered, 2))).containsExactly("s1", "s2", "pork");
	}

	@Test
	@DisplayName("고른 음식이 둘 다 창 밖이면 둘 다 올린다 — 서로 다른 자리로")
	void bothMissingCuisinesAreBroughtIn() {
		List<EngineCandidate> ordered = List.of(
				food("a", 0.9), food("b", 0.8), food("c", 0.7), food("d", 0.6),
				food("pork", 0.5, "PORK_SOUP"), food("milm", 0.4, "MILMYEON"));

		List<EngineCandidate> result = new ArrayList<>(CandidateAssembler.withCuisineCoverage(ordered, 4));

		assertThat(namesOf(result).subList(0, 4)).containsExactly("a", "b", "milm", "pork");
		assertThat(namesOf(result).subList(4, 6)).containsExactlyInAnyOrder("c", "d");
	}

	@Test
	@DisplayName("후보 수와 순서 말고는 아무것도 안 바뀐다 — 같은 후보 객체가 그대로 들어 있다")
	void candidatesThemselvesAreUntouched() {
		EngineCandidate a = food("a", 0.9);
		EngineCandidate b = food("b", 0.8);
		EngineCandidate pork = food("pork", 0.7, "PORK_SOUP");

		List<EngineCandidate> result = CandidateAssembler.withCuisineCoverage(List.of(a, b, pork), 2);

		assertThat(result).containsExactlyInAnyOrder(a, b, pork);
		assertThat(result.get(0)).isSameAs(a);
		assertThat(result.get(1)).isSameAs(pork);
	}
}
