package com.gabolle.backend.event.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 노출 이벤트에 서버가 채울 사실 — 그 요청이 그 장소를 몇 번째로 왜 냈나, 어느 판으로 (S15P21E201-1689).
 *
 * <p>앱은 요청 번호 · 장소 번호 · 화면 이름만 보낸다. 순위 · 이유 코드 · 버전은 서버가 추천을 만들 때 이미 적어 뒀으므로
 * ({@code recommendation_candidate} · {@code recommendation_job}) 앱이 들고 다니게 하지 않는다. 이벤트 패키지가 추천
 * 패키지를 모르게 문을 여기 둔다 — 구현은 추천 쪽에 있다.
 */
public interface ImpressionFacts {

	/** 그 요청이 그 장소를 낸 기록. 없으면(모르는 요청 · 그 요청이 안 낸 장소) 빈 값이다. */
	Optional<Facts> lookup(UUID requestId, UUID placeId);

	/**
	 * @param finalRank 그 요청 안의 최종 순위. 순위를 못 받은 후보면 {@code null}
	 */
	record Facts(Integer finalRank, List<String> reasonCodes, String fallbackMode, String modelVersion,
			String featureVersion, String ontologyVersion, String datasetVersion, String policyVersion) {
	}
}
