package com.gabolle.backend.itinerary.presentation.dto;

import java.util.List;

import com.gabolle.backend.itinerary.domain.ItineraryVersion;

/**
 * 판 목록 조회 응답 하나.
 * 되돌리기 화면이 "어느 판으로 돌아갈지" 고르는 목록에 쓴다. 판의 전체 내용(항목·구간)은
 * 싣지 않는다 — 목록은 어느 판이 있었는지만 보여주면 된다.
 */
public record ItineraryVersionSummaryResponse(
		int version,
		/** 이 판을 만들 때 보고 있던 최신 판. 최초 생성(CREATE)만 {@code null}. */
		Integer baseVersion,
		/** {@link ItineraryVersion.Operation} 의 이름 그대로. */
		String operation,
		String createdBy,
		/** ISO-8601 문자열. */
		String createdAt,
		String requestId,
		List<String> warningCodes,
		/** 되돌리기(operation=REVERT)가 내용을 복사해 온 옛 판. REVERT 가 아니면 {@code null}. */
		Integer revertedFromVersion,
		/**
		 * {@code app_user.display_name}. 사용자 행이 없으면(탈퇴) {@code null}. 맨 뒤에 더한 칸이다 —
		 * {@code createdBy} 는 사용자 UUID 라 화면이 "누가" 를 그릴 수 없었다.
		 */
		String createdByName) {

	public static ItineraryVersionSummaryResponse of(ItineraryVersion version) {
		return of(version, null);
	}

	public static ItineraryVersionSummaryResponse of(ItineraryVersion version, String createdByName) {
		return new ItineraryVersionSummaryResponse(
				version.version(),
				version.baseVersion(),
				version.operation().name(),
				version.createdBy(),
				version.createdAt().toString(),
				version.requestId(),
				version.warningCodes(),
				version.revertedFromVersion(),
				createdByName);
	}
}
