package com.gabolle.backend.story.image;

import com.gabolle.backend.story.application.ImageUploadService;

/**
 * 파일 형식을 매직 바이트(파일 맨 앞 몇 바이트가 형식마다 정해져 있는 값)로만 판단한다.
 *
 * <h2>🔴 파일 이름·클라이언트가 보낸 Content-Type 은 보지 않는다</h2>
 * 화면에서만 형식·크기를 막으면 서버를 직접 부르는 요청(Postman, 조작된 앱)은 그 검사를
 * 지나간다. {@code photo.jpg} 라는 이름이 붙었어도 내용이 실행 파일이면 그건 사진이 아니다.
 * 그래서 이 클래스는 이름과 헤더를 완전히 무시하고 바이트 내용만 본다.
 */
public final class ImageSniffer {

	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

	private ImageSniffer() {
	}

	public static ImageFormat sniff(byte[] bytes) {
		if (isJpeg(bytes)) {
			return ImageFormat.JPEG;
		}
		if (isPng(bytes)) {
			return ImageFormat.PNG;
		}
		if (isWebp(bytes)) {
			return ImageFormat.WEBP;
		}
		throw new ImageUploadService.UnsupportedImageException();
	}

	private static boolean isJpeg(byte[] b) {
		return b.length >= 3
				&& (b[0] & 0xFF) == 0xFF
				&& (b[1] & 0xFF) == 0xD8
				&& (b[2] & 0xFF) == 0xFF;
	}

	private static boolean isPng(byte[] b) {
		if (b.length < PNG_SIGNATURE.length) {
			return false;
		}
		for (int i = 0; i < PNG_SIGNATURE.length; i++) {
			if (b[i] != PNG_SIGNATURE[i]) {
				return false;
			}
		}
		return true;
	}

	private static boolean isWebp(byte[] b) {
		if (b.length < 12) {
			return false;
		}
		boolean riff = b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F';
		boolean webp = b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
		return riff && webp;
	}
}
