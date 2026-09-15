package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

import com.gabolle.backend.story.storage.S3FileStorage;
import com.gabolle.backend.story.storage.StorageProperties;
import com.gabolle.backend.story.storage.StoragePort;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

/**
 * {@link S3FileStorage} — S15P21E201-367 결정(오브젝트 스토리지는 새 업체 대신 기존 MinIO 재사용)의
 * 두 번째 {@link StoragePort} 구현.
 *
 * <p>진짜 MinIO 앞에서 돈다. {@code LocalFileStorage} 와 달리 이 구현은 서명(AWS SigV4)·HTTP
 * 응답 코드로 "없음" 을 판단하는 로직이 있어서, 가짜로 흉내 낸 서버로는 그 부분이 안 잡힌다.
 *
 * <h2>🔴 "건너뛰면 된다" 는 이 잡에서 틀린 답이다 — 2026-09-08 세 번째 실측</h2>
 *
 * 처음 두 번은 도커가 없을 때 이 클래스를 <b>건너뛰게</b> 만드는 방향으로 고쳤다 —
 * {@code @Testcontainers}/{@code @Container} 정적 필드(클래스 로딩 시점에 터짐) → 그다음
 * {@code PostgresAvailableCondition} 가드(정적 필드라 애초에 안 걸림) → {@code @BeforeAll} 로
 * 컨테이너 생성을 옮김(가드는 이제 걸리는데, CI 러너엔 도커가 없어 여전히 실패). 세 번 다
 * MR !404 의 {@code backend:build} 를 못 지나갔다.
 *
 * <p>{@code .gitlab-ci.yml} 을 읽고서야 이유를 알았다 — 그 잡은 <b>건너뛴 테스트가 하나라도
 * 있으면 빌드를 실패시킨다</b>({@code [ "$skipped" -eq 0 ] || exit 1}, S15P21E201-266 후속,
 * "이 초록은 아무것도 뜻하지 않는다"). 이 잡에서 PostgreSQL 이 통과하는 이유도 도커가 아니라
 * <b>GitLab 이 {@code services:} 로 진짜 Postgres 컨테이너를 직접 띄워 주기 때문</b>이다
 * ({@code GABOLLE_TEST_DB_URL} 로 곧장 연결하지, Testcontainers/도커 경로를 타지 않는다).
 * 그러니 "도커가 없으면 건너뛴다" 는 이 CI 에서 애초에 통과할 수 없는 목표였다.
 *
 * <p>진짜 답은 Postgres 와 같은 모양이다 — {@code backend:build} 에 {@code minio} 서비스
 * 컨테이너를 추가하고, 이 클래스가 {@code GABOLLE_TEST_MINIO_*} 환경변수(밖에서 준 MinIO)를
 * 먼저 보고 없을 때만 Testcontainers 로 도커에 직접 띄우게 했다. CI 는 항상 앞의 길로 돌아
 * 건너뜀 자체가 안 생기고, 도커도 서비스도 없는 개발자 PC 에서만 {@link Assumptions#assumeTrue}
 * 로 건너뛴다(그런 PC 는 애초에 이 CI 잡을 돌리지 않는다).
 */
class S3FileStorageTest {

	private static final String BUCKET = "test-bucket";

	private static final String ENDPOINT_KEY = "GABOLLE_TEST_MINIO_ENDPOINT";

	private static final String ACCESS_KEY_KEY = "GABOLLE_TEST_MINIO_ACCESS_KEY";

	private static final String SECRET_KEY_KEY = "GABOLLE_TEST_MINIO_SECRET_KEY";

	private static String endpoint;

	private static String accessKey;

	private static String secretKey;

	private S3FileStorage storage;

	@BeforeAll
	static void startMinio() throws Exception {
		String externalEndpoint = setting(ENDPOINT_KEY);
		if (externalEndpoint != null && !externalEndpoint.isBlank()) {
			// 🔴 GitLab CI 는 항상 이 길이다 — services: 로 이미 떠 있는 MinIO 를 그대로 쓴다.
			endpoint = externalEndpoint;
			accessKey = setting(ACCESS_KEY_KEY);
			secretKey = setting(SECRET_KEY_KEY);
		} else {
			Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
					"MinIO 를 못 구한다 — " + ENDPOINT_KEY + " 를 주거나 도커를 켜십시오");
			// 🔴 2026-09-15 (S15P21E201-866) — quay.io 에서 받는다. 도커 허브가 `minio/minio` 공개
			//    배포를 접어서 `.gitlab-ci.yml` 의 services 는 2026-09-12 에 옮겼는데, 이 줄만 남아
			//    있었다. CI 는 위 환경변수 길로 가므로 여기 안 닿지만, 도커만 켜고 환경변수 없이
			//    돌리는 개발자 PC 는 전부 여기서 pull 이 404 로 죽는다 (실측 2026-09-15:
			//    `pull access denied for minio/minio, repository does not exist`).
			//
			//    태그는 그대로 둔다 — 판을 올리는 것이 아니라 같은 판을 다른 창고에서 받는 것뿐이라,
			//    테스트가 겪는 MinIO 는 어제와 똑같다.
			//
			//    🔴 asCompatibleSubstituteFor 가 필요하다 — Testcontainers 는 이미지 이름이 자기가 아는
			//       것과 다르면 «호환되는지 확인할 수 없다» 며 거부한다. 창고만 바뀌고 같은 이미지라는
			//       것을 코드로 말해 줘야 한다 (실측 2026-09-15: 이 줄 없이 quay.io 만 쓰면 여전히 죽는다).
			MinIOContainer container = new MinIOContainer(
					DockerImageName.parse("quay.io/minio/minio:RELEASE.2024-10-13T13-34-11Z")
						.asCompatibleSubstituteFor("minio/minio"));
			container.start();
			endpoint = container.getS3URL();
			accessKey = container.getUserName();
			secretKey = container.getPassword();
		}
		try (MinioClient client = MinioClient.builder()
				.endpoint(endpoint)
				.credentials(accessKey, secretKey)
				.build()) {
			if (!client.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
				client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
			}
		}
	}

	/** 환경변수를 먼저 보고, 없으면 같은 이름의 시스템 프로퍼티({@code -D})를 본다. */
	private static String setting(String key) {
		String value = System.getenv(key);
		return (value != null) ? value : System.getProperty(key);
	}

	@BeforeEach
	void setUp() {
		StorageProperties properties = new StorageProperties();
		properties.setProvider("s3");
		StorageProperties.S3 s3 = properties.getS3();
		s3.setEndpoint(endpoint);
		s3.setBucket(BUCKET);
		s3.setAccessKey(accessKey);
		s3.setSecretKey(secretKey);
		s3.setPublicBaseUrl("https://example.test/photos");
		this.storage = new S3FileStorage(properties);
	}

	@Test
	void putReturnsPublicBaseUrlPlusKey() {
		String key = "story/" + UUID.randomUUID() + ".jpg";

		String url = this.storage.put(key, "image/jpeg", new byte[] { 1, 2, 3 });

		assertThat(url).isEqualTo("https://example.test/photos/" + key);
	}

	@Test
	void getReturnsWhatWasPut() {
		String key = "story/" + UUID.randomUUID() + ".jpg";
		byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
		this.storage.put(key, "image/jpeg", content);

		Optional<StoragePort.StoredObject> found = this.storage.get(key);

		assertThat(found).isPresent();
		assertThat(found.get().bytes()).isEqualTo(content);
		assertThat(found.get().contentType()).isEqualTo("image/jpeg");
	}

	@Test
	void getReturnsEmptyWhenMissing() {
		assertThat(this.storage.get("story/does-not-exist.jpg")).isEmpty();
	}

	@Test
	void deleteRemovesObjectAndIsIdempotent() {
		String key = "story/" + UUID.randomUUID() + ".jpg";
		this.storage.put(key, "image/jpeg", "hi".getBytes(StandardCharsets.UTF_8));

		this.storage.delete(key);
		assertThat(this.storage.get(key)).isEmpty();

		// 🔴 두 번째도 예외 없이 성공해야 한다 — StorageCleanupService 의 재시도가 이 성질에 기댄다.
		// S3 프로토콜 자체가 이미 이렇게 동작한다(DeleteObject 는 대상이 없어도 204).
		this.storage.delete(key);
	}

	@Test
	void rejectsKeyWithParentDirectoryTraversal() {
		assertThatThrownBy(() -> this.storage.put("../x", "image/jpeg", new byte[] { 1 }))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsKeyContainingTraversalInMiddle() {
		assertThatThrownBy(() -> this.storage.get("story/../../etc/passwd"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
