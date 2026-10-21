package com.gabolle.backend.share.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.share.application.ShareCloneService;
import com.gabolle.backend.share.presentation.dto.CloneTripResponse;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.presentation.dto.CreateTripRequest;
import com.gabolle.backend.trip.presentation.dto.CreateTripRequestMapper;

import jakarta.validation.Valid;

/**
 * 공유 일정을 내 조건으로 복제한다. 조회와 달리 인증이 필요하다 — 새 여행에 주인이 있어야
 * 하기 때문이고, SecurityConfig 가 GET 만 열어 둔 이유다.
 *
 * 본문은 여행 생성과 같은 CreateTripRequest 이고 같은 매퍼를 지난다.
 *
 * 응답 상태는 셋 다 성공이다 — 여행과 Job 을 만들었으면 202, 여행만 만들었으면 201,
 * Idempotency-Key 재시도면 200.
 */
@RestController
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class ShareCloneController {

	private final ShareCloneService cloneService;

	public ShareCloneController(ShareCloneService cloneService) {
		this.cloneService = cloneService;
	}

	@PostMapping("/api/v1/shares/{token}/clone")
	public ResponseEntity<ApiResponse<CloneTripResponse>> clone(@PathVariable String token,
			@Valid @RequestBody CreateTripRequest request,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			Authentication authentication) {

		// 복제는 새 여행을 만드는 자리라 POST /trips 와 같이 익명 세션도 주인이 된다.
		AuthenticatedUsers.Owner owner = AuthenticatedUsers.requireOwner(authentication);
		Trip.OwnerType ownerType = owner.anonymous() ? Trip.OwnerType.ANONYMOUS : Trip.OwnerType.USER;
		ShareCloneService.Result result = this.cloneService.clone(token,
				CreateTripRequestMapper.toCommand(request, owner.id().toString(), ownerType), idempotencyKey);

		String jobId = result.job() == null ? null : result.job().getJobId().toString();
		CloneTripResponse body = new CloneTripResponse(
				result.trip().tripId(),
				result.sourceTripId(),
				result.shareLinkId(),
				result.seedPlaceCount(),
				jobId,
				jobId == null ? null : "/api/v1/jobs/" + jobId,
				result.created(),
				result.warningCodes());

		HttpStatus status = !result.created() ? HttpStatus.OK
				: (jobId != null ? HttpStatus.ACCEPTED : HttpStatus.CREATED);
		return ResponseEntity.status(status).body(ApiResponse.success(body, "req_" + UUID.randomUUID()));
	}
}
