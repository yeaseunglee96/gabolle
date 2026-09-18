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
 * {@link StoragePort} 의 S3 호환 구현 — S15P21E201-367/-213.
 *
 * <p>-367 이 결정한 것: 새 업체에 가입하지 않고 personalization 파이프라인이 이미 쓰던 MinIO 를
 * 재사용한다({@code StorageProperties} 클래스 주석에 근거가 있다). 이 클래스는 MinIO 전용이 아니다 —
 * MinIO 공식 SDK 는 어떤 S3 호환 엔드포인트에도 그대로 붙는다.
 *
 * <h2>🔴 공개 주소는 이 클래스가 아니라 nginx 가 만든다</h2>
 *
 * {@link #put} 이 돌려주는 주소는 {@code publicBaseUrl + "/" + key} 다. 그 주소는 EC2 의 nginx가
 * {@code /photos/} 를 MinIO 의 S3 API 포트로 그대로 프록시해서 응답한다(리버스 프록시 설정은
 * 저장소 밖 — 호스트의 nginx 설정이다). 버킷 정책이 익명 {@code GetObject} 만 허용하고
 * {@code ListBucket} 은 거부하므로, 이 주소를 아는 사람만 그 파일을 받는다 — 버킷 안 목록을
 * 통째로 훑을 수는 없다(-367 완료 기준 2번).
 *
 * <h2>🔴 이 클래스가 쓰는 자격은 루트 자격이 아니다</h2>
 *
 * {@code gabolle-backend} 라는 이 버킷 전용 최소권한 계정을 따로 만들어 쓴다(MinIO
 * {@code admin policy}로 {@code gabolle-photos} 버킷에만 {@code PutObject}·{@code GetObject}·
 * {@code DeleteObject}·{@code ListBucket} 을 준다). 루트 자격(minioadmin)을 그대로 쓰면 이
 * 서비스 하나가 새면 MinIO 전체(personalization 파이프라인의 다른 버킷 포함)가 함께 샌다.
 *
 * <p>{@link LocalFileStorage} 와 같은 이유로 키를 검증한다 — S3 오브젝트 키는 파일 시스템 경로가
 * 아니라 순회(traversal) 위험은 없지만, 어차피 저장소 하나만 이 규칙을 지키면 되므로 두 구현이
 * 같은 키 모양을 강제하는 편이 호출자({@code ImageUploadService})입장에서 어느 구현이 켜져
 * 있어도 동작이 갈리지 않는다.
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
		// 🔴 지역을 반드시 준다. 안 주면 SDK 가 객체를 넣기 전에 버킷 위치를 서버에 묻고,
		//    그 호출에는 s3:GetBucketLocation 권한이 필요하다 — 운영 정책에 그것이 없어서
		//    2026-09-17 새벽까지 사진이 한 장도 안 올라갔다. 근거는 StorageProperties.S3.region.
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

	/**
	 * 🟢 <b>MinIO SDK 는 원래 스트림을 받는다.</b> 위 {@code put(byte[])} 은 우리가 만든
	 * {@code byte[]} 를 {@link ByteArrayInputStream} 으로 <b>도로 감싸고</b> 있다 — {@code byte[]} 는
	 * MinIO 가 요구한 것이 아니라 이 인터페이스가 강요한 것이다. 여기서는 감싸는 줄이 없어진다.
	 */
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
			// 🔴 없는 키를 지우는 것도 성공이다(StoragePort 계약). S3 프로토콜 자체가 이미 그렇게
			// 동작한다 — DeleteObject 는 대상이 없어도 204 를 준다. LocalFileStorage 처럼 별도로
			// "없으면 건너뛴다" 분기가 필요 없다.
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
			// 🔴 "없으면 빈 값" 은 StoragePort 계약이다. S3 는 없는 키를 예외(NoSuchKey)로
			// 알리므로, 그 코드일 때만 빈 값으로 바꾸고 다른 오류는 그대로 StorageException 이다.
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
