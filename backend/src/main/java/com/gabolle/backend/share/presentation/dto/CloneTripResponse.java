package com.gabolle.backend.share.presentation.dto;

import java.util.List;

/**
 * 공유 일정 복제 응답. jobId 는 /api/v1/jobs/{jobId} 로 폴링하고, 추천을 접수하지 못했으면
 * null 이며 그 이유가 warningCodes 에 있다.
 * created 는 Idempotency-Key 재시도면 false 이고 그때 기존 여행을 돌려준다.
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
