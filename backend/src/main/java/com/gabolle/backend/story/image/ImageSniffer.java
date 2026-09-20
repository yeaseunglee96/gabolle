package com.gabolle.backend.story.image;

import com.gabolle.backend.story.application.ImageUploadService;

/**
 * 파일 형식을 매직 바이트(파일 맨 앞 몇 바이트가 형식마다 정해져 있는 값)로만 판단한다. 파일
 * 이름과 클라이언트가 보낸 Content-Type 은 일부러 보지 않는다 — 둘 다 보내는 쪽이 정하는 값이라
 * {@code photo.jpg} 라는 이름으로 실행 파일을 올릴 수 있다.
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
