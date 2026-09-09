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
import com.gabolle.backend.trip.presentation.dto.CreateTripRequest;
import com.gabolle.backend.trip.presentation.dto.CreateTripRequestMapper;

import jakarta.validation.Valid;

/**
 * {@code POST /api/v1/shares/{token}/clone} — 공유 일정을 내 조건으로 복제 (S15P21E201-338).
 *
 * <p>🔴 <b>인증이 필요하다.</b> 공유 조회({@code GET /api/v1/shares/{token}})는 로그인 없이 열리지만, 복제는
 * 새 여행에 주인이 있어야 하니 로그인한 사람만 부른다. {@code SecurityConfig} 가 GET 만 열어 둔 이유다.
 *
 * <p>본문은 여행 생성({@code POST /api/v1/trips})과 <b>같은 {@link CreateTripRequest}</b> 다 — 앱이 이미 갖고
 * 있는 조건 입력 화면을 그대로 쓰라는 뜻이다. 번역도 같은 {@link CreateTripRequestMapper} 를 지난다.
 *
 * <p>응답 상태 — 새 여행을 만들고 Job 을 접수했으면 {@code 202}(비동기, 폴링 필요), 여행은 만들었지만 Job 을
 * 접수하지 못했으면 {@code 201}, {@code Idempotency-Key} 재시도면 {@code 200}. 셋 다 성공이다.
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

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		ShareCloneService.Result result = this.cloneService.clone(token,
				CreateTripRequestMapper.toCommand(request, requester), idempotencyKey);

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
