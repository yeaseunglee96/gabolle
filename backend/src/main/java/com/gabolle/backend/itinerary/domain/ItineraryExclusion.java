package com.gabolle.backend.itinerary.domain;

import java.time.Instant;

/**
 * 판 하나에 매달린 "이 장소는 빼라" 표시 하나.
 * 판마다 복사되는 스냅샷이다 — {@link ItineraryVersion} 이 덮어쓰지 않는 스냅샷이므로 새 판을
 * 만들 때마다 바탕 판의 제외 목록도 그 판으로 복사한다. 그래서 재계산을 몇 번 해도 한 번 뺀
 * 장소가 다시 후보로 나오지 않는 것이 "매번 확인하는 로직" 이 아니라 복사 하나로 보장된다.
 * JPA·Spring 을 import 하지 않는다 — 순수 값 객체라 DB 없이 검증할 수 있다.
 *
 * @param itemKey 항목이었다면 어느 항목이었나 — 후보였을 뿐 실제 항목으로 배치된 적이
 *     없었다면 {@code null} 이다
 * @param operationalReason 사용자 자유 입력. {@code null} 일 수 있다. 이벤트 payload 에는
 *     싣지 않는다 — 문장에 개인정보·민감 정보가 실릴 수 있다
 * @param createdAt 언제 뺐는가. 판을 복사할 때 이 값은 원본 그대로 물려준다 —
 *     {@link ItineraryItem}·{@link ItineraryLeg} 의 {@code createdAt} 이 판마다 "이 행이 새로
 *     생긴 시각" 으로 갱신되는 것과 다르다. 제외는 "언제 그 장소를 뺐는가" 가 사실이고, 판을
 *     복사했다고 다시 뺀 것이 아니다
 */
public record ItineraryExclusion(String itineraryExclusionId, String itineraryVersionId, String placeId,
        String itemKey, String excludedBy, String reasonCode, String operationalReason, Instant createdAt) {

    /** 사용자가 직접 항목을 뺐다. */
    public static final String REASON_USER_REMOVED = "USER_REMOVED";
}
