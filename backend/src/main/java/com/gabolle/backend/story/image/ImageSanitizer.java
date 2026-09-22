package com.gabolle.backend.story.image;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import com.gabolle.backend.story.application.ImageUploadService;

/**
 * 촬영 위치 정보(EXIF GPS)를 포함한 메타데이터를 제거한다. 세 형식 모두 세그먼트·청크를 순서대로
 * 읽다가 메타데이터를 담는 종류만 건너뛰고 나머지는 그대로 옮기므로, 픽셀을 디코딩하지 않아 화질이
 * 바뀌지 않는다. 그 대신 구조가 깨진 입력(길이가 파일 끝을 넘는 등)은 끝까지 읽을 수 없으므로
 * {@link ImageUploadService.UnsupportedImageException} 으로 거부한다.
 */
public final class ImageSanitizer {

	private ImageSanitizer() {
	}

	public static byte[] strip(byte[] in, ImageFormat format) {
		return switch (format) {
			case JPEG -> stripJpeg(in);
			case PNG -> stripPng(in);
			case WEBP -> stripWebp(in);
		};
	}

	// JPEG — SOI(FFD8) 뒤로 "FF + 마커코드 + (있으면) 2바이트 길이 + 그 길이-2 만큼의 내용" 이
	// 반복된다. APP1(EXIF·XMP)·APP2~APP13·COM(주석) 을 버리고 나머지는 그대로 옮긴다.
	// SOS 뒤는 압축된 픽셀 데이터라 마커처럼 파싱하지 않고 그대로 붙인다.

	private static byte[] stripJpeg(byte[] in) {
		if (in.length < 4 || (in[0] & 0xFF) != 0xFF || (in[1] & 0xFF) != 0xD8) {
			throw new ImageUploadService.UnsupportedImageException();
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
		out.write(in, 0, 2); // SOI
		int pos = 2;
		int len = in.length;

		while (true) {
			if (pos >= len || (in[pos] & 0xFF) != 0xFF) {
				throw new ImageUploadService.UnsupportedImageException();
			}
			int p = pos + 1;
			// 마커 앞에 채움용 FF 바이트가 더 올 수 있다.
			while (p < len && (in[p] & 0xFF) == 0xFF) {
				p++;
			}
			if (p >= len) {
				throw new ImageUploadService.UnsupportedImageException();
			}
			int marker = in[p] & 0xFF;
			p++; // 마커 코드 다음 위치

			if (marker == 0xD9) { // EOI
				out.write(0xFF);
				out.write(marker);
				break;
			}
			if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
				// TEM·RSTn — 길이 필드가 없다.
				out.write(0xFF);
				out.write(marker);
				pos = p;
				continue;
			}
			if (p + 1 >= len) {
				throw new ImageUploadService.UnsupportedImageException();
			}
			int segLen = ((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF);
			if (segLen < 2 || p + segLen > len) {
				throw new ImageUploadService.UnsupportedImageException();
			}
			if (marker == 0xDA) { // SOS — 이 헤더까지만 쓰고 나머지는 그대로 붙인다.
				out.write(0xFF);
				out.write(marker);
				out.write(in, p, segLen);
				int afterHeader = p + segLen;
				out.write(in, afterHeader, len - afterHeader);
				break;
			}
			boolean drop = marker == 0xE1 || (marker >= 0xE2 && marker <= 0xED) || marker == 0xFE;
			if (!drop) {
				out.write(0xFF);
				out.write(marker);
				out.write(in, p, segLen);
			}
			pos = p + segLen;
		}
		return out.toByteArray();
	}

	// PNG — 청크(길이+타입+데이터+CRC) 단위로 읽는다.

	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

	private static boolean isDroppedPngChunk(String type) {
		return type.equals("eXIf") || type.equals("tEXt") || type.equals("zTXt") || type.equals("iTXt")
				|| type.equals("tIME");
	}

	private static byte[] stripPng(byte[] in) {
		if (in.length < PNG_SIGNATURE.length) {
			throw new ImageUploadService.UnsupportedImageException();
		}
		for (int i = 0; i < PNG_SIGNATURE.length; i++) {
			if (in[i] != PNG_SIGNATURE[i]) {
				throw new ImageUploadService.UnsupportedImageException();
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
		out.write(in, 0, PNG_SIGNATURE.length);
		int i = PNG_SIGNATURE.length;
		int len = in.length;

		while (i < len) {
			if (i + 8 > len) {
				throw new ImageUploadService.UnsupportedImageException();
			}
			long dataLength = ((long) (in[i] & 0xFF) << 24) | ((in[i + 1] & 0xFF) << 16)
					| ((in[i + 2] & 0xFF) << 8) | (in[i + 3] & 0xFF);
			String type = new String(in, i + 4, 4, StandardCharsets.US_ASCII);
			long chunkTotal = 12L + dataLength; // 길이(4)+타입(4)+데이터+CRC(4)
			if (dataLength < 0 || i + chunkTotal > len) {
				throw new ImageUploadService.UnsupportedImageException();
			}
			if (!isDroppedPngChunk(type)) {
				out.write(in, i, (int) chunkTotal);
			}
			boolean isEnd = type.equals("IEND");
			i += (int) chunkTotal;
			if (isEnd) {
				break;
			}
		}
		return out.toByteArray();
	}

	// WebP — RIFF 컨테이너 청크(4바이트 fourCC + 4바이트 길이 + 데이터 + 홀수면 패딩 1바이트) 단위.

	private static byte[] stripWebp(byte[] in) {
		if (in.length < 12
				|| !(in[0] == 'R' && in[1] == 'I' && in[2] == 'F' && in[3] == 'F')
				|| !(in[8] == 'W' && in[9] == 'E' && in[10] == 'B' && in[11] == 'P')) {
			throw new ImageUploadService.UnsupportedImageException();
		}
		ByteArrayOutputStream body = new ByteArrayOutputStream(in.length);
		int i = 12;
		int len = in.length;

		while (i < len) {
			if (i + 8 > len) {
				throw new ImageUploadService.UnsupportedImageException();
			}
			String fourCC = new String(in, i, 4, StandardCharsets.US_ASCII);
			long chunkSize = (in[i + 4] & 0xFFL) | ((in[i + 5] & 0xFFL) << 8)
					| ((in[i + 6] & 0xFFL) << 16) | ((in[i + 7] & 0xFFL) << 24);
			long pad = (chunkSize % 2 == 0) ? 0 : 1;
			long total = 8L + chunkSize + pad;
			if (chunkSize < 0 || i + total > len) {
				throw new ImageUploadService.UnsupportedImageException();
			}

			boolean drop = fourCC.equals("EXIF") || fourCC.equals("XMP ");
			if (!drop) {
				if (fourCC.equals("VP8X") && chunkSize >= 1) {
					byte[] chunkBytes = new byte[(int) total];
					System.arraycopy(in, i, chunkBytes, 0, (int) total);
					// 데이터는 8바이트 헤더(fourCC+size) 뒤 첫 바이트 — 플래그 바이트의
					// EXIF(0x08)·XMP(0x04) 비트를 끈다.
					chunkBytes[8] = (byte) (chunkBytes[8] & ~(0x08 | 0x04));
					body.write(chunkBytes, 0, chunkBytes.length);
				}
				else {
					body.write(in, i, (int) total);
				}
			}
			i += (int) total;
		}

		byte[] bodyBytes = body.toByteArray();
		ByteArrayOutputStream out = new ByteArrayOutputStream(12 + bodyBytes.length);
		out.write('R');
		out.write('I');
		out.write('F');
		out.write('F');
		long riffSize = 4L + bodyBytes.length; // "WEBP" + 청크들
		out.write((int) (riffSize & 0xFF));
		out.write((int) ((riffSize >> 8) & 0xFF));
		out.write((int) ((riffSize >> 16) & 0xFF));
		out.write((int) ((riffSize >> 24) & 0xFF));
		out.write('W');
		out.write('E');
		out.write('B');
		out.write('P');
		out.write(bodyBytes, 0, bodyBytes.length);
		return out.toByteArray();
	}
}
