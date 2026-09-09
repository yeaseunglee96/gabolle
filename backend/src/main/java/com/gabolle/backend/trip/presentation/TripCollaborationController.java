package com.gabolle.backend.trip.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.trip.application.TripInviteService;
import com.gabolle.backend.trip.application.TripMemberService;
import com.gabolle.backend.trip.presentation.dto.AcceptInviteResponse;
import com.gabolle.backend.trip.presentation.dto.ChangeMemberRoleRequest;
import com.gabolle.backend.trip.presentation.dto.CreateTripInviteRequest;
import com.gabolle.backend.trip.presentation.dto.TripInviteResponse;
import com.gabolle.backend.trip.presentation.dto.TripMembersResponse;

import jakarta.validation.Valid;

/**
 * 동행자 초대·수락·참여자 관리 — S15P21E201-294 · -299 · -320.
 *
 * <p>🔴 {@link TripController} 를 넓히지 않고 새로 둔다. 이 다섯 경로는 "협업" 이라는 기능
 * 하나로 묶이고, 오류 번역도 {@code TripExceptionHandler} 가 아니라
 * {@link TripCollaborationExceptionHandler} 를 따로 쓴다.
 */
@RestController
@Profile({ "db", "dev" })
public class TripCollaborationController {

	private final TripInviteService inviteService;

	private final TripMemberService memberService;

	public TripCollaborationController(TripInviteService inviteService, TripMemberService memberService) {
		this.inviteService = inviteService;
		this.memberService = memberService;
	}

	/** 초대 발급. 그 여행의 소유자만 부를 수 있다. */
	@PostMapping("/api/v1/trips/{tripId}/invites")
	public ResponseEntity<ApiResponse<TripInviteResponse>> createInvite(
			@PathVariable String tripId,
			@Valid @RequestBody CreateTripInviteRequest request,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		TripInviteResponse response = this.inviteService.issue(tripId, requester, request.role());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, requestId()));
	}

	/** 초대 수락. 로그인만 하면 되고 표(token) 자체가 잠금이다. */
	@PostMapping("/api/v1/trip-invites/{token}/accept")
	public ApiResponse<AcceptInviteResponse> acceptInvite(
			@PathVariable String token,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		return ApiResponse.success(this.inviteService.accept(token, requester), requestId());
	}

	/** 참여자 목록. 회원이면 누구나. */
	@GetMapping("/api/v1/trips/{tripId}/members")
	public ApiResponse<TripMembersResponse> members(
			@PathVariable String tripId,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		return ApiResponse.success(this.memberService.list(tripId, requester), requestId());
	}

	/** 참여자 역할 변경. 그 여행의 소유자만 부를 수 있다. */
	@PatchMapping("/api/v1/trips/{tripId}/members/{userId}")
	public ApiResponse<TripMembersResponse.Member> changeRole(
			@PathVariable String tripId,
			@PathVariable String userId,
			@Valid @RequestBody ChangeMemberRoleRequest request,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		TripMembersResponse.Member updated = this.memberService.changeRole(tripId, requester, userId, request.role());
		return ApiResponse.success(updated, requestId());
	}

	/** 참여자 제거. 그 여행의 소유자만 부를 수 있다. 소유자는 뺄 수 없다. */
	@DeleteMapping("/api/v1/trips/{tripId}/members/{userId}")
	public ResponseEntity<Void> removeMember(
			@PathVariable String tripId,
			@PathVariable String userId,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		this.memberService.remove(tripId, requester, userId);
		return ResponseEntity.noContent().build();
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
