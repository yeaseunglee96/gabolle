package com.gabolle.backend.story.video;

/**
 * 서버가 받아들이는 동영상 형식 — 지금은 MP4 하나다. 앱이 올리기 전에 다시 인코딩해서
 * 내보내므로 사용자가 무엇을 골랐든 서버에 닿는 것은 앱이 만든 결과물이다.
 *
 * <p>받는 종류를 늘리는 것은 되돌리기 어렵다 — 그 파일들이 저장소에 쌓인다.
 *
 * <p>사진의 {@code ImageFormat} 과 섞지 않는다. 한쪽에 더한 종류가 다른 쪽 창구에도 열린다.
 */
public enum VideoFormat {

	MP4("video/mp4", "mp4");

	private final String contentType;

	private final String extension;

	VideoFormat(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}

	public String contentType() {
		return this.contentType;
	}

	public String extension() {
		return this.extension;
	}
}
