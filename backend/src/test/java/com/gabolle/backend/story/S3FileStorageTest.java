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
 * {@link S3FileStorage} 를 진짜 MinIO 앞에서 돌린다. 서명과 HTTP 응답 코드로 "없음" 을 판단하는
 * 부분은 흉내 낸 서버로는 안 잡힌다.
 *
 * MinIO 는 {@code GABOLLE_TEST_MINIO_*} 환경변수로 밖에서 준 것을 먼저 쓰고, 없을 때만
 * Testcontainers 로 띄운다. CI 잡은 건너뛴 테스트가 하나라도 있으면 빌드를 실패시키므로,
 * CI 는 항상 환경변수 길로 가야 한다. 건너뛰기는 도커도 서비스도 없는 개발자 PC 몫이다.
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
			// CI 는 항상 이 길이다 — services: 로 이미 떠 있는 MinIO 를 그대로 쓴다.
			endpoint = externalEndpoint;
			accessKey = setting(ACCESS_KEY_KEY);
			secretKey = setting(SECRET_KEY_KEY);
		} else {
			Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
					"MinIO 를 못 구한다 — " + ENDPOINT_KEY + " 를 주거나 도커를 켜십시오");
			// 도커 허브가 minio/minio 공개 배포를 접어서 quay.io 에서 받는다. 같은 판을 다른 창고에서
			// 받는 것뿐이라 태그는 그대로다. asCompatibleSubstituteFor 가 없으면 Testcontainers 가
			// 모르는 이미지 이름이라며 거부한다.
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

		// 두 번째도 예외 없이 성공해야 한다 — 뒷정리 재시도가 이 성질에 기댄다.
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
