package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.domain.Trip;

/**
 * 일정 이름에 한국어가 섞이지 않는지, 그리고 사용자가 붙인 이름을 버리지 않는지 본다.
 *
 * <p>영어 화면에서도 이름이 「2026-09-20 ~ 2026-09-22 여행 일정」으로 나오던 자리다. 이 이름은
 * 일정 화면 제목·여행권·이름 바꾸기 기본값·공유받은 사람 화면까지 그대로 간다.
 */
class TripDisplayTitleTest {

	private static Trip trip(String title) {
		Trip built = Trip.builder()
				.tripId("trip_1").createdBy("usr_1")
				.startDate(LocalDate.of(2026, 9, 20)).finishDate(LocalDate.of(2026, 9, 22))
				.partySize(2).timezone("Asia/Seoul")
				.createdAt(Instant.parse("2026-09-19T00:00:00Z"))
				.build();
		if (title != null) {
			built.rename(title, Instant.parse("2026-09-20T00:00:00Z"));
		}
		return built;
	}

	@Test
	@DisplayName("🔴 이름이 없으면 기간만 낸다 — 어느 나라 말도 섞지 않는다")
	void fallsBackToTheDateRangeWithNoWords() {
		String title = trip(null).displayTitle();

		assertThat(title).isEqualTo("2026-09-20 ~ 2026-09-22");
		// 한글이 한 글자라도 있으면 영어·일본어 사용자 화면에 그대로 나간다.
		assertThat(title.codePoints().anyMatch(code -> code >= 0xAC00 && code <= 0xD7A3)).isFalse();
	}

	@Test
	@DisplayName("🔴 사용자가 붙인 이름을 쓴다 — 예전에는 이 값을 아예 안 봤다")
	void prefersTheNameTheUserGave() {
		assertThat(trip("Busan food trip").displayTitle()).isEqualTo("Busan food trip");
	}

	@Test
	@DisplayName("이름을 지우면 다시 기간으로 돌아간다 — 빈 이름이 곧 지우기다")
	void clearingTheNameGoesBackToTheDateRange() {
		Trip subject = trip("한글 이름");
		subject.rename("   ", Instant.parse("2026-09-20T01:00:00Z"));

		assertThat(subject.displayTitle()).isEqualTo("2026-09-20 ~ 2026-09-22");
	}

	@Test
	@DisplayName("이 시험이 실제로 무언가를 가른다 — 전부 같은 값이 아니다")
	void theRuleActuallySeparates() {
		assertThat(trip("Busan food trip").displayTitle())
				.isNotEqualTo(trip(null).displayTitle());
	}
}
