package com.gabolle.backend.functional.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.auth.api.AuthTokenResponse;
import com.gabolle.backend.auth.api.LocalSignupRequest;
import com.gabolle.backend.auth.api.LoginRequest;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.user.repository.AppUserRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기능 테스트(functional test)의 공통 베이스 — S15P21E201-779.
 *
 * <h2>왜 슬라이스가 아니라 전체 앱인가</h2>
 * 기존 슬라이스 테스트({@code AuthSliceApplication} 등)는 도메인 하나만 스캔한다 — 애초에 여러
 * 도메인을 잇는 용도가 아니다. 이 클래스는 {@code DevProfileApplicationContextTest}
 * (S15P21E201-575)가 이미 겪고 고친 세 가지 실수를 그대로 물려받는다 — {@code GABOLLE_DB_SCHEMA}로
 * 스키마 이름을 지정하는 것, {@code spring.flyway.create-schemas=false}를 우회하지 않고
 * {@code @BeforeAll}에서 DB 관리자 역할을 대신하는 것, 기본값이 없는 설정을 직접 채우는 것.
 * 새로 더하는 것은 {@code webEnvironment = RANDOM_PORT} 하나뿐이다 — 그래야
 * {@link TestRestTemplate}이 진짜 소켓으로 나가고, MockMvc는 절대 안 통과하는 실제
 * {@code SecurityFilterChain}을 통과한다.
 *
 * <h2>🔴 프로퍼티를 여정마다 바꾸지 않는다</h2>
 * 지금 이 저장소에 {@code RANDOM_PORT}를 쓰는 테스트가 이 클래스가 유일하다. 이 클래스를
 * extends하는 모든 여정 테스트는 <b>같은 프로퍼티·같은 프로필</b>을 써야 한다 — 하나라도 다르게
 * 주면 그 순간 Spring이 컨텍스트를 새로 캐시하고 앱이 여러 번 뜬다. 여정마다 다른 데이터가
 * 필요하면 프로퍼티가 아니라 <b>이 클래스가 제공하는 헬퍼 호출 인자</b>로 달리한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.profiles.active=dev",
		"GABOLLE_DB_SCHEMA=functionaltest",
		"spring.mail.host=127.0.0.1",
		"gabolle.auth.jwt-secret=0123456789abcdef0123456789abcdef" })
@ExtendWith(PostgresAvailableCondition.class)
@AutoConfigureTestRestTemplate
public abstract class FunctionalJourneyTest {

	private static final String SCHEMA = "functionaltest";

	@Autowired
	protected TestRestTemplate rest;

	@Autowired
	private LocalCredentialRepository credentialRepository;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	/**
	 * 운영에서 DB 관리자가 미리 하는 일을 대신한다({@code spring.flyway.create-schemas=false}).
	 * {@code gabolle}이 아니라 {@link #SCHEMA}를 쓰므로 $user(접속 계정 이름)와 안 겹치고,
	 * 끝나고 지울 필요가 없다 — DROP 자체가 위험하다는 것이 이미 한 번 증명됐다(S15P21E201-575).
	 */
	@BeforeAll
	static void createSchema() throws Exception {
		try (Connection connection = DriverManager.getConnection(TestDatabase.url(), TestDatabase.username(),
				TestDatabase.password()); Statement statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
		}
	}

	private final List<String> createdEmails = new ArrayList<>();

	/**
	 * 회원가입 + 이메일 인증 + 로그인을 한 번에 처리한다.
	 *
	 * <p>🔴 회원가입만으로는 로그인이 안 된다 — {@code LocalAuthService.login()}이
	 * {@code emailVerifiedAt == null}이면 거부한다. 실제 이메일을 못 받으므로, 이 테스트
	 * 전용으로 {@link LocalCredential#markEmailVerified}를 직접 불러 인증을 완료한다.
	 * (관리자 승격처럼 "운영과 같은 경로"가 아니다 — 이메일을 실제로 받을 방법이 없어서
	 * 테스트에서만 쓰는 지름길이라는 점을 여기 남긴다.)
	 */
	protected AuthedClient loginAsNewUser(String emailPrefix) {
		return loginAsNewUser(emailPrefix, Map.of());
	}

	/**
	 * 선택 동의를 함께 켜고 가입한다 — S15P21E201-549.
	 *
	 * <p>🔴 <b>기본값을 바꾸지 않고 인자로 받는 이유.</b> 여기에 동의를 전부 켜 두면 모든 여정이
	 * "무엇에든 동의한 사람" 으로 돌게 되고, 동의를 안 받았을 때 막히는지는 아무도 안 재게 된다.
	 * 필요한 여정만 명시적으로 켠다 — 지금은 민감 제약(알레르기)을 보내는
	 * {@code RecommendationWithRealPlacesFunctionalTest} 하나다.
	 *
	 * @param extraConsents 필수 둘 위에 더 켤 동의. 예: {@code Map.of("HEALTH_CONSTRAINTS", true)}
	 */
	protected AuthedClient loginAsNewUser(String emailPrefix, Map<String, Boolean> extraConsents) {
		String email = emailPrefix + "+" + UUID.randomUUID() + "@example.com";
		createdEmails.add(email);

		// TERMS_OF_SERVICE·PRIVACY_POLICY 는 ConsentPolicy 가 무조건 요구한다(REQUIRED_CONSENT_MISSING).
		Map<String, Boolean> consents = new java.util.LinkedHashMap<>(
				Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true));
		consents.putAll(extraConsents);

		ResponseEntity<Void> signup = rest.postForEntity("/api/v1/auth/signup",
				new LocalSignupRequest(email, "correct-horse-battery-staple", "기능테스트", "KO", true, "functional-test",
						consents, false),
				Void.class);
		assertThat(signup.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		// 🔴 credential.getUser() 는 지연 로딩(LAZY)이라, findByEmail()의 트랜잭션이 이미
		// 끝난 뒤에 부르면 LazyInitializationException 이 난다. 여기서만 별도 트랜잭션으로
		// 감싸 그 안에서 조회·수정·저장을 전부 끝낸다.
		transactionTemplate.executeWithoutResult(status -> {
			LocalCredential credential = credentialRepository.findByEmail(email).orElseThrow();
			credential.markEmailVerified(Instant.now());
			credential.getUser().activate();
			credentialRepository.save(credential);
			userRepository.save(credential.getUser()); // @OneToOne 에 cascade 가 없어 따로 저장한다
		});

		ResponseEntity<ApiResponse<AuthTokenResponse>> login = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
				new HttpEntity<>(new LoginRequest(email, "correct-horse-battery-staple", "functional-test")),
				new ParameterizedTypeReference<ApiResponse<AuthTokenResponse>>() {
				});
		assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

		return new AuthedClient(rest, login.getBody().data().accessToken());
	}

	/**
	 * {@code RecommendationJobController}·{@code ItineraryJobController} 공통 — "생성 후
	 * 폴링" 비동기 패턴을 매 여정마다 새로 안 짜도 되게 한다. 상한을 넘기면 실패로 처리한다
	 * (무한 대기로 CI를 멈추지 않는다).
	 */
	protected <T> T pollUntil(Supplier<ResponseEntity<T>> poll, Predicate<T> done) {
		for (int i = 0; i < 25; i++) { // 200ms x 25 = 5초 상한
			ResponseEntity<T> res = poll.get();
			if (res.getBody() != null && done.test(res.getBody())) {
				return res.getBody();
			}
			sleepQuietly(200);
		}
		throw new AssertionError("5초 안에 작업이 끝나지 않았다");
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * 🔴 실제 소켓을 거쳐 커밋되므로 {@code @Transactional} 테스트 롤백(별도 커넥션이라 안
	 * 먹는다)에 기댈 수 없다. 이번 테스트가 만든 사용자 이메일로 삭제를 시도한다(베스트에포트 —
	 * 실패해도 다음 테스트를 막지 않는다). CI는 파이프라인마다 postgres 서비스 컨테이너가 새로
	 * 뜨므로 이 단계가 없어도 안전하지만, 로컬에서 항상 켜 둔 DB를 반복 사용하는 개발자에게는
	 * 이게 없으면 표가 계속 쌓인다.
	 */
	@AfterEach
	void cleanUpCreatedUsers() {
		for (String email : createdEmails) {
			try {
				credentialRepository.findByEmail(email)
						.ifPresent(c -> credentialRepository.delete(c));
			}
			catch (RuntimeException ignored) {
				// 베스트에포트 — 정리 실패가 다음 테스트를 막으면 안 된다
			}
		}
		createdEmails.clear();
	}
}
