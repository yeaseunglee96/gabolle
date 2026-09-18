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

	private Video video = new Video();

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

		/**
		 * 버킷이 있는 지역. 🔴 <b>이 값을 주는 것이 핵심이다 — 비워 두면 사진이 아예 안 올라간다.</b>
		 *
		 * <p>MinIO 자바 SDK 는 지역을 모르면 객체를 넣기 <b>전에</b>
		 * {@code GET /버킷?location=} 으로 서버에 물어본다. 그 호출에는
		 * {@code s3:GetBucketLocation} 권한이 필요한데, 운영의 정책에는 그것이 없었다 —
		 * {@code s3:PutObject}·{@code GetObject}·{@code DeleteObject}·{@code ListBucket} 만
		 * 있었다. 그래서 <b>넣어 볼 기회도 없이 403 으로 끝났다.</b>
		 *
		 * <p>2026-09-17 새벽에 운영에서 그랬다. 사진이 한 장도 안 올라갔고
		 * ({@code gabolle-photos} 버킷이 만들어진 뒤로 객체 0개였다) 화면에는
		 * <i>"사진 저장소에 연결할 수 없습니다"</i> 가 떴다 — <b>연결은 멀쩡했고 권한이 문제였다.</b>
		 * MinIO 추적으로 확인했다: 받은 요청은 {@code s3.GetBucketLocation} 하나, 응답은 403.
		 *
		 * <p>지역을 알려주면 SDK 는 그 질문을 <b>아예 하지 않는다.</b> 권한을 늘리는 것보다
		 * 이쪽이 낫다 — 쓰지도 않는 호출을 없애는 것이고, 왕복도 한 번 준다.
		 *
		 * <p>기본값 {@code us-east-1} 은 MinIO 의 기본 지역이다(2026-09-17 운영 실측 —
		 * 서버에 지역 설정이 비어 있고 버킷 위치가 {@code us-east-1} 로 나온다).
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
	 * 동영상 업로드 제한 — S15P21E201-1275.
	 *
	 * <h2>🔴 왜 상수가 아니라 설정인가</h2>
	 *
	 * 사진은 {@code UploadedImage.MAX_BYTES} 로 <b>코드에 박혀</b> 있다(3MB). 동영상은 그러지
	 * 않는다 — <b>값이 아직 실측 전</b>이고, 정해진 뒤에도 코드를 고치지 않고 바꿀 수 있어야
	 * 한다. 앱이 줄여서 보내므로(2026-09-18 결정) 「1분이 몇 MB 인가」를 실기기에서 재 봐야
	 * 나온다.
	 *
	 * <p>🔴 <b>여기 값만 올린다고 큰 파일이 올라가지는 않는다.</b> 앞에 두 층이 더 있다 —
	 * nginx 의 {@code client_max_body_size} 와 Spring 의
	 * {@code spring.servlet.multipart.max-file-size}. 셋이 어긋나면 <b>어디서 막혔는지 화면에
	 * 안 보인다</b>(nginx 가 끊으면 백엔드 로그에 아무것도 안 남는다 —
	 * {@code docs/SERVER-SETUP.md} 의 「업로드 크기 상한」 절 참고). <b>올릴 때는 세 층을 함께
	 * 올린다.</b>
	 */
	public static class Video {

		/**
		 * 동영상 한 개의 최대 바이트. 기본 3MB 는 <b>실측값이 아니다</b> — 실기기로 재기 전까지
		 * 자리를 잡아 두는 값이다.
		 *
		 * <p>🔴 <b>이 값이 상한 사슬에서 가장 작아야 한다.</b> 바깥에 multipart(4MB)와
		 * nginx(운영 5MB)가 있고, <b>같기만 해도</b> 바깥이 먼저 자른다. 그러면 여기가 준비한
		 * 설명 있는 오류({@code maxBytes} 가 실린 413)가 <b>한 번도 안 쓰인다</b> — 사용자는
		 * 얼마까지 되는지 영영 모른다.
		 */
		private long maxBytes = 3L * 1024 * 1024;

		public long getMaxBytes() {
			return this.maxBytes;
		}

		public void setMaxBytes(long maxBytes) {
			this.maxBytes = maxBytes;
		}
	}
}
