package com.gabolle.backend.trip.domain;

import java.util.UUID;

/**
 * 계정 기본 취향(설문)이 새 판으로 저장됐다 — S15P21E201-1515.
 *
 * <p>이것을 듣는 쪽이 그 사람의 취향 판을 <b>그 자리에서</b> 다시 접는다. 예전에는 판을 만드는
 * 곳이 새벽 배치 하나뿐이라, 설문을 막 마친 사람은 다음 새벽까지 설문 없이 추천을 받았다.
 *
 * @param userId 설문을 저장한 사람
 */
public record UserPreferenceDefaultsSaved(UUID userId) {
}
