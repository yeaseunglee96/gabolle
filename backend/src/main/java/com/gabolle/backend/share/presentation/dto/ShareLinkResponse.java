package com.gabolle.backend.share.presentation.dto;

import com.gabolle.backend.share.domain.TripShareLink;

/**
 * 공유 주소 발급 응답. token 43글자 난수 자체가 URL 의 잠금이고, expiresAt 은 발급 시각 +
 * 30일(ISO-8601)이다.
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
