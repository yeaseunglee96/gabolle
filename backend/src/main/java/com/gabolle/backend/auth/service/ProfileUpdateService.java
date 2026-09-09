package com.gabolle.backend.auth.service;

import java.util.UUID;

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

	public ProfileUpdateService(AppUserRepository userRepository, CurrentUserService currentUserService) {
		this.userRepository = userRepository;
		this.currentUserService = currentUserService;
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

		CurrentUserService.CurrentUser currentUser = this.currentUserService.get(userId);
		return AuthUserResponse.from(currentUser.user(), currentUser.email());
	}
}
