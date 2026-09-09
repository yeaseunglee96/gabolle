package com.gabolle.backend.share.presentation.dto;

import java.util.List;

/**
 * 공유 일정 복제 응답 — S15P21E201-338 (F-COL-04).
 *
 * @param tripId 새 여행. 소유자는 요청자다
 * @param jobId 새 여행의 일정 생성 Job. {@code /api/v1/jobs/{jobId}} 로 폴링한다(앱이 이미 갖고 있는 흐름).
 *     추천을 접수하지 못했으면 {@code null} 이고 {@code warningCodes} 에 이유가 있다
 * @param seedPlaceCount 원본에서 가져온 장소 수
 * @param created 이번 요청이 새 여행을 만들었나. {@code Idempotency-Key} 재시도면 {@code false} 이고 기존 여행을
 *     돌려준다(그때 Job 은 다시 접수하지 않는다)
 */
public record CloneTripResponse(
		String tripId,
		String sourceTripId,
		String shareLinkId,
		int seedPlaceCount,
		String jobId,
		String pollPath,
		boolean created,
		List<String> warningCodes) {
}
