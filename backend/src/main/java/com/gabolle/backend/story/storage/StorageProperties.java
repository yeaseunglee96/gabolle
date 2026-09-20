package com.gabolle.backend.story.storage;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사진 파일 저장소 설정. 필드마다 기본값을 직접 넣어 둔다 — 설정이 아예 없는 환경(다른 프로필의
 * 슬라이스 테스트 등)에서도 바인딩이 실패하지 않아야 한다.
 *
 * <p>{@code provider} 가 {@link LocalFileStorage} 와 {@link S3FileStorage} 중 하나만 빈으로
 * 등록되게 가른다. 둘이 동시에 빈이 되면 {@code StoragePort} 주입 자리에서 후보가 둘이라 기동이
 * 실패한다.
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

	private Video video = new Video();

	private OrphanCleanup orphanCleanup = new OrphanCleanup();

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

	public Video getVideo() {
		return this.video;
	}

	public void setVideo(Video video) {
		this.video = video;
	}

	public OrphanCleanup getOrphanCleanup() {
		return this.orphanCleanup;
	}

	public void setOrphanCleanup(OrphanCleanup orphanCleanup) {
		this.orphanCleanup = orphanCleanup;
	}

	/** {@code provider=s3} 일 때만 쓰이는 값. {@code provider=local} 이면 전부 빈 문자열로 둬도 된다. */
	public static class S3 {

		/** MinIO(또는 다른 S3 호환 서버)의 주소. 예: {@code http://minio:9000}. */
		private String endpoint = "";

		/** 사진을 담는 버킷 이름. */
		private String bucket = "";

		/** 이 버킷 전용 최소권한 자격. 루트 자격을 재사용하지 않아 새는 값의 피해 범위를 좁힌다. */
		private String accessKey = "";

		private String secretKey = "";

		/** 공개 주소 접두어. 주소 = 접두어 + "/" + 저장 키. nginx 가 이 경로를 버킷으로 프록시한다. */
		private String publicBaseUrl = "";

		/**
		 * 버킷이 있는 지역. 비워 두면 사진이 아예 안 올라간다 — SDK 가 지역을 모르면 객체를 넣기
		 * 전에 버킷 위치를 묻고, 그 호출에는 운영 정책에 없는 {@code s3:GetBucketLocation} 권한이
		 * 필요해 403 으로 끝난다. 지역을 주면 그 질문 자체를 안 한다. 기본값은 MinIO 의 기본 지역이다.
		 */
		private String region = "us-east-1";

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

		public String getRegion() {
			return this.region;
		}

		public void setRegion(String region) {
			this.region = region;
		}

		public void setPublicBaseUrl(String publicBaseUrl) {
			this.publicBaseUrl = publicBaseUrl;
		}
	}

	/**
	 * 동영상 업로드 제한. 사진과 달리 코드 상수가 아니라 설정값이다.
	 *
	 * <p>여기 값만 올린다고 큰 파일이 올라가지는 않는다. 앞에 nginx 의
	 * {@code client_max_body_size} 와 Spring 의 {@code spring.servlet.multipart.max-file-size}
	 * 가 있고, 올릴 때는 세 층을 함께 올려야 한다. nginx 가 끊으면 백엔드 로그에 아무것도 남지
	 * 않는다 — {@code docs/SERVER-SETUP.md} 의 「업로드 크기 상한」 절 참고.
	 */
	public static class Video {

		/**
		 * 동영상 한 개의 최대 바이트. 기본 3MB 는 실측값이 아니라 자리를 잡아 두는 값이다.
		 *
		 * <p>이 값이 상한 사슬에서 가장 작아야 한다. 바깥의 multipart(4MB)·nginx(운영 5MB)와
		 * 같기만 해도 바깥이 먼저 잘라, {@code maxBytes} 가 실린 413 이 한 번도 안 쓰인다.
		 */
		private long maxBytes = 3L * 1024 * 1024;

		public long getMaxBytes() {
			return this.maxBytes;
		}

		public void setMaxBytes(long maxBytes) {
			this.maxBytes = maxBytes;
		}
	}

	/**
	 * 어디에도 안 붙은 업로드를 치우는 청소. 기본은 끄기가 아니라 세기만 하기다 —
	 * {@code enabled=false} 여도 청소기는 돌고, 지우는 대신 지웠을 개수만 로그에 남긴다.
	 */
	public static class OrphanCleanup {

		/** 기본은 세기만 한다. {@code true} 로 바꿔야 실제로 지운다. */
		private boolean enabled = false;

		/**
		 * 올라온 지 몇 시간이 지난 것부터 고아로 보는가. 올리고 기록에 붙이기까지는 한 앱 세션
		 * 안의 일이고, 글쓰기 초안이 사진 주소를 들고 있지 않아 화면을 벗어나면 그 주소에 다시
		 * 닿을 길이 없다. 24시간은 그에 대한 넉넉한 여유다.
		 *
		 * <p>초안이 사진도 들고 있게 바뀌면 이 값을 초안이 살아 있는 기간보다 넉넉하게 다시
		 * 잡아야 한다.
		 */
		private int retentionHours = 24;

		/**
		 * 한 판에 치우는 상한. 질의가 잘못됐을 때 피해가 한 판 크기로 묶인다. 남은 것은 다음
		 * 판이 가져간다.
		 */
		private int batchLimit = 500;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public int getRetentionHours() {
			return this.retentionHours;
		}

		public void setRetentionHours(int retentionHours) {
			this.retentionHours = retentionHours;
		}

		public int getBatchLimit() {
			return this.batchLimit;
		}

		public void setBatchLimit(int batchLimit) {
			this.batchLimit = batchLimit;
		}
	}
}
