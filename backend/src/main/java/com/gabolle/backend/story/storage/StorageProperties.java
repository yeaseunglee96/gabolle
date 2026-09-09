package com.gabolle.backend.story.storage;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사진 파일 저장소 설정 — S15P21E201-216·-370·-367.
 *
 * <p>🔴 감독자가 {@code application*.properties} 에 넣는 값을 그대로 받는다. 이 클래스가 직접
 * 프로퍼티 파일을 고치지 않는다 — 이 파일은 값을 읽기만 한다.
 *
 * <pre>
 * gabolle.storage.provider=local
 * gabolle.storage.root=${GABOLLE_STORAGE_ROOT:./data/uploads}
 * gabolle.storage.public-base-path=/api/v1/uploads/images
 * </pre>
 *
 * <p>필드 기본값을 직접 넣어 둔다 — 설정이 아예 없는 환경(예: 다른 프로필의 슬라이스 테스트)에서도
 * 바인딩이 실패하지 않게 하기 위해서다({@code PlaceProperties} 와 같은 판단).
 *
 * <h2>🔴 {@code provider} — S15P21E201-367 완료(2026-09-08)</h2>
 *
 * -367 이 "[도입 필요]" 로 남겨 뒀던 오브젝트 스토리지 결정: <b>새 업체에 가입하지 않고, 이미
 * personalization 파이프라인이 쓰던 MinIO 를 재사용한다</b>(같은 도커 네트워크에 backend 가 이미
 * 있어 자격만 있으면 바로 닿는다 — 근거는 {@code infra/personalization/compose.yaml} 의
 * S15P21E201-367 주석과 {@code docs/LOCAL_ROUTE_실행계획_v7.md} 의 결정 목록).
 *
 * <p>{@code local} 이 기본값이다 — 값을 안 주면 지금까지와 같은 동작(M1, 서버 디스크)이다.
 * {@code s3} 로 바꾸면 {@link S3FileStorage} 가 대신 빈으로 등록된다({@link LocalFileStorage} 는
 * {@code @ConditionalOnProperty} 로 그 반대일 때만 등록된다 — 둘이 동시에 빈이 되면 스프링이
 * {@code StoragePort} 를 주입받는 자리에서 "후보가 둘" 이라고 기동을 거부한다).
 */
@ConfigurationProperties("gabolle.storage")
public class StorageProperties {

	/** 어떤 구현을 쓸지. {@code local}(서버 디스크) 또는 {@code s3}(S3 호환 오브젝트 스토리지). */
	private String provider = "local";

	/** 파일이 실제로 놓이는 디렉터리. {@code provider=local} 일 때만 쓰인다. */
	private Path root = Path.of("./data/uploads");

	/** 공개 주소 접두어. 주소 = 접두어 + "/" + 저장 키. {@code provider=local} 일 때만 쓰인다. */
	private String publicBasePath = "/api/v1/uploads/images";

	private S3 s3 = new S3();

	public String getProvider() {
		return this.provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public Path getRoot() {
		return this.root;
	}

	public void setRoot(Path root) {
		this.root = root;
	}

	public String getPublicBasePath() {
		return this.publicBasePath;
	}

	public void setPublicBasePath(String publicBasePath) {
		this.publicBasePath = publicBasePath;
	}

	public S3 getS3() {
		return this.s3;
	}

	public void setS3(S3 s3) {
		this.s3 = s3;
	}

	/** {@code provider=s3} 일 때만 쓰이는 값. {@code provider=local} 이면 전부 빈 문자열로 둬도 된다. */
	public static class S3 {

		/** MinIO(또는 다른 S3 호환 서버)의 주소. 예: {@code http://minio:9000}. */
		private String endpoint = "";

		/** 사진을 담는 버킷 이름. */
		private String bucket = "";

		/**
		 * 이 버킷 전용 최소권한 자격 — 루트 자격을 재사용하지 않는다(-367 완료 기준
		 * "접근 자격이 저장소 이력 어디에도 없다" 와 별개로, 새는 값의 피해 범위를 이 버킷
		 * 하나로 좁히기 위해서다).
		 */
		private String accessKey = "";

		private String secretKey = "";

		/**
		 * 공개 주소 접두어. 주소 = 접두어 + "/" + 저장 키. nginx 가 이 경로를 그대로
		 * {@code endpoint}/{@code bucket} 으로 프록시한다 — 버킷 정책이 익명 GetObject 만
		 * 허용하고 ListBucket 은 거부하므로, 이 주소를 아는 사람만 그 파일을 받는다.
		 */
		private String publicBaseUrl = "";

		public String getEndpoint() {
			return this.endpoint;
		}

		public void setEndpoint(String endpoint) {
			this.endpoint = endpoint;
		}

		public String getBucket() {
			return this.bucket;
		}

		public void setBucket(String bucket) {
			this.bucket = bucket;
		}

		public String getAccessKey() {
			return this.accessKey;
		}

		public void setAccessKey(String accessKey) {
			this.accessKey = accessKey;
		}

		public String getSecretKey() {
			return this.secretKey;
		}

		public void setSecretKey(String secretKey) {
			this.secretKey = secretKey;
		}

		public String getPublicBaseUrl() {
			return this.publicBaseUrl;
		}

		public void setPublicBaseUrl(String publicBaseUrl) {
			this.publicBaseUrl = publicBaseUrl;
		}
	}
}
