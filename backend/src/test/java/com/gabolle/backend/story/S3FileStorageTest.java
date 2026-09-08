package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.MinIOContainer;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.storage.S3FileStorage;
import com.gabolle.backend.story.storage.StorageProperties;
import com.gabolle.backend.story.storage.StoragePort;

import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

/**
 * {@link S3FileStorage} — S15P21E201-367 결정(오브젝트 스토리지는 새 업체 대신 기존 MinIO 재사용)의
 * 두 번째 {@link StoragePort} 구현.
 *
 * <p>진짜 MinIO 컨테이너 앞에서 돈다. {@code LocalFileStorage} 와 달리 이 구현은 서명(AWS SigV4)·
 * HTTP 응답 코드로 "없음" 을 판단하는 로직이 있어서, 가짜로 흉내 낸 서버로는 그 부분이 안 잡힌다.
 *
 * <h2>🔴 {@code @Testcontainers}·{@code @Container} 를 쓰지 않는 이유 — 2026-09-08 실측</h2>
 *
 * 처음엔 {@code @Testcontainers}·{@code static final @Container MinIOContainer MINIO = new
 * MinIOContainer(...)} 로 썼다. 로컬(도커 있음)에서는 통과했는데, GitLab CI 러너(도커 없음)에서
 * {@code S3FileStorageTest > initializationError} 로 <b>빌드 자체가 죽었다</b>(MR !404).
 *
 * <p>{@code @ExtendWith(PostgresAvailableCondition.class)} 를 붙였는데도 안 잡혔다 — 이유는
 * {@code static final} 필드 초기화가 <b>클래스 로딩 시점</b>에 도는데, 클래스 로딩은 JUnit5 가
 * 그 클래스를 <b>발견(discovery)</b>하기만 해도 일어난다. {@code ExecutionCondition}(내가 붙인
 * 조건)은 <b>실행할지</b> 를 거르는 것이라 발견 이후 단계에서 평가되므로, 정적 필드 생성자가 이미
 * 도커를 찾다가 던진 예외를 막을 수 없다.
 *
 * <p>그래서 {@link TestDatabase}(recommendation 도메인, PostgreSQL 컨테이너)와 같은 방식으로
 * 바꿨다 — 컨테이너 생성과 시작을 {@link #startMinio()}(a {@code @BeforeAll} 메서드) 안으로
 * 옮긴다. {@code @BeforeAll} 은 그 클래스의 실행이 <b>활성화됐을 때만</b> 도니, 여기서는
 * {@code ExecutionCondition} 이 이미 걸러진 뒤라 안전하다.
 */
@ExtendWith(PostgresAvailableCondition.class)
class S3FileStorageTest {

	private static final String BUCKET = "test-bucket";

	private static MinIOContainer minio;

	private S3FileStorage storage;

	@BeforeAll
	static void startMinio() throws Exception {
		minio = new MinIOContainer("minio/minio:RELEASE.2024-10-13T13-34-11Z");
		minio.start();
		try (MinioClient client = MinioClient.builder()
				.endpoint(minio.getS3URL())
				.credentials(minio.getUserName(), minio.getPassword())
				.build()) {
			client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
		}
	}

	@BeforeEach
	void setUp() {
		StorageProperties properties = new StorageProperties();
		properties.setProvider("s3");
		StorageProperties.S3 s3 = properties.getS3();
		s3.setEndpoint(minio.getS3URL());
		s3.setBucket(BUCKET);
		s3.setAccessKey(minio.getUserName());
		s3.setSecretKey(minio.getPassword());
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
