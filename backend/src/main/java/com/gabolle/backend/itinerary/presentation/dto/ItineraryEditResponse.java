package com.gabolle.backend.itinerary.presentation.dto;

import java.util.List;

import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.recommendation.domain.FallbackMode;

/**
 * 편집이 만든 새 판 — ITN-03 · ITN-04 의 응답 본문(S15P21E201-662).
 *
 * <h2>🔴 앞 일곱 칸은 {@link ItineraryDetailResponse} 와 이름·타입이 같다. 계약이다</h2>
 * 앱은 고정 요청의 응답을 <b>일정 전체</b>로 받아 화면 상태에 그대로 넣는다
 * ({@code frontend/src/plan/itinerary.ts} 의 {@code setItineraryItemLocked} 가
 * {@code apiRequest<ItineraryDto>} 로 부른다). 그래서 {@code days} 가 없으면 그 자리에서
 * 화면이 깨진다 — 2026-09-06 이전이 정확히 그 상태였다.
 *
 * <p><b>앞 일곱 칸의 이름·타입을 바꾸면 앱이 깨진다.</b> 조회와 편집이 같은 모양을 내야
 * 한다는 뜻이고, 한쪽만 고치면 안 된다.
 *
 * <h2>왜 {@code ItineraryDetailResponse} 를 그대로 쓰지 않는가</h2>
 * 명세 ITN-03 의 응답은 "새 {@code ItineraryVersion}" 이라 판 번호·바탕 판·편집 종류가
 * 필요하다. 그것을 조회 DTO 에 넣으면 조회 응답까지 편집 메타데이터로 오염된다. 그래서
 * 앞쪽 칸을 겹쳐 놓은 별도 레코드로 둔다 — 두 계약을 동시에 만족하는 유일한 모양이다.
 *
 * <p>뒤쪽 칸들은 앱이 안 읽는다. JSON 은 모르는 키를 무시하므로 더해도 안전하다.
 *
 * <p>🔴 S15P21E201-224 — {@code myRole}·{@code canEdit} 두 칸을 맨 뒤에 더 붙였다.
 * 이 응답이 나온다는 것 자체가 이미 편집을 성공한 뒤라는 뜻이라 {@code canEdit} 은 항상
 * {@code true} 다 — VIEWER 였다면 {@code ItineraryAccess.requireEditor} 가 이 지점까지
 * 오기 전에 403 으로 막는다. 그래도 조회 응답과 모양을 맞추기 위해 그대로 옮겨 싣는다.
 */
public record ItineraryEditResponse(
		String id,
		String title,
		int version,
		List<ItineraryDetailResponse.Day> days,
		Integer totalEstimatedCostKrw,
		Integer totalWalkingMeters,
		FallbackMode fallbackMode,

		Integer baseVersion,
		String operation,
		String createdBy,
		String requestId,
		String createdAt,

		String myRole,
		boolean canEdit) {

	public static ItineraryEditResponse of(ItineraryDetailResponse detail, ItineraryVersion saved) {
		return new ItineraryEditResponse(
				detail.id(),
				detail.title(),
				detail.version(),
				detail.days(),
				detail.totalEstimatedCostKrw(),
				detail.totalWalkingMeters(),
				detail.fallbackMode(),
				saved.baseVersion(),
				saved.operation().name(),
				saved.createdBy(),
				saved.requestId(),
				saved.createdAt().toString(),
				detail.myRole(),
				detail.canEdit());
	}
}
