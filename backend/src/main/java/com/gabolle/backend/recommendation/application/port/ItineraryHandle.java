package com.gabolle.backend.recommendation.application.port;

/** {@link ItineraryDraftPort#persist} 가 만든 일정을 가리키는 값 — Job 에 붙일 자리. */
public record ItineraryHandle(String itineraryId, int version) { }
