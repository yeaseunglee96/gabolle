package com.gabolle.backend.story.storage;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사진 파일 저장소 설정 — S15P21E201-216·-370.
 *
 * <p>🔴 감독자가 {@code application*.properties} 에 넣는 값을 그대로 받는다. 이 클래스가 직접
 * 프로퍼티 파일을 고치지 않는다 — 이 파일은 값을 읽기만 한다.
 *
 * <pre>
 * gabolle.storage.root=${GABOLLE_STORAGE_ROOT:./data/uploads}
 * gabolle.storage.public-base-path=/api/v1/uploads/images
 * </pre>
 *
 * <p>필드 기본값을 직접 넣어 둔다 — 설정이 아예 없는 환경(예: 다른 프로필의 슬라이스 테스트)에서도
 * 바인딩이 실패하지 않게 하기 위해서다({@code PlaceProperties} 와 같은 판단).
 */
@ConfigurationProperties("gabolle.storage")
public class StorageProperties {

	/** 파일이 실제로 놓이는 디렉터리. */
	private Path root = Path.of("./data/uploads");

	/** 공개 주소 접두어. 주소 = 접두어 + "/" + 저장 키. */
	private String publicBasePath = "/api/v1/uploads/images";

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
}
