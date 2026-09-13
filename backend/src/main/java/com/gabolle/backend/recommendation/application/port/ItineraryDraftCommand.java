package com.gabolle.backend.recommendation.application.port;

import java.util.List;
import java.util.UUID;

/**
 * {@link ItineraryDraftPort#assemble} 의 입력 — 추천이 순위를 매긴 장소 목록과, 그 결과를
 * 재현하는 데 필요한 버전 다섯.
 *
 * @param places {@link PlannedPlace#rank} 오름차순으로 정렬돼 있어야 한다
 */
public record ItineraryDraftCommand(
        UUID requestId, String tripId, String userId,
        List<PlannedPlace> places,
        String modelVersion, String featureVersion, String ontologyVersion,
        String policyVersion, String datasetVersion) {

    /**
     * @param category 장소 갈래({@code place.category}). 하루 구성에서 밥집과 명소를 가르는 데
     *     쓴다 — S15P21E201-903. 모르면 {@code null} 이고 그때는 명소도 밥집도 아닌 것으로
     *     다룬다(지어내지 않는다).
     */
    public record PlannedPlace(UUID placeId, int rank,
            List<String> reasonCodes, List<String> warningCodes, String category) { }
}
