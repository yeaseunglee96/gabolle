package com.gabolle.backend.user.application;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.user.domain.ConsentStatus;
import com.gabolle.backend.user.domain.ConsentType;
import com.gabolle.backend.user.repository.UserConsentRepository;

/**
 * 받아 둔 동의를 실제로 검사한다 — {@code PRECISE_LOCATION} 과 {@code HEALTH_CONSTRAINTS} 는
 * 동의 표가 유일한 근거라 여기서 본다. 행동 개인화는 {@code app_user.personalization_mode} 가
 * 켜고 끄는 값이라 다른 자리에서 판정한다.
 *
 * <p>방침 판은 보지 않는다. {@code user_consent} 는 {@code (user_id, consent_type,
 * policy_version)} 이 유일해 판마다 행이 쌓이는데, 판까지 맞춰 찾으면 방침이 올라가는 날
 * 이미 동의한 사람 전원이 조용히 미동의가 된다. 여기서는 가장 최근 결정 하나만 보고,
 * {@code REVOKED} 가 더 나중이면 그것이 이긴다.
 *
 * <p>거절은 401 이 아니라 403 이다 — 로그인은 돼 있고 동의만 없는 상태라, 401 로 내보내면
 * 앱이 토큰을 갱신하러 갔다가 같은 자리에서 다시 막힌다. 코드를 항목별로 따로 두는 것은
 * 앱이 응답만 보고 어느 동의 화면으로 보낼지 정할 수 있어야 하기 때문이다.
 *
 * <p>{@code @Profile} 을 붙이지 않는다. 이것을 쓰는 {@code TripCreationService} 가 프로필
 * 없이 {@code no-db} 에서도 떠야 해서, 여기에 프로필을 붙이면 그 컨텍스트가 기동하지 못한다.
 * 저장소도 같은 이유로 필수 인자가 아니라 {@link ObjectProvider} 로 늦게 찾는다.
 *
 * <p>저장소가 없으면 통과시키지 않고 그 자리에서 터뜨린다. 조용히 허용하면 그 프로필로 띄운
 * 서버는 동의 없이 민감정보를 받는 서버가 된다.
 */
@Component
public class ConsentGuard {

	private final ObjectProvider<UserConsentRepository> consents;

	public ConsentGuard(ObjectProvider<UserConsentRepository> consents) {
		this.consents = consents;
	}

	/** 그 항목의 가장 최근 결정이 {@code GRANTED} 인가. 결정한 적이 없으면 {@code false}. */
	@Transactional(readOnly = true)
	public boolean isGranted(UUID userId, ConsentType type) {
		if (userId == null) {
			return false;
		}
		return repository().findFirstByUserUserIdAndConsentTypeOrderByDecidedAtDesc(userId, type)
				.filter(consent -> consent.getStatus() == ConsentStatus.GRANTED)
				.isPresent();
	}

	private UserConsentRepository repository() {
		UserConsentRepository repository = this.consents.getIfAvailable();
		if (repository == null) {
			throw new IllegalStateException(
					"동의 표가 없는 프로필(no-db)에서는 동의를 확인할 수 없다. 이 경로는 db·dev 프로필에서만 쓴다");
		}
		return repository;
	}

	/**
	 * 정밀 위치 동의 — 좌표를 저장하거나 좌표로 사람에 대한 판정을 남기는 자리에서 부른다.
	 *
	 * <p>방문 인증({@code place_visit_verification})이 이 검사를 거친다 — 기기 GPS 로 판정해
	 * 사람마다 {@code distance_m} 를 남기는 자리다. {@code GET /api/v1/places/nearby} 는
	 * 막지 않는다. 아무것도 저장하지 않는 조회이고, 손으로 찍은 좌표와 GPS 좌표가 같은 모양으로
	 * 와서 서버가 출처를 가릴 수 없다.
	 *
	 * <p>TODO 아직 막지 못하는 자리 둘 — 여행 출발지가 현재 위치일 때
	 * ({@code trip.origin_source = 'CURRENT_LOCATION'}, 애플리케이션이 그 칸을 안 쓴다)와
	 * 현재 위치 기반 추천({@code RequestLocation.LocationSource#GPS}, 어떤 HTTP 경로도 살아
	 * 있는 좌표를 넘기지 않는다). 그 경로가 생기면 여기를 부른다.
	 */
	public void requirePreciseLocation(UUID userId) {
		require(userId, ConsentType.PRECISE_LOCATION, "PRECISE_LOCATION_CONSENT_REQUIRED",
				"정밀 위치 정보 사용에 동의해야 이용할 수 있습니다.");
	}

	/**
	 * 건강·식이 제약 동의 — 민감정보를 저장하는 자리에서 부른다.
	 *
	 * <p>무엇이 민감한가는 {@code TripConstraint.isSensitive} 가 정한다. 여기서 목록을 다시
	 * 적지 않는다 — 두 벌이 되면 한쪽만 늘어나고, 그 어긋남은 그 종류만 동의 없이 저장되는
	 * 모양으로 나타난다.
	 *
	 * <p>{@code TripConstraint} 생성자가 민감 종류의 자유 입력을 거부하는 것과는 다른 검사다.
	 * 그쪽이 막는 것은 평문 보관이고, 여기서 막는 것은 동의 없는 수집이다. 코드로 된 값은
	 * 그쪽을 통과하지만 그것도 건강에 관한 민감정보다.
	 */
	public void requireHealthConstraints(UUID userId) {
		require(userId, ConsentType.HEALTH_CONSTRAINTS, "HEALTH_CONSENT_REQUIRED",
				"건강·식이 정보 사용에 동의해야 이 항목을 저장할 수 있습니다.");
	}

	/**
	 * AI 도우미가 내 여행 일정을 읽고 답하는 것에 대한 동의.
	 *
	 * <p>사용자가 그 자리에서 직접 쓴 채팅 메시지는 이 동의 없이도 벤더에 넘어간다. 여기서
	 * 막는 것은 이미 만들어 둔 일정을 서버가 대신 꺼내 벤더에 얹는 것이다.
	 */
	public void requireAiAssistantAccess(UUID userId) {
		require(userId, ConsentType.AI_ASSISTANT_ACCESS, "AI_ASSISTANT_ACCESS_CONSENT_REQUIRED",
				"AI 도우미가 내 여행 일정을 참고하게 하려면 동의가 필요합니다.");
	}

	private void require(UUID userId, ConsentType type, String code, String message) {
		if (!isGranted(userId, type)) {
			throw new AuthException(code, message, HttpStatus.FORBIDDEN);
		}
	}
}
