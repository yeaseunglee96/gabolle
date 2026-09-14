package com.gabolle.backend.trip.presentation.dto;

import java.util.List;

/**
 * 참여자 목록 응답 — S15P21E201-320(진미리 님 FE 블로커).
 *
 * <p>{@code myRole}·{@code canEdit} 은 요청자 본인 기준이다 — 화면이 "역할 바꾸기" 버튼을
 * 보일지 말지를 매 참여자마다 판정하지 않고 이 두 값만 보고 정하게 하기 위해서다.
 */
public record TripMembersResponse(List<Member> members, String myRole, boolean canEdit) {

	/**
	 * 참여자 한 명.
	 *
	 * <p>🔴 역할 변경({@code PATCH .../members/{userId}}) 응답도 이 record 를 그대로 쓴다 —
	 * 그 응답이 필요로 하는 것은 {@code userId · role · joinedAt} 뿐이지만 같은 모양을
	 * 새로 만들지 않는다.
	 */
	public record Member(
			String userId,
			/** {@code app_user} 행이 없으면(탈퇴 등) {@code null}. */
			String displayName,
			String role,
			String joinedAt,
			/** 초대한 사람의 사용자 ID. 소유자이거나 초대 흔적이 없는 옛 행이면 {@code null}. */
			String invitedBy,
			String invitedAt,
			boolean isMe,
			/**
			 * 프로필 사진 주소 (S15P21E201-844). 안 골랐거나 {@code app_user} 행이 없으면 {@code null}
			 * 이고, 화면은 {@code displayName} 과 같은 규칙으로 기본 그림을 그린다.
			 */
			String avatarUrl) {
	}
}
