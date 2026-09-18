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

	/**
	 * 어디에도 안 붙은 업로드를 치우는 청소 — S15P21E201-1284.
	 *
	 * <h2>🔴 기본이 「끄기」가 아니라 「세기만 하기」다</h2>
	 *
	 * {@code enabled=false} 여도 청소기는 <b>돈다.</b> 다만 <b>지우지 않고 「지웠을 것 N개」만
	 * 로그에 남긴다.</b> 며칠 그 숫자를 보고 나서 켜는 순서다 — 파일 삭제는 되돌릴 수 없고,
	 * 되돌릴 수 없는 일은 먼저 세어 보고 한다.
	 *
	 * <p>아예 안 돌게 두면 <b>켤 때 처음으로 숫자를 보게 된다</b> — 그때는 이미 지운 뒤다.
	 */
	public static class OrphanCleanup {

		/** 🔴 기본은 세기만 한다. {@code true} 로 바꿔야 실제로 지운다. */
		private boolean enabled = false;

		/**
		 * 올라온 지 몇 시간이 지난 것부터 고아로 보는가.
		 *
		 * <h2>기본 24시간의 근거</h2>
		 *
		 * 올리고 기록에 붙이기까지는 <b>한 앱 세션 안의 일</b>이다 — 몇 분이고, 압축이 오래 걸려도
		 * 한 시간을 안 넘는다.
		 *
		 * <p>🔴 <b>초안이 사진을 안 들고 있는 것을 재 보고 정했다</b>(2026-09-18, 프론트 실측).
		 * 글쓰기 화면이 기기에 저장하는 것은 본문·지역·공개범위·공개시각뿐이고 <b>사진은 없다.</b>
		 * 즉 사용자가 화면을 벗어나면 그 사진 주소에 <b>다시 닿을 방법이 없고</b>, 고아는
		 * 24시간 뒤가 아니라 <b>사실상 즉시</b> 고아다. 24시간은 백 배쯤 여유다.
		 *
		 * <p>🔴 <b>초안이 사진도 들고 있게 바뀌면 이 값을 다시 봐야 한다.</b> 그때는 초안이
		 * 살아 있는 기간보다 넉넉해야 한다 — 안 그러면 돌아온 사용자의 사진이 사라져 있다.
		 */
		private int retentionHours = 24;

		/**
		 * 한 판에 치우는 상한.
		 *
		 * <p>🔴 무한정 지우지 않는다. 질의가 잘못됐거나 앞으로 붙는 자리가 하나 더 생겼는데
		 * 여기 안 넣었을 때, <b>피해가 한 판 크기로 묶인다.</b> 남은 것은 다음 판이 가져간다.
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
