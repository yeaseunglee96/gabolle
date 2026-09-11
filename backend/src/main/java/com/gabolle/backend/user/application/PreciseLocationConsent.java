package com.gabolle.backend.user.application;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.user.domain.ConsentStatus;
import com.gabolle.backend.user.domain.ConsentType;
import com.gabolle.backend.user.repository.UserConsentRepository;

/**
 * 정밀 위치 동의를 <b>실제로 검사한다</b> — S15P21E201-549 후속.
 *
 * <h2>🔴 여기 있기 전에는 아무도 안 봤다</h2>
 *
 * {@code ConsentType.PRECISE_LOCATION} 은 2026-09-11 까지 <b>열거형과 응답 DTO 에만</b>
 * 있었다({@code grep PRECISE_LOCATION} 이 그 둘만 찾는다). 즉 동의를 받고 기록까지 하는데
 * 그 값을 판정에 쓰는 코드가 없었다 —
 * {@code docs/recommendation-data-collection-p0.md} 11.4 의 첫 줄이
 * <i>"정밀 위치 별도 동의가 없으면 수집하지 않는다"</i> 라고 못 박아 두고 있는데도 그랬다.
 *
 * <p>행동 기반 개인화 스위치가 아무것도 안 하던 것과 <b>같은 종류의 결함</b>이다. 동의 화면은
 * 있고 기록도 남는데 그 뒤가 비어 있으면, 개인정보 처리방침과 스토어 제출 양식에 적은 문장이
 * 사실이 아닌 고지가 된다.
 *
 * <h2>🔴 무엇을 막고 무엇을 안 막는가</h2>
 *
 * <b>좌표를 저장하거나 좌표에서 사람에 대한 판정을 남기는 자리</b>만 막는다.
 *
 * <ul>
 * <li>✅ <b>방문 인증</b>({@code place_visit_verification}) — "지금 여기 있다" 를 기기 GPS 로
 *     판정해 사람마다 {@code distance_m} 를 남긴다. 11.4 의
 *     <i>"위치 미동의 사용자의 방문 여부를 추측해서 채우지 않는다"</i> 가 정확히 이 자리다</li>
 * <li>❌ <b>{@code GET /api/v1/places/nearby}</b> — 막지 <b>않는다.</b> 아무것도 저장하지 않는
 *     조회이고, 좌표의 출처를 서버가 알 수 없다(지도를 손으로 옮겨 찍은 좌표와 GPS 좌표가
 *     같은 모양으로 온다). 게다가 11.2 는 <i>"위치 권한을 거부하면 수동 위치 입력으로 동일
 *     기능을 제공한다"</i> 고 했으므로, 여기서 막으면 <b>문서가 약속한 대체 경로를 우리가
 *     끊는 것</b>이 된다</li>
 * </ul>
 *
 * <h2>🔴 아직 못 막는 자리 둘 — 생기면 여기를 부르십시오</h2>
 *
 * <ol>
 * <li><b>여행 출발지가 현재 위치일 때</b>({@code trip.origin_source = 'CURRENT_LOCATION'}).
 *     표에는 칸이 있는데 <b>애플리케이션이 그 값을 한 번도 안 쓴다</b> —
 *     {@code CreateTripRequest} 에 {@code originSource} 가 없고
 *     {@code TripJpaEntity} 도 그 컬럼을 일부러 매핑하지 않는다. 그래서 서버는 지금
 *     "주소를 입력한 좌표" 와 "현재 위치 좌표" 를 <b>구분할 방법이 없다.</b> 구분 없이 막으면
 *     주소로 여행을 만드는 사람까지 막힌다. 화면이 출처를 보내기 시작하는 날 이 가드를
 *     그 자리에 붙인다</li>
 * <li><b>현재 위치 기반 추천</b>({@code RequestLocation.LocationSource#GPS}). 지금
 *     {@code RequestLocation} 은 {@code trip.originLat/Lng} 에서만 만들어지고
 *     ({@code BaselineRecommendationEngine}) 어떤 HTTP 경로도 살아 있는 좌표를 넘기지 않는다.
 *     그 경로가 생기면 {@code source == GPS} 일 때 이 가드를 지나야 한다</li>
 * </ol>
 *
 * <h2>🔴 "지금 동의했는가" 를 어떻게 정하나 — 방침 판을 안 본다</h2>
 *
 * {@code user_consent} 는 {@code (user_id, consent_type, policy_version)} 이 유일해서 방침 판마다
 * 행이 하나씩 쌓인다. 여기서는 <b>판을 가리지 않고 가장 최근 결정 하나</b>를 본다.
 *
 * <p>판까지 맞춰 찾으면, 방침이 새 판으로 올라가는 날 <b>이미 동의한 사람 전원이 조용히
 * 미동의로 바뀐다.</b> 방문 인증이 그날부터 403 을 내는데 코드는 아무것도 안 바뀌었고, 원인을
 * 찾는 사람은 배포 이력을 본다. 다시 물어보는 것은 화면이 할 일이지 서버가 기능을 끊는
 * 방식으로 강제할 일이 아니다.
 *
 * <p>{@code REVOKED} 가 더 나중이면 그것이 이긴다 — 철회가 옛 동의에 덮이지 않는다.
 *
 * <h2>🔴 {@code @Profile({"db","dev"})} 가 없으면 컨텍스트가 아예 안 뜬다</h2>
 *
 * DB 를 쓰는 빈은 인증과 같은 방식으로 프로필이 가른다({@code application.properties} 의 팀
 * 규칙). 이 애노테이션을 빼면 {@code no-db} 프로필에 {@code UserConsentRepository} 빈이 없어
 * <b>애플리케이션 전체가 기동을 못 한다</b> — 이 클래스를 만들면서 실제로 한 번 그렇게 깨졌고
 * ({@code GabolleBackendApplicationTests.contextLoads}), 기능 검사가 아니라 그 검사가 잡았다.
 */
@Component
@Profile({ "db", "dev" })
public class PreciseLocationConsent {

	private final UserConsentRepository consents;

	public PreciseLocationConsent(UserConsentRepository consents) {
		this.consents = consents;
	}

	/** 가장 최근 결정이 {@code GRANTED} 인가. 결정한 적이 없으면 {@code false}. */
	@Transactional(readOnly = true)
	public boolean isGranted(UUID userId) {
		if (userId == null) {
			return false;
		}
		return this.consents
				.findFirstByUserUserIdAndConsentTypeOrderByDecidedAtDesc(userId, ConsentType.PRECISE_LOCATION)
				.filter(consent -> consent.getStatus() == ConsentStatus.GRANTED)
				.isPresent();
	}

	/**
	 * 동의가 없으면 403 으로 끊는다.
	 *
	 * <p>🔴 401 이 아니라 403 이다. 로그인은 돼 있고 <b>이 동작에 필요한 동의</b>가 없는
	 * 상태라, 401 로 내보내면 앱이 토큰을 갱신하러 갔다가 같은 자리에서 다시 막힌다.
	 *
	 * <p>🔴 코드({@code PRECISE_LOCATION_CONSENT_REQUIRED})를 구체적으로 두는 이유는 앱이
	 * 이 응답만 보고 <b>동의 화면으로 보낼 수 있어야</b> 하기 때문이다. 일반적인 403 과
	 * 구분되지 않으면 앱은 "권한이 없습니다" 만 띄우고 사용자는 어디를 눌러야 하는지 모른다.
	 */
	public void requireGranted(UUID userId) {
		if (!isGranted(userId)) {
			throw new AuthException("PRECISE_LOCATION_CONSENT_REQUIRED",
					"정밀 위치 정보 사용에 동의해야 이용할 수 있습니다.", HttpStatus.FORBIDDEN);
		}
	}
}
