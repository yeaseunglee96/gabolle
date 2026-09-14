package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	// ── 여행 답을 계정으로 이어받기 (S15P21E201-639) ──────────────────────────
	//
	// 🔴 아래 검사들이 지키는 것은 **갇히지 않는다** 이다.
	//
	//    처음에는 "계정에 비어 있을 때만 채운다" 로 만들었다. 명세 2.2 를 글자 그대로
	//    지키는 쪽이었는데, 계정 기본값을 고치는 화면이 없어서(소비 성향 하나뿐) 사용자가
	//    첫 답에 **영구히 갇혔다** — 화면의 값을 고쳐도 그 여행에만 적용되고 계정은 그대로라
	//    다음 여행에 또 옛 값이 채워진다. 「고친_값이_계정에도_간다」 가 그 되돌림을 지킨다.
	//
	//    막는 것은 그대로 둔다 — 건너뛴 것과 안 물어본 것은 계정을 안 건드린다.

	@Test
	@DisplayName("🔴 계정이 비어 있으면 여행에서 고른 답이 계정에 남는다 — 두 번째 여행부터 안 묻는 부분")
	void 빈칸이면_채운다() {
		List<String> changed = this.service.carryOver(USER, List.of(
				selected("QUIETNESS", "0.8"), selected("LOCALITY", "0.6")));

		assertThat(changed).containsExactlyInAnyOrder("QUIETNESS", "LOCALITY");
		assertThat(this.service.find(USER)).get()
				.satisfies((s) -> assertThat(s.answers()).hasSize(2));
	}

	@Test
	@DisplayName("🔴 화면에서 고친 값이 계정에도 간다 — 안 그러면 첫 답에 영구히 갇힌다")
	void 고친_값이_계정에도_간다() {
		this.service.carryOver(USER, List.of(selected("QUIETNESS", "0.8")));

		// 다음 여행: 화면에 0.8 이 채워져 보였고, 사용자가 0.1 로 고쳤다
		List<String> changed = this.service.carryOver(USER, List.of(selected("QUIETNESS", "0.1")));

		assertThat(changed).containsExactly("QUIETNESS");
		assertThat(this.service.find(USER)).get().satisfies((s) -> {
			// 🔴 0.8 이 그대로라면 사용자는 고칠 방법이 없다. 고치는 화면도 없다.
			assertThat(s.answers().get(0).valueJson()).isEqualTo("0.1");
			assertThat(s.version()).isEqualTo(2);
		});
	}

	@Test
	@DisplayName("🔴 값이 같으면 판을 새로 쓰지 않는다 — 화면이 채워진 값을 그대로 돌려보내기 때문")
	void 같은_값이면_판을_안_쓴다() {
		this.service.carryOver(USER, List.of(selected("QUIETNESS", "0.8")));

		// 다음 여행: 사용자가 아무것도 안 고쳤다. 화면은 채워진 0.8 을 그대로 보낸다
		List<String> changed = this.service.carryOver(USER, List.of(selected("QUIETNESS", "0.8")));

		assertThat(changed).isEmpty();
		// 판이 올랐다면 created_at 만 다른 판이 쌓여 "언제 정한 취향인가" 를 못 보게 된다.
		assertThat(this.service.find(USER)).get()
				.satisfies((s) -> assertThat(s.version()).isEqualTo(1));
	}

	@Test
	@DisplayName("🔴 이어받지 않기로 한 차원은 안 들어간다 — 카테고리·분위기·그늘·소비성향")
	void 이어받지_않는_차원은_뺀다() {
		List<String> changed = this.service.carryOver(USER, List.of(
				selected("CATEGORY", "\"SEA\""),          // 이번엔 바다, 다음엔 문화
				selected("ATMOSPHERE", "\"CALM\""),       // 여행 성격에 따라 다르다
				selected("SHADE_PREFERENCE", "0.9"),      // 9월엔 그늘, 12월엔 볕
				selected("SPEND_PROFILE", "\"MID\""),     // SpendProfileService 가 따로 맡는다
				selected("SLOPE_PREFERENCE", "0.2")));    // 몸에 붙은 것 — 이것만 이어받는다

		assertThat(changed).containsExactly("SLOPE_PREFERENCE");
	}

	@Test
	@DisplayName("🔴 건너뛴 답은 계정을 안 건드린다 — 「이번 여행만 이 조건 빼고」 가 살아남아야 한다")
	void 건너뛰면_계정은_그대로() {
		this.service.carryOver(USER, List.of(selected("QUIETNESS", "0.8")));

		// 다음 여행: 조용함을 **일부러 건너뛰었다**
		List<String> changed = this.service.carryOver(USER,
				List.of(new PreferenceAnswer("QUIETNESS", null, AnswerStatus.SKIPPED)));

		assertThat(changed).isEmpty();
		assertThat(this.service.find(USER)).get()
				.satisfies((s) -> assertThat(s.answers().get(0).valueJson()).isEqualTo("0.8"));
	}

	@Test
	@DisplayName("안 물어본 차원도 계정을 안 건드린다 — 사용자의 의사가 없다")
	void 안_물어봤으면_그대로() {
		List<String> changed = this.service.carryOver(USER,
				List.of(new PreferenceAnswer("LOCALITY", null, AnswerStatus.UNKNOWN)));

		assertThat(changed).isEmpty();
		assertThat(this.service.find(USER)).isEmpty();
	}

	@Test
	@DisplayName("이어받은 뒤에는 겹치기가 그 값을 다음 여행에 넣는다 — 두 걸음이 이어지는지")
	void 이어받은_뒤에_겹쳐진다() {
		this.service.carryOver(USER, List.of(selected("QUIETNESS", "0.8")));

		// 다음 여행: 조용함을 안 물어봤다(UNKNOWN)
		List<PreferenceAnswer> merged = this.service.overlayDefaults(USER,
				List.of(new PreferenceAnswer("QUIETNESS", null, AnswerStatus.UNKNOWN)));

		assertThat(merged).hasSize(1);
		assertThat(merged.get(0).valueJson()).isEqualTo("0.8");
		assertThat(merged.get(0).status()).isEqualTo(AnswerStatus.SELECTED);
	}

	// ── 온보딩·마이페이지가 직접 고치기 (S15P21E201-639) ──────────────────────
	//
	// 🔴 carryOver 와 갈리는 자리는 UNKNOWN 하나다. 여행에서 온 UNKNOWN 은 "안 물어봤다"
	//    라서 계정을 안 건드리고, 마이페이지에서 온 UNKNOWN 은 "잊어 달라" 라서 지운다.
	//    같은 값이 두 곳에서 다른 뜻이므로, 뭉개지 않았는지를 검사로 못 박는다.

	@Test
	@DisplayName("🔴 보낸 차원만 바뀌고 안 보낸 차원은 남는다 — 부분 갱신")
	void 보낸_것만_바뀐다() {
		this.service.putTaste(USER, List.of(selected("LOCALITY", "0.6"), selected("QUIETNESS", "0.8")));

		List<String> changed = this.service.putTaste(USER, List.of(selected("LOCALITY", "0.2")));

		assertThat(changed).containsExactly("LOCALITY");
		assertThat(this.service.findTaste(USER)).hasSize(2);
		assertThat(this.service.findTaste(USER))
				.anySatisfy((a) -> assertThat(a.valueJson()).isEqualTo("0.2"))
				.anySatisfy((a) -> assertThat(a.valueJson()).isEqualTo("0.8"));
	}

	@Test
	@DisplayName("🔴 UNKNOWN 으로 보내면 그 차원을 지운다 — 마이페이지의 「잊어 주세요」")
	void 지우기() {
		this.service.putTaste(USER, List.of(selected("LOCALITY", "0.6"), selected("QUIETNESS", "0.8")));

		List<String> changed = this.service.putTaste(USER,
				List.of(new PreferenceAnswer("LOCALITY", null, AnswerStatus.UNKNOWN)));

		assertThat(changed).containsExactly("LOCALITY");
		assertThat(this.service.findTaste(USER)).hasSize(1);
		assertThat(this.service.findTaste(USER).get(0).dimension()).isEqualTo("QUIETNESS");
	}

	@Test
	@DisplayName("🔴 SKIPPED 는 지우기가 아니다 — 「물어봤는데 안 답했다」라 계정을 안 건드린다")
	void 건너뜀은_지우기가_아니다() {
		this.service.putTaste(USER, List.of(selected("LOCALITY", "0.6")));

		List<String> changed = this.service.putTaste(USER,
				List.of(new PreferenceAnswer("LOCALITY", null, AnswerStatus.SKIPPED)));

		assertThat(changed).isEmpty();
		assertThat(this.service.findTaste(USER)).hasSize(1);
	}

	@Test
	@DisplayName("🔴 계정 기본값으로 둘 수 없는 차원은 거절한다 — 조용히 버리면 화면이 저장된 줄 안다")
	void 못_두는_차원은_거절() {
		// 여행마다 다른 것
		assertThatThrownBy(() -> this.service.putTaste(USER, List.of(selected("CATEGORY", "\"SEA\""))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CATEGORY");

		// 다른 경로(/spend)가 맡고 있는 것 — 두 경로가 같은 차원을 쓰면 나중 것이 앞을 덮는다
		assertThatThrownBy(() -> this.service.putTaste(USER, List.of(selected("SPEND_PROFILE", "\"MID\""))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("앱이 보내는 camelCase 이름도 받는다 — 화면은 locality 로 보낸다")
	void 앱_이름도_받는다() {
		List<String> changed = this.service.putTaste(USER, List.of(selected("locality", "0.6")));

		assertThat(changed).containsExactly("LOCALITY");
		// 되돌려 줄 때는 어휘로 맞춘다 — 화면이 어느 칸인지 헷갈리지 않게
		assertThat(this.service.findTaste(USER).get(0).dimension()).isEqualTo("LOCALITY");
	}

	@Test
	@DisplayName("🔴 findTaste 는 소비 성향을 빼고 준다 — /spend 화면과 겹치면 한쪽만 고쳤을 때 어긋난다")
	void 소비성향은_빼고_준다() {
		this.service.replace(USER, List.of(
				selected("QUIETNESS", "0.8"), selected("SPEND_PROFILE", "\"MID\"")));

		assertThat(this.service.findTaste(USER)).hasSize(1);
		assertThat(this.service.findTaste(USER).get(0).dimension()).isEqualTo("QUIETNESS");
		// 계정에서 지워진 것은 아니다 — 안 보여줄 뿐이다
		assertThat(this.service.find(USER)).get()
				.satisfies((s) -> assertThat(s.answers()).hasSize(2));
	}

	@Test
	@DisplayName("한 번도 저장한 적 없으면 빈 목록 — 오류가 아니다")
	void 없으면_빈_목록() {
		assertThat(this.service.findTaste(USER)).isEmpty();
	}

	private static PreferenceAnswer selected(String dimension, String value) {
		return new PreferenceAnswer(dimension, value, AnswerStatus.SELECTED);
	}
}
