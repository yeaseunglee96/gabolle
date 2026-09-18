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
 * 받아 둔 동의를 <b>실제로 검사한다</b> — S15P21E201-549.
 *
 * <h2>🔴 여기 있기 전에는 아무도 안 봤다</h2>
 *
 * {@code ConsentType} 다섯 중 셋({@code BEHAVIOR_PERSONALIZATION} ·
 * {@code PRECISE_LOCATION} · {@code HEALTH_CONSTRAINTS})은 <b>동의를 받고 기록까지 하는데
 * 그 값을 판정에 쓰는 코드가 없었다.</b> 화면에는 스위치가 있고 표에는 행이 쌓이는데 뒤가
 * 비어 있었다.
 *
 * <p>그건 기능이 없는 것보다 나쁘다. 개인정보 처리방침과 스토어 제출 양식(Google Play
 * <b>데이터 안전</b> · Apple <b>App Privacy</b>)에 "동의를 받은 경우에만 처리합니다" 라고
 * 적는 순간, 검사하지 않는 동의는 <b>사실이 아닌 고지</b>가 된다. 심사자는 두 번 눌러
 * 확인할 수 있다.
 *
 * <p>행동 개인화는 켜고 끄는 값이 {@code app_user.personalization_mode} 라는 별도 칸에
 * 있어 다른 자리에서 판정한다({@code EventIngestService} · {@code TasteVectorFoldService}).
 * 나머지 둘은 동의 표가 유일한 근거라 여기서 본다.
 *
 * <h2>🔴 "지금 동의했는가" 를 어떻게 정하나 — 방침 판을 안 본다</h2>
 *
 * {@code user_consent} 는 {@code (user_id, consent_type, policy_version)} 이 유일해서 방침 판마다
 * 행이 하나씩 쌓인다. 여기서는 <b>판을 가리지 않고 가장 최근 결정 하나</b>를 본다.
 *
 * <p>판까지 맞춰 찾으면, 방침이 새 판으로 올라가는 날 <b>이미 동의한 사람 전원이 조용히
 * 미동의로 바뀐다.</b> 기능이 그날부터 403 을 내는데 코드는 아무것도 안 바뀌었고, 원인을
 * 찾는 사람은 배포 이력을 본다. 다시 물어보는 것은 화면이 할 일이지 서버가 기능을 끊는
 * 방식으로 강제할 일이 아니다.
 *
 * <p>{@code REVOKED} 가 더 나중이면 그것이 이긴다 — 철회가 옛 동의에 덮이지 않는다.
 *
 * <h2>🔴 401 이 아니라 403 이다</h2>
 *
 * 로그인은 돼 있고 <b>이 동작에 필요한 동의</b>가 없는 상태다. 401 로 내보내면 앱이 토큰을
 * 갱신하러 갔다가 같은 자리에서 다시 막힌다. 코드를 항목별로 따로 두는 이유는 앱이 응답만
 * 보고 <b>어느 동의 화면으로 보낼지</b> 정할 수 있어야 하기 때문이다 — 일반적인 403 과
 * 구분되지 않으면 "권한이 없습니다" 만 띄우고 사용자는 어디를 눌러야 하는지 모른다.
 *
 * <h2>🔴 {@code @Profile} 을 안 붙이고 저장소를 늦게 찾는 이유</h2>
 *
 * DB 를 쓰는 빈은 보통 {@code @Profile({"db","dev"})} 로 가른다. <b>이 클래스는 그럴 수
 * 없다.</b> {@code TripCreationService} 가 이것을 쓰는데 그쪽은 프로필이 없다 —
 * {@code no-db} 에서도 {@code InMemoryTripRepository} 로 여행이 만들어져야 하기 때문이다.
 * 여기에 프로필을 붙이면 {@code no-db} 컨텍스트가 <b>기동조차 못 한다</b>(만들면서 실제로 한 번
 * 그렇게 깨졌고, 기능 검사가 아니라 {@code GabolleBackendApplicationTests.contextLoads} 가 잡았다).
 *
 * <p>그렇다고 저장소를 필수 인자로 두면 같은 문제다. 그래서 {@link ObjectProvider} 로 <b>있으면
 * 쓰고 없으면 그 자리에서 터뜨린다.</b>
 *
 * <p>🔴 <b>없을 때 통과시키지 않는다.</b> 동의 표가 없는 프로필에서 조용히 허용하면, 그
 * 프로필로 띄운 서버는 <b>동의 없이 민감정보를 받는 서버</b>가 된다. {@code no-db} 는 개발용이지만
 * 개발용으로 켜 둔 것이 그대로 시연에 나가는 일은 흔하다. 못 하는 것은 못 한다고 터지는 편이
 * 낫다 — 터지면 그 자리에서 보이고, 조용히 통과하면 아무도 모른다.
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
	 * <h2>무엇을 막고 무엇을 안 막는가</h2>
	 * <ul>
	 * <li>✅ <b>방문 인증</b>({@code place_visit_verification}) — "지금 여기 있다" 를 기기 GPS 로
	 *     판정해 사람마다 {@code distance_m} 를 남긴다.
	 *     {@code docs/recommendation-data-collection-p0.md} 11.4 의 <i>"위치 미동의 사용자의
	 *     방문 여부를 추측해서 채우지 않는다"</i> 가 정확히 이 자리다</li>
	 * <li>❌ <b>{@code GET /api/v1/places/nearby}</b> — 막지 <b>않는다.</b> 아무것도 저장하지 않는
	 *     조회이고, 좌표의 출처를 서버가 알 수 없다(지도를 손으로 옮겨 찍은 좌표와 GPS 좌표가
	 *     같은 모양으로 온다). 11.2 가 <i>"위치 권한을 거부하면 수동 위치 입력으로 동일 기능을
	 *     제공한다"</i> 고 했으므로, 여기서 막으면 <b>문서가 약속한 대체 경로를 우리가 끊는
	 *     것</b>이 된다</li>
	 * </ul>
	 *
	 * <h2>🔴 아직 못 막는 자리 둘 — 생기면 여기를 부르십시오</h2>
	 * <ol>
	 * <li><b>여행 출발지가 현재 위치일 때</b>({@code trip.origin_source = 'CURRENT_LOCATION'}).
	 *     표에는 칸이 있는데 애플리케이션이 그 값을 한 번도 안 쓴다 —
	 *     {@code CreateTripRequest} 에 {@code originSource} 가 없고 {@code TripJpaEntity} 도 그
	 *     컬럼을 일부러 매핑하지 않는다. 그래서 서버는 지금 "주소로 찍은 좌표" 와 "현재 위치"
	 *     를 <b>구분할 방법이 없다.</b> 구분 없이 막으면 주소로 여행을 만드는 사람까지 막힌다</li>
	 * <li><b>현재 위치 기반 추천</b>({@code RequestLocation.LocationSource#GPS}). 지금
	 *     {@code RequestLocation} 은 {@code trip.originLat/Lng} 에서만 만들어지고
	 *     ({@code BaselineRecommendationEngine}) 어떤 HTTP 경로도 살아 있는 좌표를 넘기지 않는다</li>
	 * </ol>
	 */
	public void requirePreciseLocation(UUID userId) {
		require(userId, ConsentType.PRECISE_LOCATION, "PRECISE_LOCATION_CONSENT_REQUIRED",
				"정밀 위치 정보 사용에 동의해야 이용할 수 있습니다.");
	}

	/**
	 * 건강·식이 제약 동의 — <b>민감정보</b>를 저장하는 자리에서 부른다.
	 *
	 * <h2>🔴 무엇이 민감한가는 {@code TripConstraint.isSensitive} 가 정한다</h2>
	 *
	 * 알레르기, 그리고 의료·종교상 <b>반드시 지켜야 하는</b> 식단({@code DIET} +
	 * {@code dietRequirement=REQUIRED})이다. 여기서 목록을 다시 적지 않는다 — 두 벌이 되면
	 * 한쪽만 늘어나고, 그 어긋남은 "이 종류만 동의 없이 저장되는" 모양으로 나타난다.
	 *
	 * <p>🔴 <b>자유 입력이 이미 막혀 있다고 이 검사가 필요 없는 것이 아니다.</b>
	 * {@code TripConstraint} 생성자는 민감 종류의 자유 입력({@code constraintKey="OTHER"})만
	 * 거부한다 — 암호화 칸이 아직 없어서다. <b>코드로 된 값</b>({@code ALLERGY}+{@code PEANUT})
	 * 은 그대로 저장되고, 그것도 개인정보보호법이 말하는 건강에 관한 민감정보다.
	 * 거부되는 것은 평문 보관 위험이고, 여기서 막는 것은 <b>동의 없는 수집</b>이다. 다른 문제다.
	 */
	public void requireHealthConstraints(UUID userId) {
		require(userId, ConsentType.HEALTH_CONSTRAINTS, "HEALTH_CONSENT_REQUIRED",
				"건강·식이 정보 사용에 동의해야 이 항목을 저장할 수 있습니다.");
	}

	/**
	 * AI 도우미가 내 여행 일정을 읽고 답하는 것에 대한 동의 — S15P21E201-987.
	 *
	 * <p>일반 채팅 메시지({@code AssistantChatService.chat} 의 {@code message})는 사용자가
	 * 그 자리에서 직접 쓴 것이라 이 동의 없이도 벤더에 넘어간다({@code -802} 범위). 여기서
	 * 막는 것은 그것과 다르다 — 사용자가 이미 만들어 둔 일정(장소·시각 등)을 서버가 <b>대신
	 * 꺼내서</b> 벤더에 얹는 것이다. 자기가 방금 입력한 문장과 서버가 조회해 얹는 데이터는
	 * 다른 종류의 노출이라 별도 동의로 가른다.
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
