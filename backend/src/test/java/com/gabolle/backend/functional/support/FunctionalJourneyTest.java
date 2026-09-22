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
 * 기능 테스트의 공통 베이스. 슬라이스가 아니라 전체 앱을 {@code RANDOM_PORT} 로 띄운다 — 그래야
 * {@link TestRestTemplate} 이 진짜 소켓으로 나가고, MockMvc 는 절대 안 지나는 실제
 * {@code SecurityFilterChain} 을 통과한다.
 *
 * <p>이 클래스를 extends 하는 여정 테스트는 같은 프로퍼티·같은 프로필을 써야 한다. 하나라도 다르게
 * 주면 Spring 이 컨텍스트를 새로 캐시해 앱이 여러 번 뜬다. 여정마다 다른 데이터가 필요하면
 * 프로퍼티가 아니라 헬퍼 호출 인자로 달리한다.
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
	 * {@code spring.flyway.create-schemas=false} 라 운영에서 DB 관리자가 미리 하는 일을 대신한다.
	 * {@link #SCHEMA} 는 $user(접속 계정 이름)와 안 겹쳐야 하고, 끝나고 지우면 안 된다 — 캐시된
	 * 다른 컨텍스트가 그 표를 쓰는 중일 수 있다.
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
	 * 회원가입 + 이메일 인증 + 로그인을 한 번에 처리한다. 가입만으로는 로그인이 안 되고
	 * ({@code emailVerifiedAt == null} 이면 거부한다) 실제 이메일을 못 받으므로,
	 * {@link LocalCredential#markEmailVerified} 를 직접 부르는 테스트 전용 지름길을 쓴다.
	 */
	protected AuthedClient loginAsNewUser(String emailPrefix) {
		return loginAsNewUser(emailPrefix, Map.of());
	}

	/**
	 * 선택 동의를 함께 켜고 가입한다. 기본값을 바꾸지 않고 인자로 받는다 — 동의를 전부 켜 두면 모든
	 * 여정이 "무엇에든 동의한 사람" 으로 돌아, 동의를 안 받았을 때 막히는지를 아무도 안 재게 된다.
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

		// credential.getUser() 는 지연 로딩이라 findByEmail() 의 트랜잭션이 끝난 뒤에 부르면
		// LazyInitializationException 이 난다. 조회·수정·저장을 한 트랜잭션 안에서 끝낸다.
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
	 * 실제 소켓을 거쳐 커밋되므로 {@code @Transactional} 롤백이 안 먹는다. 이번 테스트가 만든
	 * 사용자를 베스트에포트로 지운다 — 실패해도 다음 테스트를 막지 않는다.
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
