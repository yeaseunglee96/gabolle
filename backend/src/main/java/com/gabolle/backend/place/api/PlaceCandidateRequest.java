package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 추천 후보를 뽑을 조건 (S15P21E201-102).
 *
 * <p>이 요청은 사용자가 아니라 <b>추천 엔진</b>이 보낸다. 화면에 그대로 보이는 목록이 아니라,
 * 랭킹 전에 후보를 좁히는 단계다.
 *
 * @param center 중심 좌표. 반경과 함께 지역 조건이 된다
 * @param radiusM 반경(m)
 * @param categories {@code place.category} 로 거른다. 비어 있으면 안 거른다
 * @param requiredFeatures 이 표식이 <b>있어야</b> 통과한다
 * @param excludedFeatures 이 표식이 있으면 <b>뺀다</b>. 알레르기 같은 하드 필터가 여기 온다
 * @param openNowAt 이 시각에 여는 곳만. 🔴 지금은 적용할 수 없고 응답의 {@code notApplied} 에
 *        그 사실이 실린다 — 영업시간 칸이 아직 없다
 * @param minimumCount 이만큼은 나와야 한다. 못 채우면 응답에 {@code belowMinimum} 이 붙는다.
 *        🔴 조건을 자동으로 풀어서 채우지 않는다
 */
public record PlaceCandidateRequest(
		@NotNull @Valid Center center,
		@NotNull @Min(100) @Max(50_000) Integer radiusM,
		List<String> categories,
		// 🔴 @Valid 가 없으면 List 자체는 검증해도 원소(FeatureMatch)는 안 들어가서
		// FeatureMatch.featureType 의 @NotNull 이 안 돈다 — {"featureType":null} 이 그대로 통과해
		// 400 대신 빈 목록 취급 200 이 나갔다.
		List<@Valid FeatureMatch> requiredFeatures,
		List<@Valid FeatureMatch> excludedFeatures,
		OffsetDateTime openNowAt,
		@Min(0) Integer minimumCount,
		@Min(1) @Max(500) Integer limit) {

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
