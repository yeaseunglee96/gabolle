package com.gabolle.backend.share.presentation.dto;

import com.gabolle.backend.share.domain.TripShareLink;

/**
 * 공유 주소 발급 응답 — S15P21E201-330.
 *
 * @param shareLinkId 발급된 표 행의 식별자
 * @param tripId 이 표가 가리키는 여행
 * @param token 43글자 난수. 이 값 자체가 URL 의 잠금이다
 * @param expiresAt 발급 시각 + 30일(ISO-8601)
 * @param path 비로그인 조회가 열 상대 경로 — {@code "/api/v1/shares/" + token}
 */
public record ShareLinkResponse(String shareLinkId, String tripId, String token, String expiresAt, String path) {

	public static ShareLinkResponse of(TripShareLink link) {
		return new ShareLinkResponse(
				link.getTripShareLinkId().toString(),
				link.getTripId().toString(),
				link.getToken(),
				link.getExpiresAt().toString(),
				"/api/v1/shares/" + link.getToken());
	}
}
