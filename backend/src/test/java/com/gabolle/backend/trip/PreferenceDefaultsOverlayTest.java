package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.application.PreferenceDefaultsService;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.PreferenceSnapshot.AnswerStatus;
import com.gabolle.backend.trip.domain.PreferenceSnapshot.PreferenceAnswer;
import com.gabolle.backend.trip.infra.InMemoryTripRepository;

/**
 * 계정 기본 취향이 여행 답과 겹칠 때의 규칙 (S15P21E201-547).
 *
 * <p>🔴 이 규칙의 핵심은 {@code SKIPPED} 와 {@code UNKNOWN} 을 <b>다르게</b> 다루는
 * 것이고, 그 차이는 코드를 읽어서는 맞는지 알 수 없다. 그래서 표의 네 줄을 그대로
 * 검사한다 — {@code PreferenceDefaultsService} javadoc 의 표가 이 테스트와 같은 것을
 * 말해야 한다.
 *
 * <p>🔴 DB 를 쓰지 않는다({@link InMemoryTripRepository}). 이 저장소의 Postgres 테스트는
 * Docker 가 없으면 <b>실패가 아니라 건너뜀</b>이라, 검사가 실제로 돌았는지를 알 수 없는
 * 자리에 이 규칙을 두지 않았다.
 */
class PreferenceDefaultsOverlayTest {

	private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");

	private static final String USER = "11111111-1111-1111-1111-111111111111";

	private InMemoryTripRepository repository;

	private PreferenceDefaultsService service;

	@BeforeEach
	void setUp() {
		this.repository = new InMemoryTripRepository();
		this.service = new PreferenceDefaultsService(this.repository, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("계정 기본값이 없으면 여행 답이 그대로 나온다 — 지금까지의 동작")
	void 기본값이_없으면_그대로() {
		List<PreferenceAnswer> tripAnswers = List.of(selected("QUIETNESS", "0.8"));

		List<PreferenceAnswer> merged = this.service.overlayDefaults(USER, tripAnswers);

		assertThat(merged).isEqualTo(tripAnswers);
	}

	@Test
	@DisplayName("🔴 안 물어본 차원(UNKNOWN)은 계정 기본값으로 채워진다 — 20문항을 다시 앉히지 않는 부분")
	void 안_물어본_차원은_채워진다() {
		this.service.replace(USER, List.of(selected("QUIETNESS", "0.9")));

		List<PreferenceAnswer> merged = this.service.overlayDefaults(USER,
				List.of(new PreferenceAnswer("QUIETNESS", null, AnswerStatus.UNKNOWN)));

		assertThat(merged).hasSize(1);
		assertThat(merged.get(0).status()).isEqualTo(AnswerStatus.SELECTED);
		assertThat(merged.get(0).valueJson()).isEqualTo("0.9");
	}

	@Test
	@DisplayName("🔴 일부러 건너뛴 차원(SKIPPED)은 계정 기본값으로 되살아나지 않는다")
	void 건너뛴_차원은_되살아나지_않는다() {
		this.service.replace(USER, List.of(selected("QUIETNESS", "0.9")));

		List<PreferenceAnswer> merged = this.service.overlayDefaults(USER,
				List.of(new PreferenceAnswer("QUIETNESS", null, AnswerStatus.SKIPPED)));

		// "이번 여행만 이 조건 빼고" 가 기본값에 덮이면 사용자는 그 이유를 알 수 없다.
		assertThat(merged).hasSize(1);
		assertThat(merged.get(0).status()).isEqualTo(AnswerStatus.SKIPPED);
		assertThat(merged.get(0).valueJson()).isNull();
	}

	@Test
	@DisplayName("여행에서 답한 값(SELECTED)은 계정 기본값이 못 이긴다")
	void 여행_답이_이긴다() {
		this.service.replace(USER, List.of(selected("QUIETNESS", "0.9")));

		List<PreferenceAnswer> merged = this.service.overlayDefaults(USER,
				List.of(selected("QUIETNESS", "0.1")));

		assertThat(merged).hasSize(1);
		assertThat(merged.get(0).valueJson()).isEqualTo("0.1");
	}

	@Test
	@DisplayName("여행에 아예 없는 차원은 계정 기본값이 더해진다")
	void 없는_차원은_더해진다() {
		this.service.replace(USER, List.of(selected("SHADE_PREFERENCE", "0.7")));

		List<PreferenceAnswer> merged = this.service.overlayDefaults(USER,
				List.of(selected("QUIETNESS", "0.1")));

		assertThat(merged).hasSize(2);
		assertThat(merged).anySatisfy((a) -> {
			assertThat(a.dimension()).isEqualTo("SHADE_PREFERENCE");
			assertThat(a.valueJson()).isEqualTo("0.7");
		});
	}

	@Test
	@DisplayName("계정 기본값 쪽의 SKIPPED·UNKNOWN 은 채울 값이 없어 아무 일도 하지 않는다")
	void 기본값의_빈_답은_아무것도_채우지_않는다() {
		this.repository.saveUserDefaults(USER,
				List.of(new PreferenceAnswer("QUIETNESS", null, AnswerStatus.SKIPPED)), NOW);

		List<PreferenceAnswer> tripAnswers =
				List.of(new PreferenceAnswer("QUIETNESS", null, AnswerStatus.UNKNOWN));
		List<PreferenceAnswer> merged = this.service.overlayDefaults(USER, tripAnswers);

		assertThat(merged.get(0).status()).isEqualTo(AnswerStatus.UNKNOWN);
	}

	@Test
	@DisplayName("🔴 차원 이름의 대소문자가 달라도 같은 차원으로 본다 — 아니면 기본값이 여행 답을 덮는다")
	void 대소문자가_달라도_같은_차원이다() {
		this.service.replace(USER, List.of(selected("QUIETNESS", "0.9")));

		List<PreferenceAnswer> merged = this.service.overlayDefaults(USER,
				List.of(selected("quietness", "0.1")));

		// 둘로 보이면 같은 차원이 두 줄이 되고, 추천이 어느 값을 쓸지 알 수 없게 된다.
		assertThat(merged).hasSize(1);
		assertThat(merged.get(0).valueJson()).isEqualTo("0.1");
	}

	@Test
	@DisplayName("판을 바꾸면 판 번호가 오르고 앞의 판은 남는다 — 불변이다")
	void 판을_바꾸면_번호가_오른다() {
		PreferenceSnapshot first = this.service.replace(USER, List.of(selected("QUIETNESS", "0.1")));
		PreferenceSnapshot second = this.service.replace(USER, List.of(selected("QUIETNESS", "0.9")));

		assertThat(first.version()).isEqualTo(1);
		assertThat(second.version()).isEqualTo(2);
		assertThat(this.service.find(USER)).get()
				.satisfies((s) -> assertThat(s.version()).isEqualTo(2));
	}

	@Test
	@DisplayName("계정 기본값은 scope=USER 이고 tripId 가 없다 — 스키마가 그것을 요구한다")
	void 계정_기본값은_여행이_없다() {
		PreferenceSnapshot saved = this.service.replace(USER, List.of(selected("QUIETNESS", "0.5")));

		// ck_preference_snapshot_scope_trip : (scope = 'TRIP') = (trip_id IS NOT NULL)
		assertThat(saved.scope()).isEqualTo(PersonalizationScope.USER);
		assertThat(saved.tripId()).isNull();
	}

	private static PreferenceAnswer selected(String dimension, String value) {
		return new PreferenceAnswer(dimension, value, AnswerStatus.SELECTED);
	}
}
