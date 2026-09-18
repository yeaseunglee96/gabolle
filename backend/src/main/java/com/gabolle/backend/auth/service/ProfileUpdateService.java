package com.gabolle.backend.auth.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.auth.api.AuthUserResponse;
import com.gabolle.backend.auth.api.UpdateProfileRequest;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * {@code PATCH /api/v1/auth/me} 를 처리한다 — 이름·언어 부분 수정 (S15P21E201-423).
 *
 * <p>조회는 새로 만들지 않고 {@link CurrentUserService#get(UUID)} 를 그대로 재사용해서, 수정 뒤
 * {@code GET /api/v1/auth/me} 와 같은 모양의 {@link AuthUserResponse} 를 돌려준다. 프런트가 수정
 * 뒤 다시 조회하지 않아도 된다.
 *
 * <p>🔴 엔티티를 불러와 고치고 변경 감지에 맡긴다. JPQL 벌크 {@code UPDATE} 로 쓰는 방법도 있는데
 * 그쪽은 영속성 컨텍스트를 우회한다 — 같은 트랜잭션 안에서 뒤이어 읽으면 <b>1차 캐시에 남은 옛
 * 값</b>이 나올 수 있고, 그러면 방금 이름을 바꾼 사용자에게 바뀌기 전 이름을 응답하게 된다. 이
 * 메서드가 정확히 그 순서(수정 → 조회 → 응답)로 돌기 때문에 그 위험이 실재한다. 벌크 UPDATE 는
 * {@code @PreUpdate} 도 안 타서 {@code updatedAt} 을 손으로 채워야 하는 문제도 따라온다.
 */
@Service
@Profile({"db", "dev"})
public class ProfileUpdateService {

	private final AppUserRepository userRepository;

	private final CurrentUserService currentUserService;

	/**
	 * 프로필 사진 주소로 받아들일 접두사들 (S15P21E201-844).
	 *
	 * <p>🔴 <b>아무 주소나 받으면 안 된다.</b> 임의의 외부 주소를 넣을 수 있으면 프로필 사진이 우리가
	 * 통제하지 못하는 서버를 가리키게 되고, 그 사진이 뜨는 화면을 연 사람들의 접속 기록이 그쪽에
	 * 남는다. 사진 자체도 언제든 다른 것으로 바뀔 수 있다.
	 *
	 * <p>값은 저장소 설정에서 그대로 읽는다. 여기에 주소를 새로 적어 두면 저장소를 옮길 때 두 곳을
	 * 맞춰야 하고, 한쪽만 고쳐지는 순간 <b>멀쩡히 올린 사진이 거부된다.</b> 로컬 저장소는 경로
	 * ({@code /api/v1/uploads/images})를, S3 는 절대 주소를 낸다 — 둘 중 지금 켜져 있는 쪽만 값이
	 * 차므로 빈 값은 목록에서 뺀다.
	 *
	 * <p>🔴 목록이 비면 <b>전부 거부</b>한다. 설정이 빠진 채로 통과시키면 그때부터 아무 주소나
	 * 들어오고, 그 구멍은 아무 검사도 못 잡는다.
	 */
	private final List<String> allowedAvatarUrlPrefixes;

	public ProfileUpdateService(AppUserRepository userRepository, CurrentUserService currentUserService,
			@Value("${gabolle.storage.public-base-path:}") String storagePublicBasePath,
			@Value("${gabolle.storage.s3.public-base-url:}") String s3PublicBaseUrl) {
		this.userRepository = userRepository;
		this.currentUserService = currentUserService;
		this.allowedAvatarUrlPrefixes = Stream.of(storagePublicBasePath, s3PublicBaseUrl)
				.filter(prefix -> prefix != null && !prefix.isBlank())
				.map(String::trim)
				.toList();
	}

	@Transactional
	public AuthUserResponse update(UUID userId, UpdateProfileRequest request) {
		// 🔴 빈 이름은 "지운다" 가 아니라 잘못된 요청이다. Bean Validation(@Size(min = 1))이 컨트롤러
		//    경계에서 이미 막지만, 이 서비스가 다른 경로에서 재사용될 때도 이름 없는 계정이 생기지
		//    않도록 여기서 한 번 더 막는다.
		if (request.displayName() != null && request.displayName().isBlank()) {
			throw new AuthException("DISPLAY_NAME_BLANK", "이름을 빈 값으로 바꿀 수 없습니다.", HttpStatus.BAD_REQUEST);
		}

		AppUser user = this.userRepository.findById(userId)
				.filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
				.orElseThrow(() -> new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.",
						HttpStatus.UNAUTHORIZED));

		// 보내지 않은 필드는 건드리지 않는다. null 은 "안 바꾼다" 이지 "지운다" 가 아니다.
		if (request.displayName() != null) {
			user.rename(request.displayName());
		}
		if (request.language() != null) {
			user.changeLanguage(LanguageNormalizer.normalize(request.language()));
		}
		// 빈 문자열은 "뗀다" 다 — 그 판정은 요청 쪽이 소유한다(UpdateProfileRequest.removesAvatar).
		if (request.removesAvatar()) {
			user.changeAvatarUrl(null);
		}
		else if (request.avatarUrl() != null) {
			user.changeAvatarUrl(requireAllowedAvatarUrl(request.avatarUrl()));
		}

		CurrentUserService.CurrentUser currentUser = this.currentUserService.get(userId);
		return AuthUserResponse.from(currentUser.user(), currentUser.email());
	}

	/** 우리 업로드 자리에서 나온 주소만 통과시킨다 — 근거는 {@link #allowedAvatarUrlPrefixes} 가 소유한다. */
	private String requireAllowedAvatarUrl(String avatarUrl) {
		String trimmed = avatarUrl.trim();
		boolean allowed = this.allowedAvatarUrlPrefixes.stream().anyMatch(trimmed::startsWith);
		if (!allowed) {
			throw new AuthException("AVATAR_URL_NOT_ALLOWED", "프로필 사진은 올린 사진의 주소만 쓸 수 있습니다.",
					HttpStatus.BAD_REQUEST);
		}
		return trimmed;
	}
}
