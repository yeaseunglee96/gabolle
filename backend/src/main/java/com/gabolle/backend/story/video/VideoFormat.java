package com.gabolle.backend.story.video;

/**
 * 서버가 받아들이는 동영상 형식 — 지금은 <b>MP4 하나</b>다. S15P21E201-1275.
 *
 * <h2>🔴 {@code .mov} 를 안 받는 이유</h2>
 *
 * 앱이 올리기 전에 <b>다시 인코딩해서 내보낸다</b>(2026-09-18 결정 — 줄이기를 앱이 한다). 그러면
 * 사용자가 무엇을 골랐든 서버에 닿는 것은 앱이 만든 결과물이고, 그 형식은 앱이 정한다. 원본이
 * {@code .mov} 였는지 서버가 알 이유가 없다.
 *
 * <p>받는 종류를 늘리는 것은 <b>되돌리기 어렵다</b> — 한번 받기 시작하면 그 파일들이 저장소에
 * 쌓이고, 나중에 줄이려면 이미 올라간 것을 어떻게 할지부터 정해야 한다. 늘리는 쪽이 언제나
 * 쉬우므로 <b>좁게 시작한다.</b>
 *
 * <p>🔴 <b>이 목록은 사진의 {@code ImageFormat} 과 섞이지 않는다.</b> 창구가 다르고
 * ({@code /uploads/story-video} 대 {@code /uploads/story-image}) 판별기도 다르다. 한쪽에 종류를
 * 더한 것이 다른 쪽에도 열리면 <b>사진 창구가 동영상을 받는다.</b>
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
