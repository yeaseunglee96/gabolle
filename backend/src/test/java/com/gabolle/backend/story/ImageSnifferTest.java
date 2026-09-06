package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.gabolle.backend.story.application.ImageUploadService;
import com.gabolle.backend.story.image.ImageFormat;
import com.gabolle.backend.story.image.ImageSniffer;

/** {@link ImageSniffer} 는 매직 바이트로만 판단한다 — 이름·헤더는 안 본다. */
class ImageSnifferTest {

	@Test
	void detectsJpegByMagicBytes() {
		byte[] bytes = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10 };

		assertThat(ImageSniffer.sniff(bytes)).isEqualTo(ImageFormat.JPEG);
	}

	@Test
	void detectsPngByMagicBytes() {
		byte[] bytes = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

		assertThat(ImageSniffer.sniff(bytes)).isEqualTo(ImageFormat.PNG);
	}

	@Test
	void detectsWebpByMagicBytes() {
		byte[] bytes = new byte[20];
		bytes[0] = 'R';
		bytes[1] = 'I';
		bytes[2] = 'F';
		bytes[3] = 'F';
		bytes[8] = 'W';
		bytes[9] = 'E';
		bytes[10] = 'B';
		bytes[11] = 'P';

		assertThat(ImageSniffer.sniff(bytes)).isEqualTo(ImageFormat.WEBP);
	}

	@Test
	void rejectsTextFileDisguisedWithJpgExtensionContent() {
		byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> ImageSniffer.sniff(bytes))
				.isInstanceOf(ImageUploadService.UnsupportedImageException.class);
	}

	@Test
	void rejectsEmptyByteArray() {
		assertThatThrownBy(() -> ImageSniffer.sniff(new byte[0]))
				.isInstanceOf(ImageUploadService.UnsupportedImageException.class);
	}
}
