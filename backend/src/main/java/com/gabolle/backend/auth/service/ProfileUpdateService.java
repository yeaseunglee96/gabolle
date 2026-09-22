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
import com.gabolle.backend.story.domain.UploadedImage;
import com.gabolle.backend.story.repository.UploadedImageRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * {@code PATCH /api/v1/auth/me} 를 처리한다 — 이름·언어·사진 부분 수정.
 *
 * <p>조회는 {@link CurrentUserService#get(UUID)} 를 재사용해 {@code GET /api/v1/auth/me} 와 같은
 * 모양을 돌려준다.
 *
 * <p>엔티티를 불러와 고치고 변경 감지에 맡긴다. JPQL 벌크 {@code UPDATE} 는 영속성 컨텍스트를
 * 우회하므로, 수정 → 조회 → 응답 순으로 도는 이 메서드에서는 1차 캐시에 남은 옛 이름이 나갈 수
 * 있다. {@code @PreUpdate} 도 안 타서 {@code updatedAt} 을 손으로 채워야 한다.
 */
@Service
@Profile({"db", "dev"})
public class ProfileUpdateService {

	private final AppUserRepository userRepository;

	private final CurrentUserService currentUserService;

	/**
	 * 프로필 사진 주소로 받아들일 접두사들. 임의의 외부 주소를 허용하면 사진이 뜨는 화면을 연
	 * 사람들의 접속 기록이 우리가 통제하지 못하는 서버에 남고, 사진도 언제든 바뀔 수 있다.
	 *
	 * <p>값은 저장소 설정에서 그대로 읽는다 — 여기에 주소를 새로 적으면 저장소를 옮길 때 한쪽만
	 * 고쳐지는 순간 멀쩡히 올린 사진이 거부된다. 둘 중 지금 켜져 있는 쪽만 값이 차므로 빈 값은
	 * 목록에서 뺀다.
	 *
	 * <p>목록이 비면 전부 거부한다 — 설정이 빠진 채로 통과시키면 아무 주소나 들어온다.
	 */
	private final List<String> allowedAvatarUrlPrefixes;

	/**
	 * 커버 사진 주소가 본인이 올린 것인지 보려면 업로드 표를 봐야 한다.
	 *
	 * <p>프로필 사진이 쓰는 앞부분 검사로는 부족하다. 그 검사는 우리 서버에서 나온 주소인지만
	 * 답하는데, 남이 올린 사진도 우리 서버 주소라 주소만 알아내면 통과한다. 업로드 표 조회는
	 * 올린 사람까지 보고, 표에 없는 외부 주소는 조회 자체가 안 되므로 앞부분 검사를 겸한다.
	 */
	private final UploadedImageRepository uploadedImageRepository;

	public ProfileUpdateService(AppUserRepository userRepository, CurrentUserService currentUserService,
			UploadedImageRepository uploadedImageRepository,
			@Value("${gabolle.storage.public-base-path:}") String storagePublicBasePath,
			@Value("${gabolle.storage.s3.public-base-url:}") String s3PublicBaseUrl) {
		this.userRepository = userRepository;
		this.currentUserService = currentUserService;
		this.uploadedImageRepository = uploadedImageRepository;
		this.allowedAvatarUrlPrefixes = Stream.of(storagePublicBasePath, s3PublicBaseUrl)
				.filter(prefix -> prefix != null && !prefix.isBlank())
				.map(String::trim)
				.toList();
	}

	@Transactional
	public AuthUserResponse update(UUID userId, UpdateProfileRequest request) {
		// 빈 이름은 "지운다" 가 아니라 잘못된 요청이다. 컨트롤러의 Bean Validation 이 이미 막지만,
		// 이 서비스가 다른 경로에서 재사용될 때도 이름 없는 계정이 생기지 않게 한 번 더 막는다.
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
		// 커버 사진도 「빈 문자열 = 뗀다」로 같다. 다만 받아들이는 기준이 더 좁다 — 아래 참고.
		if (request.removesCover()) {
			user.changeCoverUrl(null);
		}
		else if (request.coverUrl() != null) {
			user.changeCoverUrl(requireOwnUploadUrl(userId, request.coverUrl()));
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

	/**
	 * 본인이 올린 사진의 주소만 통과시킨다.
	 *
	 * <p>두 거절을 다른 코드로 나눈다 — 「올라가 있지 않다」는 다시 올리면 되고 「남의 것이다」는
	 * 그 사진을 못 쓴다. 주소에 무작위 식별자가 들어 있어 이 구분으로 남의 사진을 훑을 수는 없다.
	 *
	 * <p>둘 다 400 이다. 403 으로 답하면 화면이 로그인이 풀린 것으로 읽고 사용자를 로그아웃시킨다.
	 */
	private String requireOwnUploadUrl(UUID userId, String coverUrl) {
		String trimmed = coverUrl.trim();
		UploadedImage upload = this.uploadedImageRepository.findByImageUrlIn(List.of(trimmed)).stream()
				.filter(candidate -> !candidate.isDeleted())
				.findFirst()
				.orElseThrow(() -> new AuthException("COVER_URL_NOT_UPLOADED",
						"올라가 있지 않은 사진 주소입니다.", HttpStatus.BAD_REQUEST));
		if (!upload.isOwnedBy(userId)) {
			// 주소를 알아내도 남이 올린 것은 못 쓴다 — 기록에 사진 붙이는 길과 같은 규칙이다.
			throw new AuthException("COVER_URL_NOT_OWNED", "내가 올린 사진만 커버로 쓸 수 있습니다.",
					HttpStatus.BAD_REQUEST);
		}
		return trimmed;
	}
}
