package com.gabolle.backend.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.gabolle.backend.recommendation.domain.ConstraintSeverity;
import com.gabolle.backend.recommendation.domain.UnknownExclusionThreshold;

/**
 * 추천 로깅 설정.
 *
 * <p>🔴 {@code serviceVersion} 과 {@code deploymentEnvironment} 에는 <b>기본값을 두지 않는다.</b>
 * 값이 없으면 그 요청은 실패로 남아야 한다 — 기본값을 넣는 순간 "이 결과가 어느 배포에서
 * 나왔는가" 를 영영 알 수 없게 되는데, 데이터는 멀쩡해 보인다.
 *
 * @param serviceVersion 이 백엔드 빌드 버전. 배포 파이프라인이 주입한다
 * @param deploymentEnvironment {@code local} · {@code dev} · {@code prod} 등
 * @param defaultTopK 요청이 topK 를 안 주면 쓸 값
 * @param eventVersion 추천 이벤트 스키마 버전. 지금은 {@code recommendation_requested} 와
 *     {@code recommendation_failed} 가 같은 값을 쓴다 — 둘 다 새 이벤트라 1 에서 함께 출발한다.
 *     🔴 한쪽 스키마만 바뀌는 날 <b>이 설정을 이벤트 종류별로 쪼개야 한다</b>. 그러지 않으면
 *     안 바뀐 이벤트의 버전이 같이 올라가서 소비자가 없는 변경을 찾게 된다
 * @param unknownExclusionThreshold 확인하지 못한 제약이 어느 <b>등급</b> 이상일 때 그 후보를
 *     결과에서 뺄 것인가. 데이터가 채워지는 만큼 기준선을 내리면 되고, 그때 코드는 안 고친다
 * @param unspecifiedSeverity 온톨로지가 등급을 안 보냈을 때 무엇으로 볼 것인가
 */
@ConfigurationProperties(prefix = "gabolle.recommendation")
public record RecommendationProperties(
		String serviceVersion,
		String deploymentEnvironment,
		Integer defaultTopK,
		Integer eventVersion,
		UnknownExclusionThreshold unknownExclusionThreshold,
		ConstraintSeverity unspecifiedSeverity) {

	public RecommendationProperties {
		defaultTopK = (defaultTopK == null) ? 10 : defaultTopK;
		eventVersion = (eventVersion == null) ? 1 : eventVersion;
		// 🔴 기본값: 안전 제약(REQUIRED)이 미확인인 후보만 뺀다. 그 아래는 경고를 달고 내보낸다.
		unknownExclusionThreshold = (unknownExclusionThreshold == null)
				? UnknownExclusionThreshold.REQUIRED
				: unknownExclusionThreshold;
		// 🔴 등급이 안 왔으면 가장 위험한 것으로 본다 — 없는 값을 낙관적으로 채우지 않는다.
		unspecifiedSeverity = (unspecifiedSeverity == null)
				? ConstraintSeverity.REQUIRED
				: unspecifiedSeverity;
		if (defaultTopK < 1) {
			throw new IllegalArgumentException("gabolle.recommendation.default-top-k 는 1 이상이어야 한다");
		}
		if (eventVersion < 1) {
			throw new IllegalArgumentException("gabolle.recommendation.event-version 은 1 이상이어야 한다");
		}
	}
}
