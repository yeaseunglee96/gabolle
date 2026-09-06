package com.gabolle.backend.recommendation.application.port;

import java.util.List;

/**
 * 하루를 다시 채운 결과 — 아직 저장되지 않은 새 판의 초안. S15P21E201-249.
 *
 * <p>🔴 이것은 <b>인터페이스</b>다. {@link ItineraryDraft} 처럼 값 record 로 두지 않은 이유 —
 * 새 판의 내용은 일정 도메인의 객체({@code ItineraryItem}·{@code ItineraryLeg}·
 * {@code ItineraryExclusion})인데, 그 타입을 이 포트 패키지가 import 하면 추천 계층이 일정
 * 도메인에 의존하게 된다. 지금까지 의존은 일정 → 추천 한 방향이고({@code ItineraryQueryService}
 * 가 {@code recommendation.domain} 을 읽는다), 반대 방향은 이 포트로만 뚫려 있다. 그래서 추천
 * 계층이 알아야 하는 것(경고·개수)만 여기 노출하고, 실제 내용은 일정 쪽 구현체가 들고 있다가
 * {@link ItineraryDraftPort#publish} 에서 자기 것을 꺼내 쓴다.
 *
 * <p>추천 계층은 이 객체를 <b>들고만 있다가 그대로 되돌려준다.</b> 안을 열어 보지 않는다.
 */
public interface ItineraryRevisionDraft {

    /** 이 판에 남길 경고. 빈 자리를 못 채웠을 때 등. {@code itinerary/domain/ItineraryWarningCodes} 의 값. */
    List<String> warningCodes();

    /** 그 날에서 보존한 항목 수(다른 날짜는 세지 않는다). */
    int keptCount();

    /** 그 날에 새로 채운 항목 수. */
    int filledCount();
}
