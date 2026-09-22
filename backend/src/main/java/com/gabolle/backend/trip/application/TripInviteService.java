package com.gabolle.backend.trip.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.common.security.OpaqueTokens;
import com.gabolle.backend.trip.domain.TripInvite;
import com.gabolle.backend.trip.domain.TripInviteRepository;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripMemberJoined;
import com.gabolle.backend.trip.domain.TripMembershipRepository;
import com.gabolle.backend.trip.presentation.dto.AcceptInviteResponse;
import com.gabolle.backend.trip.presentation.dto.TripInviteResponse;

/**
 * 동행자 초대 발급·수락.
 *
 * <p>발급은 소유자만, 수락은 표(token)를 아는 로그인 사용자면 누구나 — 표 자체가 유일한 잠금이다.
 *
 * <p>"이미 참여 중" 은 실패가 아니라 성공(200)이다. 같은 표를 두 번 누른 것도, 동시에 눌러
 * {@link TripMembershipRepository.AlreadyMemberException} 이 난 것도 전부 여기서 합쳐진다.
 */
@Service
@Profile({ "db", "dev" })
@Transactional
public class TripInviteService {

	private final TripInviteRepository inviteRepository;

	private final TripMembershipRepository membershipRepository;

	private final TripQueryService tripQueryService;

	private final Clock clock;

	/**
	 * 「동행이 들어왔다」를 알리는 자리. 듣는 쪽은 원래 있던 사람들 폰에 알림을 띄우는
	 * {@code TripPushNotifier} 하나이고, 커밋이 끝난 뒤에만 받는다 (S15P21E201-1391).
	 */
	private final ApplicationEventPublisher events;

	public TripInviteService(TripInviteRepository inviteRepository, TripMembershipRepository membershipRepository,
			TripQueryService tripQueryService, Clock clock, ApplicationEventPublisher events) {
		this.inviteRepository = inviteRepository;
		this.membershipRepository = membershipRepository;
		this.tripQueryService = tripQueryService;
		this.clock = clock;
		this.events = events;
	}

	/**
	 * 초대를 발급한다.
	 *
	 * @throws TripQueryService.TripNotFoundException 요청자가 그 여행의 회원이 아니다 — 404.
	 * @throws TripInviteForbiddenException 회원이지만 소유자가 아니다 — 403.
	 * @throws InvalidInviteRoleException {@code role} 이 {@code EDITOR}·{@code VIEWER} 가 아니다 — 400.
	 */
	public TripInviteResponse issue(String tripId, String requesterId, String roleRaw) {
		TripMember.Role role = parseInviteRole(roleRaw);

		TripQueryService.View view = this.tripQueryService.get(tripId, requesterId);
		if (view.role() != TripMember.Role.OWNER) {
			throw new TripInviteForbiddenException();
		}

		Instant now = this.clock.instant();
		TripInvite invite = TripInvite.issue(UUID.randomUUID().toString(), tripId, OpaqueTokens.generate(), role,
				requesterId, now);
		this.inviteRepository.save(invite);

		return new TripInviteResponse(invite.tripInviteId(), invite.tripId(), invite.role().name(), invite.token(),
				invite.expiresAt().toString(), "/api/v1/trip-invites/" + invite.token() + "/accept");
	}

	/**
	 * 표로 참여한다. 이미 그 여행의 회원이면(OWNER 포함, 역할 무관) 역할을 바꾸지 않고 기존
	 * 상태를 그대로 200 으로 돌려준다.
	 *
	 * @throws TripInviteNotFoundException 그런 표가 없다 — 404.
	 * @throws TripInviteExpiredException 발급 후 7일이 지났다 — 410. 회원 행은 만들지 않는다.
	 */
	public AcceptInviteResponse accept(String token, String requesterId) {
		TripInvite invite = this.inviteRepository.findByToken(token)
				.orElseThrow(TripInviteNotFoundException::new);

		Instant now = this.clock.instant();
		if (invite.isExpiredAt(now)) {
			throw new TripInviteExpiredException();
		}

		return this.membershipRepository.findMember(invite.tripId(), requesterId)
				.map(existing -> toAcceptResponse(invite.tripId(), existing, true))
				.orElseGet(() -> acceptFresh(invite, requesterId, now));
	}

	private AcceptInviteResponse acceptFresh(TripInvite invite, String requesterId, Instant now) {
		TripMember newMember = TripMember.invited(UUID.randomUUID().toString(), invite.tripId(), requesterId,
				invite.role(), now, invite.tripInviteId(), invite.createdBy(), invite.createdAt());
		try {
			TripMember saved = this.membershipRepository.add(newMember);
			// 🔴 «정말로 새로 들어왔을 때만» 낸다. 아래 catch 로 빠진 쪽과 accept() 의 「이미 참여 중」
			//    갈래는 아무 일도 안 일어난 것이라 알리지 않는다 — 같은 표를 두 번 누른 사람 때문에
			//    「동행이 합류했어요」가 두 번 뜨면, 동행자는 두 사람이 들어온 줄 안다.
			this.events.publishEvent(new TripMemberJoined(invite.tripId(), requesterId, saved.role()));
			return toAcceptResponse(invite.tripId(), saved, false);
		}
		catch (TripMembershipRepository.AlreadyMemberException e) {
			// 같은 표를 동시에 두 번 눌러 UNIQUE 가 막은 경쟁이다. 진 쪽도 성공(이미 참여)이다.
			TripMember existing = this.membershipRepository.findMember(invite.tripId(), requesterId)
					.orElseThrow(() -> e);
			return toAcceptResponse(invite.tripId(), existing, true);
		}
	}

	private AcceptInviteResponse toAcceptResponse(String tripId, TripMember member, boolean alreadyMember) {
		return new AcceptInviteResponse(tripId, member.role().name(), alreadyMember, member.joinedAt().toString());
	}

	private TripMember.Role parseInviteRole(String raw) {
		TripMember.Role role;
		try {
			role = TripMember.Role.valueOf(raw == null ? "" : raw.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			throw new InvalidInviteRoleException(raw);
		}
		if (role == TripMember.Role.OWNER) {
			// 소유자는 초대로 만들 수 없다 — TripMember.invited·TripInvite 생성자와 같은 규칙.
			throw new InvalidInviteRoleException(raw);
		}
		return role;
	}

	public static class TripInviteForbiddenException extends RuntimeException {
		public TripInviteForbiddenException() {
			super("여행 소유자만 초대할 수 있어요.");
		}
	}

	/** 빈 값·{@code OWNER}·모르는 값이 전부 여기로 온다. */
	public static class InvalidInviteRoleException extends RuntimeException {
		public InvalidInviteRoleException(String raw) {
			super("초대 역할은 EDITOR 또는 VIEWER 여야 해요: " + raw);
		}
	}

	public static class TripInviteNotFoundException extends RuntimeException {
		public TripInviteNotFoundException() {
			super("초대를 찾을 수 없습니다.");
		}
	}

	/** 표는 있지만 발급 후 7일이 지났다. 표 행은 지우지 않는다. */
	public static class TripInviteExpiredException extends RuntimeException {
		public TripInviteExpiredException() {
			super("초대가 만료됐어요.");
		}
	}
}
