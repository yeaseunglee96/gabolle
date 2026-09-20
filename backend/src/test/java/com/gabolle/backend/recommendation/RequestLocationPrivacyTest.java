package com.gabolle.backend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.common.privacy.SensitivePayloadGuard;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.recommendation.adapter.BaselineCandidateScorer;
import com.gabolle.backend.recommendation.adapter.EngineCandidate;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.recommendation.domain.CoarseArea;
import com.gabolle.backend.recommendation.domain.DistanceBucket;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.domain.RequestLocation;
import com.gabolle.backend.recommendation.domain.RequestLocation.LocationSource;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정확 좌표가 어디에도 남지 않는다. 채점기를 정밀 좌표로 돌린 뒤 저장되는 것들을 훑어
 * 좌표가 없는 것을 보고, 좌표를 담는 칸 자체가 없다는 것도 함께 본다 — 주기적으로 지우는
 * 규칙은 언젠가 안 지켜지지만 없는 칸은 안 지켜질 수 없다.
 *
 * DB 를 쓰지 않는다. 이 저장소의 Postgres 테스트는 Docker 가 없으면 실패가 아니라
 * 건너뜀이라, 개인정보 검사를 그런 자리에 두지 않았다.
 */
class RequestLocationPrivacyTest {

	/** 부산 어딘가의 정밀 좌표. 소수점 일곱 자리 — 실제 GPS 가 주는 정밀도다. */
	private static final double PRECISE_LAT = 35.1796432;

	private static final double PRECISE_LNG = 129.0756123;

	/** {@code 35.1796, 129.0756} 처럼 소수점 넷 자리 이상인 좌표 쌍. */
	private static final Pattern COORDINATE_PAIR = Pattern
			.compile("-?\\d{1,3}\\.\\d{4,}\\s*,\\s*-?\\d{1,3}\\.\\d{4,}");

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	private final BaselineCandidateScorer scorer = new BaselineCandidateScorer(this.objectMapper);

	private final SensitivePayloadGuard guard = new SensitivePayloadGuard();

	// ── 완료 기준 2 · 3 ───────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 채점 결과에 정밀 좌표가 없다 — 거리와 굵은 칸만 남는다")
	void 정밀_좌표가_저장물에_없다() {
		EngineCandidate scored = score(PRECISE_LAT, PRECISE_LNG, 1_234L);

		String featureJson = this.objectMapper.writeValueAsString(scored.featureValues());
		String componentJson = this.objectMapper.writeValueAsString(scored.scoreComponents());

		// 좌표 값 자체가 문자열로도 숫자로도 안 남는다.
		assertThat(featureJson).doesNotContain("35.1796432").doesNotContain("129.0756123");
		assertThat(componentJson).doesNotContain("35.1796432").doesNotContain("129.0756123");
		// 좌표처럼 보이는 것도 없다.
		assertThat(COORDINATE_PAIR.matcher(featureJson).find()).isFalse();

		// 남아야 하는 것은 남는다.
		assertThat(scored.featureValues()).containsKey("distanceM");
		assertThat(scored.featureValues()).containsEntry("localityBucket", "3518:12908");
		assertThat(scored.featureValues()).containsEntry("distanceBucket", DistanceBucket.KM1_TO_3KM);
	}

	@Test
	@DisplayName("🔴 개인정보 그물이 채점 결과를 통과시킨다 — lat·lng 키를 넣었다면 막혔다")
	void 그물이_통과시킨다() {
		EngineCandidate scored = score(PRECISE_LAT, PRECISE_LNG, 800L);

		assertThatCode(() -> {
			this.guard.verify(scored.featureValues(), "feature_values");
			this.guard.verify(scored.scoreComponents(), "score_components");
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 lat·lng 키를 넣으면 그물이 실제로 막는다 — 위 통과가 우연이 아님을 보인다")
	void 좌표_키는_막힌다() {
		// 이 테스트가 없으면 "그물이 통과했다" 가 "그물이 아무것도 안 본다" 와 구별되지 않는다.
		//
		// Map.of 는 순회 순서를 보장하지 않아 lat 과 lng 중 어느 쪽이 먼저 걸릴지 모른다.
		// 그래서 키 이름 대신 막혔다는 사실만 본다.
		assertThatThrownBy(() -> this.guard.verify(Map.of("lat", PRECISE_LAT), "feature_values"))
				.hasMessageContaining("금지된 키 이름");
		assertThatThrownBy(() -> this.guard.verify(Map.of("lng", PRECISE_LNG), "feature_values"))
				.hasMessageContaining("금지된 키 이름");
	}

	@Test
	@DisplayName("🔴 Job 에는 정밀 좌표를 담을 자리가 없다 — 굵은 칸과 출처만 남는다")
	void job_에는_좌표_칸이_없다() {
		RecommendationJob job = RecommendationJob.start(UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), JobType.NOW_RECOMMENDATION, java.time.OffsetDateTime.now());

		job.applyOrigin(new RequestLocation(PRECISE_LAT, PRECISE_LNG, LocationSource.GPS, 12.5,
				Instant.parse("2026-09-08T00:00:00Z")));

		assertThat(job.getOriginAreaCode()).isEqualTo("3518:12908");
		assertThat(job.getOriginSource()).isEqualTo(LocationSource.GPS);
		// 좌표를 담는 필드가 없는지를 본다. 필드가 곧 DB 칸이라 이것이 맞는 검사다 —
		// 메서드 이름을 훑으면 applyLatencies 의 "lat" 에 걸려 오탐이 난다.
		List<String> coordinateFields = java.util.Arrays.stream(RecommendationJob.class.getDeclaredFields())
				.map(java.lang.reflect.Field::getName)
				.filter((name) -> List.of("lat", "lng", "latitude", "longitude", "originLat", "originLng",
						"currentLat", "currentLng").contains(name))
				.toList();
		assertThat(coordinateFields).isEmpty();
	}

	// ── 새는 가장 흔한 길: 로그 ────────────────────────────────────────────

	@Test
	@DisplayName("🔴 toString 이 좌표를 찍지 않는다 — 그물은 저장물만 보고 로그는 못 본다")
	void toString_이_좌표를_숨긴다() {
		RequestLocation location = new RequestLocation(PRECISE_LAT, PRECISE_LNG, LocationSource.GPS, 12.5,
				Instant.parse("2026-09-08T00:00:00Z"));

		String text = location.toString();

		assertThat(text).doesNotContain("35.1796432").doesNotContain("129.0756123");
		assertThat(text).contains("GPS").contains("3518:12908");
	}

	// ── 굵기 ──────────────────────────────────────────────────────────────

	@Test
	@DisplayName("굵은 칸은 소수점을 남기지 않는다 — 좌표처럼 보이면 다음 사람이 좌표로 쓴다")
	void 칸_번호에_소수점이_없다() {
		String area = CoarseArea.of(PRECISE_LAT, PRECISE_LNG);

		assertThat(area).isEqualTo("3518:12908").doesNotContain(".");
		assertThat(area.length()).isLessThanOrEqualTo(CoarseArea.maxLength());
	}

	@Test
	@DisplayName("🔴 1km 안쪽은 같은 칸이 되어 원래 위치를 되돌릴 수 없다")
	void 굵기가_실제로_뭉갠다() {
		// 같은 1km 칸 안의 서로 다른 두 지점.
		String a = CoarseArea.of(35.1796432, 129.0756123);
		String b = CoarseArea.of(35.1798888, 129.0757777);

		assertThat(a).isEqualTo(b);
	}

	@Test
	@DisplayName("좌표가 없으면 칸도 없다 — 0 으로 채우지 않는다")
	void 좌표가_없으면_칸도_null() {
		assertThat(CoarseArea.of(null, 129.0)).isNull();
		assertThat(CoarseArea.of(35.1, null)).isNull();
	}

	// ── 완료 기준 1: 두 출처가 똑같이 동작한다 ────────────────────────────

	@Test
	@DisplayName("🔴 수기 입력이 GPS 와 똑같이 동작한다 — 1급 fallback 이라는 뜻")
	void 수기_입력이_열등하지_않다() {
		RequestLocation gps = new RequestLocation(PRECISE_LAT, PRECISE_LNG, LocationSource.GPS, 12.5,
				Instant.now());
		RequestLocation manual = new RequestLocation(PRECISE_LAT, PRECISE_LNG, LocationSource.MANUAL, null,
				Instant.now());

		// 같은 좌표면 같은 칸이 나온다 — 출처가 계산을 바꾸지 않는다.
		assertThat(manual.areaCode()).isEqualTo(gps.areaCode());
		// 출처만 구분되어 남는다.
		assertThat(manual.source()).isEqualTo(LocationSource.MANUAL);
		assertThat(manual.accuracyM()).isNull();
	}

	@Test
	@DisplayName("오차 반경이 없다고 GPS 를 수기 입력으로 바꾸지 않는다")
	void 출처를_값으로_짐작하지_않는다() {
		RequestLocation gpsWithoutAccuracy = new RequestLocation(PRECISE_LAT, PRECISE_LNG, LocationSource.GPS,
				null, Instant.now());

		assertThat(gpsWithoutAccuracy.source()).isEqualTo(LocationSource.GPS);
	}

	@Test
	@DisplayName("여행 출발지가 없으면 위치를 만들지 않는다 — 좌표를 지어내지 않는다")
	void 출발지가_없으면_null() {
		assertThat(RequestLocation.ofTripOrigin(null, null, Instant.now())).isNull();
		assertThat(RequestLocation.ofTripOrigin(35.1, null, Instant.now())).isNull();
	}

	@Test
	@DisplayName("범위를 벗어난 좌표와 음수 오차는 거부한다")
	void 잘못된_값은_거부한다() {
		Instant now = Instant.now();
		assertThatThrownBy(() -> new RequestLocation(91.0, 129.0, LocationSource.GPS, null, now))
				.hasMessageContaining("위도");
		assertThatThrownBy(() -> new RequestLocation(35.1, 181.0, LocationSource.GPS, null, now))
				.hasMessageContaining("경도");
		assertThatThrownBy(() -> new RequestLocation(35.1, 129.0, LocationSource.GPS, -1.0, now))
				.hasMessageContaining("오차");
		assertThatThrownBy(() -> new RequestLocation(35.1, 129.0, null, null, now))
				.hasMessageContaining("출처");
	}

	// ── 거리 띠 ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("거리 띠 경계")
	void 거리_띠_경계() {
		assertThat(DistanceBucket.of(0L)).isEqualTo(DistanceBucket.UNDER_500M);
		assertThat(DistanceBucket.of(499L)).isEqualTo(DistanceBucket.UNDER_500M);
		assertThat(DistanceBucket.of(500L)).isEqualTo(DistanceBucket.M500_TO_1KM);
		assertThat(DistanceBucket.of(999L)).isEqualTo(DistanceBucket.M500_TO_1KM);
		assertThat(DistanceBucket.of(1_000L)).isEqualTo(DistanceBucket.KM1_TO_3KM);
		assertThat(DistanceBucket.of(2_999L)).isEqualTo(DistanceBucket.KM1_TO_3KM);
		assertThat(DistanceBucket.of(3_000L)).isEqualTo(DistanceBucket.KM3_TO_10KM);
		assertThat(DistanceBucket.of(9_999L)).isEqualTo(DistanceBucket.KM3_TO_10KM);
		assertThat(DistanceBucket.of(10_000L)).isEqualTo(DistanceBucket.OVER_10KM);
	}

	@Test
	@DisplayName("🔴 음수 거리는 '가까움' 이 아니라 계산 오류다 — null 로 둔다")
	void 음수_거리는_null() {
		assertThat(DistanceBucket.of(-1L)).isNull();
		assertThat(DistanceBucket.of(null)).isNull();
	}

	// ── 도우미 ────────────────────────────────────────────────────────────

	private EngineCandidate score(double lat, double lng, long distanceM) {
		PlaceCandidateResponse.Candidate candidate = new PlaceCandidateResponse.Candidate(UUID.randomUUID(),
				"어느 카페", "CAFE", lat, lng, distanceM, List.of());

		return this.scorer.score(candidate, null, List.of(), 5_000,
				new BaselineEngineProperties.Weights(null, null, null, null, null, null),
				new PreferenceAlignmentWeights(null, null, null, null, null), List.of(), List.of(),
				List.of(), 0.05);
	}
}
