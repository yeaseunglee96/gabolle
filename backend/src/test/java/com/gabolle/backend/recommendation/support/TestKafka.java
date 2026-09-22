package com.gabolle.backend.recommendation.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트가 쓸 진짜 카프카를 잡는다. {@link TestDatabase} 와 같은 방식이다.
 *
 * <ol>
 * <li>밖에서 준 브로커 — 환경변수 {@code GABOLLE_TEST_KAFKA_BOOTSTRAP} 이 있으면 그것을 쓴다</li>
 * <li>Testcontainers — 아무것도 안 줬고 도커가 있으면 컨테이너를 하나 띄운다</li>
 * </ol>
 *
 * <p>🔴 <b>1번이 없으면 CI 에서 못 돈다.</b> 이 저장소의 CI 러너에는 도커 소켓이 없다
 * (docker-in-docker 가 아니다). PostgreSQL·MinIO 를 <b>서비스 컨테이너</b>로 옆에 붙이고
 * 주소를 환경변수로 넘기는 것이 이 저장소의 규약이고, 카프카도 같은 자리에 붙였다
 * ({@code .gitlab-ci.yml} 의 {@code backend:build}).
 *
 * <p>실제로 이 규약을 안 따랐다가 MR !1442 가 빨개졌다 —
 * {@code Could not find a valid Docker environment}. 로컬에서는 도커가 있어 통과하고
 * CI 에서만 죽는 종류라, 올리기 전에는 안 드러난다.
 *
 * <p>둘 다 없으면 테스트는 통과가 아니라 <b>건너뜀</b>으로 표시된다
 * ({@link #isAvailable()}, {@link KafkaAvailableCondition}). 다만 CI 는 <b>건너뛴 테스트가
 * 하나라도 있으면 일부러 빨갛게</b> 만들므로, CI 에서 건너뛰는 일은 없어야 한다.
 *
 * <p>컨테이너는 JVM 당 한 번만 띄우고 클래스마다 다시 띄우지 않는다. 정리는 Testcontainers 의
 * 청소 컨테이너가 JVM 종료 시 처리한다.
 */
public final class TestKafka {

	private static final String BOOTSTRAP_KEY = "GABOLLE_TEST_KAFKA_BOOTSTRAP";

	/**
	 * compose·CI 와 <b>같은 판</b>이어야 한다.
	 *
	 * <p>🔴 3.9.0 을 쓰면 안 된다. Testcontainers 1.21.3 이 그 판을 띄울 때
	 * {@code advertised.listeners} 에 {@code 0.0.0.0} 을 넣는데 카프카가 기동 전에 그것을
	 * 거부한다({@code cannot use the nonroutable meta-address}). 컨테이너가 종료 코드 1 로
	 * 죽고 시험은 {@code initializationError} 로만 보여서 원인이 안 드러난다.
	 */
	private static final String IMAGE = "apache/kafka:3.8.0";

	private static KafkaContainer container;

	private static String bootstrapServers;

	private TestKafka() {
	}

	/** {@link KafkaAvailableCondition} 이 부른다. 거짓이면 그 테스트 클래스는 건너뛴 것으로 표시된다. */
	public static boolean isAvailable() {
		return external() != null || DockerClientFactory.instance().isDockerAvailable();
	}

	/** {@code @DynamicPropertySource} 가 그대로 쓸 수 있게 한 자리에 모은다. */
	public static void registerBootstrapServers(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.bootstrap-servers", TestKafka::bootstrapServers);
	}

	public static synchronized String bootstrapServers() {
		if (bootstrapServers != null) {
			return bootstrapServers;
		}
		String given = external();
		if (given != null) {
			bootstrapServers = given;
			return bootstrapServers;
		}
		container = new KafkaContainer(DockerImageName.parse(IMAGE));
		container.start();
		bootstrapServers = container.getBootstrapServers();
		return bootstrapServers;
	}

	private static String external() {
		String value = System.getenv(BOOTSTRAP_KEY);
		if (value == null || value.isBlank()) {
			value = System.getProperty(BOOTSTRAP_KEY);
		}
		return (value == null || value.isBlank()) ? null : value.trim();
	}
}
