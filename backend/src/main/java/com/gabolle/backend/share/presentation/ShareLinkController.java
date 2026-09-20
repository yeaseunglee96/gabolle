package com.gabolle.backend.share.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.share.application.ShareLinkService;
import com.gabolle.backend.share.domain.TripShareLink;
import com.gabolle.backend.share.presentation.dto.ShareLinkResponse;

/** 읽기 전용 공유 주소 발급. 인증이 필요하고 그중에서도 여행 OWNER 만 부를 수 있다. */
@RestController
@RequestMapping("/api/v1/trips")
@Profile({ "db", "dev" })
public class ShareLinkController {

	private final ShareLinkService shareLinkService;

	public ShareLinkController(ShareLinkService shareLinkService) {
		this.shareLinkService = shareLinkService;
	}

	@PostMapping("/{tripId}/share-links")
	public ResponseEntity<ApiResponse<ShareLinkResponse>> issue(@PathVariable String tripId,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		TripShareLink link = this.shareLinkService.issue(tripId, requester);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(ShareLinkResponse.of(link), "req_" + UUID.randomUUID()));
	}
}
