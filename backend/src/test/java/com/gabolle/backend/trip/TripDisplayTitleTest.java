package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.domain.Trip;

/**
 * 일정 이름이 언제나 한국어이던 것 — S15P21E201-1348.
 *
 * <p>🔴 2026-09-20 실기(SM-G973N, versionCode 23, <b>영어</b>)에서 일정을 만들었더니 이름이
 * 이렇게 나왔다.
 *
 * <pre>
 *   2026-09-20 ~ 2026-09-22 여행 일정
 * </pre>
 *
 * 이 이름은 일정 화면 제목, 여행권, 이름 바꾸기 창의 기본값, 그리고 공유 링크로 받은 사람
 * 화면에까지 그대로 나온다. 이 앱을 쓰는 사람은 <b>한글을 못 읽어서</b> 쓰는데, 여행이 둘
 * 이상이면 목록에서 어느 것이 무슨 여행인지 고를 수가 없다.
 *
 * <p>그리고 같은 자리에서 {@code title} 을 <b>아예 안 보고 있었다</b> — 이름을 바꿔도 그
 * 두 화면에는 지어낸 이름이 그대로 나왔다.
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
