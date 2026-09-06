package com.gabolle.backend.story.image;

/** 서버가 받아들이는 사진 형식 셋. */
public enum ImageFormat {

	JPEG("image/jpeg", "jpg"),
	PNG("image/png", "png"),
	WEBP("image/webp", "webp");

	private final String contentType;

	private final String extension;

	ImageFormat(String contentType, String extension) {
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
