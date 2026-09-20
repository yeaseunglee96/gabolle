package com.gabolle.backend.trip.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripMembershipRepository;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.trip.presentation.dto.TripMembersResponse;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 참여자 목록·역할 변경·제거.
 *
 * <p>역할 변경·제거의 소유자 판정은 {@link #requireOwner} 하나로 모았다.
 * {@code TripInviteService.issue} 가 같은 모양의 판정을 따로 갖는 것은 거부 메시지가 달라서다.
 */
@Service
@Profile({ "db", "dev" })
@Transactional
public class TripMemberService {

	private final TripRepository tripRepository;

	private final TripMembershipRepository membershipRepository;

	private final TripQueryService tripQueryService;

	private final AppUserRepository appUserRepository;

	public TripMemberService(TripRepository tripRepository, TripMembershipRepository membershipRepository,
			TripQueryService tripQueryService, AppUserRepository appUserRepository) {
		this.tripRepository = tripRepository;
		this.membershipRepository = membershipRepository;
		this.tripQueryService = tripQueryService;
		this.appUserRepository = appUserRepository;
	}

	/**
	 * 참여자 목록. 회원이면 누구나 볼 수 있다 — OWNER 먼저, 그다음 {@code joinedAt} 오름차순.
	 *
	 * @throws TripQueryService.TripNotFoundException 요청자가 그 여행의 회원이 아니다 — 404.
	 */
	public TripMembersResponse list(String tripId, String requesterId) {
		TripQueryService.View view = this.tripQueryService.get(tripId, requesterId);
		List<TripMember> members = this.tripRepository.findMembers(tripId);

		// 참여자마다 질의하지 않고 한 번에 읽는다. 칸 하나가 아니라 사용자 자체를 담는 것은
		// 필요한 칸이 늘 때마다 맵이 하나씩 늘지 않게 하기 위해서다.
		Map<UUID, AppUser> profiles = this.appUserRepository
				.findAllById(members.stream().map(m -> UUID.fromString(m.userId())).toList())
				.stream()
				.collect(Collectors.toMap(AppUser::getUserId, user -> user));

		List<TripMembersResponse.Member> dtos = members.stream()
				.sorted(Comparator
						.comparing((TripMember m) -> m.role() == TripMember.Role.OWNER ? 0 : 1)
						.thenComparing(TripMember::joinedAt))
				.map(m -> toMemberDto(m, requesterId, profiles))
				.toList();

		return new TripMembersResponse(dtos, view.role().name(), view.role().canEdit());
	}

	/**
	 * 참여자의 역할을 바꾼다. 소유자만 부를 수 있다.
	 *
	 * @throws TripQueryService.TripNotFoundException 요청자가 그 여행의 회원이 아니다 — 404.
	 * @throws TripMemberForbiddenException 요청자가 회원이지만 소유자가 아니다 — 403.
	 * @throws TripMemberNotFoundException 대상이 그 여행의 회원이 아니다 — 404.
	 * @throws IllegalArgumentException 대상이 OWNER 이거나 새 역할이 OWNER 이거나 role 값이
	 *     {@code EDITOR}·{@code VIEWER} 가 아니다 — 400.
	 */
	public TripMembersResponse.Member changeRole(String tripId, String requesterId, String targetUserId,
			String newRoleRaw) {
		requireOwner(tripId, requesterId, "여행 소유자만 역할을 바꿀 수 있어요.");

		TripMember target = this.membershipRepository.findMember(tripId, targetUserId)
				.orElseThrow(TripMemberNotFoundException::new);

		TripMember.Role newRole = parseRole(newRoleRaw);
		// 결과를 버리는 호출이다. "소유자는 못 바꾼다"·"소유자로는 못 바꾼다" 검증만 태운다.
		target.withRole(newRole);

		TripMember updated = this.membershipRepository.changeRole(tripId, targetUserId, newRole)
				.orElseThrow(TripMemberNotFoundException::new);

		Map<UUID, AppUser> profile = this.appUserRepository.findById(UUID.fromString(targetUserId))
				.map(user -> Map.of(user.getUserId(), user))
				.orElseGet(Map::of);
		return toMemberDto(updated, requesterId, profile);
	}

	/**
	 * 참여자를 뺀다. 소유자만 부를 수 있다.
	 *
	 * @throws TripQueryService.TripNotFoundException 요청자가 그 여행의 회원이 아니다 — 404.
	 * @throws TripMemberForbiddenException 요청자가 회원이지만 소유자가 아니다 — 403.
	 * @throws TripMemberNotFoundException 대상이 그 여행의 회원이 아니다 — 404.
	 * @throws IllegalArgumentException 대상이 OWNER 다 — 소유자는 뺄 수 없다 — 400.
	 */
	public void remove(String tripId, String requesterId, String targetUserId) {
		requireOwner(tripId, requesterId, "여행 소유자만 참여자를 뺄 수 있어요.");

		TripMember target = this.membershipRepository.findMember(tripId, targetUserId)
				.orElseThrow(TripMemberNotFoundException::new);
		if (target.role() == TripMember.Role.OWNER) {
			throw new IllegalArgumentException("소유자는 뺄 수 없어요.");
		}

		this.membershipRepository.remove(tripId, targetUserId);
	}

	private void requireOwner(String tripId, String requesterId, String forbiddenMessage) {
		TripQueryService.View view = this.tripQueryService.get(tripId, requesterId);
		if (view.role() != TripMember.Role.OWNER) {
			throw new TripMemberForbiddenException(forbiddenMessage);
		}
	}

	private TripMember.Role parseRole(String raw) {
		try {
			return TripMember.Role.valueOf(raw == null ? "" : raw.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("role 은 EDITOR 또는 VIEWER 여야 해요: " + raw);
		}
	}

	private TripMembersResponse.Member toMemberDto(TripMember member, String requesterId,
			Map<UUID, AppUser> profiles) {
		// 탈퇴 등으로 app_user 행이 없으면 이름도 사진도 없다 — 그 경우를 null 둘로 그대로 내보낸다.
		AppUser profile = profiles.get(UUID.fromString(member.userId()));
		return new TripMembersResponse.Member(
				member.userId(),
				profile == null ? null : profile.getDisplayName(),
				member.role().name(),
				member.joinedAt().toString(),
				member.invitedBy(),
				member.invitedAt() == null ? null : member.invitedAt().toString(),
				member.userId().equals(requesterId),
				profile == null ? null : profile.getAvatarUrl());
	}

	public static class TripMemberForbiddenException extends RuntimeException {
		public TripMemberForbiddenException(String message) {
			super(message);
		}
	}

	public static class TripMemberNotFoundException extends RuntimeException {
		public TripMemberNotFoundException() {
			super("그 여행의 참여자가 아닙니다.");
		}
	}
}
