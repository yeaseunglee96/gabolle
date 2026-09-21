package com.gabolle.backend.tripnaming;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.tripnaming.application.PlaceWordGuard;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지어낸 장소 이름이 여행 제목에 박히는 것을 막는다. 이 검사가 있어야 모델이 실패해도
 * 거짓말이 화면에 안 나온다.
 */
class PlaceWordGuardTest {

	/** 이 여행의 일정에 실제로 들어 있는 장소들. */
	private static final List<String> ITINERARY = List.of(
			"해운대 해수욕장", "광안리 해수욕장", "감천문화마을", "자갈치시장");

	@Nested
	@DisplayName("🔴 지어낸 장소는 버린다")
	class RejectsInventedPlaces {

		/**
		 * 조사가 붙어도 걸려야 한다. 꼬리말을 끝에서만 찾으면 「불국사」는 걸리고
		 * 「불국사에서」는 지나간다.
		 */
		@Test
		@DisplayName("🔴 일정에 없는 절 이름은 조사가 붙어도 버린다")
		void rejectsInventedTemple() {
			assertThat(PlaceWordGuard.isTruthful("불국사 하루", ITINERARY)).isFalse();
			assertThat(PlaceWordGuard.isTruthful("불국사에서 보낸 이틀", ITINERARY)).isFalse();
		}

		@Test
		@DisplayName("일정에 없는 다리 이름이 들어오면 버린다")
		void rejectsInventedBridge() {
			assertThat(PlaceWordGuard.isTruthful("광안대교 야경 이틀", ITINERARY)).isFalse();
		}

		/** 꼬리말이 없어 위 규칙에 안 걸리는 종류다. 지역 이름은 따로 본다. */
		@Test
		@DisplayName("다른 지역 이름이 들어오면 버린다")
		void rejectsOtherRegion() {
			assertThat(PlaceWordGuard.isTruthful("제주 바다 이틀", ITINERARY)).isFalse();
			assertThat(PlaceWordGuard.isTruthful("경주 나들이", ITINERARY)).isFalse();
		}

		@Test
		@DisplayName("일정에 없는 시장 이름이 들어오면 버린다")
		void rejectsInventedMarket() {
			assertThat(PlaceWordGuard.isTruthful("국제시장 한 바퀴", ITINERARY)).isFalse();
		}
	}

	@Nested
	@DisplayName("멀쩡한 이름은 통과시킨다")
	class AcceptsHonestNames {

		/**
		 * 「일정에 있는 낱말만 써라」를 곧이곧대로 하지 않은 이유다. 조사와 보통 명사는 전부
		 * 장소 목록 밖이지만 거짓이 아니다.
		 */
		@Test
		@DisplayName("🔴 조사와 평범한 말이 붙어도 통과한다")
		void acceptsParticlesAndOrdinaryWords() {
			assertThat(PlaceWordGuard.isTruthful("해운대에서 보낸 이틀", ITINERARY)).isTrue();
			assertThat(PlaceWordGuard.isTruthful("혼자 걷는 광안리", ITINERARY)).isTrue();
			assertThat(PlaceWordGuard.isTruthful("자갈치시장 아침", ITINERARY)).isTrue();
		}

		/** 부산은 어느 여행에서도 참이다 — 이 서비스는 부산 여행만 다룬다. */
		@Test
		@DisplayName("🔴 부산은 막지 않는다")
		void busanIsAlwaysTrue() {
			assertThat(PlaceWordGuard.isTruthful("9월 부산 혼자", ITINERARY)).isTrue();
		}

		@Test
		@DisplayName("일정에 있는 장소는 통째로도 통과한다")
		void acceptsPlacesFromTheItinerary() {
			assertThat(PlaceWordGuard.isTruthful("감천문화마을 산책", ITINERARY)).isTrue();
			assertThat(PlaceWordGuard.isTruthful("해운대 해수욕장 이틀", ITINERARY)).isTrue();
		}

		@Test
		@DisplayName("장소가 하나도 안 나오는 이름도 거짓은 아니다")
		void nameWithoutAnyPlaceIsFine() {
			assertThat(PlaceWordGuard.isTruthful("천천히 걸은 이틀", ITINERARY)).isTrue();
		}
	}

	@Nested
	@DisplayName("틀리는 방향")
	class WhichWayItErrs {

		/**
		 * 「사계절」은 장소가 아닌데 「절」로 끝나서 같이 걸린다. 일부러 이렇게 뒀다 —
		 * 후보 하나를 잃는 것은 물러설 곳이 있지만, 지어낸 장소를 한 번 통과시키면 거짓이
		 * 화면에 박힌다.
		 */
		@Test
		@DisplayName("🔴 장소가 아닌 말도 같이 걸릴 수 있다 — 싼 쪽으로 틀리게 뒀다")
		void errsTowardsRejecting() {
			assertThat(PlaceWordGuard.isTruthful("사계절 바다", ITINERARY)).isFalse();
		}

		/** 한 글자 꼬리말이 평범한 두 글자 말을 잡아먹지 않는지. */
		@Test
		@DisplayName("두 글자 평범한 말은 한 글자 꼬리말로 걸리지 않는다")
		void shortOrdinaryWordsSurvive() {
			assertThat(PlaceWordGuard.isTruthful("혼자 간 이틀", ITINERARY)).isTrue();
			assertThat(PlaceWordGuard.isTruthful("우리 둘의 기록", ITINERARY)).isTrue();
		}

		@Test
		@DisplayName("빈 이름은 통과시키지 않는다")
		void blankIsNotAName() {
			assertThat(PlaceWordGuard.isTruthful("", ITINERARY)).isFalse();
			assertThat(PlaceWordGuard.isTruthful(null, ITINERARY)).isFalse();
		}

		/**
		 * 일정이 비어 있으면 기댈 곳이 없으므로 장소처럼 생긴 말은 전부 지어낸 것이다.
		 * 부르는 쪽이 템플릿으로 물러서므로 여기까지 올 일은 없지만, 와도 통과시키지 않는다.
		 */
		@Test
		@DisplayName("🔴 일정이 비어 있으면 장소처럼 생긴 말을 전부 버린다")
		void emptyItineraryTrustsNothing() {
			assertThat(PlaceWordGuard.isTruthful("해운대 해수욕장 이틀", List.of())).isFalse();
			assertThat(PlaceWordGuard.isTruthful("천천히 걸은 이틀", List.of())).isTrue();
		}
	}
}
