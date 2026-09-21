package com.gabolle.backend.story.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;

/**
 * {@link StoragePort} 의 S3 호환 구현. MinIO 공식 SDK 를 쓰지만 MinIO 전용은 아니다 — 어떤 S3 호환
 * 엔드포인트에도 그대로 붙는다.
 *
 * <p>{@link #put} 이 돌려주는 주소({@code publicBaseUrl + "/" + key})에 응답하는 것은 이 서비스가
 * 아니라 저장소 밖에 있는 nginx 설정이다. 버킷 정책이 익명 {@code GetObject} 만 허용하고
 * {@code ListBucket} 은 거부하므로 주소를 아는 사람만 그 파일을 받는다.
 *
 * <p>자격은 이 버킷 전용 최소권한 계정이다. 루트 자격을 쓰면 이 서비스 하나가 새는 것으로 같은
 * 서버의 다른 버킷까지 함께 샌다.
 *
 * <p>S3 오브젝트 키에는 경로 순회 위험이 없지만 {@link LocalFileStorage} 와 같은 규칙으로 키를
 * 검증한다 — 호출자 입장에서 어느 구현이 켜져 있든 동작이 갈리지 않게 한다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(prefix = "gabolle.storage", name = "provider", havingValue = "s3")
public class S3FileStorage implements StoragePort {

	private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9/_\\-.]*$");

	private final MinioClient client;

	private final StorageProperties.S3 s3Properties;

	public S3FileStorage(StorageProperties properties) {
		this.s3Properties = properties.getS3();
		// 지역을 반드시 준다. 안 주면 SDK 가 객체를 넣기 전에 버킷 위치를 서버에 묻고, 그 호출에는
		// 운영 정책에 없는 s3:GetBucketLocation 권한이 필요하다 — StorageProperties.S3.region 참고.
		this.client = MinioClient.builder()
				.endpoint(this.s3Properties.getEndpoint())
				.region(this.s3Properties.getRegion())
				.credentials(this.s3Properties.getAccessKey(), this.s3Properties.getSecretKey())
				.build();
	}

	@Override
	public String put(String key, String contentType, byte[] bytes) {
		validateKey(key);
		try {
			this.client.putObject(PutObjectArgs.builder()
					.bucket(this.s3Properties.getBucket())
					.object(key)
					.stream(new ByteArrayInputStream(bytes), bytes.length, -1)
					.contentType(contentType)
					.build());
		}
		catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException
				| InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException
				| XmlParserException e) {
			throw new StorageException("사진 파일을 S3 에 저장하지 못했다: " + key, e);
		}
		return this.s3Properties.getPublicBaseUrl() + "/" + key;
	}

	/** MinIO SDK 는 원래 스트림을 받는다. 위 {@code put(byte[])} 과 달리 감싸는 단계가 없다. */
	@Override
	public String put(String key, String contentType, InputStream in, long size) {
		validateKey(key);
		try {
			this.client.putObject(PutObjectArgs.builder()
					.bucket(this.s3Properties.getBucket())
					.object(key)
					.stream(in, size, -1)
					.contentType(contentType)
					.build());
		}
		catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException
				| InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException
				| XmlParserException e) {
			throw new StorageException("동영상 파일을 S3 에 저장하지 못했다: " + key, e);
		}
		return this.s3Properties.getPublicBaseUrl() + "/" + key;
	}

	@Override
	public void delete(String key) {
		validateKey(key);
		try {
			// 없는 키를 지우는 것도 성공이라는 계약은 DeleteObject 가 대상이 없어도 204 를 주므로
			// 그대로 지켜진다 — 따로 분기하지 않는다.
			this.client.removeObject(RemoveObjectArgs.builder()
					.bucket(this.s3Properties.getBucket())
					.object(key)
					.build());
		}
		catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException
				| InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException
				| XmlParserException e) {
			throw new StorageException("사진 파일을 S3 에서 지우지 못했다: " + key, e);
		}
	}

	@Override
	public Optional<StoredObject> get(String key) {
		validateKey(key);
		try (GetObjectResponse response = this.client.getObject(GetObjectArgs.builder()
				.bucket(this.s3Properties.getBucket())
				.object(key)
				.build())) {
			byte[] bytes = response.readAllBytes();
			String contentType = response.headers().get("Content-Type");
			return Optional.of(new StoredObject(contentType, bytes));
		}
		catch (ErrorResponseException e) {
			// S3 는 없는 키를 예외로 알리므로 그 코드일 때만 빈 값으로 바꾼다 — 「없으면 빈 값」은
			// StoragePort 계약이고, 다른 오류는 그대로 StorageException 이어야 한다.
			if ("NoSuchKey".equals(e.errorResponse().code())) {
				return Optional.empty();
			}
			throw new StorageException("사진 파일을 S3 에서 읽지 못했다: " + key, e);
		}
		catch (InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException
				| IOException | NoSuchAlgorithmException | ServerException | XmlParserException e) {
			throw new StorageException("사진 파일을 S3 에서 읽지 못했다: " + key, e);
		}
	}

	private void validateKey(String key) {
		if (key == null || !KEY_PATTERN.matcher(key).matches() || key.contains("..")) {
			throw new IllegalArgumentException("올바르지 않은 저장 키다: " + key);
		}
	}
}
