package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 추천 후보를 뽑을 조건. 사용자가 아니라 추천 엔진이 보낸다 — 화면에 그대로 보이는 목록이 아니라
 * 랭킹 전에 후보를 좁히는 단계다.
 *
 * @param radiusM 반경(m)
 * @param categories {@code place.category} 로 거른다. 비어 있으면 안 거른다
 * @param requiredFeatures 이 표식이 있어야 통과한다
 * @param excludedFeatures 이 표식이 있으면 뺀다. 알레르기 같은 하드 필터가 여기 온다
 * @param openNowAt 이 시각에 여는 곳만. 영업시간 칸이 아직 없어 적용할 수 없고, 응답의
 *        {@code notApplied} 에 그 사실이 실린다
 * @param minimumCount 이만큼은 나와야 한다. 못 채우면 응답에 {@code belowMinimum} 이 붙는다 —
 *        조건을 자동으로 풀어서 채우지 않는다
 * @param limit 돌려줄 후보 수의 상한. 거리순으로 자른다 — 점수를 모르는 단계라 그럴 수밖에 없다.
 *        채점을 할 호출자는 여기를 작게 주면 안 된다
 *        ({@link com.gabolle.backend.place.service.PlaceCandidateQueryService} 클래스 주석)
 */
public record PlaceCandidateRequest(
		@NotNull @Valid Center center,
		@NotNull @Min(100) @Max(50_000) Integer radiusM,
		List<String> categories,
		// 원소의 @Valid 가 없으면 FeatureMatch.featureType 의 @NotNull 이 안 돈다 —
		// {"featureType":null} 이 400 대신 빈 목록 취급 200 으로 통과한다.
		List<@Valid FeatureMatch> requiredFeatures,
		List<@Valid FeatureMatch> excludedFeatures,
		OffsetDateTime openNowAt,
		@Min(0) Integer minimumCount,
		// 상한이 큰 이유: 추천 엔진은 반경 안 후보를 전부 받아 채점한 뒤에 잘라야 하는데
		// 부산은 반경 5km 안에 음식점만 평균 9,422곳이다. 큰 값을 줘도 실제 작업량은 무한정
		// 늘지 않는다 — DB 에서 읽어 오는 행 수는 gabolle.place.candidate-max-scanned 가 막는다.
		@Min(1) @Max(50_000) Integer limit) {

	public record Center(
			@NotNull @jakarta.validation.constraints.DecimalMin("-90") @jakarta.validation.constraints.DecimalMax("90") Double lat,
			@NotNull @jakarta.validation.constraints.DecimalMin("-180") @jakarta.validation.constraints.DecimalMax("180") Double lng) {
	}

	/**
	 * 표식 하나를 가리킨다.
	 *
	 * @param featureKey 태그형이면 코드가 오고, 점수형·참거짓형이면 {@code null} 이다
	 */
	public record FeatureMatch(@NotNull String featureType, String featureKey) {
	}

	public List<String> categoriesOrEmpty() {
		return this.categories == null ? List.of() : this.categories;
	}

	public List<FeatureMatch> requiredOrEmpty() {
		return this.requiredFeatures == null ? List.of() : this.requiredFeatures;
	}

	public List<FeatureMatch> excludedOrEmpty() {
		return this.excludedFeatures == null ? List.of() : this.excludedFeatures;
	}

	public int minimumCountOrDefault() {
		return this.minimumCount == null ? 0 : this.minimumCount;
	}

	public int limitOrDefault() {
		return this.limit == null ? 200 : this.limit;
	}
}
