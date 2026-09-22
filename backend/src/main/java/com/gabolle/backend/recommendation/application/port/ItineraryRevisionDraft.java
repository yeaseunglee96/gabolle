package com.gabolle.backend.recommendation.application.port;

import java.util.List;

/**
 * 하루를 다시 채운 결과 — 아직 저장되지 않은 새 판의 초안.
 *
 * {@link ItineraryDraft} 와 달리 값 record 가 아니라 인터페이스다. 새 판의 내용은 일정
 * 도메인의 객체인데 그 타입을 이 포트 패키지가 import 하면 추천 계층이 일정 도메인에
 * 의존하게 된다. 추천 계층이 알아야 하는 경고와 개수만 노출하고, 실제 내용은 일정 쪽
 * 구현체가 들고 있다가 {@link ItineraryDraftPort#publish} 에서 꺼내 쓴다. 추천 계층은 이
 * 객체의 안을 열어 보지 않고 그대로 되돌려준다.
 */
public interface ItineraryRevisionDraft {

    /** 이 판에 남길 경고. 빈 자리를 못 채웠을 때 등. {@code itinerary/domain/ItineraryWarningCodes} 의 값. */
    List<String> warningCodes();

    /** 그 날에서 보존한 항목 수(다른 날짜는 세지 않는다). */
    int keptCount();

    /** 그 날에 새로 채운 항목 수. */
    int filledCount();
}
