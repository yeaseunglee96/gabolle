package com.gabolle.backend.place.photo.proxy;

import java.util.Optional;

/**
 * 받아 준 사진의 종류. 상대 서버의 Content-Type 은 믿지 않고 파일 첫 바이트(매직 넘버)로 가린다 —
 * www.visitbusan.net 은 사진에 Content-Type 을 아예 안 붙인다(2026-10-02 실측).
 */
public enum ImageKind {

	JPEG("image/jpeg", "jpg"),
	PNG("image/png", "png"),
	WEBP("image/webp", "webp");

	private final String contentType;

	private final String extension;

	ImageKind(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}

	public String contentType() {
		return this.contentType;
	}

	public String extension() {
		return this.extension;
	}

	public static Optional<ImageKind> sniff(byte[] b) {
		if (b.length >= 3 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF) {
			return Optional.of(JPEG);
		}
		if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
				&& b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
			return Optional.of(PNG);
		}
		if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
				&& b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}
}
