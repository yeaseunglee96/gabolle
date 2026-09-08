package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
 */
@Testcontainers
class S3FileStorageTest {

	private static final String BUCKET = "test-bucket";

	@Container
	static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2024-10-13T13-34-11Z");

	private S3FileStorage storage;

	@BeforeAll
	static void createBucket() throws Exception {
		try (MinioClient client = MinioClient.builder()
				.endpoint(MINIO.getS3URL())
				.credentials(MINIO.getUserName(), MINIO.getPassword())
				.build()) {
			client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
		}
	}

	@BeforeEach
	void setUp() {
		StorageProperties properties = new StorageProperties();
		properties.setProvider("s3");
		StorageProperties.S3 s3 = properties.getS3();
		s3.setEndpoint(MINIO.getS3URL());
		s3.setBucket(BUCKET);
		s3.setAccessKey(MINIO.getUserName());
		s3.setSecretKey(MINIO.getPassword());
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
